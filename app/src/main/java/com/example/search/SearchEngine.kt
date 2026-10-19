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
        val normalizedQuery = Normalizer.normalize(query.text, Normalizer.Form.NFC)

        val targetText = if (query.matchCase) normalizedQuery else normalizedQuery.lowercase()
        val textToSearch = if (query.matchCase) normalizedSource else normalizedSource.lowercase()

        val matches = mutableListOf<TextMatch>()
        var searchStart = 0
        var matchId = 0

        while (searchStart < textToSearch.length) {
            val foundIndex = textToSearch.indexOf(targetText, searchStart)
            if (foundIndex < 0) break

            val matchEnd = foundIndex + targetText.length

            if (!query.wholeWord || isWordBoundary(normalizedSource, foundIndex, matchEnd)) {
                val matchedSnippet = sourceText.substring(foundIndex, matchEnd)
                val snippetWithContext = createSnippet(sourceText, foundIndex, matchEnd)

                matches.add(
                    TextMatch(
                        id = matchId++,
                        start = foundIndex,
                        end = matchEnd,
                        matchedText = matchedSnippet,
                        snippet = snippetWithContext
                    )
                )
            }

            searchStart = foundIndex + 1
        }

        val initialActiveIndex = if (matches.isNotEmpty()) 0 else -1
        return SearchResult(
            query = query,
            matches = matches,
            activeIndex = initialActiveIndex
        )
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
