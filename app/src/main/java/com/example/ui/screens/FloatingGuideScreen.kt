package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.UniversalFindAccessibilityService
import com.example.service.FloatingSearchService
import com.example.ui.theme.ElectricBluePrimary
import com.example.ui.theme.SearchAmberAccent

@Composable
fun FloatingGuideScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(checkOverlayPermission(context)) }
    val isAccessibilityConnected by UniversalFindAccessibilityService.isServiceConnectedFlow.collectAsState()
    var isServiceRunning by remember { mutableStateOf(false) }

    // Re-check permissions when screen is viewed
    LaunchedEffect(Unit) {
        hasOverlayPermission = checkOverlayPermission(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Hero Header
        Text(
            text = "Floating Find & Privacy",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Configure system overlay and accessibility to find text across other apps.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Service Master Toggle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceRunning)
                    ElectricBluePrimary.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            border = if (isServiceRunning)
                CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ElectricBluePrimary))
            else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isServiceRunning) "Floating Widget Active" else "Floating Widget Inactive",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isServiceRunning)
                            "A floating 🔍 button is visible over apps. Tap to trigger Chrome find bar."
                        else
                            "Enable overlay permission below to launch floating search bar.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        if (isServiceRunning) {
                            FloatingSearchService.stop(context)
                            isServiceRunning = false
                        } else {
                            if (checkOverlayPermission(context)) {
                                FloatingSearchService.start(context)
                                isServiceRunning = true
                            } else {
                                openOverlaySettings(context)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceRunning) Color(0xFFEF4444) else ElectricBluePrimary
                    ),
                    modifier = Modifier.testTag("floating_service_toggle")
                ) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isServiceRunning) "Stop" else "Start")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Permissions Checklist Section
        Text(
            text = "Required Android Permissions",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(10.dp))

        // 1. Overlay Permission (SYSTEM_ALERT_WINDOW)
        PermissionItemCard(
            title = "Display Over Other Apps (Overlay)",
            description = "Allows the Chrome-style find bar and floating search trigger to float above other applications.",
            isGranted = hasOverlayPermission,
            icon = Icons.Default.Info,
            actionLabel = if (hasOverlayPermission) "Granted" else "Grant Permission",
            onAction = { openOverlaySettings(context) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Accessibility Service (Optional View Hierarchy search)
        PermissionItemCard(
            title = "Universal Find Accessibility (Optional)",
            description = "Allows reading native view hierarchy text directly without taking screenshots. Highly efficient for system apps.",
            isGranted = isAccessibilityConnected,
            icon = Icons.Default.Person,
            actionLabel = if (isAccessibilityConnected) "Active" else "Enable in Settings",
            onAction = { openAccessibilitySettings(context) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Privacy and Security Guarantees Section
        Text(
            text = "Zero-Cloud Privacy Architecture",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                PrivacyRuleRow(
                    title = "100% On-Device Processing",
                    desc = "All text recognition, OCR, and keyword matching run locally on your device CPU/NPU using Google ML Kit."
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrivacyRuleRow(
                    title = "No Cloud Uploads or Telemetry",
                    desc = "This application makes ZERO network requests for text search. No screenshots or documents ever leave your device."
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrivacyRuleRow(
                    title = "Respects Android FLAG_SECURE",
                    desc = "Banking, password managers, and DRM protected apps explicitly block screen capture by Android platform security rules. Universal Find strictly honors these protections."
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrivacyRuleRow(
                    title = "Ephemeral In-Memory Buffers",
                    desc = "Screen frames are kept in volatile memory only while actively matching. As soon as the search bar is dismissed, buffers are released."
                )
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f)
                        else SearchAmberAccent.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF10B981) else SearchAmberAccent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onAction,
                enabled = !isGranted,
                modifier = Modifier.height(36.dp)
            ) {
                Text(actionLabel, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PrivacyRuleRow(title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF10B981),
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

private fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

private fun openOverlaySettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {}
}
