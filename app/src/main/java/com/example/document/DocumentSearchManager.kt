package com.example.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.ocr.OcrResult
import com.example.ocr.OcrTextProvider
import com.example.search.SearchEngine
import com.example.search.SearchQuery
import com.example.search.SearchResult
import com.example.search.TextMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

enum class DocumentType {
    PLAIN_TEXT,
    PDF,
    IMAGE,
    SCREEN_CAPTURE
}

data class DocumentPage(
    val pageNumber: Int, // 1-indexed
    val text: String,
    val previewBitmap: Bitmap? = null,
    val ocrResult: OcrResult? = null
)

data class LoadedDocument(
    val title: String,
    val type: DocumentType,
    val pages: List<DocumentPage>,
    val fullText: String
)

class DocumentSearchManager(
    private val context: Context,
    private val ocrProvider: OcrTextProvider = OcrTextProvider(),
    private val searchEngine: SearchEngine = SearchEngine()
) {

    fun queryFileName(uri: Uri, fallback: String): String {
        var result = fallback
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) result = name
                    }
                }
            } catch (_: Exception) {}
        } else if (uri.scheme == "file") {
            uri.lastPathSegment?.let { if (it.isNotBlank()) result = it }
        }
        return result
    }

    /**
     * Loads a plain text file from Uri.
     */
    suspend fun loadTextFromUri(uri: Uri, fileName: String = ""): LoadedDocument =
        withContext(Dispatchers.IO) {
            val resolvedTitle = if (fileName.isNotBlank()) fileName else queryFileName(uri, "Imported Text.txt")
            val contentResolver = context.contentResolver
            val textBuilder = StringBuilder()
            contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        textBuilder.append(line).append("\n")
                    }
                }
            }
            val text = textBuilder.toString()
            val page = DocumentPage(pageNumber = 1, text = text)
            LoadedDocument(
                title = resolvedTitle,
                type = DocumentType.PLAIN_TEXT,
                pages = listOf(page),
                fullText = text
            )
        }

    /**
     * Loads and OCRs an image from Uri.
     */
    suspend fun loadImageFromUri(uri: Uri, fileName: String = ""): LoadedDocument =
        withContext(Dispatchers.IO) {
            val resolvedTitle = if (fileName.isNotBlank()) fileName else queryFileName(uri, "Scanned Image.png")
            val contentResolver = context.contentResolver
            val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: throw IllegalArgumentException("Could not decode image from URI")

            val ocr = ocrProvider.recognizeText(bitmap)
            val page = DocumentPage(
                pageNumber = 1,
                text = ocr.rawText,
                previewBitmap = bitmap,
                ocrResult = ocr
            )
            LoadedDocument(
                title = resolvedTitle,
                type = DocumentType.IMAGE,
                pages = listOf(page),
                fullText = ocr.rawText
            )
        }

    /**
     * Loads and renders a PDF document, running OCR or extracting text from rendered pages.
     */
    suspend fun loadPdfFromUri(
        uri: Uri,
        fileName: String = "",
        maxPages: Int = 10
    ): LoadedDocument = withContext(Dispatchers.IO) {
        val resolvedTitle = if (fileName.isNotBlank()) fileName else queryFileName(uri, "Document.pdf")
        val contentResolver = context.contentResolver
        val pfd: ParcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Unable to open PDF descriptor")

        val pages = mutableListOf<DocumentPage>()
        val fullTextBuilder = StringBuilder()

        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val pageCount = minOf(renderer.pageCount, maxPages)
                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    // Render page at readable resolution (2x standard density)
                    val width = page.width * 2
                    val height = page.height * 2
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    // OCR the rendered page bitmap
                    val ocr = ocrProvider.recognizeText(bitmap)
                    val pageText = ocr.rawText
                    fullTextBuilder.append("--- Page ${i + 1} ---\n").append(pageText).append("\n\n")

                    pages.add(
                        DocumentPage(
                            pageNumber = i + 1,
                            text = pageText,
                            previewBitmap = bitmap,
                            ocrResult = ocr
                        )
                    )
                }
            }
        }

        LoadedDocument(
            title = fileName,
            type = DocumentType.PDF,
            pages = pages,
            fullText = fullTextBuilder.toString()
        )
    }

    /**
     * Searches across all pages of a loaded document.
     */
    fun searchDocument(
        document: LoadedDocument,
        query: SearchQuery,
        activeGlobalIndex: Int = 0
    ): SearchResult {
        if (query.isEmpty) {
            return SearchResult(query = query, matches = emptyList(), activeIndex = -1)
        }

        val allMatches = mutableListOf<TextMatch>()
        var globalMatchId = 0

        for (page in document.pages) {
            if (page.ocrResult != null) {
                val pageSearchResult = ocrProvider.findMatchesInOcr(page.ocrResult, query)
                for (match in pageSearchResult.matches) {
                    allMatches.add(
                        match.copy(
                            id = globalMatchId++,
                            page = page.pageNumber
                        )
                    )
                }
            } else {
                val pageSearchResult = searchEngine.search(page.text, query)
                for (match in pageSearchResult.matches) {
                    allMatches.add(
                        match.copy(
                            id = globalMatchId++,
                            page = page.pageNumber
                        )
                    )
                }
            }
        }

        val resolvedIndex = when {
            allMatches.isEmpty() -> -1
            activeGlobalIndex in allMatches.indices -> activeGlobalIndex
            else -> 0
        }

        return SearchResult(
            query = query,
            matches = allMatches,
            activeIndex = resolvedIndex
        )
    }

    /**
     * Provides ready-to-test sample documents.
     */
    fun getSampleDocument(sampleType: SampleDocType): LoadedDocument {
        return when (sampleType) {
            SampleDocType.WEB_ARTICLE -> {
                val text = """
                    Android 15 Innovations: Edge-to-Edge and Adaptive Performance
                    
                    The modern Android application platform introduces enhanced edge-to-edge system insets,
                    predictive back navigation, and dynamic window size classes. Users interact with applications
                    across phones, foldables, and tablets.
                    
                    Finding text in a webpage or document is one of the most foundational user journeys.
                    When browsing documentation, reading technical specifications, or reviewing contracts,
                    the browser Find in Page capability allows instant jumping between matching keywords.
                    Universal Find brings this fluid Chrome-style find bar to your workflow.
                    
                    Key Highlights:
                    1. Instant case-sensitive and case-insensitive keyword discovery.
                    2. Precise match counter: e.g. 1 of 12 occurrences highlighted with high visibility.
                    3. Bounding box overlay alignment for OCR captures and rendered pages.
                    4. Unicode compliance: flawlessly matches multi-lingual scripts including Devanagari.
                    5. Strict zero-cloud privacy: all queries and image captures remain 100% on-device.
                """.trimIndent()
                LoadedDocument(
                    title = "Sample: Web Article (Find in Page)",
                    type = DocumentType.PLAIN_TEXT,
                    pages = listOf(DocumentPage(pageNumber = 1, text = text)),
                    fullText = text
                )
            }
            SampleDocType.MULTILINGUAL_INDIAN_SCRIPTS -> {
                val text = """
                    बहुभाषी पाठ खोज परीक्षण (Multilingual Text Search Test)
                    
                    यूनिवर्सल फाइंड (Universal Find) भारतीय भाषाओं और यूनिकोड पाठ के लिए पूरी तरह से अनुकूलित है।
                    Devanagari script support includes Hindi, Marathi, Nepali, and Sanskrit.
                    
                    उदाहरण वाक्य (Example sentences):
                    - भारत एक विशाल और सुंदर देश है। (India is a vast and beautiful country.)
                    - हिंदी भाषा में खोज करना अब बहुत आसान है। (Searching in Hindi is now very easy.)
                    - यूनिवर्सल फाइंड से आप किसी भी पृष्ठ पर शब्द खोज सकते हैं।
                    
                    Unicode Test Words:
                    - नमस्ते (Namaste)
                    - भारत (Bharat)
                    - खोज (Search / Find)
                    - तकनीक (Technology)
                    - पृष्ठ (Page)
                    
                    Repeated occurrences test:
                    खोज, खोज, खोज - यह तीन बार लिखा गया शब्द है।
                    Search, Search, Search - these words repeat for index navigation testing.
                """.trimIndent()
                LoadedDocument(
                    title = "Sample: Indian Scripts & Multilingual",
                    type = DocumentType.PLAIN_TEXT,
                    pages = listOf(DocumentPage(pageNumber = 1, text = text)),
                    fullText = text
                )
            }
            SampleDocType.TECHNICAL_SPEC -> {
                val text = """
                    SYSTEM ARCHITECTURE SPECIFICATION: Universal Find
                    
                    1. Overview
                    Universal Find is designed to replicate Chrome's Find in Page functionality across Android.
                    It operates in two principal modes:
                    - In-App Viewport Finder: Loads documents (TXT, PDF, Images) and renders interactive overlays.
                    - Floating Screen Finder: Runs as an overlay service (via MediaProjection and SYSTEM_ALERT_WINDOW)
                      to allow on-screen finding over other applications.
                      
                    2. Security and Privacy Guarantees
                    - All OCR processing is executed strictly on-device using Google ML Kit.
                    - Screen captures are held in volatile memory only for the duration of the search session.
                    - No telemetry, analytics, or screenshot data is transmitted over the network.
                    - Banking and secure apps using WindowManager.LayoutParams.FLAG_SECURE cannot and will not
                      be captured, strictly following Android platform security policy.
                      
                    3. Performance
                    - Coordinate mapping handles aspect-ratio fitting and viewport translation.
                    - Search query execution takes under 5ms on typical text payloads up to 100,000 characters.
                """.trimIndent()
                LoadedDocument(
                    title = "Sample: Technical Specification",
                    type = DocumentType.PLAIN_TEXT,
                    pages = listOf(DocumentPage(pageNumber = 1, text = text)),
                    fullText = text
                )
            }
        }
    }
}

enum class SampleDocType {
    WEB_ARTICLE,
    MULTILINGUAL_INDIAN_SCRIPTS,
    TECHNICAL_SPEC
}
