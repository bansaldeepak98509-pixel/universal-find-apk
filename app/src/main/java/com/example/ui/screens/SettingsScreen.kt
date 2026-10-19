package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.UniversalFindAccessibilityService
import com.example.search.SearchEngine
import com.example.search.SearchQuery
import com.example.ui.theme.GoogleBlue

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var benchmarkResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "System Permissions & Diagnostics",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // 1. System Alert Window Permission Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Display Over Other Apps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Required for floating search bubble widget over other apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val canDraw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.canDrawOverlays(context)
                    } else true

                    if (canDraw) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Granted",
                            tint = Color(0xFF34A853),
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.testTag("grant_overlay_button")
                        ) {
                            Text("Grant")
                        }
                    }
                }
            }
        }

        // 2. Accessibility Service Permission Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accessibility Service",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Inspects live UI view hierarchy nodes directly without taking screenshots.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (UniversalFindAccessibilityService.isServiceConnected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = Color(0xFF34A853),
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.testTag("open_accessibility_settings")
                        ) {
                            Text("Configure")
                        }
                    }
                }
            }
        }

        // 3. Engine Performance & Latency Benchmark
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Search Engine Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Test search latency and NFC normalization performance across 1,000 text iterations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val engine = SearchEngine()
                        val sampleCorpus = "Universal Find brings Chrome-style search to Android across all apps. भारत में खोज तकनीक को सरल बनाता है। ".repeat(100)
                        val start = System.currentTimeMillis()
                        repeat(1000) {
                            engine.search(sampleCorpus, SearchQuery("Android"))
                            engine.search(sampleCorpus, SearchQuery("खोज"))
                        }
                        val elapsed = System.currentTimeMillis() - start
                        benchmarkResult = "Completed 1,000 dual-script search passes in ${elapsed}ms (${"%.2f".format(elapsed / 1000.0)}ms per pass)"
                    },
                    modifier = Modifier.testTag("run_benchmark_button")
                ) {
                    Icon(Icons.Default.Build, contentDescription = null)
                    Text("  Run 1,000-Pass Benchmark")
                }

                if (benchmarkResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = benchmarkResult!!,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = GoogleBlue
                    )
                }
            }
        }

        // 4. Privacy & System Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "100% On-Device & Zero Telemetry",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "All text recognition, OCR, and string matching algorithms execute entirely on your device using Google Play Services ML Kit. No documents or screen captures leave your phone.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
