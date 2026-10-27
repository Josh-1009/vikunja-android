package cloud.codella.vikunja.core.util

import java.util.*

data class ParsedQuickAdd(
    val cleanTitle: String,
    val dueDateIso: String? = null,
    val priority: Int? = null,
    val projectQuery: String? = null,
    val labels: List<String> = emptyList(),
    val assignees: List<String> = emptyList()
)

object QuickAddParser {

    fun parse(input: String): ParsedQuickAdd {
        val tokens = input.trim().split("\\s+".toRegex())
        val cleanTitleParts = mutableListOf<String>()
        var dueDateIso: String? = null
        var priority: Int? = null
        var projectQuery: String? = null
        val labels = mutableListOf<String>()
        val assignees = mutableListOf<String>()

        val cal = Calendar.getInstance()

        for (token in tokens) {
            when {
                token.startsWith("*") -> {
                    val dateStr = token.substring(1).lowercase()
                    when (dateStr) {
                        "today" -> {
                            cal.timeInMillis = System.currentTimeMillis()
                            cal.set(Calendar.HOUR_OF_DAY, 18)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            dueDateIso = DateUtils.formatMillisToIso(cal.timeInMillis)
                        }
                        "tomorrow" -> {
                            cal.timeInMillis = System.currentTimeMillis()
                            cal.add(Calendar.DAY_OF_YEAR, 1)
                            cal.set(Calendar.HOUR_OF_DAY, 18)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            dueDateIso = DateUtils.formatMillisToIso(cal.timeInMillis)
                        }
                        "nextweek" -> {
                            cal.timeInMillis = System.currentTimeMillis()
                            cal.add(Calendar.WEEK_OF_YEAR, 1)
                            dueDateIso = DateUtils.formatMillisToIso(cal.timeInMillis)
                        }
                        else -> {
                            // If standard yyyy-MM-dd is given
                            dueDateIso = "${dateStr}T18:00:00Z"
                        }
                    }
                }
                token.startsWith("!") -> {
                    val pStr = token.substring(1).lowercase()
                    priority = when (pStr) {
                        "urgent", "5" -> 5
                        "high", "4" -> 4
                        "medium", "3" -> 3
                        "normal", "2" -> 2
                        "low", "1" -> 1
                        else -> pStr.toIntOrNull() ?: 1
                    }
                }
                token.startsWith("~") -> {
                    projectQuery = token.substring(1).replace("_", " ")
                }
                token.startsWith("#") -> {
                    labels.add(token.substring(1))
                }
                token.startsWith("@") -> {
                    assignees.add(token.substring(1))
                }
                else -> {
                    cleanTitleParts.add(token)
                }
            }
        }

        val cleanTitle = cleanTitleParts.joinToString(" ")
        return ParsedQuickAdd(
            cleanTitle = if (cleanTitle.isNotBlank()) cleanTitle else input,
            dueDateIso = dueDateIso,
            priority = priority,
            projectQuery = projectQuery,
            labels = labels,
            assignees = assignees
        )
    }
}
