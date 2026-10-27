package cloud.codella.vikunja.ui.screens.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class ShareIntentParserTest {

    private val urlPattern = Pattern.compile("https?://\\S+")

    private fun parseSharedText(subject: String?, text: String?): Pair<String, String> {
        var title = ""
        var description = ""

        if (!text.isNullOrBlank()) {
            val matcher = urlPattern.matcher(text)
            if (matcher.find()) {
                val extractedUrl = matcher.group()
                val textWithoutUrl = text.replace(extractedUrl, "").trim()

                title = when {
                    !subject.isNullOrBlank() -> subject.trim()
                    textWithoutUrl.isNotBlank() -> textWithoutUrl
                    else -> "Shared Link"
                }
                description = extractedUrl
            } else {
                title = subject?.takeIf { it.isNotBlank() } ?: text.take(60).trim()
                description = if (text != title) text else ""
            }
        } else if (!subject.isNullOrBlank()) {
            title = subject.trim()
        }

        return Pair(title, description)
    }

    @Test
    fun `extracts pure URL and assigns fallback title`() {
        val (title, description) = parseSharedText(null, "https://vikunja.io/docs")
        assertEquals("Shared Link", title)
        assertEquals("https://vikunja.io/docs", description)
    }

    @Test
    fun `extracts URL with snippet into title and description`() {
        val (title, description) = parseSharedText(null, "Check out this documentation: https://vikunja.io/features")
        assertEquals("Check out this documentation:", title)
        assertEquals("https://vikunja.io/features", description)
    }

    @Test
    fun `extracts plain text task without URL`() {
        val (title, description) = parseSharedText(null, "Complete phase 4 implementation")
        assertEquals("Complete phase 4 implementation", title)
        assertEquals("", description)
    }

    @Test
    fun `uses subject as title when present with body`() {
        val (title, description) = parseSharedText("Bug Report", "App crashes when rotated https://example.com/log")
        assertEquals("Bug Report", title)
        assertEquals("https://example.com/log", description)
    }
}
