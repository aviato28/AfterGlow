package com.nbansal.afterglow

import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.onboarding.OnboardingStep.*
import com.nbansal.afterglow.onboarding.OnboardingSteps
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingStepsTest {
    @Test
    fun stepsRunInOrder() {
        var s = OnboardingSteps.first
        val seen = mutableListOf(s)
        while (s != Done) { s = OnboardingSteps.next(s); seen += s }
        assertEquals(listOf(Welcome, Apps, Order, Updates, Home, SpeedUp, Done), seen)
        assertEquals(Done, OnboardingSteps.next(Done))
    }

    @Test
    fun skippingAppsSkipsOrderToo() {
        assertEquals(Updates, OnboardingSteps.next(Apps, skip = true))
        assertEquals(Order, OnboardingSteps.next(Apps))
        assertEquals(Home, OnboardingSteps.next(Updates, skip = true))
    }

    @Test
    fun backStopsAtWelcomeAndHandlesSkippedOrder() {
        assertEquals(Welcome, OnboardingSteps.back(Welcome))
        assertEquals(Apps, OnboardingSteps.back(Order))
        assertEquals(Order, OnboardingSteps.back(Updates, pickedApps = true))
        assertEquals(Apps, OnboardingSteps.back(Updates, pickedApps = false))
        assertEquals(SpeedUp, OnboardingSteps.back(Done))
    }

    @Test
    fun favouritesFollowTickOrder() {
        val installed = listOf("a.a", "b.b", "c.c").map { AppEntry(it, "x", it) }
        var t = emptyList<String>()
        for (p in listOf("c.c", "a.a", "b.b")) t = OnboardingSteps.toggle(t, p)
        assertEquals(listOf("c.c", "a.a", "b.b"), OnboardingSteps.favouritesFrom(t, installed))
        t = OnboardingSteps.toggle(t, "a.a")
        assertEquals(listOf("c.c", "b.b"), t)
        assertEquals(listOf("c.c"), OnboardingSteps.favouritesFrom(listOf("c.c", "gone.x", "c.c"), installed))
        assertEquals(listOf("b.b", "c.c"), OnboardingSteps.moveEarlier(t, "b.b"))
    }
}
