package com.ounben.amaradio.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.utils.LocaleUtils
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ounben.amaradio.R
import com.ounben.amaradio.station.DataRadioStation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(
    viewModel: FilterViewModel,
    tabIndex: Int,
    onStationClick: (DataRadioStation) -> Unit,
    onFavoriteClick: (DataRadioStation) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTab = uiState.tabs.getOrNull(tabIndex) ?: return
    
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 1. STICKY FILTER HEADER
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable { showFilterSheet = true }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentTab.label.isNotBlank()) currentTab.label else stringResource(R.string.action_filter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentTab.label.isNotBlank()) AmaradioAmber else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.filter_tap_to_configure),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Configure",
                    tint = AmaradioAmber
                )
            }
        }

        // 2. CONTENT AREA
        if (currentTab.label.trim().isEmpty() && currentTab.name.isEmpty() && currentTab.countryCode.isEmpty() && currentTab.tag.isEmpty()) {
            FilterEmptyState()
        } else {
            StationListTemplate(
                stations = currentTab.stations,
                isGrid = uiState.isGrid,
                isLoading = uiState.isSearching,
                error = uiState.error,
                emptyMessage = stringResource(R.string.searchpreference_no_results),
                onRefresh = { viewModel.performSearch(tabIndex) },
                onStationClick = onStationClick,
                onFavoriteClick = onFavoriteClick,
                isFavorite = { uuid -> uiState.favoriteIds.contains(uuid) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.action_filter),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showFilterSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AmaradioAmber)
                    }
                }

                // RENAME FIELD
                CustomFilterField(
                    label = stringResource(R.string.action_rename_tab),
                    value = currentTab.label,
                    icon = Icons.AutoMirrored.Filled.Label,
                    onValueChange = { viewModel.updateTabLabel(tabIndex, it) },
                    onClear = { viewModel.updateTabLabel(tabIndex, "") }
                )

                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

                // SEARCH NAME
                CustomFilterField(
                    label = stringResource(R.string.detail_name),
                    value = currentTab.name,
                    icon = Icons.Default.Search,
                    onValueChange = { viewModel.onNameChange(tabIndex, it) },
                    onClear = { viewModel.onNameChange(tabIndex, "") }
                )

                // COUNTRY / LANGUAGE / TAG
                CustomFilterField(
                    label = stringResource(R.string.filter_country),
                    value = if (currentTab.countryEmoji.isNotEmpty()) "${currentTab.countryEmoji} ${currentTab.countryLabel}" else currentTab.countryLabel,
                    icon = Icons.Default.Public,
                    isReadOnly = true,
                    onClear = { viewModel.clearCountry(tabIndex) },
                    dialogContent = { onDismiss ->
                        SearchableSelectionDialog(
                            title = stringResource(R.string.filter_country),
                            options = uiState.countries,
                            onSelect = { item -> 
                                viewModel.onCountrySelect(tabIndex, item.code, item.label)
                                onDismiss()
                            },
                            onDismiss = onDismiss
                        )
                    }
                )

                CustomFilterField(
                    label = stringResource(R.string.filter_language),
                    value = currentTab.languageLabel,
                    icon = Icons.Default.Language,
                    isReadOnly = true,
                    onClear = { viewModel.clearLanguage(tabIndex) },
                    dialogContent = { onDismiss ->
                        SearchableSelectionDialog(
                            title = stringResource(R.string.filter_language),
                            options = uiState.languages,
                            onSelect = { item -> 
                                viewModel.onLanguageSelect(tabIndex, item.code, item.label)
                                onDismiss()
                            },
                            onDismiss = onDismiss
                        )
                    }
                )

                CustomFilterField(
                    label = stringResource(R.string.filter_tag),
                    value = currentTab.tag,
                    icon = Icons.Default.Tag,
                    isReadOnly = true,
                    onClear = { viewModel.clearTag(tabIndex) },
                    dialogContent = { onDismiss ->
                        SearchableSelectionDialog(
                            title = stringResource(R.string.filter_tag),
                            options = uiState.tags,
                            onSelect = { item -> 
                                viewModel.onTagSelect(tabIndex, item.label)
                                onDismiss()
                            },
                            onDismiss = onDismiss
                        )
                    }
                )

                // SORTING
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SortField(
                        selectedSort = currentTab.sortBy,
                        onSortChange = { viewModel.onSortByChange(tabIndex, it) },
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(
                        onClick = { viewModel.onReverseChange(tabIndex, !currentTab.reverse) },
                        modifier = Modifier.padding(top = 20.dp)
                    ) {
                        Icon(
                            imageVector = if (currentTab.reverse) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = stringResource(R.string.accessibility_filter_reverse),
                            tint = AmaradioAmber
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ACTIONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.tabs.size > 1) {
                        Button(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB71C1C), // Solid Dark Red
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_delete))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    val context = LocalContext.current
                    Button(
                        onClick = { 
                            if (currentTab.label.isBlank()) {
                                Toast.makeText(context, R.string.error_filter_name_required, Toast.LENGTH_SHORT).show()
                            } else {
                                showFilterSheet = false
                                viewModel.performSearch(tabIndex) 
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmaradioAmber, // Solid Amber
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.filter_apply))
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.action_delete), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.confirm_delete_filter_tab, currentTab.label)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeTab(tabIndex)
                        showDeleteConfirm = false
                        showFilterSheet = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.yes), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Text(stringResource(R.string.no), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun FilterEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Radio,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = AmaradioAmber
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.filter_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.filter_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.filter_empty_filter_by), 
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.filter_empty_filter_desc), 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.filter_empty_sort_by), 
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.filter_empty_sort_desc), 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    val clearDescription = stringResource(R.string.accessibility_clear_text) + " " + label

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .semantics(mergeDescendants = true) {
                    if (isReadOnly) {
                        role = Role.Button
                    }
                }
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    if (isReadOnly) {
                        Text(
                            text = if (value.isEmpty()) label else value,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription = label
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(hintLocales = LocaleUtils.getLatinHintLocales())
                        )
                    }
                }

                if (value.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = clearDescription,
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
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val filteredOptions = remember(searchQuery, options) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            options
        } else {
            options.filter { it.label.contains(q, ignoreCase = true) }
                .sortedWith(
                    compareByDescending<FilterViewModel.CategoryItem> { item ->
                        SearchUtils.calculateScore(item.label, q)
                    }.thenByDescending { it.count }
                )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
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
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AmaradioAmber) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).focusRequester(focusRequester),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmaradioAmber,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
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
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
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
    val label = stringResource(R.string.filter_sort_by)

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "$label: $currentLabel"
                },
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
                modifier = Modifier.fillMaxWidth(0.45f).background(MaterialTheme.colorScheme.surface)
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
