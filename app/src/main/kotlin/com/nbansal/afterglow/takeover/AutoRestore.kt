package com.nbansal.afterglow.takeover

/** Crash-loop recovery: gives Home back to Google TV with the existing [TakeoverFlow.restore] (no new adb commands). */
object AutoRestore {
    const val FILE_NAME = "auto-restore.txt"
    const val LAST_ATTEMPT_FILE = "auto-restore-last.txt"
    const val MIN_GAP_MS = 10 * 60_000L
    const val TIMEOUT_MS = 10_000L

    class Outcome(val restored: Boolean, val text: String)

    /** Never more than one attempt per [MIN_GAP_MS]. */
    fun allowed(lastAttemptMs: Long?, nowMs: Long): Boolean = lastAttemptMs == null || nowMs - lastAttemptMs !in 0 until MIN_GAP_MS

    /**
     * Null = nothing done (guard hit, or no saved packages). Otherwise one restore attempt; [markAttempt] is called before it
     * so even a hang cannot cause a second attempt within the gap. adb trouble is returned as text, never thrown, never retried.
     */
    suspend fun run(
        flow: TakeoverFlow,
        store: DisabledStore,
        lastAttemptMs: Long?,
        nowMs: Long,
        markAttempt: () -> Unit,
    ): Outcome? {
        if (!allowed(lastAttemptMs, nowMs)) return null
        if (store.get().isEmpty()) return null
        markAttempt()
        return try {
            when (val r = flow.restore()) {
                is FlowResult.Done -> Outcome(true, r.message)
                is FlowResult.Stopped -> Outcome(false, r.message)
            }
        } catch (e: AdbException) {
            Outcome(false, "The TV's debugging port was not reachable (${e.kind}).")
        }
    }
}

/** Has a TV update undone the takeover? Pure logic, unit-tested with fakes. */
object FirmwareCheck {
    const val DAY_MS = 24 * 60 * 60_000L

    fun checkDue(lastCheckMs: Long, nowMs: Long) = nowMs - lastCheckMs !in 0 until DAY_MS

    /** Only meaningful after a takeover (saved list not empty): a switched-off package is on again, or Home is no longer Afterglow. */
    fun cameBack(disabledHomes: Set<String>, isEnabled: (String) -> Boolean, homePackage: String?, self: String): Boolean =
        disabledHomes.isNotEmpty() && (disabledHomes.any(isEnabled) || homePackage != self)

    /** Ask when flagged, and not already asked ("Not now") within the last day. */
    fun promptDue(flag: Boolean, lastPromptMs: Long, nowMs: Long) = flag && nowMs - lastPromptMs !in 0 until DAY_MS

    fun isEnabled(pm: android.content.pm.PackageManager, pkg: String): Boolean = try {
        val s = pm.getApplicationEnabledSetting(pkg)
        s == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            s == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    } catch (_: IllegalArgumentException) {
        false
    }
}
