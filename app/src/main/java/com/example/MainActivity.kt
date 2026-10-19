package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.document.DocumentSearchManager
import com.example.ocr.OcrTextProvider
import com.example.ui.screens.DocumentViewerScreen
import com.example.ui.screens.ScreenSearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val ocrProvider = OcrTextProvider()
        val documentManager = DocumentSearchManager(this, ocrProvider)

        setContent {
            MyApplicationTheme {
                MainAppScaffold(
                    ocrProvider = ocrProvider,
                    documentManager = documentManager
                )
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    ocrProvider: OcrTextProvider,
    documentManager: DocumentSearchManager
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("main_navigation_bar")) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Documents") },
                    label = { Text("Find in Page") },
                    modifier = Modifier.testTag("nav_documents")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Screen Search") },
                    label = { Text("Screen Find") },
                    modifier = Modifier.testTag("nav_screen_search")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> DocumentViewerScreen(
                documentManager = documentManager,
                modifier = Modifier.padding(innerPadding)
            )
            1 -> ScreenSearchScreen(
                ocrProvider = ocrProvider,
                modifier = Modifier.padding(innerPadding)
            )
            2 -> SettingsScreen(
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
