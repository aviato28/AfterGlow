package com.nbansal.afterglow.companion

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class HttpRequest(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: String)

/** A request the server refuses before it reaches the API (too big, or not HTTP). */
class HttpRefused(val status: Int, message: String) : IOException(message)

/** Just enough HTTP/1.1 for the phone page: one request per connection, body only with Content-Length (unit-tested). */
object Http {
    const val MaxBody = 64 * 1024
    private const val MaxLine = 8 * 1024
    private const val MaxHeaders = 64

    fun parse(input: InputStream): HttpRequest {
        val first = readLine(input) ?: throw HttpRefused(400, "Empty request")
        val parts = first.split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) throw HttpRefused(400, "Bad request line")
        val target = parts[1]
        if (!target.startsWith("/")) throw HttpRefused(400, "Bad target")
        val headers = HashMap<String, String>()
        for (n in 0..MaxHeaders) {
            val line = readLine(input) ?: throw HttpRefused(400, "Cut-off headers")
            if (line.isEmpty()) break
            if (n == MaxHeaders) throw HttpRefused(431, "Too many headers")
            val i = line.indexOf(':')
            if (i <= 0) throw HttpRefused(400, "Bad header")
            headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        if (headers.containsKey("transfer-encoding")) throw HttpRefused(400, "Chunked bodies are not supported")
        val length = headers["content-length"]?.let { it.toIntOrNull() ?: throw HttpRefused(400, "Bad length") } ?: 0
        if (length < 0) throw HttpRefused(400, "Bad length")
        if (length > MaxBody) throw HttpRefused(413, "Request too large")
        val body = ByteArray(length)
        var got = 0
        while (got < length) {
            val n = input.read(body, got, length - got)
            if (n < 0) throw HttpRefused(400, "Cut-off body")
            got += n
        }
        val path = target.substringBefore('?')
        return HttpRequest(parts[0], path, parseQuery(target.substringAfter('?', "")), headers, String(body, Charsets.UTF_8))
    }

    fun parseQuery(q: String): Map<String, String> {
        if (q.isEmpty()) return emptyMap()
        val out = HashMap<String, String>()
        for (pair in q.split('&')) {
            if (pair.isEmpty()) continue
            val k = pair.substringBefore('=')
            val v = pair.substringAfter('=', "")
            out[decode(k)] = decode(v)
        }
        return out
    }

    private fun decode(s: String) = try { URLDecoder.decode(s, "UTF-8") } catch (_: IllegalArgumentException) { s }

    /** One line without its CR/LF; null at end of stream before any byte. */
    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > MaxLine) throw HttpRefused(431, "Line too long")
        }
        return buf.toString("UTF-8")
    }

    fun write(out: OutputStream, status: Int, type: String, body: ByteArray) {
        val reason = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"
            405 -> "Method Not Allowed"; 409 -> "Conflict"; 410 -> "Gone"; 413 -> "Payload Too Large"
            429 -> "Too Many Requests"; 431 -> "Request Header Fields Too Large"; 502 -> "Bad Gateway"; else -> "Error"
        }
        val head = "HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }
}

/** The phone companion's web server: plain sockets, port 8765, two worker threads, home-network clients only. */
class CompanionServer(private val api: CompanionApi) {
    @Volatile private var socket: ServerSocket? = null
    private var pool: ThreadPoolExecutor? = null

    val running: Boolean get() = socket?.isClosed == false

    /** False when the port could not be opened (another copy running, or no network). */
    @Synchronized
    fun start(): Boolean {
        if (running) return true
        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(Port))
            }
        } catch (_: IOException) {
            return false
        }
        socket = server
        pool = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue(16), ThreadPoolExecutor.AbortPolicy())
        Thread({ acceptLoop(server) }, "companion-accept").apply { isDaemon = true }.start()
        return true
    }

    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        socket = null
        pool?.shutdownNow()
        pool = null
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val client = try { server.accept() } catch (_: IOException) { break }
            try {
                client.soTimeout = 10_000
                if (!LanFilter.allowed(client.inetAddress.hostAddress.orEmpty())) {
                    refuse(client, 403, "Only devices on the home network may connect")
                    continue
                }
                val p = pool ?: break
                p.execute { serve(client) }
            } catch (_: RejectedExecutionException) {
                refuse(client, 429, "Busy")
            } catch (_: Exception) {
                runCatching { client.close() }
            }
        }
    }

    private fun refuse(client: Socket, status: Int, message: String) {
        runCatching {
            client.use { Http.write(it.getOutputStream(), status, "text/plain; charset=utf-8", message.toByteArray()) }
        }
    }

    private fun serve(client: Socket) {
        client.use {
            val out = it.getOutputStream()
            try {
                val req = Http.parse(it.getInputStream())
                val reply = runBlocking { api.handle(req.method, req.path, req.query, req.headers["authorization"], req.body) }
                Http.write(out, reply.status, reply.type, reply.body)
            } catch (e: HttpRefused) {
                runCatching { Http.write(out, e.status, "text/plain; charset=utf-8", (e.message ?: "Refused").toByteArray()) }
            } catch (_: Exception) {
                runCatching { Http.write(out, 500, "text/plain; charset=utf-8", "Error".toByteArray()) }
            }
        }
    }

    companion object {
        const val Port = 8765
    }
}

/** The TV's own address on the home network, for the QR code. */
object CompanionNet {
    fun ipv4(context: android.content.Context): String? {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        val fromNetwork = runCatching {
            cm?.getLinkProperties(cm.activeNetwork)?.linkAddresses
                ?.map { it.address }
                ?.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
        if (fromNetwork != null) return fromNetwork
        return runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }

    fun url(ip: String, pin: String) = "http://$ip:${CompanionServer.Port}/?pin=$pin"
}
