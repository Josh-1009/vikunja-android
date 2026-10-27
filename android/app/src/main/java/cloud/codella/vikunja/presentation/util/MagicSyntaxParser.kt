package cloud.codella.vikunja.presentation.util

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Immutable
data class ParsedTaskDraft(
    val cleanTitle: String,
    val dueDateIso: String? = null,
    val dueDateLabel: String? = null,
    val priority: Int = 0,
    val labels: List<String> = emptyList(),
    val projectName: String? = null,
    val rawTokens: List<ParsedToken> = emptyList()
)

@Immutable
data class ParsedToken(
    val text: String,
    val type: TokenType
) {
    enum class TokenType {
        DUE_DATE,
        PRIORITY,
        LABEL,
        PROJECT
    }
}

object MagicSyntaxParser {

    private val DATE_TOKEN_REGEX = Regex("""\*(today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|\d{4}-\d{2}-\d{2})\b""", RegexOption.IGNORE_CASE)
    private val PRIORITY_TOKEN_REGEX = Regex("""!([1-5])\b""")
    private val LABEL_TOKEN_REGEX = Regex("""#([A-Za-z0-9_\-]+)""")
    private val PROJECT_TOKEN_REGEX = Regex("""~([A-Za-z0-9_\-]+)""")

    fun parse(rawInput: String): ParsedTaskDraft {
        if (rawInput.isBlank()) {
            return ParsedTaskDraft(cleanTitle = "")
        }

        val tokens = mutableListOf<ParsedToken>()
        var workingText = rawInput

        // 1. Extract Due Date (*today, *tomorrow, *monday, *YYYY-MM-DD)
        var parsedDueDateIso: String? = null
        var dueDateDisplay: String? = null
        val dateMatch = DATE_TOKEN_REGEX.find(workingText)
        if (dateMatch != null) {
            val fullMatch = dateMatch.value
            val dateKeyword = dateMatch.groupValues[1].lowercase()
            tokens.add(ParsedToken(fullMatch, ParsedToken.TokenType.DUE_DATE))
            workingText = workingText.replace(fullMatch, " ")

            val targetDate = resolveDateKeyword(dateKeyword)
            if (targetDate != null) {
                parsedDueDateIso = targetDate.atTime(18, 0).toString() + "Z"
                dueDateDisplay = when (dateKeyword) {
                    "today" -> "Today"
                    "tomorrow" -> "Tomorrow"
                    else -> targetDate.format(DateTimeFormatter.ofPattern("MMM d"))
                }
            }
        }

        // 2. Extract Priority (!1 to !5)
        var parsedPriority = 0
        val priorityMatch = PRIORITY_TOKEN_REGEX.find(workingText)
        if (priorityMatch != null) {
            val fullMatch = priorityMatch.value
            parsedPriority = priorityMatch.groupValues[1].toIntOrNull() ?: 0
            tokens.add(ParsedToken(fullMatch, ParsedToken.TokenType.PRIORITY))
            workingText = workingText.replace(fullMatch, " ")
        }

        // 3. Extract Labels (#label)
        val parsedLabels = mutableListOf<String>()
        LABEL_TOKEN_REGEX.findAll(workingText).forEach { match ->
            val fullMatch = match.value
            val labelName = match.groupValues[1]
            parsedLabels.add(labelName)
            tokens.add(ParsedToken(fullMatch, ParsedToken.TokenType.LABEL))
            workingText = workingText.replace(fullMatch, " ")
        }

        // 4. Extract Project (~project)
        var parsedProject: String? = null
        val projectMatch = PROJECT_TOKEN_REGEX.find(workingText)
        if (projectMatch != null) {
            val fullMatch = projectMatch.value
            parsedProject = projectMatch.groupValues[1]
            tokens.add(ParsedToken(fullMatch, ParsedToken.TokenType.PROJECT))
            workingText = workingText.replace(fullMatch, " ")
        }

        // 5. Clean up normalized title
        val cleanTitle = workingText.replace(Regex("""\s+"""), " ").trim()

        return ParsedTaskDraft(
            cleanTitle = cleanTitle,
            dueDateIso = parsedDueDateIso,
            dueDateLabel = dueDateDisplay,
            priority = parsedPriority,
            labels = parsedLabels,
            projectName = parsedProject,
            rawTokens = tokens
        )
    }

    private fun resolveDateKeyword(keyword: String): LocalDate? {
        val today = LocalDate.now()
        return when (keyword) {
            "today" -> today
            "tomorrow" -> today.plusDays(1)
            "monday" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
            "tuesday" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY))
            "wednesday" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
            "thursday" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY))
            "friday" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
            "saturday" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            "sunday" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            else -> try {
                LocalDate.parse(keyword, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {
                null
            }
        }
    }
}
