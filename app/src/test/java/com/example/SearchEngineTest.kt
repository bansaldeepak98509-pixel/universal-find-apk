package com.example

import com.example.search.SearchEngine
import com.example.search.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchEngineTest {

    private lateinit var searchEngine: SearchEngine

    @Before
    fun setup() {
        searchEngine = SearchEngine()
    }

    @Test
    fun `empty query returns zero matches`() {
        val text = "Universal Find brings Chrome-style search to Android."
        val result = searchEngine.search(text, SearchQuery(""))
        assertEquals(0, result.totalMatches)
        assertEquals(-1, result.activeIndex)
        assertEquals("", result.displayCounter)
    }

    @Test
    fun `case-insensitive search finds multiple occurrences`() {
        val text = "Find here, find there, everywhere we find."
        val result = searchEngine.search(text, SearchQuery("find", matchCase = false))
        assertEquals(3, result.totalMatches)
        assertEquals(0, result.activeIndex)
        assertEquals("1/3", result.displayCounter)
        assertEquals("Find", result.matches[0].matchedText)
        assertEquals("find", result.matches[1].matchedText)
        assertEquals("find", result.matches[2].matchedText)
    }

    @Test
    fun `case-sensitive search filters correctly`() {
        val text = "Find here, find there, everywhere we Find."
        val result = searchEngine.search(text, SearchQuery("Find", matchCase = true))
        assertEquals(2, result.totalMatches)
        assertEquals(0, result.activeIndex)
        assertEquals("Find", result.matches[0].matchedText)
        assertEquals("Find", result.matches[1].matchedText)
    }

    @Test
    fun `nextMatch advances and wraps around`() {
        val text = "one two one three one"
        var result = searchEngine.search(text, SearchQuery("one"))
        assertEquals(3, result.totalMatches)
        assertEquals(0, result.activeIndex)

        result = searchEngine.nextMatch(result)
        assertEquals(1, result.activeIndex)

        result = searchEngine.nextMatch(result)
        assertEquals(2, result.activeIndex)

        // Wraps around to 0
        result = searchEngine.nextMatch(result)
        assertEquals(0, result.activeIndex)
    }

    @Test
    fun `previousMatch moves backward and wraps around`() {
        val text = "alpha beta alpha"
        var result = searchEngine.search(text, SearchQuery("alpha"))
        assertEquals(2, result.totalMatches)
        assertEquals(0, result.activeIndex)

        // Wraps back to last match
        result = searchEngine.previousMatch(result)
        assertEquals(1, result.activeIndex)

        result = searchEngine.previousMatch(result)
        assertEquals(0, result.activeIndex)
    }

    @Test
    fun `whole word search distinguishes boundaries`() {
        val text = "Android is in the page and inside the app."
        val wholeWordResult = searchEngine.search(text, SearchQuery("in", wholeWord = true))
        assertEquals(1, wholeWordResult.totalMatches)
        assertEquals("in", wholeWordResult.matches[0].matchedText)

        val partialResult = searchEngine.search(text, SearchQuery("in", wholeWord = false))
        // "in" appears in "in", "inside"
        assertTrue(partialResult.totalMatches >= 2)
    }

    @Test
    fun `unicode and indian devanagari script search works accurately`() {
        val hindiText = "यूनिवर्सल फाइंड भारत में खोज तकनीक को सरल बनाता है। खोज जारी है।"
        val result = searchEngine.search(hindiText, SearchQuery("खोज"))
        assertEquals(2, result.totalMatches)
        assertEquals("खोज", result.matches[0].matchedText)
        assertEquals("खोज", result.matches[1].matchedText)
    }

    @Test
    fun `regex search finds pattern matches and reports syntax errors`() {
        val text = "Section 101: Test, Section 202: Pass, Section 303: Complete"
        val regexQuery = SearchQuery("Section \\d+", useRegex = true)
        val result = searchEngine.search(text, regexQuery)

        assertEquals(3, result.totalMatches)
        assertEquals("Section 101", result.matches[0].matchedText)
        assertEquals("Section 202", result.matches[1].matchedText)
        assertEquals("Section 303", result.matches[2].matchedText)

        // Malformed regex
        val invalidResult = searchEngine.search(text, SearchQuery("[unclosed", useRegex = true))
        assertEquals(0, invalidResult.totalMatches)
        assertNotNull(invalidResult.errorMessage)
        assertEquals("Error", invalidResult.displayCounter)
    }

    @Test
    fun `repeated and overlapping matches are captured accurately`() {
        val text = "banana aaa"
        // "ana" in "banana" at index 1 and 3
        val anaResult = searchEngine.search(text, SearchQuery("ana"))
        assertEquals(2, anaResult.totalMatches)
        assertEquals(1, anaResult.matches[0].start)
        assertEquals(3, anaResult.matches[1].start)

        // "aa" in "aaa" (overlapping at 7 and 8)
        val aaResult = searchEngine.search(text, SearchQuery("aa"))
        assertEquals(2, aaResult.totalMatches)
        assertEquals(7, aaResult.matches[0].start)
        assertEquals(8, aaResult.matches[1].start)
    }

    @Test
    fun `whitespace query and missing target match boundaries`() {
        val text = "Quick brown fox"
        // Whitespace only
        val blankResult = searchEngine.search(text, SearchQuery("   "))
        assertEquals(0, blankResult.totalMatches)
        assertEquals("", blankResult.displayCounter)

        // Missing target
        val missingResult = searchEngine.search(text, SearchQuery("zebra"))
        assertEquals(0, missingResult.totalMatches)
        assertEquals("0/0", missingResult.displayCounter)

        // Single match
        val singleResult = searchEngine.search(text, SearchQuery("fox"))
        assertEquals(1, singleResult.totalMatches)
        assertEquals("1/1", singleResult.displayCounter)
        val next = searchEngine.nextMatch(singleResult)
        assertEquals(0, next.activeIndex)
        val prev = searchEngine.previousMatch(singleResult)
        assertEquals(0, prev.activeIndex)
    }

    @Test
    fun `unicode accented letters case sensitivity`() {
        val text = "Café de Paris et CAFÉ Central"
        val insensitive = searchEngine.search(text, SearchQuery("café", matchCase = false))
        assertEquals(2, insensitive.totalMatches)

        val sensitive = searchEngine.search(text, SearchQuery("Café", matchCase = true))
        assertEquals(1, sensitive.totalMatches)
        assertEquals("Café", sensitive.matches[0].matchedText)
    }

    @Test
    fun `hindi whole word distinguishes inflected compounds`() {
        val text = "यह एक अच्छी खोज है लेकिन खोजकर्ता व्यस्त है।"
        // "खोज" as a whole word should match the standalone word, but NOT "खोजकर्ता"
        val wholeWordResult = searchEngine.search(text, SearchQuery("खोज", wholeWord = true))
        assertEquals(1, wholeWordResult.totalMatches)
        assertEquals("खोज", wholeWordResult.matches[0].matchedText)

        // "खोज" as partial should match both
        val partialResult = searchEngine.search(text, SearchQuery("खोज", wholeWord = false))
        assertEquals(2, partialResult.totalMatches)
    }
}
