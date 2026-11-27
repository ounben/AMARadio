package com.ounben.amaradio.favorites

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.R
import com.ounben.amaradio.database.user.FavoriteListEntity
import com.ounben.amaradio.station.DataRadioStation
import com.ounben.amaradio.ui.AmaradioAmber
import com.ounben.amaradio.ui.BindListScrollRangeAccessibility
import com.ounben.amaradio.ui.StationIcon
import com.ounben.amaradio.ui.listItemSemantics
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun FavoritePlaylistFilterBar(
    lists: List<FavoriteListEntity>,
    selectedListId: String?,
    totalFavoritesCount: Int,
    listItemCounts: Map<String, Int>,
    onSelectList: (String?) -> Unit,
    onCreateListClick: () -> Unit,
    onManageListsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }

    val allLabel = stringResource(R.string.favorite_list_all)
    val selectedList = remember(lists, selectedListId) {
        lists.find { it.id == selectedListId }
    }
    val isFiltered = selectedList != null
    val displayLabel = selectedList?.name ?: allLabel
    val filterSemanticsDesc = stringResource(R.string.accessibility_filter_playlist, displayLabel)

    Surface(
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                FilterChip(
                    selected = isFiltered,
                    onClick = { showDropdown = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = filterSemanticsDesc
                        },
                    label = {
                        Text(
                            text = displayLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clearAndSetSemantics { }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = AmaradioAmber,
                        selectedLabelColor = Color.Black,
                        selectedLeadingIconColor = Color.Black,
                        selectedTrailingIconColor = Color.Black
                    )
                )

                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "$allLabel ($totalFavoritesCount)",
                                fontWeight = if (!isFiltered) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onSelectList(null)
                            showDropdown = false
                        }
                    )

                    if (lists.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                        lists.forEach { list ->
                            val count = listItemCounts[list.id] ?: 0
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${list.name} ($count)",
                                        fontWeight = if (selectedListId == list.id) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onSelectList(list.id)
                                    showDropdown = false
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.favorite_list_create),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            showDropdown = false
                            onCreateListClick()
                        }
                    )
                }
            }

            IconButton(
                onClick = onManageListsClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.favorite_list_manage),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }

            IconButton(
                onClick = { onSelectList(null) },
                enabled = isFiltered,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = AmaradioAmber,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_clear_playlist_filter),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CreateEditFavoriteListDialog(
    initialName: String = "",
    isRename: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(initialName) { mutableStateOf(initialName) }
    val trimmed = text.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(if (isRename) R.string.favorite_list_rename else R.string.favorite_list_create),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.favorite_list_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AmaradioAmber,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = AmaradioAmber,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = AmaradioAmber
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (trimmed.isNotEmpty()) {
                        onConfirm(trimmed)
                    }
                },
                enabled = trimmed.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = AmaradioAmber,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    text = stringResource(R.string.action_save),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
