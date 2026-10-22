package me.thatonedevil.blissDiscord.chatfilter

import ObfuscatedChar
import ObfuscatedDigit
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.instance
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.io.path.Path

class ChatFilter {
    @Volatile
    private var badWords: List<String> = emptyList()

    @Volatile
    private var compiledPatterns: Map<String, Regex> = emptyMap()

    /** Every word in one regex, so a clean message (the common case) is checked in a single pass. */
    @Volatile
    private var combined: Regex? = null

    private fun toRegexPattern(word: String): Regex {
        val parts = buildList {
            for (c in word) {
                val variants = when {
                    c.isLetter() -> ObfuscatedChar.getVariants(c)
                    c.isDigit() -> ObfuscatedDigit.getVariants(c)
                    else -> listOf(Regex.escape(c.toString()))
                }

                val escaped = variants?.distinct()?.joinToString("|") { Regex.escape(it) }
                    ?: Regex.escape(c.toString())

                add("(?:$escaped)[\\W\\d_]*")
            }
        }

        // No ".*" around it: containsMatchIn already searches, and the wrappers cause heavy backtracking.
        return Regex(parts.joinToString(""), RegexOption.IGNORE_CASE)
    }

    private fun readBadWordsAsync(): CompletableFuture<List<String>> {
        return CompletableFuture.supplyAsync {
            val filePath = Path(instance.dataFolder.path).resolve("badwords.txt")
            try {
                if (Files.exists(filePath) && Files.isReadable(filePath)) {
                    Files.readAllLines(filePath)
                        .asSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .toList()
                } else {
                    println("Bad words file not found or not readable. Using empty list.")
                    emptyList()
                }
            } catch (e: Exception) {
                println("Error reading bad words file: ${e.message}")
                emptyList()
            }
        }
    }

    fun reloadBadWords() {
        readBadWordsAsync().thenAccept { words ->
            val compiled = HashMap<String, Regex>(words.size)
            for (word in words) {
                compiled[word] = toRegexPattern(word)
            }
            badWords = words
            compiledPatterns = compiled
            combined = if (compiled.isEmpty()) null
                else Regex(compiled.values.joinToString("|") { "(?:${it.pattern})" }, RegexOption.IGNORE_CASE)
            println("Reloaded bad words. Total: ${words.size}")
        }.exceptionally {
            println("Reload failed: ${it.message}")
            null
        }
    }

    fun findBadWord(message: String): String? {
        val all = combined ?: return null
        if (!all.containsMatchIn(message)) return null
        return compiledPatterns.entries.firstOrNull { (_, regex) ->
            regex.containsMatchIn(message)
        }?.key
    }
}