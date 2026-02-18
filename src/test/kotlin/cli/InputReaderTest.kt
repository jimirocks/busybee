package rocks.jimi.calsync.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class InputReaderTest {

    private fun createMockReader(inputs: List<String>): InputReader {
        val iterator = inputs.iterator()
        return InputReader { iterator.next() }
    }

    @Test
    fun `readLine returns input when no validation required`() {
        val reader = createMockReader(listOf("hello"))
        val result = reader.readLine()
        assertEquals("hello", result)
    }

    @Test
    fun `readLine returns trimmed input`() {
        val reader = createMockReader(listOf("  hello  "))
        val result = reader.readLine()
        assertEquals("hello", result)
    }

    @Test
    fun `readLine accepts valid option`() {
        val reader = createMockReader(listOf("y"))
        val result = reader.readLine(listOf("y", "n"))
        assertEquals("y", result)
    }

    @Test
    fun `readLine does exact match - case sensitive`() {
        val reader = createMockReader(listOf("Y", "y"))
        val result = reader.readLine(listOf("y", "n"))
        assertEquals("y", result)
    }

    @Test
    fun `readLine prompts again for invalid input`() {
        val reader = createMockReader(listOf("invalid", "valid"))
        val result = reader.readLine(listOf("valid"))
        assertEquals("valid", result)
    }

    @Test
    fun `readNonEmpty returns non-empty input`() {
        val reader = createMockReader(listOf("some input"))
        val result = reader.readNonEmpty("Enter value:")
        assertEquals("some input", result)
    }

    @Test
    fun `readOptional returns input when provided`() {
        val reader = createMockReader(listOf("custom"))
        val result = reader.readOptional("Enter value:", "default")
        assertEquals("custom", result)
    }

    @Test
    fun `readOptional returns default when empty`() {
        val reader = createMockReader(listOf(""))
        val result = reader.readOptional("Enter value:", "default")
        assertEquals("default", result)
    }

    @Test
    fun `readInt returns parsed integer`() {
        val reader = createMockReader(listOf("42"))
        val result = reader.readInt("Enter number:", 10)
        assertEquals(42, result)
    }

    @Test
    fun `readInt returns default for invalid input`() {
        val reader = createMockReader(listOf("abc", "10"))
        val result = reader.readInt("Enter number:", 10)
        assertEquals(10, result)
    }

    @Test
    fun `readInt returns default for empty input`() {
        val reader = createMockReader(listOf(""))
        val result = reader.readInt("Enter number:", 99)
        assertEquals(99, result)
    }
}
