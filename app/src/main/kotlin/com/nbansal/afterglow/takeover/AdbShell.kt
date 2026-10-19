package com.nbansal.afterglow.takeover

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/** Output of one shell command on the TV. */
class ShellResult(val exitCode: Int, val output: String)

class AdbException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NotListening, NotAllowed, Rejected, Failed }
}

/** The only commands Afterglow may ever send to the TV's debugging port. */
object AdbWhitelist {
    const val ResolveHome = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    const val QueryHomes = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    const val ListDisabled = "pm list packages -d"
    const val GetScreensaver = "settings get secure screensaver_components"
    const val GetScreensaverEnabled = "settings get secure screensaver_enabled"

    /** One component `pkg/class`, or several separated by commas (that is how the setting stores them), or the word null. */
    private val Components = Regex("null|[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+(,[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+)*")

    fun putScreensaver(value: String): String {
        require(Components.matches(value)) { "bad component" }
        return "settings put secure screensaver_components $value"
    }

    fun putScreensaverEnabled(on: Boolean) = "settings put secure screensaver_enabled ${if (on) 1 else 0}"

    fun allows(command: String): Boolean {
        if (command == ResolveHome || command == QueryHomes || command == ListDisabled ||
            command == GetScreensaver || command == GetScreensaverEnabled) return true
        val parts = command.split(' ')
        return when {
            parts.size == 5 && parts[0] == "pm" && parts[1] == "disable-user" && parts[2] == "--user" && parts[3] == "0" ->
                Takeover.PackageName.matches(parts[4])
            parts.size == 5 && parts[0] == "settings" && parts[1] == "put" && parts[2] == "secure" ->
                (parts[3] == "screensaver_components" && Components.matches(parts[4])) ||
                    (parts[3] == "screensaver_enabled" && (parts[4] == "0" || parts[4] == "1"))
            parts.size == 3 && parts[0] == "pm" && parts[1] == "enable" -> Takeover.PackageName.matches(parts[2])
            else -> false
        }
    }
}

/** Runs whitelisted commands; everything else is refused before it reaches [exec]. */
abstract class Adb {
    suspend fun run(command: String): ShellResult {
        if (!AdbWhitelist.allows(command)) throw AdbException(AdbException.Kind.Rejected, "Command not allowed")
        return exec(command)
    }

    protected abstract suspend fun exec(command: String): ShellResult
}

/** adb over TCP to this TV's own port 5555 (Developer options > USB debugging). */
class AdbShell(private val context: Context) : Adb(), AutoCloseable {
    private var dadb: Dadb? = null

    /** Key already made (the TV has probably been asked "Allow USB debugging?" before). */
    fun hasKey() = File(keyDir(), "adbkey").exists()

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val r = connection().shell(command)
            ShellResult(r.exitCode, r.allOutput)
        } catch (e: AdbException) {
            throw e
        } catch (e: Exception) {
            close()
            throw AdbException(AdbException.Kind.NotAllowed, e.message ?: "No answer from the TV's debugging service")
        }
    }

    private fun connection(): Dadb {
        dadb?.let { return it }
        if (!listening()) throw AdbException(AdbException.Kind.NotListening, "Nothing listens on 127.0.0.1:$Port")
        // The socket timeout also covers the wait while the TV shows "Allow USB debugging?".
        return Dadb.create(Host, Port, keyPair(), ConnectTimeoutMs, AllowWaitMs).also { dadb = it }
    }

    private fun listening(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(Host, Port), ConnectTimeoutMs) }
        true
    } catch (_: Exception) {
        false
    }

    /** Created once; stays private to the app (never logged, never exported). */
    private fun keyPair(): AdbKeyPair {
        val dir = keyDir().apply { mkdirs() }
        val priv = File(dir, "adbkey")
        val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    private fun keyDir() = File(context.filesDir, "adb")

    override fun close() {
        runCatching { dadb?.close() }
        dadb = null
    }

    companion object {
        const val Host = "127.0.0.1"
        const val Port = 5555
        const val ConnectTimeoutMs = 15_000
        const val AllowWaitMs = 30_000

        const val AllowHint = "On the TV press Allow and tick Always allow."
        const val NotListeningHint =
            "The TV's debugging port is off. Turn on Developer options > USB debugging in the TV's settings, then try again."
    }
}
