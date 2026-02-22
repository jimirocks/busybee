package rocks.jimi.busybee.cli

class InputReader(
    private val inputProvider: () -> String = { readLine()!! }
) {

    fun readLine(validOptions: List<String>? = null): String {
        while (true) {
            val input = inputProvider().trim()
            if (validOptions == null || input in validOptions) {
                return input
            }
            println("Invalid input. Please try again.")
        }
    }

    fun readNonEmpty(prompt: String): String {
        println(prompt)
        return readLine()
    }

    fun readOptional(prompt: String, default: String): String {
        println(prompt)
        val input = readLine()
        return input.ifEmpty { default }
    }

    fun readInt(prompt: String, default: Int): Int {
        println(prompt)
        return readLine().toIntOrNull() ?: default
    }
}
