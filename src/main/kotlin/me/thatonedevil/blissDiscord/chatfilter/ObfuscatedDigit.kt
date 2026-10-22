enum class ObfuscatedDigit(val variants: List<String>) {
    ZERO(listOf("0", "O", "o")),
    ONE(listOf("1", "i", "I", "l", "!", "|")),
    TWO(listOf("2", "Z", "z")),
    THREE(listOf("3", "E", "e")),
    FOUR(listOf("4", "A", "a")),
    FIVE(listOf("5", "S", "s")),
    SIX(listOf("6", "G", "g")),
    SEVEN(listOf("7", "T", "t")),
    EIGHT(listOf("8", "B", "b")),
    NINE(listOf("9", "G", "g"));

    companion object {
        fun getVariants(c: Char): List<String>? =
            if (c in '0'..'9') entries[c.digitToInt()].variants else null
    }
}
