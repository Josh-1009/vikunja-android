package cloud.codella.vikunja.core.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val displayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun parseIsoToMillis(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return try {
            isoFormat.parse(isoString)?.time
        } catch (e: Exception) {
            try {
                // Try format with milliseconds if any
                val altFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                altFormat.parse(isoString)?.time
            } catch (ex: Exception) {
                null
            }
        }
    }

    fun formatMillisToIso(millis: Long): String {
        return isoFormat.format(Date(millis))
    }

    fun formatDisplayDate(isoString: String?): String {
        val millis = parseIsoToMillis(isoString) ?: return ""
        return displayFormat.format(Date(millis))
    }

    fun formatDisplayDateTime(isoString: String?): String {
        val millis = parseIsoToMillis(isoString) ?: return ""
        val date = Date(millis)
        return "${displayFormat.format(date)} ${timeFormat.format(date)}"
    }

    fun isToday(millis: Long): Boolean {
        val calTask = Calendar.getInstance().apply { timeInMillis = millis }
        val calNow = Calendar.getInstance()
        return calTask.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calTask.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
    }

    fun isUpcoming(millis: Long): Boolean {
        val now = System.currentTimeMillis()
        val calNow = Calendar.getInstance().apply { timeInMillis = now }
        val calTask = Calendar.getInstance().apply { timeInMillis = millis }

        // Future but not today
        return millis > now && (calTask.get(Calendar.YEAR) > calNow.get(Calendar.YEAR) ||
                calTask.get(Calendar.DAY_OF_YEAR) > calNow.get(Calendar.DAY_OF_YEAR))
    }

    fun isOverdue(millis: Long): Boolean {
        return millis < System.currentTimeMillis() && !isToday(millis)
    }

    fun isYesterday(millis: Long): Boolean {
        val calTask = Calendar.getInstance().apply { timeInMillis = millis }
        val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return calTask.get(Calendar.YEAR) == calYesterday.get(Calendar.YEAR) &&
                calTask.get(Calendar.DAY_OF_YEAR) == calYesterday.get(Calendar.DAY_OF_YEAR)
    }

    fun isThisWeek(millis: Long): Boolean {
        val calTask = Calendar.getInstance().apply { timeInMillis = millis }
        val calNow = Calendar.getInstance()
        return calTask.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calTask.get(Calendar.WEEK_OF_YEAR) == calNow.get(Calendar.WEEK_OF_YEAR)
    }

    fun isThisMonth(millis: Long): Boolean {
        val calTask = Calendar.getInstance().apply { timeInMillis = millis }
        val calNow = Calendar.getInstance()
        return calTask.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calTask.get(Calendar.MONTH) == calNow.get(Calendar.MONTH)
    }

    fun isSameDay(millis1: Long, millis2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = millis1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = millis2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
}
