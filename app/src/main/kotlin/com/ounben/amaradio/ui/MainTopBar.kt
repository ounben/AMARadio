package com.ounben.amaradio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ounben.amaradio.station.DataRadioStation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.recorder.formatAccessibleDuration
import com.ounben.amaradio.utils.LocaleUtils
import androidx.compose.ui.unit.sp
import com.ounben.amaradio.R

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun MainTopBar(
    isSearching: Boolean,
    searchQuery: String,
    isLoading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onFilterClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onSaveClick: () -> Unit,
    onLoadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onViewToggleClick: () -> Unit,
    isGridView: Boolean,
    sleepTimerRemainingMinutes: Int = 0,
    isDeleteVisible: Boolean,
    deleteTitleRes: Int,
    currentStation: DataRadioStation? = null,
    onEqualizerClick: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var textFieldValue by remember(isSearching) {
        mutableStateOf(
            TextFieldValue(
                text = searchQuery,
                selection = TextRange(searchQuery.length)
            )
        )
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            title = {
                if (isSearching) {
                    TextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            if (newValue.text != searchQuery) {
                                onSearchQueryChange(newValue.text)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.searchpreference_search), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = AmaradioAmber
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search,
                            hintLocales = LocaleUtils.getLatinHintLocales()
                        ),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cat_face),
                            contentDescription = null,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            tint = Color.Unspecified
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            color = AmaradioAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                        if (isLoading) {
                            Spacer(modifier = Modifier.width(6.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AmaradioAmber
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                if (isSearching) {
                    IconButton(
                        onClick = { onSearchToggle(false) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.accessibility_search_back),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            actions = {
                if (isSearching) {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                textFieldValue = TextFieldValue("", TextRange(0))
                                onSearchQueryChange("")
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.accessibility_clear_text),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CastButton(modifier = Modifier.size(48.dp))

                        // Record Button with Timer
                        com.ounben.amaradio.recorder.RecordButtonWithTimer(
                            currentStation = currentStation,
                            iconSize = 24.dp,
                            modifier = Modifier.size(48.dp)
                        )

                        IconButton(
                            onClick = { onSearchToggle(true) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.action_search),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        val isSleepTimerActive = sleepTimerRemainingMinutes > 0
                        val configuration = LocalConfiguration.current
                        val locale = remember(configuration) {
                            configuration.locales.takeIf { !it.isEmpty }?.get(0) ?: java.util.Locale.getDefault()
                        }
                        val sleepTimerStateDescription = if (isSleepTimerActive) {
                            val remainingDuration = remember(sleepTimerRemainingMinutes, locale) {
                                formatAccessibleDuration(sleepTimerRemainingMinutes.toLong() * 60_000L, locale)
                            }
                            stringResource(
                                R.string.accessibility_sleep_timer_active_remaining,
                                remainingDuration
                            )
                        } else {
                            null
                        }
                        IconButton(
                            onClick = onSleepTimerClick,
                            modifier = Modifier
                                .size(48.dp)
                                .semantics {
                                    if (sleepTimerStateDescription != null) {
                                        stateDescription = sleepTimerStateDescription
                                    }
                                }
                        ) {
                            if (isSleepTimerActive) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_timer_play_24dp),
                                    contentDescription = stringResource(R.string.nav_item_add_sleep),
                                    tint = AmaradioAmber,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = stringResource(R.string.nav_item_add_sleep),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.accessibility_menu),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (isGridView) R.string.action_list_view else R.string.action_grid_view)) },
                            leadingIcon = { Icon(if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Default.GridView, contentDescription = null) },
                            onClick = {
                                onViewToggleClick()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_filter)) },
                            leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                            onClick = {
                                onFilterClick()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_equalizer)) },
                            leadingIcon = { Icon(Icons.Default.Equalizer, contentDescription = null) },
                            onClick = {
                                onEqualizerClick()
                                showMenu = false
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_item_save_playlist)) },
                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                            onClick = {
                                onSaveClick()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_item_load_playlist)) },
                            leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                            onClick = {
                                onLoadClick()
                                showMenu = false
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_export_backup)) },
                            leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                            onClick = {
                                onExportBackup()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_import_backup)) },
                            leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                            onClick = {
                                onImportBackup()
                                showMenu = false
                            }
                        )
                        if (isDeleteVisible) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
                            DropdownMenuItem(
                                text = { Text(stringResource(deleteTitleRes)) },
                                leadingIcon = { Icon(painter = painterResource(R.drawable.delete_24), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    onDeleteClick()
                                    showMenu = false
                                }
                            )
                        }
                            }
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        
        if (isLoading && isSearching) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(2.dp),
                color = AmaradioAmber,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}
