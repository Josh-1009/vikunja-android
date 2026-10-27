package cloud.codella.vikunja.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast

/**
 * Helper for Google Calendar & Android System Calendar Integration.
 */
object GoogleCalendarHelper {

    /**
     * Launches the system/Google Calendar event creation screen with prefilled task title,
     * cleaned description, and start/end time based on task due date.
     */
    fun exportTaskToCalendar(
        context: Context,
        title: String,
        description: String = "",
        dueDateIso: String? = null
    ) {
        try {
            val dueMillis = DateUtils.parseIsoToMillis(dueDateIso) ?: (System.currentTimeMillis() + 3600000L)
            val cleanDesc = DescriptionUtils.cleanSnippet(description)

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                if (cleanDesc.isNotBlank()) {
                    putExtra(CalendarContract.Events.DESCRIPTION, cleanDesc)
                }
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dueMillis)
                // Default 1-hour duration for the task event
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, dueMillis + 3600000L)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open Calendar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Returns the official Vikunja iCal subscription feed URL for the user.
     * Google Calendar supports subscribing to iCal feeds via URL.
     */
    fun getIcalUrl(serverUrl: String): String {
        val base = if (serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) {
            serverUrl.trimEnd('/')
        } else {
            "https://${serverUrl.trimEnd('/')}"
        }
        return "$base/api/v1/user/cal.ics"
    }

    /**
     * Opens Google Calendar in browser where user can add the Vikunja calendar by URL.
     */
    fun openGoogleCalendarWebSettings(context: Context) {
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://calendar.google.com/calendar/r/settings/addbyurl")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
