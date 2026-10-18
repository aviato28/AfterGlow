package com.nbansal.afterglow

import com.nbansal.afterglow.update.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateTest {
    @Test
    fun versionCompare() {
        assertTrue(Versions.isNewer("v0.10.0", "0.9.0"))
        assertFalse(Versions.isNewer("v0.9.0", "0.10.0"))
        assertFalse(Versions.isNewer("v0.2.0", "0.2.0"))
        assertTrue(Versions.isNewer("0.2.1", "0.2.0-dev"))
        assertTrue(Versions.isNewer("v1.0", "0.9.9"))
    }

    @Test
    fun prereleaseTagsIgnored() {
        assertNull(Versions.parseRelease("v1.0.0-beta"))
        assertFalse(Versions.isNewer("v9.0.0-rc1", "0.1.0"))
        assertEquals(listOf(0, 3, 0), Versions.parseRelease("v0.3.0"))
    }

    @Test
    fun checksum() {
        val f = File.createTempFile("afterglow", ".apk").apply { writeText("hello"); deleteOnExit() }
        val hex = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertTrue(Versions.checksumMatches(f, "$hex  afterglow-0.2.0.apk\n"))
        assertTrue(Versions.checksumMatches(f, hex.uppercase()))
        assertFalse(Versions.checksumMatches(f, "0".repeat(64)))
        assertFalse(Versions.checksumMatches(f, ""))
    }

    @Test
    fun throttle() {
        val h = 60 * 60 * 1000L
        assertTrue(Versions.shouldCheck(0, 100 * h))
        assertFalse(Versions.shouldCheck(100 * h, 111 * h))
        assertTrue(Versions.shouldCheck(100 * h, 112 * h))
        assertTrue(Versions.shouldCheck(200 * h, 100 * h)) // clock went backwards
    }
}
