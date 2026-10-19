package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.search.SearchQuery
import com.example.search.SearchResult
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleTextPrimary
import com.example.ui.theme.GoogleTextSecondary
import com.example.ui.theme.SearchCountBg

@Composable
fun ChromeFindBar(
    query: SearchQuery,
    searchResult: SearchResult,
    onQueryChange: (SearchQuery) -> Unit,
    onNextMatch: () -> Unit,
    onPreviousMatch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOptionsMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(28.dp))
            .testTag("chrome_find_bar"),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Magnifier Icon
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = GoogleBlue,
                    modifier = Modifier.padding(start = 8.dp, end = 6.dp)
                )

                // Editable Search Input Field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.text.isEmpty()) {
                        Text(
                            text = "Find in page…",
                            style = TextStyle(
                                color = GoogleTextSecondary.copy(alpha = 0.7f),
                                fontSize = 16.sp
                            )
                        )
                    }
                    BasicTextField(
                        value = query.text,
                        onValueChange = { newText ->
                            onQueryChange(query.copy(text = newText))
                        },
                        textStyle = TextStyle(
                            color = GoogleTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(GoogleBlue),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onNextMatch() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_text_input")
                    )
                }

                // Match Counter Badge (e.g. "3/14", "0/0", or "Error")
                if (searchResult.displayCounter.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (searchResult.errorMessage != null) Color(0xFFFFEBEE)
                                else SearchCountBg
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("match_counter_badge")
                    ) {
                        Text(
                            text = searchResult.displayCounter,
                            style = TextStyle(
                                color = if (searchResult.errorMessage != null) Color(0xFFC62828)
                                else if (searchResult.totalMatches > 0) GoogleBlue
                                else GoogleTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Previous Match Button
                IconButton(
                    onClick = onPreviousMatch,
                    enabled = searchResult.totalMatches > 0,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("prev_match_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous Match",
                        tint = if (searchResult.totalMatches > 0) GoogleTextPrimary else GoogleTextSecondary.copy(alpha = 0.4f)
                    )
                }

                // Next Match Button
                IconButton(
                    onClick = onNextMatch,
                    enabled = searchResult.totalMatches > 0,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("next_match_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next Match",
                        tint = if (searchResult.totalMatches > 0) GoogleTextPrimary else GoogleTextSecondary.copy(alpha = 0.4f)
                    )
                }

                // 3-Dots Menu for Match Case, Whole Word, Regex
                Box {
                    IconButton(
                        onClick = { showOptionsMenu = !showOptionsMenu },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("find_options_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Search Options",
                            tint = if (query.matchCase || query.wholeWord || query.useRegex) GoogleBlue else GoogleTextSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(if (query.matchCase) "✓ Match Case (Aa)" else "  Match Case (Aa)")
                            },
                            onClick = {
                                onQueryChange(query.copy(matchCase = !query.matchCase))
                                showOptionsMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (query.wholeWord) "✓ Match Whole Word" else "  Match Whole Word")
                            },
                            onClick = {
                                onQueryChange(query.copy(wholeWord = !query.wholeWord))
                                showOptionsMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (query.useRegex) "✓ Regular Expression (.*)" else "  Regular Expression (.*)")
                            },
                            onClick = {
                                onQueryChange(query.copy(useRegex = !query.useRegex))
                                showOptionsMenu = false
                            }
                        )
                    }
                }

                // Dismiss / Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("close_find_bar_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Find Bar",
                        tint = GoogleTextSecondary
                    )
                }
            }

            // Optional Regex Error Note
            if (searchResult.errorMessage != null) {
                Text(
                    text = "Regex: ${searchResult.errorMessage}",
                    style = TextStyle(color = Color(0xFFC62828), fontSize = 11.sp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }
    }
}
