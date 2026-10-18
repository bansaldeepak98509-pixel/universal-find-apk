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
    val rawText: String,
    val items: List<OcrItem>,
    val imageWidth: Int,
    val imageHeight: Int,
    val recognizedScript: String = "Latin/Devanagari"
)

class OcrTextProvider {

    // Latin recognizer
    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // Devanagari recognizer for Hindi, Marathi, Sanskrit and related Indian scripts
    private val devanagariRecognizer by lazy {
        try {
            TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Recognizes text from a Bitmap on-device using ML Kit.
     * Safely processes on IO dispatcher and returns bounding boxes.
     */
    suspend fun recognizeText(bitmap: Bitmap, preferDevanagari: Boolean = false): OcrResult =
        withContext(Dispatchers.Default) {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = if (preferDevanagari && devanagariRecognizer != null) {
                devanagariRecognizer!!
            } else {
                latinRecognizer
            }

            try {
                suspendCancellableCoroutine { continuation ->
                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val items = mutableListOf<OcrItem>()
                            var lineIdx = 0

                            for (block in visionText.textBlocks) {
                                for (line in block.lines) {
                                    for (element in line.elements) {
                                        val box = element.boundingBox ?: Rect(0, 0, 0, 0)
                                        items.add(
                                            OcrItem(
                                                text = element.text,
                                                boundingBox = MatchRect.fromAndroidRect(box),
                                                lineIndex = lineIdx,
                                                elementIndex = items.size
                                            )
                                        )
                                    }
                                    lineIdx++
                                }
                            }

                            val result = OcrResult(
                                rawText = visionText.text,
                                items = items,
                                imageWidth = bitmap.width,
                                imageHeight = bitmap.height,
                                recognizedScript = if (preferDevanagari) "Devanagari" else "Latin"
                            )
                            if (continuation.isActive) {
                                continuation.resume(result)
                            }
                        }
                        .addOnFailureListener { exception ->
                            if (continuation.isActive) {
                                continuation.resumeWithException(exception)
                            }
                        }
                }
            } catch (e: Exception) {
                // Return empty result on failure without crashing
                OcrResult(
                    rawText = "",
                    items = emptyList(),
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    recognizedScript = "Error: ${e.message}"
                )
            }
        }

    /**
     * Maps a SearchQuery over OCR items to compute precise bounding boxes for every match.
     */
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
