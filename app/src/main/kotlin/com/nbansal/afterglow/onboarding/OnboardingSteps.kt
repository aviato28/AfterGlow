package com.nbansal.afterglow.onboarding

import com.nbansal.afterglow.apps.AppEntry
import com.nbansal.afterglow.data.Favourites

enum class OnboardingStep { Welcome, Apps, Order, Updates, Home, SpeedUp, Done }

/** Step order and skip rules of the first-run walk-through (pure, unit-tested). */
object OnboardingSteps {
    val first = OnboardingStep.Welcome

    /** Next step; skipping Apps also skips Order, since nothing was picked to order. */
    fun next(step: OnboardingStep, skip: Boolean = false): OnboardingStep = when {
        step == OnboardingStep.Apps && skip -> OnboardingStep.Updates
        step == OnboardingStep.Done -> OnboardingStep.Done
        else -> OnboardingStep.entries[step.ordinal + 1]
    }

    /** Back never goes before Welcome; from Updates it returns to Order only if apps were picked. */
    fun back(step: OnboardingStep, pickedApps: Boolean = true): OnboardingStep = when {
        step == OnboardingStep.Welcome -> OnboardingStep.Welcome
        step == OnboardingStep.Updates && !pickedApps -> OnboardingStep.Apps
        else -> OnboardingStep.entries[step.ordinal - 1]
    }

    /** Ticking adds to the end, unticking removes: the list is the order the user ticked in. */
    fun toggle(ticked: List<String>, pkg: String): List<String> = if (pkg in ticked) ticked - pkg else ticked + pkg

    /** Ticked apps that are still installed, in ticked order, without repeats. */
    fun favouritesFrom(ticked: List<String>, installed: List<AppEntry>): List<String> {
        val have = installed.map { it.pkg }.toSet()
        return ticked.filter { it in have }.distinct()
    }

    fun moveEarlier(list: List<String>, pkg: String) = Favourites.moveEarlier(list, pkg)
    fun moveLater(list: List<String>, pkg: String) = Favourites.moveLater(list, pkg)
}
