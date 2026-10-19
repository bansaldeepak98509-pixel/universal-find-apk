package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.document.DocumentPage
import com.example.document.DocumentSearchManager
import com.example.document.DocumentType
import com.example.document.LoadedDocument
import com.example.document.SampleDocType
import com.example.search.SearchQuery
import com.example.search.SearchResult
import com.example.ui.components.ChromeFindBar
import com.example.ui.components.HighlightOverlayView
import com.example.ui.theme.HighlightActiveBorder
import com.example.ui.theme.HighlightActiveColor
import com.example.ui.theme.HighlightInactiveColor
import com.example.ui.theme.SearchAmberAccent
import kotlinx.coroutines.launch

@Composable
fun DocumentViewerScreen(
    documentManager: DocumentSearchManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf(SearchQuery(text = "")) }
    var currentDocument by remember {
        mutableStateOf(documentManager.getSampleDocument(SampleDocType.WEB_ARTICLE))
    }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var searchResult by remember { mutableStateOf(SearchResult(query = searchQuery)) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val textPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                isLoading = true
                statusMessage = "Loading text file…"
                try {
                    val doc = documentManager.loadTextFromUri(it)
                    currentDocument = doc
                    selectedPageIndex = 0
                    searchResult = documentManager.searchDocument(doc, searchQuery)
                    statusMessage = null
                } catch (e: Exception) {
                    statusMessage = "Error loading text: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                isLoading = true
                statusMessage = "Running on-device OCR on image…"
                try {
                    val doc = documentManager.loadImageFromUri(it)
                    currentDocument = doc
                    selectedPageIndex = 0
                    searchResult = documentManager.searchDocument(doc, searchQuery)
                    statusMessage = null
                } catch (e: Exception) {
                    val msg = e.message ?: "Unknown error"
                    statusMessage = if (msg.contains("download", ignoreCase = true)) {
                        "OCR model is downloading in background. Please wait a moment and try again."
                    } else {
                        "Error OCRing image: $msg"
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                isLoading = true
                statusMessage = "Rendering PDF pages & OCRing on-device…"
                try {
                    val doc = documentManager.loadPdfFromUri(it)
                    currentDocument = doc
                    selectedPageIndex = 0
                    searchResult = documentManager.searchDocument(doc, searchQuery)
                    statusMessage = null
                } catch (e: Exception) {
                    val msg = e.message ?: "Unknown error"
                    statusMessage = if (msg.contains("download", ignoreCase = true)) {
                        "OCR model is downloading in background. Please wait a moment and try again."
                    } else {
                        "Error parsing PDF: $msg"
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(searchQuery, currentDocument) {
        searchResult = documentManager.searchDocument(currentDocument, searchQuery)
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
        ) {
            // Document Selection & Import Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sample 1: Web Article
                SampleChip(
                    label = "Web Article",
                    isSelected = currentDocument.title.contains("Web Article"),
                    onClick = {
                        currentDocument = documentManager.getSampleDocument(SampleDocType.WEB_ARTICLE)
                        selectedPageIndex = 0
                    }
                )
                // Sample 2: Multilingual Hindi
                SampleChip(
                    label = "Hindi & Devanagari",
                    isSelected = currentDocument.title.contains("Multilingual"),
                    onClick = {
                        currentDocument = documentManager.getSampleDocument(SampleDocType.MULTILINGUAL_INDIAN_SCRIPTS)
                        selectedPageIndex = 0
                    }
                )
                // Sample 3: Technical Spec
                SampleChip(
                    label = "System Spec",
                    isSelected = currentDocument.title.contains("Spec"),
                    onClick = {
                        currentDocument = documentManager.getSampleDocument(SampleDocType.TECHNICAL_SPEC)
                        selectedPageIndex = 0
                    }
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Import Actions
                OutlinedButton(
                    onClick = { textPickerLauncher.launch("text/plain") },
                    modifier = Modifier.testTag("import_txt_button")
                ) {
                    Text("+ Text")
                }
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.testTag("import_img_button")
                ) {
                    Text("+ Image")
                }
                OutlinedButton(
                    onClick = { pdfPickerLauncher.launch("application/pdf") },
                    modifier = Modifier.testTag("import_pdf_button")
                ) {
                    Text("+ PDF")
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(statusMessage ?: "Loading…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Document Viewport
            val activePage = currentDocument.pages.getOrNull(selectedPageIndex) ?: currentDocument.pages.firstOrNull()

            if (activePage != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    if (activePage.previewBitmap != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                bitmap = activePage.previewBitmap.asImageBitmap(),
                                contentDescription = "Scanned Page Document",
                                modifier = Modifier.fillMaxSize()
                            )
                            HighlightOverlayView(
                                searchResult = searchResult,
                                sourceWidth = activePage.previewBitmap.width.toFloat(),
                                sourceHeight = activePage.previewBitmap.height.toFloat()
                            )
                        }
                    } else {
                        // Plain Text Document View with Inline Highlights
                        val scrollState = rememberScrollState()
                        val annotatedText = buildAnnotatedDocText(activePage.text, searchResult)

                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = annotatedText,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    lineHeight = 26.sp,
                                    fontFamily = FontFamily.Default
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .verticalScroll(scrollState)
                                    .testTag("plain_text_viewport")
                            )

                            // Document mini-map tick indicators on right
                            HighlightOverlayView(
                                searchResult = searchResult,
                                sourceWidth = 1000f,
                                sourceHeight = (activePage.text.length * 1.8f).coerceAtLeast(1000f)
                            )
                        }
                    }
                }
            }

            // Multi-Page Pagination Bar if Document has multiple pages
            if (currentDocument.pages.size > 1) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (selectedPageIndex > 0) selectedPageIndex-- },
                            enabled = selectedPageIndex > 0
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Previous Page")
                        }

                        Text(
                            text = "Page ${selectedPageIndex + 1} of ${currentDocument.pages.size}",
                            style = MaterialTheme.typography.labelLarge
                        )

                        IconButton(
                            onClick = { if (selectedPageIndex < currentDocument.pages.size - 1) selectedPageIndex++ },
                            enabled = selectedPageIndex < currentDocument.pages.size - 1
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Next Page")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) ButtonDefaults.outlinedButtonBorder else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildAnnotatedDocText(source: String, searchResult: SearchResult): AnnotatedString {
    if (searchResult.matches.isEmpty()) {
        return AnnotatedString(source)
    }

    return buildAnnotatedString {
        append(source)
        for ((idx, match) in searchResult.matches.withIndex()) {
            val isCurrent = (idx == searchResult.activeIndex)
            val start = match.start.coerceIn(0, source.length)
            val end = match.end.coerceIn(0, source.length)
            if (start < end) {
                addStyle(
                    style = SpanStyle(
                        background = if (isCurrent) HighlightActiveColor else HighlightInactiveColor,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    ),
                    start = start,
                    end = end
                )
            }
        }
    }
}
