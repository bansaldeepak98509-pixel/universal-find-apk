package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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

    // System File Pickers
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
                    statusMessage = "Error OCRing image: ${e.message}"
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
                    statusMessage = "Error rendering PDF: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Refresh search results when query or document changes
    fun performSearch(query: SearchQuery, activeIdx: Int = 0) {
        searchQuery = query
        searchResult = documentManager.searchDocument(currentDocument, query, activeIdx)
        // Auto-switch to active match's page if multi-page
        searchResult.activeMatch?.let { match ->
            val pageIdx = (match.page - 1).coerceIn(0, currentDocument.pages.size - 1)
            if (pageIdx != selectedPageIndex) {
                selectedPageIndex = pageIdx
            }
        }
    }

    // Auto-search on initial load or sample change
    LaunchedEffect(currentDocument) {
        performSearch(searchQuery)
    }

    val textScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Chrome-style Find Bar docked at top
        ChromeFindBar(
            query = searchQuery,
            searchResult = searchResult,
            onQueryChanged = { text ->
                performSearch(searchQuery.copy(text = text))
            },
            onMatchCaseToggled = { matchCase ->
                performSearch(searchQuery.copy(matchCase = matchCase))
            },
            onWholeWordToggled = { wholeWord ->
                performSearch(searchQuery.copy(wholeWord = wholeWord))
            },
            onRegexToggled = { regex ->
                performSearch(searchQuery.copy(useRegex = regex))
            },
            onPreviousMatch = {
                val nextActive = if (searchResult.activeIndex <= 0) {
                    searchResult.matches.size - 1
                } else {
                    searchResult.activeIndex - 1
                }
                performSearch(searchQuery, nextActive)
            },
            onNextMatch = {
                val nextActive = if (searchResult.matches.isNotEmpty()) {
                    (searchResult.activeIndex + 1) % searchResult.matches.size
                } else {
                    -1
                }
                performSearch(searchQuery, nextActive)
            },
            onClose = {
                performSearch(searchQuery.copy(text = ""))
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Document Source Picker Row (Sample presets & File Import buttons)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                AssistChip(
                    onClick = {
                        currentDocument = documentManager.getSampleDocument(SampleDocType.WEB_ARTICLE)
                        selectedPageIndex = 0
                    },
                    label = { Text("Web Article") },
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, Modifier.size(16.dp)) }
                )
            }
            item {
                AssistChip(
                    onClick = {
                        currentDocument = documentManager.getSampleDocument(SampleDocType.MULTILINGUAL_INDIAN_SCRIPTS)
                        selectedPageIndex = 0
                    },
                    label = { Text("Hindi / Devanagari") },
                    leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null, Modifier.size(16.dp)) }
                )
            }
            item {
                AssistChip(
                    onClick = {
                        currentDocument = documentManager.getSampleDocument(SampleDocType.TECHNICAL_SPEC)
                        selectedPageIndex = 0
                    },
                    label = { Text("Tech Spec") },
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, Modifier.size(16.dp)) }
                )
            }
            item {
                AssistChip(
                    onClick = { textPickerLauncher.launch("text/plain") },
                    label = { Text("Import TXT") },
                    leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null, Modifier.size(16.dp)) }
                )
            }
            item {
                AssistChip(
                    onClick = { pdfPickerLauncher.launch("application/pdf") },
                    label = { Text("Import PDF") },
                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, Modifier.size(16.dp)) }
                )
            }
            item {
                AssistChip(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    label = { Text("Import Image") },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, Modifier.size(16.dp)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Multi-page selector if document has multiple pages
        if (currentDocument.pages.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = selectedPageIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                edgePadding = 0.dp
            ) {
                currentDocument.pages.forEachIndexed { idx, _ ->
                    Tab(
                        selected = selectedPageIndex == idx,
                        onClick = { selectedPageIndex = idx },
                        text = { Text("Page ${idx + 1}") }
                    )
                }
            }
        }

        // Active Document Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = currentDocument.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (searchResult.totalMatches > 0) {
                Text(
                    text = "${searchResult.totalMatches} matches found",
                    fontSize = 12.sp,
                    color = SearchAmberAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Document Viewer Canvas / Text Area
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = statusMessage ?: "Processing document…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val activePage = currentDocument.pages.getOrNull(selectedPageIndex)
                        ?: currentDocument.pages.firstOrNull()

                    if (activePage?.previewBitmap != null) {
                        // Image or PDF Render with Canvas Highlight Overlay
                        val bitmap = activePage.previewBitmap
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Document page ${activePage.pageNumber}",
                                modifier = Modifier.fillMaxSize()
                            )

                            // Render Chrome-style Highlight Overlays and vertical tick marks
                            val pageMatches = searchResult.matches.filter { it.page == activePage.pageNumber }
                            val pageSearchResult = searchResult.copy(matches = pageMatches)

                            HighlightOverlayView(
                                searchResult = pageSearchResult,
                                sourceWidth = bitmap.width.toFloat(),
                                sourceHeight = bitmap.height.toFloat(),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else if (activePage != null) {
                        // Plain Text Viewer with formatted highlights
                        val rawText = activePage.text
                        val annotatedText = buildAnnotatedStringWithHighlights(
                            text = rawText,
                            query = searchQuery,
                            searchResult = searchResult,
                            activePage = activePage.pageNumber
                        )

                        Text(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 22.sp,
                                fontSize = 14.sp
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(textScrollState)
                                .testTag("document_text_view")
                        )
                    }
                }
            }
        }

        // Bottom Match Snippets Carousel (Instant tap to jump to any match)
        if (searchResult.totalMatches > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            val listState = rememberLazyListState()

            LaunchedEffect(searchResult.activeIndex) {
                if (searchResult.activeIndex in searchResult.matches.indices) {
                    listState.animateScrollToItem(searchResult.activeIndex)
                }
            }

            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(searchResult.matches) { index, match ->
                    val isActive = index == searchResult.activeIndex
                    Card(
                        modifier = Modifier
                            .width(220.dp)
                            .clickable {
                                performSearch(searchQuery, index)
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive)
                                HighlightActiveColor.copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (isActive)
                            CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(HighlightActiveBorder))
                        else null
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "#${index + 1} (Page ${match.page})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isActive) HighlightActiveBorder else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = match.matchedText,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = match.snippet,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildAnnotatedStringWithHighlights(
    text: String,
    query: SearchQuery,
    searchResult: SearchResult,
    activePage: Int
): AnnotatedString {
    if (query.isEmpty || searchResult.matches.isEmpty()) {
        return AnnotatedString(text)
    }

    val builder = AnnotatedString.Builder(text)
    val pageMatches = searchResult.matches.filter { it.page == activePage }

    for ((index, match) in pageMatches.withIndex()) {
        val s = match.start.coerceIn(0, text.length)
        val e = match.end.coerceIn(0, text.length)
        if (s >= e) continue

        val isCurrentActive = (searchResult.activeIndex >= 0 &&
                searchResult.activeIndex < searchResult.matches.size &&
                searchResult.matches[searchResult.activeIndex].id == match.id)

        val bg = if (isCurrentActive) HighlightActiveColor else HighlightInactiveColor
        val textColor = androidx.compose.ui.graphics.Color(0xFF0F172A)

        builder.addStyle(
            style = SpanStyle(
                background = bg,
                color = textColor,
                fontWeight = if (isCurrentActive) FontWeight.ExtraBold else FontWeight.Medium
            ),
            start = s,
            end = e
        )
    }

    return builder.toAnnotatedString()
}
