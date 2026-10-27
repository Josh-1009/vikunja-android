package cloud.codella.vikunja.core.util

import androidx.core.text.HtmlCompat

/**
 * Utility for sanitizing and formatting task descriptions.
 * Handles removing raw HTML tags (e.g., <p>, <br>, <strong>, <span>, <div>, <ul>, <li>)
 * left by rich-text editors (like Vikunja web / TipTap) and decodes HTML entities.
 */
object DescriptionUtils {

    private val HTML_TAG_REGEX = Regex("<[^>]*>")
    private val MULTI_WHITESPACE_REGEX = Regex("\\s+")

    /**
     * Cleans raw HTML/markdown markup to produce a clean plain-text snippet for cards and lists.
     * Removes all HTML tags and unescapes entities like &amp;, &lt;, &gt;, &quot;, &#39;, &nbsp;.
     */
    fun cleanSnippet(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val withoutTags = try {
            HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        } catch (_: Exception) {
            raw.replace(HTML_TAG_REGEX, " ")
        }
        return withoutTags.replace(MULTI_WHITESPACE_REGEX, " ").trim()
    }

    /**
     * Strips HTML tags cleanly for text editing so users don't see ugly markup.
     */
    fun stripHtml(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val parsed = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            parsed.trim()
        } catch (_: Exception) {
            raw.replace(HTML_TAG_REGEX, "").trim()
        }
    }

    /**
     * Extracts hashtags (#tag) from text if present.
     */
    fun extractHashtags(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val regex = Regex("""#([A-Za-z0-9_\-]+)""")
        return regex.findAll(text).map { it.groupValues[1] }.toList()
    }
}
