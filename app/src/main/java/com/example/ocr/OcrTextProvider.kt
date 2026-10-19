package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.search.MatchRect
import com.example.search.SearchEngine
import com.example.search.SearchQuery
import com.example.search.SearchResult
import com.example.search.TextMatch
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.Normalizer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrItem(
    val text: String,
    val boundingBox: MatchRect,
    val lineIndex: Int = 0,
    val elementIndex: Int = 0
)

data class OcrResult(
    val items: List<OcrItem> = emptyList(),
    val rawText: String = "",
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
)

class OcrTextProvider(
    private val useDevanagari: Boolean = true
) {
    private val primaryRecognizer by lazy {
        if (useDevanagari) {
            TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        } else {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
    }

    private val fallbackRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeText(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        try {
            processImage(primaryRecognizer, image, bitmap.width, bitmap.height)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (useDevanagari && msg.contains("download", ignoreCase = true)) {
                // If optional Devanagari module is downloading, fallback to default bundled recognizer
                processImage(fallbackRecognizer, image, bitmap.width, bitmap.height)
            } else {
                throw e
            }
        }
    }

    private suspend fun processImage(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        image: InputImage,
        width: Int,
        height: Int
    ): OcrResult = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val items = mutableListOf<OcrItem>()
                val fullTextBuilder = StringBuilder()

                var lineIdx = 0
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        var elemIdx = 0
                        for (element in line.elements) {
                            val box = element.boundingBox ?: Rect(0, 0, 0, 0)
                            val matchRect = MatchRect(
                                left = box.left.toFloat(),
                                top = box.top.toFloat(),
                                right = box.right.toFloat(),
                                bottom = box.bottom.toFloat()
                            )
                            items.add(
                                OcrItem(
                                    text = element.text,
                                    boundingBox = matchRect,
                                    lineIndex = lineIdx,
                                    elementIndex = elemIdx++
                                )
                            )
                        }
                        fullTextBuilder.append(line.text).append("\n")
                        lineIdx++
                    }
                }

                continuation.resume(
                    OcrResult(
                        items = items,
                        rawText = fullTextBuilder.toString().trim(),
                        imageWidth = width,
                        imageHeight = height
                    )
                )
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    private val searchEngine = SearchEngine()

    fun findMatchesInOcr(
        ocrResult: OcrResult,
        query: SearchQuery,
        activeIndex: Int = 0
    ): SearchResult {
        if (query.isEmpty || ocrResult.items.isEmpty()) {
            return SearchResult(query = query, matches = emptyList(), activeIndex = -1)
        }

        val allMatches = mutableListOf<TextMatch>()
        var globalMatchId = 0

        for (item in ocrResult.items) {
            val itemSearchResult = searchEngine.search(item.text, query)
            if (itemSearchResult.errorMessage != null && allMatches.isEmpty()) {
                return SearchResult(
                    query = query,
                    matches = emptyList(),
                    activeIndex = -1,
                    errorMessage = itemSearchResult.errorMessage
                )
            }

            for (match in itemSearchResult.matches) {
                val itemLen = item.text.length.coerceAtLeast(1)
                val fractionStart = (match.start.toFloat() / itemLen).coerceIn(0f, 1f)
                val fractionEnd = (match.end.toFloat() / itemLen).coerceIn(0f, 1f)

                val boxWidth = item.boundingBox.width
                val matchLeft = item.boundingBox.left + (boxWidth * fractionStart)
                val matchRight = item.boundingBox.left + (boxWidth * fractionEnd)

                val matchRect = MatchRect(
                    left = matchLeft,
                    top = item.boundingBox.top,
                    right = matchRight,
                    bottom = item.boundingBox.bottom
                )

                allMatches.add(
                    match.copy(
                        id = globalMatchId++,
                        rect = matchRect,
                        snippet = item.text
                    )
                )
            }
        }

        val resolvedIndex = when {
            allMatches.isEmpty() -> -1
            activeIndex in allMatches.indices -> activeIndex
            else -> 0
        }

        return SearchResult(
            query = query,
            matches = allMatches,
            activeIndex = resolvedIndex
        )
    }
}
