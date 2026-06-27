package com.ounben.amaradio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ounben.amaradio.data.DataCategory
import com.ounben.amaradio.station.SearchStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    url: String?,
    searchStyle: SearchStyle,
    singleUseFilter: Boolean,
    onCategoryClick: (DataCategory) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(url, searchStyle, singleUseFilter) {
        url?.let { viewModel.loadCategories(it, searchStyle, singleUseFilter) }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { url?.let { viewModel.loadCategories(it, searchStyle, singleUseFilter, forceUpdate = true) } },
        modifier = Modifier.fillMaxSize()
    ) {
        if (uiState.isLoading && uiState.categories.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
                Button(onClick = { url?.let { viewModel.loadCategories(it, searchStyle, singleUseFilter, forceUpdate = true) } }) {
                    Text("Retry")
                }
            }
        } else {
            CategoryList(
                categories = uiState.categories,
                isGrid = uiState.isGrid,
                onCategoryClick = onCategoryClick
            )
        }
    }
}
