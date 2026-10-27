package cloud.codella.vikunja.presentation.util

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MagicSyntaxParserTest {

    @Test
    fun `parses clean title and extracts today due date and priority`() {
        val input = "Buy organic milk *today !3"
        val result = MagicSyntaxParser.parse(input)

        assertEquals("Buy organic milk", result.cleanTitle)
        assertEquals(3, result.priority)
        assertEquals("Today", result.dueDateLabel)
        assertNotNull(result.dueDateIso)
        assertTrue(result.labels.isEmpty())
        assertNull(result.projectName)
    }

    @Test
    fun `parses tomorrow, multiple labels, urgent priority, and project`() {
        val input = "Fix server deadlock *tomorrow !5 #backend #critical ~DevOps"
        val result = MagicSyntaxParser.parse(input)

        assertEquals("Fix server deadlock", result.cleanTitle)
        assertEquals(5, result.priority)
        assertEquals("Tomorrow", result.dueDateLabel)
        assertEquals(listOf("backend", "critical"), result.labels)
        assertEquals("DevOps", result.projectName)
    }

    @Test
    fun `parses explicit ISO date correctly`() {
        val input = "Submit Q3 tax report *2026-10-25 !4"
        val result = MagicSyntaxParser.parse(input)

        assertEquals("Submit Q3 tax report", result.cleanTitle)
        assertEquals(4, result.priority)
        assertTrue(result.dueDateIso?.startsWith("2026-10-25") == true)
    }

    @Test
    fun `handles input without syntax tokens cleanly`() {
        val input = "Simple standalone task with no magic syntax"
        val result = MagicSyntaxParser.parse(input)

        assertEquals("Simple standalone task with no magic syntax", result.cleanTitle)
        assertEquals(0, result.priority)
        assertNull(result.dueDateIso)
        assertNull(result.dueDateLabel)
        assertTrue(result.labels.isEmpty())
        assertNull(result.projectName)
    }

    @Test
    fun `handles empty or blank input gracefully`() {
        val result = MagicSyntaxParser.parse("   ")
        assertEquals("", result.cleanTitle)
        assertEquals(0, result.priority)
        assertTrue(result.rawTokens.isEmpty())
    }
}
