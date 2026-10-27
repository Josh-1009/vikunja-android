package cloud.codella.vikunja.data.local.fts

import org.junit.Assert.assertEquals
import org.junit.Test

class FtsSearchQueryTest {

    private fun buildFtsPrefixQuery(rawQuery: String): String {
        val sanitized = rawQuery.trim().replace(Regex("[^a-zA-Z0-9\\s]"), "")
        if (sanitized.isBlank()) return ""
        return sanitized.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
    }

    @Test
    fun `single word query appends prefix wildcard`() {
        val result = buildFtsPrefixQuery("meeting")
        assertEquals("meeting*", result)
    }

    @Test
    fun `multi word query appends prefix wildcard to each token`() {
        val result = buildFtsPrefixQuery("buy groceries milk")
        assertEquals("buy* groceries* milk*", result)
    }

    @Test
    fun `special characters and punctuation are stripped to prevent SQLite syntax errors`() {
        val result = buildFtsPrefixQuery("test's \"special\" -query*!")
        assertEquals("tests* special* query*", result)
    }

    @Test
    fun `blank or whitespace only query returns empty string`() {
        val result = buildFtsPrefixQuery("   \t  \n  ")
        assertEquals("", result)
    }

    @Test
    fun `only punctuation query returns empty string`() {
        val result = buildFtsPrefixQuery("---'\"***$$$")
        assertEquals("", result)
    }
}
