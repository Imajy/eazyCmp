package com.aj.shared.pdf.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aj.shared.pdf.model.PdfPageInfo
import com.aj.shared.pdf.state.PdfViewerState
import com.aj.shared.pdf.state.rememberPdfViewerState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * High-level, feature-rich PDF Viewer Composable for Kotlin Multiplatform.
 * Supports continuous vertical scrolling, single-page paging, pinch-to-zoom,
 * pan, in-document keyword search, page thumbnail drawer, rotation, and night mode.
 */
@Composable
fun PdfViewer(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    fileName: String? = null,
    password: String? = null,
    state: PdfViewerState = rememberPdfViewerState(),
    enableSearch: Boolean = true,
    enableThumbnails: Boolean = true,
    enableZoomControls: Boolean = true,
    onPageChanged: ((page: Int, totalPages: Int) -> Unit)? = null
) {
    LaunchedEffect(bytes, password) {
        state.load(bytes, password)
    }

    DisposableEffect(Unit) {
        onDispose {
            state.release()
        }
    }

    LaunchedEffect(state.currentPage, state.totalPages) {
        if (state.totalPages > 0) {
            onPageChanged?.invoke(state.currentPage + 1, state.totalPages)
        }
    }

    val backgroundColor = if (state.isNightMode) Color(0xFF1E1E1E) else Color(0xFFF0F2F5)
    val contentColor = if (state.isNightMode) Color(0xFFE0E0E0) else Color(0xFF212121)

    // Night mode color inversion matrix
    val nightModeMatrix = remember {
        ColorMatrix(
            floatArrayOf(
                -1f,  0f,  0f, 0f, 255f,
                 0f, -1f,  0f, 0f, 255f,
                 0f,  0f, -1f, 0f, 255f,
                 0f,  0f,  0f, 1f,   0f
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // --- Top Control Bar ---
        Surface(
            color = if (state.isNightMode) Color(0xFF2C2C2C) else MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Document Title & Page Count
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = fileName ?: state.documentInfo?.title ?: "PDF Document",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.totalPages > 0) {
                            Text(
                                text = "Page ${state.currentPage + 1} of ${state.totalPages}",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Action buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (enableZoomControls) {
                            TextButton(onClick = { state.zoomOut() }) {
                                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = contentColor)
                            }
                            Text(
                                text = "${(state.zoomScale * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor,
                                modifier = Modifier
                                    .clickable { state.resetZoom() }
                                    .padding(horizontal = 4.dp)
                            )
                            TextButton(onClick = { state.zoomIn() }) {
                                Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = contentColor)
                            }
                        }

                        // Rotate
                        TextButton(onClick = { state.rotateClockwise() }) {
                            Text("⟳", fontSize = 16.sp, color = contentColor)
                        }

                        // Night Mode toggle
                        TextButton(onClick = { state.toggleNightMode() }) {
                            Text(if (state.isNightMode) "☀️" else "🌙", fontSize = 14.sp)
                        }

                        // Search
                        if (enableSearch) {
                            TextButton(onClick = { state.toggleSearch() }) {
                                Text("🔍", fontSize = 14.sp)
                            }
                        }

                        // Thumbnails
                        if (enableThumbnails) {
                            TextButton(onClick = { state.showThumbnails = !state.showThumbnails }) {
                                Text("📑", fontSize = 14.sp)
                            }
                        }
                    }
                }

                // --- In-Document Search Bar ---
                AnimatedVisibility(visible = state.isSearchActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (state.isNightMode) Color(0xFF383838) else Color(0xFFE8ECEF))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = { state.performSearch(it) },
                            singleLine = true,
                            textStyle = TextStyle(color = contentColor, fontSize = 14.sp),
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (state.isNightMode) Color(0xFF222222) else Color.White,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            decorationBox = { innerTextField ->
                                if (state.searchQuery.isEmpty()) {
                                    Text("Search in document...", color = Color.Gray, fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (state.searchMatches.isNotEmpty()) {
                            Text(
                                text = "${state.currentMatchIndex + 1}/${state.searchMatches.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor
                            )
                            TextButton(onClick = { state.prevSearchMatch() }) {
                                Text("▲", fontSize = 12.sp, color = contentColor)
                            }
                            TextButton(onClick = { state.nextSearchMatch() }) {
                                Text("▼", fontSize = 12.sp, color = contentColor)
                            }
                        } else if (state.searchQuery.isNotBlank()) {
                            Text(
                                text = "No matches",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        TextButton(onClick = { state.toggleSearch() }) {
                            Text("✕", fontSize = 14.sp, color = contentColor)
                        }
                    }
                }
            }
        }

        // --- Main Content Area ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading PDF...", color = contentColor)
                    }
                }
                state.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Error Loading PDF",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                state.pageInfos.isNotEmpty() -> {
                    if (state.isContinuousScroll) {
                        ContinuousPdfScrollView(
                            state = state,
                            nightModeMatrix = if (state.isNightMode) nightModeMatrix else null
                        )
                    } else {
                        SinglePagePdfView(
                            state = state,
                            nightModeMatrix = if (state.isNightMode) nightModeMatrix else null
                        )
                    }
                }
            }

            // Quick floating page jumper controls
            if (state.totalPages > 1 && !state.isLoading) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (state.isNightMode) Color(0xCC333333) else Color(0xCCFFFFFF),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (state.showThumbnails) 100.dp else 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { state.prevPage() }, enabled = state.currentPage > 0) {
                            Text("◀", fontSize = 12.sp, color = contentColor)
                        }
                        Text(
                            text = "${state.currentPage + 1} / ${state.totalPages}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = contentColor,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        TextButton(onClick = { state.nextPage() }, enabled = state.currentPage < state.totalPages - 1) {
                            Text("▶", fontSize = 12.sp, color = contentColor)
                        }
                    }
                }
            }
        }

        // --- Thumbnail Drawer Bar ---
        AnimatedVisibility(visible = state.showThumbnails && state.pageInfos.isNotEmpty()) {
            Surface(
                color = if (state.isNightMode) Color(0xFF252525) else Color(0xFFE5E9EC),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(state.pageInfos) { index, page ->
                        val isSelected = index == state.currentPage
                        Card(
                            modifier = Modifier
                                .height(80.dp)
                                .aspectRatio(page.aspectRatio.coerceIn(0.5f, 1.5f))
                                .clickable {
                                    state.jumpToPage(index)
                                }
                                .border(
                                    width = if (isSelected) 2.dp else 0.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = RoundedCornerShape(4.dp)
                                ),
                            shape = RoundedCornerShape(4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val bitmap = state.pageBitmaps[index]
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Thumbnail page ${index + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    LaunchedEffect(index) {
                                        state.renderPageBitmap(index, scale = 0.5f)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${index + 1}", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                                // Page badge
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(Color(0x99000000), RoundedCornerShape(topStart = 4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Continuous vertical scrolling PDF view.
 */
@Composable
private fun ContinuousPdfScrollView(
    state: PdfViewerState,
    nightModeMatrix: ColorMatrix?
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Sync lazy list scroll to current page
    LaunchedEffect(state.currentPage) {
        if (state.currentPage in state.pageInfos.indices && !listState.isScrollInProgress) {
            val visibleIndex = listState.firstVisibleItemIndex
            if (visibleIndex != state.currentPage) {
                listState.animateScrollToItem(state.currentPage)
            }
        }
    }

    // Sync scroll position back to current page
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisible ->
                if (firstVisible != state.currentPage && firstVisible in state.pageInfos.indices) {
                    state.currentPage = firstVisible
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newZoom = (state.zoomScale * zoom).coerceIn(0.75f, 4.0f)
                    state.zoomScale = newZoom
                    if (newZoom > 1.0f) {
                        state.panOffset += pan
                    } else {
                        state.panOffset = androidx.compose.ui.geometry.Offset.Zero
                    }
                }
            },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(state.pageInfos) { index, page ->
            PdfPageItem(
                pageIndex = index,
                pageInfo = page,
                state = state,
                nightModeMatrix = nightModeMatrix
            )
        }
    }
}

/**
 * Single-page swipeable PDF view.
 */
@Composable
private fun SinglePagePdfView(
    state: PdfViewerState,
    nightModeMatrix: ColorMatrix?
) {
    val page = state.pageInfos.getOrNull(state.currentPage) ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        PdfPageItem(
            pageIndex = state.currentPage,
            pageInfo = page,
            state = state,
            nightModeMatrix = nightModeMatrix
        )
    }
}

/**
 * Card containing an individual rendered PDF page with zoom/pan and shadows.
 */
@Composable
private fun PdfPageItem(
    pageIndex: Int,
    pageInfo: PdfPageInfo,
    state: PdfViewerState,
    nightModeMatrix: ColorMatrix?
) {
    LaunchedEffect(pageIndex) {
        state.renderPageBitmap(pageIndex, scale = 1.5f)
    }

    val bitmap = state.pageBitmaps[pageIndex]
    val rotation = (pageInfo.rotation + state.rotationOffset) % 360

    Card(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .aspectRatio(pageInfo.aspectRatio.coerceIn(0.5f, 1.8f))
            .graphicsLayer {
                scaleX = state.zoomScale
                scaleY = state.zoomScale
                translationX = state.panOffset.x
                translationY = state.panOffset.y
                rotationZ = rotation.toFloat()
            }
            .shadow(6.dp, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "PDF Page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    colorFilter = nightModeMatrix?.let { ColorFilter.colorMatrix(it) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
