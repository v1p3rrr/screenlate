package com.vpr.screenlate.overlay

/**
 * What the check [ScreenlateAccessibilityService] runs a few seconds after a stop does about the "service is off"
 * notification. UiAutomation clients such as `uiautomator dump` pause accessibility services for about 1.5 s, and the
 * system binds the service again in the same process; a notification at the stop itself would flash for each of them.
 */
internal enum class StopFollowUp {
    /** The service has not connected again: show the notification. */
    NOTIFY,

    /** The service connected again since the stop, even if it stopped once more: nothing to tell. */
    BACK,

    /** The service has not connected again, but notifications are not allowed: only a log line. */
    LOG_ONLY,
    ;

    companion object {
        /** [connectionsAtStop] and [connectionsNow] count the service's connections in this process. */
        fun after(connectionsAtStop: Int, connectionsNow: Int, notificationsAllowed: Boolean): StopFollowUp = when {
            connectionsNow != connectionsAtStop -> BACK
            notificationsAllowed -> NOTIFY
            else -> LOG_ONLY
        }
    }
}
