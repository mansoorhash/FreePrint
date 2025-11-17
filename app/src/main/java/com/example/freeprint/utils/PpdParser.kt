package com.example.freeprint.utils

import android.util.Log
import java.io.InputStream
import java.lang.StringBuilder

data class ParsedPpd(val options: List<PpdOption>)

// --- CHANGE 1: Add displayOrder to force essential options to the top ---
data class PpdOption(
    val keyword: String,
    val displayName: String,
    val choices: List<PpdChoice>,
    val defaultChoice: String,
    val displayOrder: Int // Lower numbers appear first
)

data class PpdChoice(
    val keyword: String,
    val displayName: String,
    val invocationCode: String
)

object PpdParser {
    fun parse(inputStream: InputStream): ParsedPpd {
        val options = mutableListOf<PpdOption>()
        val lines = inputStream.bufferedReader().readLines()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith("*OpenUI", ignoreCase = true)) {
                val parts = line.split(" ", limit = 3)
                if (parts.size >= 2) {
                    val keywordAndName = parts[1].removePrefix("*").split("/", limit = 2)
                    val keyword = keywordAndName[0]
                    val displayName = if (keywordAndName.size > 1) keywordAndName[1] else keyword

                    // --- CHANGE 2: Give all parsed options a high displayOrder by default ---
                    val blockResult = parseOptionBlock(lines, i + 1, keyword, displayName, 100)
                    blockResult.option?.let { options.add(it) }
                    i = blockResult.nextLineIndex
                    continue
                }
            }
            i++
        }

        Log.d("PpdParser", "Parsed ${options.size} options from driver.")
        return ParsedPpd(options)
    }

    private data class BlockParseResult(val option: PpdOption?, val nextLineIndex: Int)

    private fun parseOptionBlock(
        lines: List<String>,
        startIndex: Int,
        keyword: String,
        displayName: String,
        // --- CHANGE 3: Accept displayOrder as a parameter ---
        displayOrder: Int
    ): BlockParseResult {
        val choices = mutableListOf<PpdChoice>()
        var defaultChoice = ""
        var currentIndex = startIndex

        while (currentIndex < lines.size) {
            val line = lines[currentIndex].trim()

            if (line.startsWith("*CloseUI", ignoreCase = true)) {
                // --- CHANGE 4: Pass displayOrder to the PpdOption constructor ---
                val finalOption = if (choices.isNotEmpty()) PpdOption(keyword, displayName, choices, defaultChoice, displayOrder) else null
                return BlockParseResult(finalOption, currentIndex)
            }

            if (line.startsWith("*Default$keyword", ignoreCase = true)) {
                defaultChoice = line.substringAfter(":").trim()
            } else if (line.startsWith("*$keyword", ignoreCase = true)) {
                try {
                    val (choice, nextIndex) = parseChoiceAndCode(lines, currentIndex, keyword)
                    choice?.let { choices.add(it) }
                    currentIndex = nextIndex
                    continue // Skip the currentIndex++ at the end of the loop
                } catch (e: Exception) {
                    Log.w("PpdParser", "Could not parse choice line block starting at: $line", e)
                }
            }
            currentIndex++
        }
        val finalOption = if (choices.isNotEmpty()) PpdOption(keyword, displayName, choices, defaultChoice, displayOrder) else null
        return BlockParseResult(finalOption, currentIndex)
    }

    // --- THIS FUNCTION IS NOW FIXED AND MORE ROBUST ---
    private fun parseChoiceAndCode(lines: List<String>, startIndex: Int, optionKeyword: String): Pair<PpdChoice?, Int> {
        val firstLine = lines[startIndex].trim()
        val choiceParts = firstLine.split(":", limit = 2)

        val keyPart = choiceParts[0].substringAfter("*$optionKeyword").trim()
        val choiceKeywordAndName = keyPart.split("/", limit = 2)
        val choiceKeyword = choiceKeywordAndName[0]
        val choiceDisplayName = if (choiceKeywordAndName.size > 1) choiceKeywordAndName[1] else choiceKeyword

        val codeBuilder = StringBuilder()

        // Case 1: Code is on the same line
        if (choiceParts.size > 1) {
            val codeOnFirstLine = choiceParts[1].trim()
            codeBuilder.append(codeOnFirstLine.removePrefix("\""))

            // If the code is fully contained on this line, we are done.
            if (codeOnFirstLine.endsWith("\"")) {
                return PpdChoice(choiceKeyword, choiceDisplayName, codeBuilder.toString().removeSuffix("\"").trim()) to startIndex + 1
            }
        }

        // Case 2: Code is on subsequent lines (multi-line block)
        var currentIndex = startIndex + 1
        while (currentIndex < lines.size) {
            val currentLine = lines[currentIndex]
            val trimmedLine = currentLine.trim()

            // Stop condition: a new PPD keyword is found, signaling the end of our code block.
            if (trimmedLine.startsWith("*")) {
                val finalCode = codeBuilder.toString().replace("\n", " ").replace("\r", "").trim().removeSuffix("\"").trim()
                return PpdChoice(choiceKeyword, choiceDisplayName, finalCode) to currentIndex
            }

            codeBuilder.append(" ").append(trimmedLine) // Append with a space for proper PostScript
            currentIndex++
        }

        // Failsafe: reached end of file
        val finalCode = codeBuilder.toString().replace("\n", " ").replace("\r", "").trim().removeSuffix("\"").trim()
        return PpdChoice(choiceKeyword, choiceDisplayName, finalCode) to currentIndex
    }
}
