package rocks.jimi.busybee.cli

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InputReaderTest : StringSpec({

    fun createMockReader(inputs: List<String>): InputReader {
        val iterator = inputs.iterator()
        return InputReader { iterator.next() }
    }

    "readLine returns input when no validation required" {
        val reader = createMockReader(listOf("hello"))
        val result = reader.readLine()
        result shouldBe "hello"
    }

    "readLine returns trimmed input" {
        val reader = createMockReader(listOf("  hello  "))
        val result = reader.readLine()
        result shouldBe "hello"
    }

    "readLine accepts valid option" {
        val reader = createMockReader(listOf("y"))
        val result = reader.readLine(listOf("y", "n"))
        result shouldBe "y"
    }

    "readLine does exact match - case sensitive" {
        val reader = createMockReader(listOf("Y", "y"))
        val result = reader.readLine(listOf("y", "n"))
        result shouldBe "y"
    }

    "readLine prompts again for invalid input" {
        val reader = createMockReader(listOf("invalid", "valid"))
        val result = reader.readLine(listOf("valid"))
        result shouldBe "valid"
    }

    "readNonEmpty returns non-empty input" {
        val reader = createMockReader(listOf("some input"))
        val result = reader.readNonEmpty("Enter value:")
        result shouldBe "some input"
    }

    "readOptional returns input when provided" {
        val reader = createMockReader(listOf("custom"))
        val result = reader.readOptional("Enter value:", "default")
        result shouldBe "custom"
    }

    "readOptional returns default when empty" {
        val reader = createMockReader(listOf(""))
        val result = reader.readOptional("Enter value:", "default")
        result shouldBe "default"
    }

    "readInt returns parsed integer" {
        val reader = createMockReader(listOf("42"))
        val result = reader.readInt("Enter number:", 10)
        result shouldBe 42
    }

    "readInt returns default for invalid input" {
        val reader = createMockReader(listOf("abc", "10"))
        val result = reader.readInt("Enter number:", 10)
        result shouldBe 10
    }

    "readInt returns default for empty input" {
        val reader = createMockReader(listOf(""))
        val result = reader.readInt("Enter number:", 99)
        result shouldBe 99
    }
})
