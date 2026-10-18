package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.example.capture.ScreenCaptureManager
import com.example.document.DocumentSearchManager
import com.example.ocr.OcrTextProvider
import com.example.search.SearchEngine
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.DocumentViewerScreen
import com.example.ui.screens.FloatingGuideScreen
import com.example.ui.screens.ScreenSearchScreen
import com.example.ui.theme.ElectricBluePrimary
import com.example.ui.theme.UniversalFindTheme

enum class AppDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    FIND_IN_PAGE("Find in Page", Icons.Filled.Description, Icons.Outlined.Description, "nav_find_in_page"),
    SCREEN_OCR("Screen Find", Icons.Filled.Screenshot, Icons.Outlined.Screenshot, "nav_screen_ocr"),
    FLOATING("Floating & Privacy", Icons.Filled.Layers, Icons.Outlined.Layers, "nav_floating"),
    DIAGNOSTICS("Diagnostics", Icons.Filled.Speed, Icons.Outlined.Speed, "nav_diagnostics")
}

class MainActivity : ComponentActivity() {

    private val searchEngine by lazy { SearchEngine() }
    private val ocrProvider by lazy { OcrTextProvider() }
    private val documentManager by lazy { DocumentSearchManager(applicationContext, ocrProvider, searchEngine) }
    private val captureManager by lazy { ScreenCaptureManager(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            UniversalFindTheme {
                var currentDestination by remember { mutableStateOf(AppDestination.FIND_IN_PAGE) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.testTag("bottom_nav_bar")
                        ) {
                            AppDestination.values().forEach { destination ->
                                val isSelected = currentDestination == destination
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentDestination = destination },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                            contentDescription = destination.title
                                        )
                                    },
                                    label = { Text(destination.title) },
                                    modifier = Modifier.testTag(destination.tag),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ElectricBluePrimary,
                                        selectedTextColor = ElectricBluePrimary,
                                        indicatorColor = ElectricBluePrimary.copy(alpha = 0.15f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentDestination) {
                            AppDestination.FIND_IN_PAGE -> {
                                DocumentViewerScreen(documentManager = documentManager)
                            }
                            AppDestination.SCREEN_OCR -> {
                                ScreenSearchScreen(
                                    captureManager = captureManager,
                                    ocrProvider = ocrProvider
                                )
                            }
                            AppDestination.FLOATING -> {
                                FloatingGuideScreen()
                            }
                            AppDestination.DIAGNOSTICS -> {
                                DiagnosticsScreen(searchEngine = searchEngine)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        captureManager.stopProjection()
    }
}
