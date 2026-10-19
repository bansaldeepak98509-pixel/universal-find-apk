package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.accessibility.UniversalFindAccessibilityService
import com.example.capture.ScreenCaptureManager
import com.example.ocr.OcrResult
import com.example.ocr.OcrTextProvider
import com.example.search.SearchQuery
import com.example.search.SearchResult
import com.example.service.FloatingSearchService
import com.example.ui.components.ChromeFindBar
import com.example.ui.components.HighlightOverlayView
import com.example.ui.theme.GoogleBlue
import kotlinx.coroutines.launch

@Composable
fun ScreenSearchScreen(
    ocrProvider: OcrTextProvider,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val captureManager = remember { ScreenCaptureManager(context) }

    var searchQuery by remember { mutableStateOf(SearchQuery(text = "")) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var ocrResult by remember { mutableStateOf<OcrResult?>(null) }
    var searchResult by remember { mutableStateOf(SearchResult(query = searchQuery)) }

    var isCapturing by remember { mutableStateOf(false) }
    var isFloatingActive by remember { mutableStateOf(FloatingSearchService.isServiceRunning) }
    var statusText by remember { mutableStateOf<String?>(null) }

    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            coroutineScope.launch {
                isCapturing = true
                statusText = "Capturing screen buffer…"
                val bitmap = captureManager.captureScreen(result.resultCode, result.data!!)
                if (bitmap != null) {
                    capturedBitmap = bitmap
                    statusText = "Running on-device ML Kit OCR…"
                    try {
                        val ocr = ocrProvider.recognizeText(bitmap)
                        ocrResult = ocr
                        searchResult = ocrProvider.findMatchesInOcr(ocr, searchQuery)
                        statusText = null
                    } catch (e: Exception) {
                        val msg = e.message ?: "OCR error"
                        statusText = if (msg.contains("download", ignoreCase = true)) {
                            "OCR model is downloading in background. Please wait a moment and try again."
                        } else {
                            "OCR error: $msg"
                        }
                    }
                } else {
                    statusText = "Screen capture failed"
                }
                isCapturing = false
            }
        } else {
            statusText = "Screen capture permission declined"
        }
    }

    LaunchedEffect(searchQuery, ocrResult) {
        val ocr = ocrResult
        if (ocr != null) {
            searchResult = ocrProvider.findMatchesInOcr(ocr, searchQuery)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ChromeFindBar(
                query = searchQuery,
                searchResult = searchResult,
                onQueryChange = { newQ -> searchQuery = newQ },
                onNextMatch = {
                    if (searchResult.totalMatches > 0) {
                        val next = (searchResult.activeIndex + 1) % searchResult.totalMatches
                        searchResult = searchResult.copy(activeIndex = next)
                    }
                },
                onPreviousMatch = {
                    if (searchResult.totalMatches > 0) {
                        val prev = if (searchResult.activeIndex <= 0) searchResult.totalMatches - 1 else searchResult.activeIndex - 1
                        searchResult = searchResult.copy(activeIndex = prev)
                    }
                },
                onClose = {
                    searchQuery = searchQuery.copy(text = "")
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Live Floating Bubble Control Card
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("floating_control_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Universal Floating Widget",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Float a Chrome-style search bubble over WhatsApp, Instagram, PDFs, or any Android app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isFloatingActive,
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    val serviceIntent = Intent(context, FloatingSearchService::class.java).apply {
                                        action = FloatingSearchService.ACTION_START
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(serviceIntent)
                                    } else {
                                        context.startService(serviceIntent)
                                    }
                                    isFloatingActive = true
                                }
                            } else {
                                val serviceIntent = Intent(context, FloatingSearchService::class.java).apply {
                                    action = FloatingSearchService.ACTION_STOP
                                }
                                context.stopService(serviceIntent)
                                isFloatingActive = false
                            }
                        },
                        modifier = Modifier.testTag("floating_bubble_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Screen Capture Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val intent = captureManager.createScreenCaptureIntent()
                        projectionLauncher.launch(intent)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("capture_screen_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Capture & OCR Screen")
                }

                if (capturedBitmap != null) {
                    OutlinedButton(
                        onClick = {
                            capturedBitmap = null
                            ocrResult = null
                            searchResult = SearchResult(query = searchQuery)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Clear")
                    }
                }
            }

            if (isCapturing || statusText != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = statusText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Screen Capture Frame Display with Overlay
            val bitmap = capturedBitmap
            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Screen Capture Preview",
                        modifier = Modifier.fillMaxSize()
                    )

                    HighlightOverlayView(
                        searchResult = searchResult,
                        sourceWidth = bitmap.width.toFloat(),
                        sourceHeight = bitmap.height.toFloat()
                    )
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = GoogleBlue,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Find Anything on Screen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'Capture & OCR Screen' to snapshot the active display and search text in any messaging, reading, or video app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
