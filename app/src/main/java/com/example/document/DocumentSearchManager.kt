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
    IMAGE,
    PDF
}

data class DocumentPage(
    val pageNumber: Int,
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
    private val ocrProvider: OcrTextProvider? = null,
    private val searchEngine: SearchEngine = SearchEngine()
) {
    private val activeOcrProvider by lazy { ocrProvider ?: OcrTextProvider() }

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

    suspend fun loadImageFromUri(uri: Uri, fileName: String = ""): LoadedDocument =
        withContext(Dispatchers.IO) {
            val resolvedTitle = if (fileName.isNotBlank()) fileName else queryFileName(uri, "Scanned Image.png")
            val contentResolver = context.contentResolver
            val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: throw IllegalArgumentException("Could not decode image from URI")

            val ocr = activeOcrProvider.recognizeText(bitmap)
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
                    val width = page.width * 2
                    val height = page.height * 2
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val ocr = activeOcrProvider.recognizeText(bitmap)
                    pages.add(
                        DocumentPage(
                            pageNumber = i + 1,
                            text = ocr.rawText,
                            previewBitmap = bitmap,
                            ocrResult = ocr
                        )
                    )
                    fullTextBuilder.append(ocr.rawText).append("\n--- Page Break ---\n")
                }
            }
        }

        LoadedDocument(
            title = resolvedTitle,
            type = DocumentType.PDF,
            pages = pages,
            fullText = fullTextBuilder.toString()
        )
    }

    fun searchDocument(document: LoadedDocument, query: SearchQuery): SearchResult {
        if (query.isEmpty) {
            return SearchResult(query = query, matches = emptyList(), activeIndex = -1)
        }

        val allMatches = mutableListOf<TextMatch>()
        var globalMatchId = 0

        for (page in document.pages) {
            val pageSearchResult = if (page.ocrResult != null) {
                activeOcrProvider.findMatchesInOcr(page.ocrResult, query)
            } else {
                searchEngine.search(page.text, query)
            }

            for (match in pageSearchResult.matches) {
                allMatches.add(match.copy(id = globalMatchId++))
            }
        }

        val activeIdx = if (allMatches.isNotEmpty()) 0 else -1
        return SearchResult(
            query = query,
            matches = allMatches,
            activeIndex = activeIdx
        )
    }

    fun getSampleDocument(sampleType: SampleDocType): LoadedDocument {
        val (title, text) = when (sampleType) {
            SampleDocType.WEB_ARTICLE -> Pair(
                "Sample: Web Article (Find in Page)",
                """
                Chrome Find in Page Features Overview
                
                The Google Chrome browser features one of the most reliable and intuitive 'Find in page' experiences across desktop and mobile operating systems.
                
                Key functionality includes:
                1. Immediate incremental matching as you type each letter.
                2. Visible count badges such as 1/14 or 3/20 indicating current selection and total occurrences.
                3. Directional navigation buttons (Previous and Next arrows) with cyclic wrap-around navigation.
                4. Distinct highlight states: the currently selected match is glowing vibrant yellow with an orange border, while other matches are soft yellow.
                5. An overview tick mini-map showing where matches exist across long documents.
                
                Universal Find brings this exact Google Chrome desktop and Android browsing luxury directly to every app, scanned receipt, PDF document, and screenshot on your phone!
                """.trimIndent()
            )
            SampleDocType.MULTILINGUAL_INDIAN_SCRIPTS -> Pair(
                "Sample: Multilingual & Indian Scripts",
                """
                भारत में भाषा और प्रौद्योगिकी का संगम (Universal Find in Hindi & Indian Languages)
                
                यूनिवर्सल फाइंड एक आधुनिक खोज इंजन है जो देवनागरी लिपि और भारतीय भाषाओं को पूर्ण रूप से समर्थन देता है।
                
                मुख्य विशेषताएं:
                - हिंदी (Hindi), मराठी (Marathi), और संस्कृत (Sanskrit) शब्दों की सटीक पहचान।
                - मात्राओं और संयुक्त अक्षरों (जैसे: 'खोज', 'प्रौद्योगिकी', 'ज्ञान', 'भारतीय') का सटीक मिलान।
                - OCR तकनीक द्वारा स्क्रीन और तस्वीरों में छिपे शब्दों को तुरंत खोजना।
                
                यह खोज प्रणाली केस-सेंसिटिव और रेगुलर एक्सप्रेशन (Regex) दोनों का समर्थन करती है।
                """.trimIndent()
            )
            SampleDocType.TECHNICAL_SPEC -> Pair(
                "Sample: Android System Spec",
                """
                Universal Find Android Architecture Specification
                
                Package ID: com.aistudio.universalfind.uqzrtk
                Target SDK: 36 (Android 15+)
                Min SDK: 24 (Android 7.0 Nougat)
                
                Core Components:
                - FloatingSearchService: SYSTEM_ALERT_WINDOW floating bubble with FLAG_NOT_FOCUSABLE.
                - UniversalFindAccessibilityService: BIND_ACCESSIBILITY_SERVICE view hierarchy inspection.
                - ScreenCaptureManager: MediaProjection API high-framerate buffer reader.
                - OcrTextProvider: Google Play Services ML Kit On-Device Text Recognition.
                - SearchEngine: NFC normalization, Boyer-Moore style indexOf, regex patterns, and Devanagari Unicode support.
                
                Security & Privacy:
                All OCR inference and text indexing happens completely on-device. Zero telemetry and zero off-device network transmission. FLAG_SECURE prevents capturing DRM content.
                """.trimIndent()
            )
        }

        val page = DocumentPage(pageNumber = 1, text = text)
        return LoadedDocument(
            title = title,
            type = DocumentType.PLAIN_TEXT,
            pages = listOf(page),
            fullText = text
        )
    }
}

enum class SampleDocType {
    WEB_ARTICLE,
    MULTILINGUAL_INDIAN_SCRIPTS,
    TECHNICAL_SPEC
}
