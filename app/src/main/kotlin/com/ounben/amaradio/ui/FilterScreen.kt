package com.ounben.amaradio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation

@Composable
fun FilterScreen(
    viewModel: FilterViewModel,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit,
    isFavorite: (String) -> Boolean
) {
    val uiState by viewModel.uiState.collectAsState()
    var isExpanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isExpanded = !isExpanded }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.action_filter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.FilterList,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Rename Tab
                        CustomFilterField(
                            label = stringResource(R.string.action_rename_tab),
                            value = uiState.tabName,
                            icon = Icons.AutoMirrored.Filled.Label,
                            onValueChange = viewModel::onTabNameChange,
                            onClear = { viewModel.onTabNameChange("") }
                        )

                        CustomFilterField(
                            label = stringResource(R.string.detail_name),
                            value = uiState.name,
                            icon = Icons.Default.Search,
                            onValueChange = viewModel::onNameChange,
                            onClear = { viewModel.onNameChange("") }
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CustomFilterField(
                                label = stringResource(R.string.filter_country),
                                value = if (uiState.countryEmoji.isNotEmpty()) "${uiState.countryEmoji} ${uiState.countryLabel}" else uiState.countryLabel,
                                icon = Icons.Default.Public,
                                isReadOnly = true,
                                modifier = Modifier.weight(1f),
                                onClear = { viewModel.clearCountry() },
                                dialogContent = { onDismiss ->
                                    SearchableSelectionDialog(
                                        title = stringResource(R.string.filter_country),
                                        options = uiState.countries,
                                        onSelect = { item -> 
                                            viewModel.onCountrySelect(item.code, item.label)
                                            onDismiss()
                                        },
                                        onDismiss = onDismiss
                                    )
                                }
                            )
                            CustomFilterField(
                                label = stringResource(R.string.filter_language),
                                value = uiState.languageLabel,
                                icon = Icons.Default.Language,
                                isReadOnly = true,
                                modifier = Modifier.weight(1f),
                                onClear = { viewModel.clearLanguage() },
                                dialogContent = { onDismiss ->
                                    SearchableSelectionDialog(
                                        title = stringResource(R.string.filter_language),
                                        options = uiState.languages,
                                        onSelect = { item -> 
                                            viewModel.onLanguageSelect(item.code, item.label)
                                            onDismiss()
                                        },
                                        onDismiss = onDismiss
                                    )
                                }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CustomFilterField(
                                label = stringResource(R.string.filter_tag),
                                value = uiState.tag,
                                icon = Icons.Default.Tag,
                                isReadOnly = true,
                                modifier = Modifier.weight(1f),
                                onClear = { viewModel.clearTag() },
                                dialogContent = { onDismiss ->
                                    SearchableSelectionDialog(
                                        title = stringResource(R.string.filter_tag),
                                        options = uiState.tags,
                                        onSelect = { item -> 
                                            viewModel.onTagSelect(item.label)
                                            onDismiss()
                                        },
                                        onDismiss = onDismiss
                                    )
                                }
                            )
                            SortField(
                                selectedSort = uiState.sortBy,
                                onSortChange = viewModel::onSortByChange,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { viewModel.onReverseChange(!uiState.reverse) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.reverse) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                    contentDescription = "Direction",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { 
                                    isExpanded = false
                                    viewModel.performSearch() 
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                modifier = Modifier.height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.filter_apply))
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AmaradioAmber)
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
            }
        } else {
            StationList(
                stations = uiState.stations,
                isGrid = uiState.isGrid,
                onStationClick = onStationClick,
                onFavoriteClick = onFavoriteClick,
                isFavorite = isFavorite,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun CustomFilterField(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = false,
    onValueChange: (String) -> Unit = {},
    onClear: () -> Unit = {},
    dialogContent: @Composable ((onDismiss: () -> Unit) -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .background(Color.Transparent)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    if (isReadOnly) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                            modifier = Modifier.fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { showDialog = true }
                                )
                                .wrapContentHeight(Alignment.CenterVertically)
                        )
                    } else {
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                if (value.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onClear() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDialog && dialogContent != null) {
        dialogContent { showDialog = false }
    }
}

@Composable
fun SearchableSelectionDialog(
    title: String,
    options: List<FilterViewModel.CategoryItem>,
    onSelect: (FilterViewModel.CategoryItem) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredOptions = remember(searchQuery, options) {
        if (searchQuery.length < 2) options.take(200)
        else options.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title, 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.searchpreference_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    )
                )

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filteredOptions, key = { it.code + it.label + it.count }) { item ->
                        ListItem(
                            headlineContent = { Text(item.label, style = MaterialTheme.typography.bodyLarge) },
                            supportingContent = if (item.count > 0) {
                                { Text("${item.count} stations", style = MaterialTheme.typography.labelSmall) }
                            } else null,
                            leadingContent = if (item.emoji.isNotEmpty()) {
                                { Text(item.emoji, style = MaterialTheme.typography.titleLarge) }
                            } else null,
                            modifier = Modifier.clickable { onSelect(item) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.label_button_cancel))
                    }
                }
            }
        }
    }
}

@Composable
fun SortField(
    selectedSort: String,
    onSortChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        "name" to stringResource(R.string.sort_name),
        "votes" to stringResource(R.string.sort_votes),
        "clickcount" to stringResource(R.string.sort_clicks),
        "lastchangetime" to stringResource(R.string.sort_lastchange)
    )
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.find { it.first == selectedSort }?.second ?: ""

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.filter_sort_by),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                    maxLines = 1
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.45f).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            onSortChange(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
