package com.nbansal.afterglow.panel

import android.service.notification.NotificationListenerService

/**
 * Does nothing with notifications. Android only lets an app call `MediaSessionManager.getActiveSessions` when it holds an enabled
 * notification listener, so Quick panel grants this one over adb (and removes it again when switched off).
 */
class NowPlayingListener : NotificationListenerService()
