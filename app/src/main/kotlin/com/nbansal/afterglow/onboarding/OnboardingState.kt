package com.nbansal.afterglow.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Where the walk-through is and which apps are ticked; the apps become favourites when the Apps step is left. */
class OnboardingState(private val onPicked: (List<String>) -> Unit) {
    var step by mutableStateOf(OnboardingSteps.first); private set
    var ticked by mutableStateOf<List<String>>(emptyList()); private set

    fun restart(initial: List<String>) { ticked = initial; step = OnboardingSteps.first }

    fun toggle(pkg: String) { ticked = OnboardingSteps.toggle(ticked, pkg) }

    fun move(pkg: String, delta: Int) {
        ticked = if (delta < 0) OnboardingSteps.moveEarlier(ticked, pkg) else OnboardingSteps.moveLater(ticked, pkg)
    }

    /** Leaving Order (Next) saves the ticked apps as favourites; with nothing ticked the favourites stay as they are. */
    fun next() {
        step = when {
            step == OnboardingStep.Apps && ticked.isEmpty() -> OnboardingStep.Updates
            step == OnboardingStep.Order -> { onPicked(ticked); OnboardingSteps.next(step) }
            else -> OnboardingSteps.next(step)
        }
    }

    /** Skip leaves the favourites untouched. */
    fun skip() { step = OnboardingSteps.next(step, skip = true) }

    fun back() { step = OnboardingSteps.back(step, pickedApps = ticked.isNotEmpty()) }
}
