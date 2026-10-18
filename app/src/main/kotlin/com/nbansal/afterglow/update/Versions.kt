package com.nbansal.afterglow.update

import java.io.File
import java.security.MessageDigest

/** Pure update rules (unit-tested). */
object Versions {
    const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

    /** `v0.10.0` -> [0,10,0]; null for pre-release tags (`v1.0.0-beta`) and anything that is not numeric semver. */
    fun parseRelease(tag: String): List<Int>? {
        val t = tag.trim().removePrefix("v").removePrefix("V")
        if (!Regex("\\d+(\\.\\d+){0,2}").matches(t)) return null
        return t.split('.').map { it.toInt() }
    }

    /** The running build's own name may carry a suffix (`0.0.1-dev`): compare the numeric part only. */
    private fun parseCurrent(name: String): List<Int> =
        Regex("\\d+(\\.\\d+){0,2}").find(name)?.value?.split('.')?.map { it.toInt() } ?: listOf(0)

    fun isNewer(latestTag: String, current: String): Boolean {
        val latest = parseRelease(latestTag) ?: return false
        val cur = parseCurrent(current)
        for (i in 0 until maxOf(latest.size, cur.size)) {
            val a = latest.getOrElse(i) { 0 }
            val b = cur.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun shouldCheck(lastCheckMs: Long, nowMs: Long) = nowMs - lastCheckMs >= CHECK_INTERVAL_MS || nowMs < lastCheckMs

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** `.sha256` files hold "<hex>  <name>" or just "<hex>". */
    fun checksumMatches(file: File, sha256Text: String): Boolean {
        val expected = sha256Text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: return false
        return expected.length == 64 && expected == sha256(file)
    }

    fun firstLines(text: String, n: Int = 12): String = text.lines().take(n).joinToString("\n").trim()
}
