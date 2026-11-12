package com.ounben.amaradio.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.utils.LocaleUtils
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTab = uiState.tabs.getOrNull(tabIndex) ?: return
    
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 1. STICKY FILTER HEADER
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clickable { showFilterSheet = true }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                    contentDescription = stringResource(R.string.accessibility_configure),
                    tint = AmaradioAmber
                )
            }
        }

        // 2. CONTENT AREA
        val isTabEmpty = currentTab.name.isEmpty() && currentTab.countryCode.isEmpty() && 
                         currentTab.tag.isEmpty() && currentTab.languageCode.isEmpty()

        if (isTabEmpty) {
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
                val context = LocalContext.current
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { 
                                if (currentTab.label.isBlank()) {
                                    Toast.makeText(context, R.string.error_filter_name_required, Toast.LENGTH_SHORT).show()
                                } else {
                                    showFilterSheet = false
                                    viewModel.performSearch(tabIndex) 
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmaradioAmber,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.filter_apply), fontWeight = FontWeight.Bold)
                        }

                        IconButton(onClick = { showFilterSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.accessibility_close), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                        MultiSearchableSelectionDialog(
                            title = stringResource(R.string.filter_country),
                            options = uiState.countries,
                            initialSelectedCodes = currentTab.countryCode.split(",").filter { it.isNotBlank() }.toSet(),
                            onConfirm = { selectedItems -> 
                                viewModel.onMultiCountrySelect(tabIndex, selectedItems)
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

                // TAGS SECTION
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.filter_tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    // Tag Operator Selector (AND / OR / NOT - strictly English)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("AND", "OR", "NOT").forEach { op ->
                            val isSelected = currentTab.tagOperator == op
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .background(
                                        if (isSelected) AmaradioAmber else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { viewModel.onTagOperatorSelect(tabIndex, op) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = op,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Slot 1
                    val tag1Label = "${stringResource(R.string.filter_tag)} 1"
                    CustomFilterField(
                        label = tag1Label,
                        value = currentTab.tag,
                        icon = Icons.Default.Tag,
                        isReadOnly = true,
                        onClear = { viewModel.clearTag(tabIndex, 1) },
                        dialogContent = { onDismiss ->
                            SearchableSelectionDialog(
                                title = tag1Label,
                                options = uiState.tags,
                                onSelect = { item -> 
                                    viewModel.onTagSelect(tabIndex, item.label)
                                    onDismiss()
                                },
                                onDismiss = onDismiss
                            )
                        }
                    )

                    // Slot 2
                    val tag2Label = "${stringResource(R.string.filter_tag)} 2"
                    CustomFilterField(
                        label = tag2Label,
                        value = currentTab.tag2,
                        icon = Icons.Default.Tag,
                        isReadOnly = true,
                        onClear = { viewModel.clearTag(tabIndex, 2) },
                        dialogContent = { onDismiss ->
                            SearchableSelectionDialog(
                                title = tag2Label,
                                options = uiState.tags,
                                onSelect = { item -> 
                                    viewModel.onTag2Select(tabIndex, item.label)
                                    onDismiss()
                                },
                                onDismiss = onDismiss
                            )
                        }
                    )

                    // Slot 3
                    val tag3Label = "${stringResource(R.string.filter_tag)} 3"
                    CustomFilterField(
                        label = tag3Label,
                        value = currentTab.tag3,
                        icon = Icons.Default.Tag,
                        isReadOnly = true,
                        onClear = { viewModel.clearTag(tabIndex, 3) },
                        dialogContent = { onDismiss ->
                            SearchableSelectionDialog(
                                title = tag3Label,
                                options = uiState.tags,
                                onSelect = { item -> 
                                    viewModel.onTag3Select(tabIndex, item.label)
                                    onDismiss()
                                },
                                onDismiss = onDismiss
                            )
                        }
                    )
                }

                // CODEC & BITRATE SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val codecOptions = remember { listOf("MP3", "AAC", "AAC+", "OGG", "FLAC") }
                    DropdownFilterField(
                        label = stringResource(R.string.filter_codec),
                        value = currentTab.codec,
                        options = codecOptions,
                        icon = Icons.Default.GraphicEq,
                        onSelect = { viewModel.onCodecSelect(tabIndex, it) },
                        onClear = { viewModel.clearCodec(tabIndex) },
                        modifier = Modifier.weight(1f)
                    )

                    val bitrateOptions = remember {
                        listOf(32, 48, 64, 96, 128, 160, 192, 256, 320).map { "\u200E$it kbps" }
                    }
                    DropdownFilterField(
                        label = stringResource(R.string.filter_bitrate),
                        value = if (currentTab.bitrate > 0) "\u200E${currentTab.bitrate} kbps" else "",
                        options = bitrateOptions,
                        icon = Icons.Default.Speed,
                        onSelect = { selectedStr ->
                            val bitrateNum = selectedStr.filter { it.isDigit() }.toIntOrNull() ?: 0
                            viewModel.onBitrateSelect(tabIndex, bitrateNum)
                        },
                        onClear = { viewModel.clearBitrate(tabIndex) },
                        modifier = Modifier.weight(1.3f)
                    )

                    BitrateOperatorField(
                        selectedOperator = currentTab.bitrateOperator,
                        onOperatorChange = { viewModel.onBitrateOperatorSelect(tabIndex, it) },
                        modifier = Modifier.width(68.dp)
                    )
                }

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
        val isDark = isSystemInDarkTheme()
        val deleteRedColor = if (isDark) SolidRedError else SolidRedErrorLight

        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.action_delete), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(stringResource(R.string.confirm_delete_filter_tab, currentTab.label), color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeTab(tabIndex)
                        showDeleteConfirm = false
                        showFilterSheet = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = deleteRedColor)
                ) {
                    Text(stringResource(R.string.yes), fontWeight = FontWeight.Bold, color = deleteRedColor)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text(stringResource(R.string.no), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .background(MaterialTheme.colorScheme.surfaceVariant),
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
fun MultiSearchableSelectionDialog(
    title: String,
    options: List<FilterViewModel.CategoryItem>,
    initialSelectedCodes: Set<String>,
    onConfirm: (List<FilterViewModel.CategoryItem>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selectedCodes = remember { mutableStateListOf<String>().apply { addAll(initialSelectedCodes) } }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    val filteredOptions = remember(searchQuery, options) {
        SearchUtils.filterAndSortCategories(options, searchQuery)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .imePadding()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title, 
                        style = MaterialTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close, 
                            contentDescription = stringResource(R.string.label_button_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.searchpreference_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AmaradioAmber) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else null,
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
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(
                        items = filteredOptions, 
                        key = { index, item -> "${item.code}_${item.label}_$index" },
                        contentType = { _, _ -> "category_item" }
                    ) { _, item ->
                        val isChecked = selectedCodes.contains(item.code)
                        ListItem(
                            headlineContent = { Text(item.label, style = MaterialTheme.typography.bodyLarge) },
                            leadingContent = if (item.emoji.isNotEmpty()) {
                                { Text(item.emoji, style = MaterialTheme.typography.titleLarge) }
                            } else null,
                            trailingContent = {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedCodes.add(item.code) else selectedCodes.remove(item.code)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = AmaradioAmber)
                                )
                            },
                            modifier = Modifier.clickable {
                                if (isChecked) selectedCodes.remove(item.code) else selectedCodes.add(item.code)
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), 
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { selectedCodes.clear() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text(stringResource(R.string.sleep_timer_clear))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text(stringResource(R.string.label_button_cancel))
                        }

                        Button(
                            onClick = {
                                val selectedList = options.filter { selectedCodes.contains(it.code) }
                                onConfirm(selectedList)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmaradioAmber, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.action_ok), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchableSelectionDialog(
    title: String,
    options: List<FilterViewModel.CategoryItem>,
    onSelect: (FilterViewModel.CategoryItem) -> Unit,
    onDismiss: () -> Unit,
    showCount: Boolean = true
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Reset scroll to top when search query changes
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    val filteredOptions = remember(searchQuery, options) {
        SearchUtils.filterAndSortCategories(options, searchQuery)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .imePadding()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title, 
                        style = MaterialTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close, 
                            contentDescription = stringResource(R.string.label_button_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.searchpreference_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AmaradioAmber) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else null,
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
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(
                        items = filteredOptions, 
                        key = { index, item -> "${item.code}_${item.label}_$index" },
                        contentType = { _, _ -> "category_item" }
                    ) { _, item ->
                        ListItem(
                            headlineContent = { Text(item.label, style = MaterialTheme.typography.bodyLarge) },
                            supportingContent = if (showCount && item.count > 0) {
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

                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text(stringResource(R.string.label_button_cancel), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DropdownFilterField(
    label: String,
    value: String,
    options: List<String>,
    icon: ImageVector,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

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
                    contentDescription = "$label: ${value.ifEmpty { "None" }}"
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value.ifEmpty { label },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (value.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onClear() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal,
                                color = if (option == value) AmaradioAmber else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BitrateOperatorField(
    selectedOperator: String,
    onOperatorChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(">=", "=", "<=")
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable { expanded = true }
            .padding(horizontal = 8.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = selectedOperator
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedOperator,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            options.forEach { op ->
                DropdownMenuItem(
                    text = { 
                        Text(
                            text = op, 
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.onSurface
                        ) 
                    },
                    onClick = {
                        onOperatorChange(op)
                        expanded = false
                    }
                )
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
        "Name" to stringResource(R.string.sort_name),
        "Votes" to stringResource(R.string.sort_votes),
        "clickcount" to stringResource(R.string.sort_clicks),
        "LastChangeTime" to stringResource(R.string.sort_lastchange)
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
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
