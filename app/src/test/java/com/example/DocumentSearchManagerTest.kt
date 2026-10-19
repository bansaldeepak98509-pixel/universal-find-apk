package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.document.DocumentSearchManager
import com.example.document.SampleDocType
import com.example.search.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentSearchManagerTest {

    private lateinit var context: Context
    private lateinit var documentManager: DocumentSearchManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        documentManager = DocumentSearchManager(context)
    }

    @Test
    fun `sample web article document loads and searches keywords`() {
        val doc = documentManager.getSampleDocument(SampleDocType.WEB_ARTICLE)
        assertEquals("Sample: Web Article (Find in Page)", doc.title)
        assertTrue(doc.fullText.contains("Chrome-style"))

        val result = documentManager.searchDocument(doc, SearchQuery("Android"))
        assertTrue(result.totalMatches >= 1)
        assertEquals(0, result.activeIndex)
        assertNotNull(result.activeMatch)
    }

    @Test
    fun `hindi sample document searches devanagari script accurately`() {
        val doc = documentManager.getSampleDocument(SampleDocType.MULTILINGUAL_INDIAN_SCRIPTS)
        assertTrue(doc.fullText.contains("खोज"))

        val result = documentManager.searchDocument(doc, SearchQuery("खोज"))
        assertTrue(result.totalMatches >= 4)
        assertEquals("खोज", result.matches[0].matchedText)
    }

    @Test
    fun `technical spec document matches case sensitivity`() {
        val doc = documentManager.getSampleDocument(SampleDocType.TECHNICAL_SPEC)

        val caseSensitive = documentManager.searchDocument(doc, SearchQuery("FLAG_SECURE", matchCase = true))
        assertTrue(caseSensitive.totalMatches >= 1)

        val caseMismatch = documentManager.searchDocument(doc, SearchQuery("flag_secure", matchCase = true))
        assertEquals(0, caseMismatch.totalMatches)
    }
}
