package com.example.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.capture.ScreenCaptureManager
import com.example.ocr.OcrResult
import com.example.ocr.OcrTextProvider
import com.example.search.SearchQuery
import com.example.search.SearchResult
import com.example.ui.components.ChromeFindBar
import com.example.ui.components.HighlightOverlayView
import com.example.ui.theme.ElectricBluePrimary
import com.example.ui.theme.SearchAmberAccent
import kotlinx.coroutines.launch

@Composable
fun ScreenSearchScreen(
    captureManager: ScreenCaptureManager,
    ocrProvider: OcrTextProvider,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf(SearchQuery(text = "")) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentOcrResult by remember { mutableStateOf<OcrResult?>(null) }
    var searchResult by remember { mutableStateOf(SearchResult(query = searchQuery)) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    // MediaProjection Consent Intent Launcher
    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            coroutineScope.launch {
                isProcessing = true
                statusText = "Capturing screen via MediaProjection…"
                try {
                    captureManager.startProjection(result.resultCode, result.data!!)
                    val bitmap = captureManager.captureScreen()
                    if (bitmap != null) {
                        currentBitmap = bitmap
                        statusText = "Analyzing text with on-device ML Kit…"
                        val ocr = ocrProvider.recognizeText(bitmap)
                        currentOcrResult = ocr
                        searchResult = ocrProvider.findMatchesInOcr(ocr, searchQuery)
                        statusText = null
                    } else {
                        statusText = "Unable to read frame (FLAG_SECURE or surface timeout)."
                    }
                } catch (e: Exception) {
                    statusText = "Capture failed: ${e.message}"
                } finally {
                    isProcessing = false
                }
            }
        } else {
            statusText = "Screen capture permission declined by user."
        }
    }

    fun triggerSearch(query: SearchQuery, activeIdx: Int = 0) {
        searchQuery = query
        val ocr = currentOcrResult
        if (ocr != null) {
            searchResult = ocrProvider.findMatchesInOcr(ocr, query, activeIdx)
        }
    }

    // Load initial simulated screen if no capture has been run yet
    LaunchedEffect(Unit) {
        if (currentBitmap == null) {
            val sampleBmp = generateSimulatedScreenBitmap()
            currentBitmap = sampleBmp
            val ocr = ocrProvider.recognizeText(sampleBmp)
            currentOcrResult = ocr
            searchResult = ocrProvider.findMatchesInOcr(ocr, searchQuery)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Find Bar
        ChromeFindBar(
            query = searchQuery,
            searchResult = searchResult,
            onQueryChanged = { text ->
                triggerSearch(searchQuery.copy(text = text))
            },
            onMatchCaseToggled = { matchCase ->
                triggerSearch(searchQuery.copy(matchCase = matchCase))
            },
            onWholeWordToggled = { wholeWord ->
                triggerSearch(searchQuery.copy(wholeWord = wholeWord))
            },
            onRegexToggled = { regex ->
                triggerSearch(searchQuery.copy(useRegex = regex))
            },
            onPreviousMatch = {
                val nextActive = if (searchResult.activeIndex <= 0) {
                    searchResult.matches.size - 1
                } else {
                    searchResult.activeIndex - 1
                }
                triggerSearch(searchQuery, nextActive)
            },
            onNextMatch = {
                val nextActive = if (searchResult.matches.isNotEmpty()) {
                    (searchResult.activeIndex + 1) % searchResult.matches.size
                } else {
                    -1
                }
                triggerSearch(searchQuery, nextActive)
            },
            onClose = {
                triggerSearch(searchQuery.copy(text = ""))
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Action Buttons Row: MediaProjection Capture & Screen Simulator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val intent = captureManager.createScreenCaptureIntent()
                    if (intent != null) {
                        projectionLauncher.launch(intent)
                    } else {
                        statusText = "MediaProjection is not supported on this device/emulator."
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("capture_screen_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBluePrimary)
            ) {
                Icon(Icons.Default.Cast, contentDescription = null, Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Capture Screen", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        isProcessing = true
                        statusText = "Rendering simulated screen & analyzing…"
                        val sampleBmp = generateSimulatedScreenBitmap()
                        currentBitmap = sampleBmp
                        val ocr = ocrProvider.recognizeText(sampleBmp)
                        currentOcrResult = ocr
                        searchResult = ocrProvider.findMatchesInOcr(ocr, searchQuery)
                        isProcessing = false
                        statusText = null
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Simulated App", fontSize = 12.sp)
            }
        }

        if (statusText != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = statusText!!,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Screen Preview Area with Interactive Highlights
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ElectricBluePrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = statusText ?: "Processing screen capture…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (currentBitmap != null) {
                    val bmp = currentBitmap!!
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Screen Capture",
                            modifier = Modifier.fillMaxSize()
                        )

                        HighlightOverlayView(
                            searchResult = searchResult,
                            sourceWidth = bmp.width.toFloat(),
                            sourceHeight = bmp.height.toFloat(),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Text(
                        text = "No screen capture available. Tap 'Capture Screen' above.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Privacy indicator badge
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Security",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "100% On-Device OCR. Screenshots are never saved or uploaded.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Creates a clean simulated Android screen with rich text for instant testing.
 */
private fun generateSimulatedScreenBitmap(): Bitmap {
    val width = 1080
    val height = 1920
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Dark slate background
    canvas.drawColor(Color.parseColor("#0F172A"))

    // Header bar
    val headerPaint = Paint().apply {
        color = Color.parseColor("#1E293B")
        style = Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, width.toFloat(), 180f, headerPaint)

    val titlePaint = Paint().apply {
        color = Color.WHITE
        textSize = 54f
        isAntiAlias = true
        isFakeBoldText = true
    }
    canvas.drawText("App Feed & Settings", 60f, 110f, titlePaint)

    // Card 1
    val cardPaint = Paint().apply {
        color = Color.parseColor("#1E293B")
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(40f, 220f, (width - 40).toFloat(), 600f, 24f, 24f, cardPaint)

    val textPaint = Paint().apply {
        color = Color.parseColor("#F8FAFC")
        textSize = 42f
        isAntiAlias = true
    }
    val highlightColor = Color.parseColor("#38BDF8")
    val subPaint = Paint().apply {
        color = highlightColor
        textSize = 34f
        isAntiAlias = true
        isFakeBoldText = true
    }

    canvas.drawText("Chrome Find in Page Features", 80f, 300f, subPaint)
    canvas.drawText("Universal Find brings Find on page to Android apps.", 80f, 380f, textPaint)
    canvas.drawText("Search through on-screen text instantly.", 80f, 450f, textPaint)
    canvas.drawText("Works with Latin, Numbers, and Devanagari text.", 80f, 520f, textPaint)

    // Card 2
    canvas.drawRoundRect(40f, 640f, (width - 40).toFloat(), 1100f, 24f, 24f, cardPaint)
    canvas.drawText("Security & On-Device Processing", 80f, 720f, subPaint)
    canvas.drawText("1. MediaProjection with explicit user consent", 80f, 800f, textPaint)
    canvas.drawText("2. On-device Google ML Kit Vision text recognizer", 80f, 870f, textPaint)
    canvas.drawText("3. Zero data telemetry or cloud uploads", 80f, 940f, textPaint)
    canvas.drawText("4. Respects FLAG_SECURE window restrictions", 80f, 1010f, textPaint)

    // Card 3
    canvas.drawRoundRect(40f, 1140f, (width - 40).toFloat(), 1600f, 24f, 24f, cardPaint)
    canvas.drawText("Devanagari Test / भारतीय भाषा परीक्षण", 80f, 1220f, subPaint)
    canvas.drawText("भारत एक विशाल और प्रगतिशील देश है।", 80f, 1300f, textPaint)
    canvas.drawText("यूनिवर्सल फाइंड से आप किसी भी शब्द को खोज सकते हैं।", 80f, 1370f, textPaint)
    canvas.drawText("खोज (Search) तकनीक हर जगह काम करती है।", 80f, 1440f, textPaint)
    canvas.drawText("Chrome Find style navigation with next and prev.", 80f, 1510f, textPaint)

    return bitmap
}
