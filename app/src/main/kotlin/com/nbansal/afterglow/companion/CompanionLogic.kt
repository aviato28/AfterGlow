package com.nbansal.afterglow.companion

import com.nbansal.afterglow.takeover.AdbWhitelist
import java.security.MessageDigest
import java.security.SecureRandom

/** Which clients may talk to the companion: private home-network addresses only (unit-tested). */
object LanFilter {
    /** [address] is the text of the client's IPv4 or IPv6 address. IPv4-mapped IPv6 (`::ffff:a.b.c.d`) is unwrapped. */
    fun allowed(address: String): Boolean {
        val a = address.substringBefore('%').lowercase().removePrefix("::ffff:")
        if (':' in a) return a.startsWith("fe80:") || a.startsWith("fc") || a.startsWith("fd") || a == "::1"
        val p = a.split('.')
        if (p.size != 4) return false
        val n = p.map { it.toIntOrNull() ?: return false }
        if (n.any { it !in 0..255 }) return false
        return n[0] == 10 ||
            (n[0] == 172 && n[1] in 16..31) ||
            (n[0] == 192 && n[1] == 168) ||
            (n[0] == 169 && n[1] == 254)
    }
}

/** The keys the phone page may press. */
object CompanionKeys {
    const val Panel = "panel"
    private val Codes = mapOf(
        "up" to 19, "down" to 20, "left" to 21, "right" to 22, "ok" to 23,
        "back" to 4, "home" to 3, "del" to 67, "enter" to 66,
    )

    /** adb command for [key], or null for `panel` and unknown names. */
    fun command(key: String): String? = Codes[key]?.let { AdbWhitelist.keyEvent(it) }

    fun known(key: String) = key == Panel || key in Codes
}

/** Typing from the phone: ASCII only, escaped for `input text`, sent in small chunks (the TV keyboard drops fast input). */
object CompanionText {
    const val ChunkChars = 8
    const val ChunkDelayMs = 150L

    class Plan(val commands: List<String>, val skipped: Int)

    /** Printable ASCII except `%` (it can clash with the `%s` space code). */
    fun typeable(c: Char) = c.code in 0x20..0x7E && c != '%'

    /** One typeable character as it appears in the command: letters and digits as they are, space as `%s`, the rest backslash-escaped. */
    fun escape(c: Char): String = when {
        c == ' ' -> "%s"
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' -> c.toString()
        else -> "\\" + c
    }

    fun plan(text: String): Plan {
        val ok = text.filter { typeable(it) }
        val commands = ok.chunked(ChunkChars).map { chunk -> AdbWhitelist.inputText(chunk.map { escape(it) }.joinToString("")) }
        return Plan(commands, text.length - ok.length)
    }
}

/** Pairing PIN and token helpers. [now] and [random] are injectable for tests. */
class Pairing(private val now: () -> Long = System::currentTimeMillis, private val random: SecureRandom = SecureRandom()) {
    private var pin: String? = null
    private var pinAtMs = 0L
    private var wrong = 0

    /** New random 4-digit PIN, valid 10 minutes; replaces the old one. */
    @Synchronized
    fun newPin(): String {
        val p = random.nextInt(10_000).toString().padStart(4, '0')
        pin = p
        pinAtMs = now()
        wrong = 0
        return p
    }

    /** The pairing screen closed: nobody can pair any more. */
    @Synchronized
    fun clearPin() { pin = null }

    enum class Result { Ok, BadPin, Expired, TooMany }

    /** Checks [attempt] once. A good PIN is used up; after [MaxWrong] wrong tries the PIN is withdrawn. */
    @Synchronized
    fun check(attempt: String): Result {
        val p = pin ?: return Result.Expired
        if (now() - pinAtMs > PinValidMs) { pin = null; return Result.Expired }
        if (!constantTimeEquals(p, attempt)) {
            wrong++
            if (wrong >= MaxWrong) { pin = null; return Result.TooMany }
            return Result.BadPin
        }
        pin = null
        return Result.Ok
    }

    fun newToken(): String = ByteArray(32).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    companion object {
        const val PinValidMs = 10 * 60 * 1000L
        const val MaxWrong = 5
        const val MaxPhones = 5

        fun constantTimeEquals(a: String, b: String): Boolean =
            MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

        /** [header] is the whole Authorization header value. */
        fun tokenValid(header: String?, tokens: List<String>): Boolean {
            val t = header?.takeIf { it.startsWith("Bearer ") }?.substring(7)?.trim().orEmpty()
            if (t.length != 64) return false
            var ok = false
            for (k in tokens) if (constantTimeEquals(k, t)) ok = true
            return ok
        }

        /** Adds [token], keeping the newest [MaxPhones]. */
        fun addToken(tokens: List<String>, token: String): List<String> = (tokens + token).takeLast(MaxPhones)
    }
}

/** Reordering favourites from the phone (unit-tested). */
object CompanionFavourites {
    /**
     * [all] is the stored list (it can hold hidden or uninstalled apps), [shown] the ones the phone sees, [requested] the new order of those.
     * [requested] must hold exactly the shown apps; they take the slots the shown apps had, everything else stays where it is. Null if invalid.
     */
    fun reorder(all: List<String>, shown: List<String>, requested: List<String>): List<String>? {
        if (requested.size != shown.size || requested.toSet().size != requested.size || requested.toSet() != shown.toSet()) return null
        val shownSet = shown.toSet()
        val next = requested.iterator()
        return all.map { p -> if (p in shownSet) next.next() else p }
    }
}
