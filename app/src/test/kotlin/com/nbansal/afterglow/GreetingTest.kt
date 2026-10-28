package com.nbansal.afterglow

import com.nbansal.afterglow.home.Greeting
import com.nbansal.afterglow.home.NAME_MAX
import com.nbansal.afterglow.home.cleanName
import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
    @Test
    fun withoutNameIsJustTheGreeting() {
        assertEquals("Good evening", Greeting.Evening.line(""))
        assertEquals("Good evening", Greeting.Evening.line("   "))
    }

    @Test
    fun withNameAddsItAfterAComma() {
        assertEquals("Good evening, Naman", Greeting.Evening.line("Naman"))
        assertEquals("Good morning, Naman", Greeting.Morning.line("  Naman "))
    }

    @Test
    fun nameIsLimitedToTwentyCharacters() {
        assertEquals(20, NAME_MAX)
        assertEquals("a".repeat(20), cleanName("a".repeat(30)))
        assertEquals("", cleanName("  "))
        assertEquals("Good night, " + "b".repeat(20), Greeting.Night.line("b".repeat(25)))
    }
}
