package com.ounben.amaradio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation

@OptIn(ExperimentalMaterial3Api::class)
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
        Card(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.action_filter),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.FilterList,
                            contentDescription = "Toggle Filter",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Name Filter
                        OutlinedTextField(
                            value = uiState.name,
                            onValueChange = viewModel::onNameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.detail_name)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (uiState.name.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onNameChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        // Country Filter
                        FilterDropdown(
                            label = stringResource(R.string.filter_country),
                            value = if (uiState.countryEmoji.isNotEmpty()) "${uiState.countryEmoji} ${uiState.countryLabel}" else uiState.countryLabel,
                            options = uiState.countries,
                            onSelect = { item -> viewModel.onCountrySelect(item.code, item.label) },
                            onClear = { viewModel.clearCountry() },
                            icon = Icons.Default.Public
                        )

                        // Language Filter
                        FilterDropdown(
                            label = stringResource(R.string.filter_language),
                            value = uiState.languageLabel,
                            options = uiState.languages,
                            onSelect = { item -> viewModel.onLanguageSelect(item.code, item.label) },
                            onClear = { viewModel.clearLanguage() },
                            icon = Icons.Default.Language
                        )

                        // Tag Filter with Auto-Complete
                        TagAutoCompleteField(
                            value = uiState.tag,
                            suggestions = uiState.suggestedTags,
                            onValueChange = viewModel::onTagChange,
                            onSelect = viewModel::onTagSelect,
                            label = stringResource(R.string.filter_tag)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.filter_sort_by), 
                                modifier = Modifier.padding(end = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            SortSpinner(
                                selectedSort = uiState.sortBy,
                                onSortChange = viewModel::onSortByChange
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = uiState.reverse,
                                onCheckedChange = viewModel::onReverseChange,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = stringResource(R.string.filter_reverse), 
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = { 
                                isExpanded = false
                                viewModel.performSearch() 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(stringResource(R.string.filter_apply))
                        }
                    }
                }
            }
        }

        if (uiState.isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(
    label: String,
    value: String,
    options: List<FilterViewModel.CategoryItem>,
    onSelect: (FilterViewModel.CategoryItem) -> Unit,
    onClear: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    var showDialog by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = false,
        onExpandedChange = { showDialog = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = false)
                }
            },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    if (showDialog) {
        SearchableSelectionDialog(
            title = label,
            options = options,
            onSelect = {
                onSelect(it)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableSelectionDialog(
    title: String,
    options: List<FilterViewModel.CategoryItem>,
    onSelect: (FilterViewModel.CategoryItem) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredOptions = remember(searchQuery, options) {
        if (searchQuery.isEmpty()) options
        else options.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.8f)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.searchpreference_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredOptions, key = { it.code }) { item ->
                        ListItem(
                            headlineContent = { Text(item.label) },
                            leadingContent = if (item.emoji.isNotEmpty()) {
                                { Text(item.emoji, style = MaterialTheme.typography.headlineSmall) }
                            } else null,
                            modifier = Modifier.clickable { onSelect(item) }
                        )
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.label_button_cancel))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagAutoCompleteField(
    value: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    label: String
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        
        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            suggestions.forEach { tag ->
                DropdownMenuItem(
                    text = { Text(tag, color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        onSelect(tag)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSpinner(
    selectedSort: String,
    onSortChange: (String) -> Unit
) {
    val options = listOf(
        "name" to stringResource(R.string.sort_name),
        "votes" to stringResource(R.string.sort_votes),
        "clickcount" to stringResource(R.string.sort_clicks),
        "lastchangetime" to stringResource(R.string.sort_lastchange)
    )
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.find { it.first == selectedSort }?.second ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        onSortChange(value)
                        expanded = false
                    }
                )
            }
        }
    }
}
