package com.aj.shared.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Lightweight Pagination Controller for Compose Multiplatform lists.
 * Eliminates the need for heavy AndroidX Paging 3 runtime dependencies.
 */
class PaginationState<T>(
    val initialPage: Int = 1,
    val pageSize: Int = 20,
    private val fetchPage: suspend (page: Int, pageSize: Int) -> List<T>
) {
    val items = mutableStateListOf<T>()
    var currentPage by mutableStateOf(initialPage)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var isEndReached by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun loadNextPage() {
        if (isLoading || isEndReached) return

        isLoading = true
        error = null

        try {
            val newItems = fetchPage(currentPage, pageSize)
            if (newItems.isEmpty() || newItems.size < pageSize) {
                isEndReached = true
            }
            items.addAll(newItems)
            currentPage++
        } catch (e: Exception) {
            error = e.message ?: "Failed to load more items"
        } finally {
            isLoading = false
        }
    }

    suspend fun refresh() {
        isRefreshing = true
        error = null
        currentPage = initialPage
        isEndReached = false

        try {
            val freshItems = fetchPage(initialPage, pageSize)
            items.clear()
            items.addAll(freshItems)
            if (freshItems.size < pageSize) {
                isEndReached = true
            }
            currentPage = initialPage + 1
        } catch (e: Exception) {
            error = e.message ?: "Failed to refresh items"
        } finally {
            isRefreshing = false
        }
    }

    fun clear() {
        items.clear()
        currentPage = initialPage
        isEndReached = false
        error = null
    }
}

/**
 * Remembers a lightweight pagination state in Compose Multiplatform.
 */
@Composable
fun <T> rememberPaginationState(
    initialPage: Int = 1,
    pageSize: Int = 20,
    fetchPage: suspend (page: Int, pageSize: Int) -> List<T>
): PaginationState<T> {
    return remember {
        PaginationState(
            initialPage = initialPage,
            pageSize = pageSize,
            fetchPage = fetchPage
        )
    }
}

/**
 * Trigger callback when scrolling approaches the bottom of a LazyList.
 */
@Composable
fun LazyListState.OnBottomReached(
    buffer: Int = 3,
    onLoadMore: () -> Unit
) {
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= (totalItems - 1 - buffer)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                onLoadMore()
            }
    }
}

/**
 * Trigger callback when scrolling approaches the bottom of a LazyVerticalGrid.
 */
@Composable
fun LazyGridState.OnBottomReached(
    buffer: Int = 4,
    onLoadMore: () -> Unit
) {
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= (totalItems - 1 - buffer)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                onLoadMore()
            }
    }
}
