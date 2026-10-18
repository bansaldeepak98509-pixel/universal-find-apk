package com.example.search

import java.text.Normalizer
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class SearchEngine {

    /**
     * Searches within plain text and returns a SearchResult with match offsets and context snippets.
     */
    fun search(sourceText: String, query: SearchQuery, activeIndex: Int = 0): SearchResult {
        if (query.isEmpty || sourceText.isEmpty()) {
            return SearchResult(query = query, matches = emptyList(), activeIndex = -1)
        }

        val matches = mutableListOf<TextMatch>()

        if (query.useRegex) {
            try {
                val flags = if (query.matchCase) 0 else (Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                val pattern = Pattern.compile(query.text, flags)
                val matcher = pattern.matcher(sourceText)

                var id = 0
                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()
                    if (start == end) continue // Avoid infinite loops on empty regex matches

                    val matchedStr = sourceText.substring(start, end)
                    val snippet = createSnippet(sourceText, start, end)
                    matches.add(
                        TextMatch(
                            id = id++,
                            start = start,
                            end = end,
                            matchedText = matchedStr,
                            snippet = snippet
                        )
                    )
                }
            } catch (e: PatternSyntaxException) {
                return SearchResult(
                    query = query,
                    matches = emptyList(),
                    activeIndex = -1,
                    errorMessage = "Invalid regex: ${e.description}"
                )
            }
        } else {
            // Normalized text search
            val target = query.text
            val normalizedSource = normalizeForSearch(sourceText, query.matchCase)
            val normalizedTarget = normalizeForSearch(target, query.matchCase)

            var searchStart = 0
            var id = 0
            val targetLen = normalizedTarget.length

            if (targetLen == 0) {
                return SearchResult(query = query, matches = emptyList(), activeIndex = -1)
            }

            while (searchStart < normalizedSource.length) {
                val foundIndex = normalizedSource.indexOf(normalizedTarget, searchStart)
                if (foundIndex < 0) break

                val matchEnd = foundIndex + targetLen

                val isWordMatch = if (query.wholeWord) {
                    isWordBoundary(normalizedSource, foundIndex, matchEnd)
                } else {
                    true
                }

                if (isWordMatch) {
                    val rawMatchedText = sourceText.substring(
                        foundIndex.coerceIn(0, sourceText.length),
                        matchEnd.coerceIn(0, sourceText.length)
                    )
                    val snippet = createSnippet(sourceText, foundIndex, matchEnd)
                    matches.add(
                        TextMatch(
                            id = id++,
                            start = foundIndex,
                            end = matchEnd,
                            matchedText = rawMatchedText,
                            snippet = snippet
                        )
                    )
                }

                searchStart = foundIndex + 1 // Advance by 1 to support finding all occurrences
            }
        }

        val resolvedActiveIndex = when {
            matches.isEmpty() -> -1
            activeIndex in matches.indices -> activeIndex
            activeIndex >= matches.size -> 0
            else -> 0
        }

        return SearchResult(
            query = query,
            matches = matches,
            activeIndex = resolvedActiveIndex
        )
    }

    /**
     * Advances to the next match with wrap-around.
     */
    fun nextMatch(currentResult: SearchResult): SearchResult {
        if (currentResult.matches.isEmpty()) return currentResult
        val nextIdx = (currentResult.activeIndex + 1) % currentResult.matches.size
        return currentResult.copy(activeIndex = nextIdx)
    }

    /**
     * Reverses to the previous match with wrap-around.
     */
    fun previousMatch(currentResult: SearchResult): SearchResult {
        if (currentResult.matches.isEmpty()) return currentResult
        val prevIdx = if (currentResult.activeIndex <= 0) {
            currentResult.matches.size - 1
        } else {
            currentResult.activeIndex - 1
        }
        return currentResult.copy(activeIndex = prevIdx)
    }

    /**
     * Moves to a specific match index.
     */
    fun selectMatch(currentResult: SearchResult, targetIndex: Int): SearchResult {
        if (currentResult.matches.isEmpty() || targetIndex !in currentResult.matches.indices) {
            return currentResult
        }
        return currentResult.copy(activeIndex = targetIndex)
    }

    private fun normalizeForSearch(text: String, matchCase: Boolean): String {
        // Unicode NFC normalization keeps combined Devanagari and accented glyphs consistent
        val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)
        return if (matchCase) nfc else nfc.lowercase()
    }

    private fun isWordBoundary(text: String, start: Int, end: Int): Boolean {
        val leftChar = if (start > 0) text[start - 1] else null
        val rightChar = if (end < text.length) text[end] else null

        val leftIsBoundary = leftChar == null || !isWordChar(leftChar)
        val rightIsBoundary = rightChar == null || !isWordChar(rightChar)

        return leftIsBoundary && rightIsBoundary
    }

    private fun isWordChar(c: Char): Boolean {
        if (Character.isLetterOrDigit(c)) return true
        val type = Character.getType(c)
        return type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt() ||
                type == Character.CONNECTOR_PUNCTUATION.toInt()
    }

    private fun createSnippet(source: String, start: Int, end: Int, contextRadius: Int = 30): String {
        val snippetStart = (start - contextRadius).coerceAtLeast(0)
        val snippetEnd = (end + contextRadius).coerceAtMost(source.length)

        val prefix = if (snippetStart > 0) "…" else ""
        val suffix = if (snippetEnd < source.length) "…" else ""

        val snippetText = source.substring(snippetStart, snippetEnd).replace('\n', ' ')
        return "$prefix$snippetText$suffix"
    }
}
