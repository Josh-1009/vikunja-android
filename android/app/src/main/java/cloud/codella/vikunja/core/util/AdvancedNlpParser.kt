package cloud.codella.vikunja.core.util

import java.util.*

object AdvancedNlpParser {

    private val timeRegex = "\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b".toRegex(RegexOption.IGNORE_CASE)

    fun parse(input: String): ParsedQuickAdd {
        var working = input.trim()
        var dueDateIso: String? = null
        var priority: Int? = null
        var projectQuery: String? = null
        val labels = mutableListOf<String>()
        val assignees = mutableListOf<String>()

        val cal = Calendar.getInstance()
        var dateFound = false
        var targetHour = 18
        var targetMinute = 0

        // 1. Check for explicit time e.g., "5pm", "14:30", "at 9am"
        val timeMatch = "\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b".toRegex(RegexOption.IGNORE_CASE).find(working)
        if (timeMatch != null) {
            val h = timeMatch.groupValues[1].toInt()
            val m = timeMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = timeMatch.groupValues[3].lowercase()
            targetHour = if (ampm == "pm" && h < 12) h + 12 else if (ampm == "am" && h == 12) 0 else h
            targetMinute = m
            working = working.replace(timeMatch.value, " ")
        }

        // 2. Check for natural day words
        val lower = working.lowercase()
        when {
            lower.contains("tomorrow") -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                dateFound = true
                working = working.replace("(?i)\\btomorrow\\b".toRegex(), " ")
            }
            lower.contains("today") -> {
                dateFound = true
                working = working.replace("(?i)\\btoday\\b".toRegex(), " ")
            }
            lower.contains("next week") -> {
                cal.add(Calendar.WEEK_OF_YEAR, 1)
                dateFound = true
                working = working.replace("(?i)\\bnext\\s+week\\b".toRegex(), " ")
            }
            lower.contains("next monday") || lower.contains("on monday") || lower.contains("monday") -> {
                setNextDayOfWeek(cal, Calendar.MONDAY)
                dateFound = true
                working = working.replace("(?i)\\b(?:next|on)?\\s*monday\\b".toRegex(), " ")
            }
            lower.contains("next tuesday") || lower.contains("on tuesday") || lower.contains("tuesday") -> {
                setNextDayOfWeek(cal, Calendar.TUESDAY)
                dateFound = true
                working = working.replace("(?i)\\b(?:next|on)?\\s*tuesday\\b".toRegex(), " ")
            }
            lower.contains("next wednesday") || lower.contains("on wednesday") || lower.contains("wednesday") -> {
                setNextDayOfWeek(cal, Calendar.WEDNESDAY)
                dateFound = true
                working = working.replace("(?i)\\b(?:next|on)?\\s*wednesday\\b".toRegex(), " ")
            }
            lower.contains("next thursday") || lower.contains("on thursday") || lower.contains("thursday") -> {
                setNextDayOfWeek(cal, Calendar.THURSDAY)
                dateFound = true
                working = working.replace("(?i)\\b(?:next|on)?\\s*thursday\\b".toRegex(), " ")
            }
            lower.contains("next friday") || lower.contains("on friday") || lower.contains("friday") -> {
                setNextDayOfWeek(cal, Calendar.FRIDAY)
                dateFound = true
                working = working.replace("(?i)\\b(?:next|on)?\\s*friday\\b".toRegex(), " ")
            }
            lower.contains("next saturday") || lower.contains("on saturday") || lower.contains("saturday") -> {
                setNextDayOfWeek(cal, Calendar.SATURDAY)
                dateFound = true
                working = working.replace("(?i)\\b(?:next|on)?\\s*saturday\\b".toRegex(), " ")
            }
            lower.contains("next sunday") || lower.contains("on sunday") || lower.contains("sunday") -> {
                setNextDayOfWeek(cal, Calendar.SUNDAY)
                dateFound = true
                working = working.replace("(?i)\\b(?:next|on)?\\s*sunday\\b".toRegex(), " ")
            }
        }

        if (dateFound) {
            cal.set(Calendar.HOUR_OF_DAY, targetHour)
            cal.set(Calendar.MINUTE, targetMinute)
            cal.set(Calendar.SECOND, 0)
            dueDateIso = DateUtils.formatMillisToIso(cal.timeInMillis)
        }

        // 3. Fallback to token parsing (*today, !urgent, #tag, ~project, @user)
        val tokens = working.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val cleanTitleParts = mutableListOf<String>()

        for (token in tokens) {
            when {
                token.startsWith("*") -> {
                    val dateStr = token.substring(1).lowercase()
                    if (dateStr == "today") {
                        val c = Calendar.getInstance()
                        c.set(Calendar.HOUR_OF_DAY, targetHour)
                        c.set(Calendar.MINUTE, targetMinute)
                        dueDateIso = DateUtils.formatMillisToIso(c.timeInMillis)
                    } else if (dateStr == "tomorrow") {
                        val c = Calendar.getInstance()
                        c.add(Calendar.DAY_OF_YEAR, 1)
                        c.set(Calendar.HOUR_OF_DAY, targetHour)
                        c.set(Calendar.MINUTE, targetMinute)
                        dueDateIso = DateUtils.formatMillisToIso(c.timeInMillis)
                    } else {
                        dueDateIso = "${dateStr}T18:00:00Z"
                    }
                }
                token.startsWith("!") -> {
                    val pStr = token.substring(1).lowercase()
                    priority = when (pStr) {
                        "urgent", "5" -> 5
                        "high", "4" -> 4
                        "med", "medium", "3" -> 3
                        "low", "1" -> 1
                        else -> pStr.toIntOrNull() ?: 1
                    }
                }
                token.startsWith("~") -> projectQuery = token.substring(1).replace("_", " ")
                token.startsWith("#") -> labels.add(token.substring(1))
                token.startsWith("@") -> assignees.add(token.substring(1))
                token.equals("priority", ignoreCase = true) -> {}
                token.equals("urgent", ignoreCase = true) -> priority = 5
                token.equals("high", ignoreCase = true) -> priority = 4
                else -> cleanTitleParts.add(token)
            }
        }

        val cleanTitle = cleanTitleParts.joinToString(" ").trim()
        return ParsedQuickAdd(
            cleanTitle = if (cleanTitle.isNotBlank()) cleanTitle else input.trim(),
            dueDateIso = dueDateIso,
            priority = priority,
            projectQuery = projectQuery,
            labels = labels,
            assignees = assignees
        )
    }

    private fun setNextDayOfWeek(cal: Calendar, dayOfWeek: Int) {
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        var diff = dayOfWeek - currentDay
        if (diff <= 0) diff += 7
        cal.add(Calendar.DAY_OF_YEAR, diff)
    }
}
