package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.search.SearchQuery
import com.example.search.SearchResult
import com.example.ui.theme.SearchAmberAccent

@Composable
fun ChromeFindBar(
    query: SearchQuery,
    searchResult: SearchResult,
    onQueryChanged: (String) -> Unit,
    onMatchCaseToggled: (Boolean) -> Unit,
    onWholeWordToggled: (Boolean) -> Unit,
    onRegexToggled: (Boolean) -> Unit,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvancedFilters by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .testTag("chrome_find_bar"),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Icon
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 4.dp)
                )

                // Query Input
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (searchResult.totalMatches > 0) SearchAmberAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.text.isEmpty()) {
                        Text(
                            text = "Find in page…",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }

                    BasicTextField(
                        value = query.text,
                        onValueChange = onQueryChanged,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onNextMatch() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("find_input")
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Match Counter Badge (Chrome Style e.g. "3/12" or "0/0")
                if (query.text.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (searchResult.totalMatches > 0)
                                    SearchAmberAccent.copy(alpha = 0.2f)
                                else
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("match_counter"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = searchResult.displayCounter,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (searchResult.totalMatches > 0)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Previous match button
                IconButton(
                    onClick = onPreviousMatch,
                    enabled = searchResult.totalMatches > 0,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous match",
                        tint = if (searchResult.totalMatches > 0)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }

                // Next match button
                IconButton(
                    onClick = onNextMatch,
                    enabled = searchResult.totalMatches > 0,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next match",
                        tint = if (searchResult.totalMatches > 0)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }

                // Close / Dismiss button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Options Bar (Case Sensitivity, Whole Word, Regex)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Match Case Toggle [Aa]
                FilterChip(
                    selected = query.matchCase,
                    onClick = { onMatchCaseToggled(!query.matchCase) },
                    label = {
                        Text(
                            text = "Aa",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("match_case_toggle")
                )

                // Whole Word Toggle
                FilterChip(
                    selected = query.wholeWord,
                    onClick = { onWholeWordToggled(!query.wholeWord) },
                    label = {
                        Text(
                            text = "\"Word\"",
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("whole_word_toggle")
                )

                // Regex Toggle [.*]
                FilterChip(
                    selected = query.useRegex,
                    onClick = { onRegexToggled(!query.useRegex) },
                    label = {
                        Text(
                            text = ".*",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("regex_toggle")
                )

                Spacer(modifier = Modifier.weight(1f))

                if (searchResult.errorMessage != null) {
                    Text(
                        text = searchResult.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
