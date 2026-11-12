package com.nbansal.afterglow

import com.nbansal.afterglow.companion.BackupCodec
import com.nbansal.afterglow.companion.CompanionFavourites
import com.nbansal.afterglow.companion.CompanionKeys
import com.nbansal.afterglow.companion.CompanionNet
import com.nbansal.afterglow.companion.CompanionText
import com.nbansal.afterglow.companion.Http
import com.nbansal.afterglow.companion.HttpRefused
import com.nbansal.afterglow.companion.LanFilter
import com.nbansal.afterglow.companion.Pairing
import com.nbansal.afterglow.data.PrefState
import com.nbansal.afterglow.takeover.AdbWhitelist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class CompanionTest {
    // ---- LAN filter ----
    @Test
    fun lanFilterAllowsOnlyPrivateRanges() {
        listOf(
            "10.0.0.5", "10.255.255.255", "172.16.0.1", "172.31.255.1", "192.168.1.20", "169.254.3.4",
            "::ffff:192.168.1.5", "fe80::1%wlan0", "fd12:3456::1", "fc00::1",
        ).forEach { assertTrue(it, LanFilter.allowed(it)) }
        listOf(
            "8.8.8.8", "172.32.0.1", "172.15.0.1", "192.169.1.1", "192.167.1.1", "11.0.0.1", "127.0.0.1", "0.0.0.0",
            "10.0.0.256", "10.0.0", "10.0.0.1.1", "a.b.c.d", "", "2001:db8::1", "::ffff:8.8.8.8", "-1.0.0.1",
        ).forEach { assertFalse("'$it'", LanFilter.allowed(it)) }
    }

    // ---- pairing ----
    @Test
    fun wrongPinThenRightPinThenUsedUp() {
        val p = Pairing()
        val pin = p.newPin()
        assertEquals(4, pin.length)
        assertTrue(pin.all { it.isDigit() })
        val wrong = if (pin == "0000") "1111" else "0000"
        assertEquals(Pairing.Result.BadPin, p.check(wrong))
        assertEquals(Pairing.Result.Ok, p.check(pin))
        assertEquals(Pairing.Result.Expired, p.check(pin)) // one-time
    }

    @Test
    fun noPinMeansNobodyCanPair() {
        val p = Pairing()
        assertEquals(Pairing.Result.Expired, p.check("1234"))
        val pin = p.newPin()
        p.clearPin()
        assertEquals(Pairing.Result.Expired, p.check(pin))
    }

    @Test
    fun pinExpiresAfterTenMinutes() {
        var t = 5L
        val p = Pairing({ t })
        val pin = p.newPin()
        t = 5L + Pairing.PinValidMs + 1
        assertEquals(Pairing.Result.Expired, p.check(pin))
        // exactly ten minutes is still fine
        t = 100L
        val pin2 = p.newPin()
        t = 100L + Pairing.PinValidMs
        assertEquals(Pairing.Result.Ok, p.check(pin2))
    }

    @Test
    fun fiveWrongTriesWithdrawThePin() {
        val p = Pairing()
        val pin = p.newPin()
        val wrong = if (pin == "0000") "1111" else "0000"
        repeat(4) { assertEquals(Pairing.Result.BadPin, p.check(wrong)) }
        assertEquals(Pairing.Result.TooMany, p.check(wrong))
        assertEquals(Pairing.Result.Expired, p.check(pin)) // even the right PIN no longer works
    }

    @Test
    fun tokensAreChecked() {
        val p = Pairing()
        val a = p.newToken()
        val b = p.newToken()
        assertEquals(64, a.length)
        assertTrue(a.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(a, b)
        assertTrue(Pairing.tokenValid("Bearer $a", listOf(a, b)))
        assertTrue(Pairing.tokenValid("Bearer $b", listOf(a, b)))
        assertFalse(Pairing.tokenValid("Bearer $b", listOf(a)))
        assertFalse(Pairing.tokenValid(null, listOf(a)))
        assertFalse(Pairing.tokenValid("", listOf(a)))
        assertFalse(Pairing.tokenValid(a, listOf(a)))
        assertFalse(Pairing.tokenValid("Basic $a", listOf(a)))
        assertFalse(Pairing.tokenValid("Bearer ${a.take(63)}", listOf(a)))
        assertFalse(Pairing.tokenValid("Bearer $a", emptyList()))
    }

    @Test
    fun atMostFivePhonesKeepTheNewest() {
        val list = (1..7).fold(emptyList<String>()) { acc, i -> Pairing.addToken(acc, "t$i") }
        assertEquals(listOf("t3", "t4", "t5", "t6", "t7"), list)
        assertEquals(listOf("a", "b"), Pairing.addToken(listOf("a"), "b"))
    }

    // ---- text and keys ----
    @Test
    fun textIsEscapedAndSentInChunksOfEight() {
        val plan = CompanionText.plan("hello world")
        assertEquals(listOf("input text hello%swo", "input text rld"), plan.commands)
        assertEquals(0, plan.skipped)
        assertEquals(listOf("input text it\\'s"), CompanionText.plan("it's").commands)
        assertEquals(listOf("input text a\\;b\\\\c"), CompanionText.plan("a;b\\c").commands)
        assertEquals(3, CompanionText.plan("a".repeat(17)).commands.size)
        assertTrue(CompanionText.plan("").commands.isEmpty())
    }

    @Test
    fun nonAsciiAndPercentAreSkippedAndCounted() {
        val plan = CompanionText.plan("héllo%\n")
        assertEquals(listOf("input text hllo"), plan.commands)
        assertEquals(3, plan.skipped)
        assertEquals(2, CompanionText.plan("éé").skipped)
        assertTrue(CompanionText.plan("é").commands.isEmpty())
    }

    @Test
    fun everyTypeableCharacterProducesAnAllowedCommand() {
        val all = (0x20..0x7E).map { it.toChar() }.joinToString("")
        val plan = CompanionText.plan(all)
        assertEquals(1, plan.skipped) // only the percent sign
        assertTrue(plan.commands.isNotEmpty())
        plan.commands.forEach { assertTrue(it, AdbWhitelist.allows(it)) }
    }

    @Test
    fun keysMapToTheWhitelistedKeyEvents() {
        assertEquals("input keyevent 19", CompanionKeys.command("up"))
        assertEquals("input keyevent 20", CompanionKeys.command("down"))
        assertEquals("input keyevent 21", CompanionKeys.command("left"))
        assertEquals("input keyevent 22", CompanionKeys.command("right"))
        assertEquals("input keyevent 23", CompanionKeys.command("ok"))
        assertEquals("input keyevent 4", CompanionKeys.command("back"))
        assertEquals("input keyevent 3", CompanionKeys.command("home"))
        assertEquals("input keyevent 67", CompanionKeys.command("del"))
        assertEquals("input keyevent 66", CompanionKeys.command("enter"))
        listOf("up", "down", "left", "right", "ok", "back", "home", "del", "enter").forEach {
            assertTrue(AdbWhitelist.allows(CompanionKeys.command(it)!!))
        }
        assertNull(CompanionKeys.command("panel"))
        assertTrue(CompanionKeys.known("panel"))
        assertNull(CompanionKeys.command("power"))
        assertFalse(CompanionKeys.known("power"))
        assertFalse(CompanionKeys.known("up; reboot"))
        try {
            AdbWhitelist.keyEvent(26)
            fail("should reject")
        } catch (_: IllegalArgumentException) {
        }
    }

    // ---- favourites order ----
    @Test
    fun reorderKeepsHiddenAppsInPlace() {
        val all = listOf("a.a", "b.b", "c.c", "d.d")
        val shown = listOf("a.a", "c.c", "d.d")
        assertEquals(listOf("d.d", "b.b", "a.a", "c.c"), CompanionFavourites.reorder(all, shown, listOf("d.d", "a.a", "c.c")))
        assertNull(CompanionFavourites.reorder(all, shown, listOf("a.a", "c.c")))
        assertNull(CompanionFavourites.reorder(all, shown, listOf("a.a", "a.a", "c.c")))
        assertNull(CompanionFavourites.reorder(all, shown, listOf("a.a", "c.c", "x.x")))
    }

    // ---- backup ----
    private val key = "0123456789abcdef0123456789abcdef"
    private val state = PrefState(
        favourites = listOf("com.a.b", "com.c.d"),
        hidden = setOf("com.e.f"),
        names = mapOf("com.a.b" to "Netflix"),
        buttons = mapOf(131 to "com.a.b"),
        userName = "Sam",
        showcase = true,
        openWith = "com.c.d",
        tmdbKey = key,
        // takeover state and the quick-panel switch: never in a backup
        disabledHomes = setOf("com.x.y"),
        disabledExtras = setOf("com.p.q"),
        saverPrevious = "saver.prev/Thing",
        quickPanel = true,
        onboarded = true,
        firmwareFlag = true,
    )

    @Test
    fun backupRoundTripWithoutKey() {
        val json = BackupCodec.encode(BackupCodec.of(state, withKey = false))
        val b = BackupCodec.decode(json)!!
        assertEquals(listOf("com.a.b", "com.c.d"), b.favourites)
        assertEquals(setOf("com.e.f"), b.hidden)
        assertEquals(mapOf("com.a.b" to "Netflix"), b.names)
        assertEquals(mapOf(131 to "com.a.b"), b.buttons)
        assertEquals("Sam", b.userName)
        assertTrue(b.showcase)
        assertEquals("com.c.d", b.openWith)
        assertNull(b.tmdbKey)
        assertFalse(json.contains(key))
    }

    @Test
    fun backupHoldsNoTakeoverState() {
        val json = BackupCodec.encode(BackupCodec.of(state, withKey = true))
        assertFalse(json.contains("com.x.y"))
        assertFalse(json.contains("com.p.q"))
        assertFalse(json.contains("saver"))
        assertFalse(json.contains("disabled"))
        assertFalse(json.contains("quickPanel"))
        assertEquals(key, BackupCodec.decode(json)!!.tmdbKey)
    }

    @Test
    fun restoreRejectsAnythingInvalid() {
        val ok = """{"app":"afterglow","version":1}"""
        val b = BackupCodec.decode(ok)!!
        assertNull(b.favourites)
        assertNull(b.tmdbKey)
        assertTrue(b.names.isEmpty())
        listOf(
            "not json", "", "[]", """{"app":"afterglow","version":2}""", """{"app":"other","version":1}""", """{"version":1}""",
            """{"app":"afterglow","version":1,"favourites":["rm -rf /"]}""",
            """{"app":"afterglow","version":1,"favourites":[1,2]}""",
            """{"app":"afterglow","version":1,"hidden":["nodots"]}""",
            """{"app":"afterglow","version":1,"names":{"com.a.b":"  "}}""",
            """{"app":"afterglow","version":1,"names":{"bad name":"x"}}""",
            """{"app":"afterglow","version":1,"buttons":{"4":"com.a.b"}}""",
            """{"app":"afterglow","version":1,"buttons":{"x":"com.a.b"}}""",
            """{"app":"afterglow","version":1,"tmdbKey":"short"}""",
            """{"app":"afterglow","version":1,"openWith":"a b"}""",
            """{"app":"afterglow","version":1,"userName":"this name is much too long for a greeting"}""",
        ).forEach { assertNull("'$it'", BackupCodec.decode(it)) }
    }

    // ---- http ----
    private fun parse(raw: String) = Http.parse(ByteArrayInputStream(raw.toByteArray()))

    private fun refusedWith(raw: String): Int {
        try {
            parse(raw)
        } catch (e: HttpRefused) {
            return e.status
        }
        fail("should be refused")
        return 0
    }

    @Test
    fun parsesARequestWithBodyAndQuery() {
        val body = """{"a":1}"""
        val r = parse(
            "POST /api/text?x=1&y=a%20b HTTP/1.1\r\nHost: tv\r\nContent-Length: ${body.length}\r\nAuthorization: Bearer abc\r\n\r\n$body",
        )
        assertEquals("POST", r.method)
        assertEquals("/api/text", r.path)
        assertEquals("1", r.query["x"])
        assertEquals("a b", r.query["y"])
        assertEquals("Bearer abc", r.headers["authorization"])
        assertEquals(body, r.body)
        assertEquals("", parse("GET / HTTP/1.1\r\n\r\n").body)
    }

    @Test
    fun refusesBadOrHugeRequests() {
        assertEquals(413, refusedWith("POST /api/restore HTTP/1.1\r\nContent-Length: 70000\r\n\r\n"))
        assertEquals(400, refusedWith("GARBAGE\r\n\r\n"))
        assertEquals(400, refusedWith("POST /x HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"))
        assertEquals(400, refusedWith("POST /x HTTP/1.1\r\nContent-Length: 10\r\n\r\nabc"))
        assertEquals(400, refusedWith("GET http://evil/ HTTP/1.1\r\n\r\n"))
    }

    @Test
    fun pairingUrlHasTheAddressAndPin() {
        assertEquals("http://192.168.1.9:8765/?pin=4821", CompanionNet.url("192.168.1.9", "4821"))
    }
}
