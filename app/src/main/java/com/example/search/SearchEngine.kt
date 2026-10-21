package com.example.search

import java.text.Normalizer
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class SearchEngine {

    fun search(sourceText: String, query: SearchQuery): SearchResult {
        if (query.isEmpty || sourceText.isEmpty()) {
            return SearchResult(query = query, matches = emptyList(), activeIndex = -1)
        }

        if (query.useRegex) {
            return searchWithRegex(sourceText, query)
        }

        val normalizedSource = Normalizer.normalize(sourceText, Normalizer.Form.NFC)
        val normalizedQuery = query.copy(text = Normalizer.normalize(query.text, Normalizer.Form.NFC))

        val pattern = buildNormalizedPattern(normalizedQuery) ?: return SearchResult(
            query = query,
            matches = emptyList(),
            activeIndex = -1
        )

        val matcher = pattern.matcher(normalizedSource)
        val matches = mutableListOf<TextMatch>()
        var searchStart = 0
        var matchId = 0

        while (searchStart < normalizedSource.length && matcher.find(searchStart)) {
            val start = matcher.start()
            val end = matcher.end()
            if (start == end) {
                searchStart = start + 1
                continue
            }

            if (!query.wholeWord || isWordBoundary(normalizedSource, start, end)) {
                val matchedSnippet = sourceText.substring(start, end)
                val snippetWithContext = createSnippet(sourceText, start, end)

                matches.add(
                    TextMatch(
                        id = matchId++,
                        start = start,
                        end = end,
                        matchedText = matchedSnippet,
                        snippet = snippetWithContext
                    )
                )
            }

            searchStart = start + 1
        }

        val initialActiveIndex = if (matches.isNotEmpty()) 0 else -1
        return SearchResult(
            query = query,
            matches = matches,
            activeIndex = initialActiveIndex
        )
    }

    private fun buildNormalizedPattern(query: SearchQuery): Pattern? {
        val trimmed = query.text.trim()
        if (trimmed.isEmpty()) return null

        val sb = StringBuilder()
        var i = 0
        val len = trimmed.length

        while (i < len) {
            val c = trimmed[i]
            when {
                c.isWhitespace() -> {
                    // Collapse any sequence of whitespace or punctuation in query to flexible separator
                    while (i < len && (trimmed[i].isWhitespace() || isPunctuation(trimmed[i]))) {
                        i++
                    }
                    sb.append("[\\s\\p{Punct}\\p{P}\\p{S}]+")
                }
                isPunctuation(c) -> {
                    // Punctuation in query (e.g. hyphens, apostrophes) matches 0 or more punctuation/whitespace in source
                    while (i < len && isPunctuation(trimmed[i])) {
                        i++
                    }
                    sb.append("[\\s\\p{Punct}\\p{P}\\p{S}]*")
                }
                else -> {
                    sb.append(Pattern.quote(c.toString()))
                    i++
                }
            }
        }

        val patternString = sb.toString()
        if (patternString.isEmpty()) return null

        val flags = if (query.matchCase) 0 else (Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
        return try {
            Pattern.compile(patternString, flags)
        } catch (_: Exception) {
            null
        }
    }

    private fun isPunctuation(c: Char): Boolean {
        if (Character.isLetterOrDigit(c) || isCombiningMark(c) || Character.isWhitespace(c)) return false
        val type = Character.getType(c)
        return type == Character.CONNECTOR_PUNCTUATION.toInt() ||
                type == Character.DASH_PUNCTUATION.toInt() ||
                type == Character.START_PUNCTUATION.toInt() ||
                type == Character.END_PUNCTUATION.toInt() ||
                type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.OTHER_PUNCTUATION.toInt() ||
                type == Character.MATH_SYMBOL.toInt() ||
                type == Character.CURRENCY_SYMBOL.toInt() ||
                type == Character.MODIFIER_SYMBOL.toInt() ||
                type == Character.OTHER_SYMBOL.toInt()
    }

    private fun isCombiningMark(c: Char): Boolean {
        val type = Character.getType(c)
        return type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
    }

    private fun searchWithRegex(sourceText: String, query: SearchQuery): SearchResult {
        return try {
            val flags = if (query.matchCase) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            val patternString = if (query.wholeWord) "\\b${query.text}\\b" else query.text
            val pattern = Pattern.compile(patternString, flags)
            val matcher = pattern.matcher(sourceText)

            val matches = mutableListOf<TextMatch>()
            var matchId = 0

            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                if (start == end) continue

                matches.add(
                    TextMatch(
                        id = matchId++,
                        start = start,
                        end = end,
                        matchedText = matcher.group(),
                        snippet = createSnippet(sourceText, start, end)
                    )
                )
            }

            val initialActiveIndex = if (matches.isNotEmpty()) 0 else -1
            SearchResult(
                query = query,
                matches = matches,
                activeIndex = initialActiveIndex
            )
        } catch (e: PatternSyntaxException) {
            SearchResult(
                query = query,
                matches = emptyList(),
                activeIndex = -1,
                errorMessage = e.description
            )
        }
    }

    fun nextMatch(current: SearchResult): SearchResult {
        if (current.matches.isEmpty()) return current
        val next = (current.activeIndex + 1) % current.totalMatches
        return current.copy(activeIndex = next)
    }

    fun previousMatch(current: SearchResult): SearchResult {
        if (current.matches.isEmpty()) return current
        val prev = if (current.activeIndex <= 0) current.totalMatches - 1 else current.activeIndex - 1
        return current.copy(activeIndex = prev)
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
        return prefix + source.substring(snippetStart, snippetEnd) + suffix
    }
}