fun ManageFavoriteListsDialog(
    lists: List<FavoriteListEntity>,
    listItemCounts: Map<String, Int>,
    onReorder: (List<FavoriteListEntity>) -> Unit,
    onRename: (FavoriteListEntity) -> Unit,
    onEditStations: (FavoriteListEntity) -> Unit,
    onDelete: (FavoriteListEntity) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val listsLocal = remember { mutableStateListOf<FavoriteListEntity>() }
    var listToMove by remember { mutableStateOf<Pair<Int, FavoriteListEntity>?>(null) }

    LaunchedEffect(lists) {
        if (listsLocal.toList() != lists) {
            listsLocal.clear()
            listsLocal.addAll(lists)
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        listsLocal.add(to.index, listsLocal.removeAt(from.index))
        onReorder(listsLocal.toList())
    }

    val moveToTopLabel = stringResource(R.string.action_move_to_top)
    val moveUpLabel = stringResource(R.string.action_move_up)
    val moveDownLabel = stringResource(R.string.action_move_down)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(R.string.favorite_list_manage),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (listsLocal.isEmpty()) {
                    Text(
                        text = stringResource(R.string.favorite_list_none_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    BindListScrollRangeAccessibility(listState = lazyListState, enabled = listsLocal.isNotEmpty())
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        itemsIndexed(listsLocal, key = { _, item -> item.id }) { index, list ->
                            ReorderableItem(reorderableState, key = list.id) { isDragging ->
                                val itemScope = this
                                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                                val count = listItemCounts[list.id] ?: 0
                                val reorderLabel = stringResource(R.string.accessibility_reorder_station, list.name)

                                val customActionsList = remember(index, listsLocal.size, moveToTopLabel, moveUpLabel, moveDownLabel) {
                                    val actions = mutableListOf<CustomAccessibilityAction>()
                                    if (index > 0) {
                                        actions.add(CustomAccessibilityAction(label = moveToTopLabel) {
                                            listsLocal.add(0, listsLocal.removeAt(index))
                                            onReorder(listsLocal.toList())
                                            true
                                        })
                                        actions.add(CustomAccessibilityAction(label = moveUpLabel) {
                                            listsLocal.add(index - 1, listsLocal.removeAt(index))
                                            onReorder(listsLocal.toList())
                                            true
                                        })
                                    }
                                    if (index < listsLocal.lastIndex) {
                                        actions.add(CustomAccessibilityAction(label = moveDownLabel) {
                                            listsLocal.add(index + 1, listsLocal.removeAt(index))
                                            onReorder(listsLocal.toList())
                                            true
                                        })
                                    }
                                    actions
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            scaleX = if (isDragging) 1.03f else 1f
                                            scaleY = if (isDragging) 1.03f else 1f
                                        },
                                    color = if (isDragging) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    shadowElevation = elevation,
                                    tonalElevation = 0.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 2.dp)
                                    ) {
                                        Text(
                                            text = "${list.name} ($count)",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .listItemSemantics(index)
                                                .padding(horizontal = 4.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { onEditStations(list) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                                    contentDescription = stringResource(R.string.accessibility_playlist_edit_stations, list.name),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            IconButton(
                                                onClick = { onRename(list) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = stringResource(R.string.accessibility_playlist_rename, list.name),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDelete(list) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.delete_24),
                                                    contentDescription = stringResource(R.string.accessibility_playlist_delete, list.name),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            with(itemScope) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clickable(role = Role.Button) { listToMove = index to list }
                                                        .draggableHandle()
                                                        .semantics(mergeDescendants = true) {
                                                            contentDescription = reorderLabel
                                                            customActions = customActionsList
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DragHandle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (index < listsLocal.lastIndex) {
                                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onCreateNew,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(start = 0.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.favorite_list_create),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text(
                        text = stringResource(R.string.accessibility_close),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )

    listToMove?.let { (currentIndex, list) ->
        PlaylistPositionPickerDialog(
            list = list,
            currentIndex = currentIndex,
            allLists = listsLocal.toList(),
            onPositionSelected = { targetIndex ->
                listsLocal.add(targetIndex, listsLocal.removeAt(currentIndex))
                onReorder(listsLocal.toList())
                listToMove = null
            },
            onDismiss = { listToMove = null }
        )
    }
}

@Composable
fun PlaylistPositionPickerDialog(
    list: FavoriteListEntity,
    currentIndex: Int,
    allLists: List<FavoriteListEntity>,
    onPositionSelected: (targetIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val currentLabel = stringResource(R.string.label_current_position)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
    BindListScrollRangeAccessibility(listState = listState, enabled = allLists.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(R.string.dialog_move_to_position_title, list.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(allLists) { idx, targetList ->
                    val isCurrent = idx == currentIndex
                    val itemText = "${idx + 1}. ${targetList.name}${if (isCurrent) " $currentLabel" else ""}"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = isCurrent,
                                role = Role.RadioButton,
                                onClick = {
                                    if (idx != currentIndex) {
                                        onPositionSelected(idx)
                                    } else {
                                        onDismiss()
                                    }
                                }
                            )
                            .listItemSemantics(idx)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isCurrent,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = AmaradioAmber,
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = itemText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) AmaradioAmber else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(
                    text = stringResource(R.string.label_button_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
fun DeleteFavoriteListConfirmDialog(
    list: FavoriteListEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val deleteRedColor = MaterialTheme.colorScheme.error

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(R.string.favorite_list_delete_title),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = stringResource(R.string.favorite_list_delete_message, list.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = deleteRedColor)
            ) {
                Text(
                    text = stringResource(R.string.action_delete),
                    fontWeight = FontWeight.Bold,
                    color = deleteRedColor
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
fun AssignFavoriteListsDialog(
    station: DataRadioStation,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val favoriteListManager = remember(context) {
        (context.applicationContext as AMARadioApp).favoriteListManager
    }
    val lists by favoriteListManager.listsFlow.collectAsStateWithLifecycle()
    var selectedIds by remember(station.StationUuid) {
        mutableStateOf(favoriteListManager.getListIdsForStationSync(station.StationUuid))
    }
    var showCreateDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.favorite_list_assign_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = station.Name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (lists.isEmpty()) {
                    Text(
                        text = stringResource(R.string.favorite_list_none_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    val assignListState = rememberLazyListState()
                    BindListScrollRangeAccessibility(listState = assignListState, enabled = lists.isNotEmpty())
                    LazyColumn(
                        state = assignListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        itemsIndexed(lists, key = { _, item -> item.id }) { index, list ->
                            val isChecked = list.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .toggleable(
                                        value = isChecked,
                                        role = Role.Checkbox,
                                        onValueChange = { checked ->
                                            selectedIds = if (checked) selectedIds + list.id else selectedIds - list.id
                                        }
                                    )
                                    .listItemSemantics(index)
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AmaradioAmber,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkmarkColor = Color.Black
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = list.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.favorite_list_create),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    favoriteListManager.setStationLists(station, selectedIds)
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = AmaradioAmber)
            ) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    if (showCreateDialog) {
        CreateEditFavoriteListDialog(
            onConfirm = { newName ->
                favoriteListManager.createList(newName) { createdId ->
                    selectedIds = selectedIds + createdId
                }
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
fun ManagePlaylistStationsDialog(
    list: FavoriteListEntity,
    allFavorites: List<DataRadioStation>,
    initialSelectedUuids: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedUuids by remember(list.id, initialSelectedUuids) {
        mutableStateOf(initialSelectedUuids)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Column {
                Text(
                    text = list.name,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.favorite_list_edit_stations),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (allFavorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.searchpreference_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val stationsListState = rememberLazyListState()
                BindListScrollRangeAccessibility(listState = stationsListState, enabled = allFavorites.isNotEmpty())
                LazyColumn(
                    state = stationsListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    itemsIndexed(allFavorites, key = { _, item -> item.StationUuid }) { index, station ->
                        val isChecked = station.StationUuid in selectedUuids
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    value = isChecked,
                                    role = Role.Checkbox,
                                    onValueChange = { checked ->
                                        selectedUuids = if (checked) {
                                            selectedUuids + station.StationUuid
                                        } else {
                                            selectedUuids - station.StationUuid
                                        }
                                    }
                                )
                                .listItemSemantics(index)
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AmaradioAmber,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    checkmarkColor = Color.Black
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.size(40.dp)) {
                                StationIcon(
                                    stationName = station.Name,
                                    stationUuid = station.StationUuid,
                                    iconUrl = station.IconUrl,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = station.Name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(selectedUuids)
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = AmaradioAmber)
            ) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
