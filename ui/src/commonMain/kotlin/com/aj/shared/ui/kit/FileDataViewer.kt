package com.aj.shared.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aj.shared.parser.Crc32
import com.aj.shared.parser.EazyFileParser
import com.aj.shared.parser.FileParseOptions
import com.aj.shared.parser.ParsedChart
import com.aj.shared.parser.ParseResult
import com.aj.shared.parser.ParseSuccess
import com.aj.shared.parser.ParsedSheet
import com.aj.shared.platform.ClipboardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val DefaultViewerMaxRows = 10_000
private const val ViewerHardMaxRows = 200_000
private const val ViewerAbsoluteMaxRows = 2_000_000
private const val LoadMoreStep = 20_000
private const val DefaultVisibleColumns = 16
private const val MaxSheetCacheEntries = 3
/** Spill bytes to temp file above this size so Compose can drop the [ByteArray]. */
private const val LargeBytesSpillThreshold = 1 * 1024 * 1024

private fun viewerHardMax(options: FileParseOptions): Int {
    val requested = options.maxRows ?: ViewerHardMaxRows
    return requested.coerceIn(DefaultViewerMaxRows, ViewerAbsoluteMaxRows)
}

/**
 * Spreadsheet viewer — progressive parse, load-more, sort, search, export.
 * Large buffers (>1MB) are spilled to a temp file automatically.
 */
@Composable
fun FileDataViewer(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    fileName: String? = null,
    mimeType: String? = null,
    options: FileParseOptions = FileParseOptions(
        allSheets = false,
        maxRows = DefaultViewerMaxRows,
        includeRowId = true,
        autoDetectHeader = true,
        skipHiddenRows = true,
    ),
    showSearch: Boolean = true,
    showRowNumbers: Boolean = true,
    cellMinWidth: Dp = 112.dp,
    onParsed: ((ParseSuccess) -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
    onExportCsv: ((String) -> Unit)? = null,
    onExportJson: ((String) -> Unit)? = null,
    editable: Boolean = false,
    onCellEdit: ((rowIndex: Int, column: String, newValue: String) -> Unit)? = null,
) {
    val contentKey = remember(bytes) { Crc32.fingerprint(bytes) }
    var spilledPath by remember(contentKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(contentKey, bytes.size) {
        if (bytes.size >= LargeBytesSpillThreshold) {
            val suffix = when {
                fileName?.endsWith(".xlsx", true) == true -> ".xlsx"
                fileName?.endsWith(".xls", true) == true -> ".xls"
                fileName?.endsWith(".csv", true) == true -> ".csv"
                fileName?.endsWith(".tsv", true) == true -> ".tsv"
                fileName?.endsWith(".vcf", true) == true -> ".vcf"
                else -> ".bin"
            }
            spilledPath = withContext(Dispatchers.Default) {
                EazyFileParser.writeTempFile(bytes, suffix)
            }
        } else {
            spilledPath = null
        }
    }

    val path = spilledPath
    if (bytes.size >= LargeBytesSpillThreshold && path != null) {
        DisposableEffect(path) {
            onDispose { EazyFileParser.deleteTempFile(path) }
        }
        FileDataViewerFromPath(
            path = path,
            modifier = modifier,
            fileName = fileName,
            mimeType = mimeType,
            options = options,
            showSearch = showSearch,
            showRowNumbers = showRowNumbers,
            cellMinWidth = cellMinWidth,
            onParsed = onParsed,
            onError = onError,
            onExportCsv = onExportCsv,
            onExportJson = onExportJson,
            editable = editable,
            onCellEdit = onCellEdit,
        )
        return
    }

    FileDataViewerBytesInner(
        bytes = bytes,
        contentKey = contentKey,
        modifier = modifier,
        fileName = fileName,
        mimeType = mimeType,
        options = options,
        showSearch = showSearch,
        showRowNumbers = showRowNumbers,
        cellMinWidth = cellMinWidth,
        onParsed = onParsed,
        onError = onError,
        onExportCsv = onExportCsv,
        onExportJson = onExportJson,
        editable = editable,
        onCellEdit = onCellEdit,
    )
}

@Composable
private fun FileDataViewerBytesInner(
    bytes: ByteArray,
    contentKey: String,
    modifier: Modifier,
    fileName: String?,
    mimeType: String?,
    options: FileParseOptions,
    showSearch: Boolean,
    showRowNumbers: Boolean,
    cellMinWidth: Dp,
    onParsed: ((ParseSuccess) -> Unit)?,
    onError: ((String) -> Unit)?,
    onExportCsv: ((String) -> Unit)?,
    onExportJson: ((String) -> Unit)?,
    editable: Boolean,
    onCellEdit: ((rowIndex: Int, column: String, newValue: String) -> Unit)?,
) {
    val hardMax = viewerHardMax(options)
    var sheetNames by remember(contentKey) { mutableStateOf<List<String>>(emptyList()) }
    var selectedSheet by remember(contentKey) { mutableIntStateOf(0) }
    var parseCap by remember(contentKey) {
        mutableIntStateOf((options.maxRows ?: DefaultViewerMaxRows).coerceAtMost(hardMax))
    }
    var visibleRows by remember(contentKey) {
        mutableIntStateOf(options.maxRows ?: DefaultViewerMaxRows)
    }
    val sheetCache = remember(contentKey) { mutableStateMapOf<String, ParseSuccess>() }
    val cacheOrder = remember(contentKey) { mutableListOf<String>() }
    val editsBySheet = remember(contentKey) { mutableStateMapOf<Int, MutableMap<String, String>>() }
    var prefetchRequested by remember(contentKey) { mutableStateOf(false) }
    var loading by remember(contentKey) { mutableStateOf(true) }
    var error by remember(contentKey) { mutableStateOf<String?>(null) }
    var errorCode by remember(contentKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(contentKey) {
        sheetNames = withContext(Dispatchers.Default) {
            EazyFileParser.listExcelSheetNames(bytes) { isActive }
                .ifEmpty {
                    listOf(
                        when {
                            fileName?.endsWith(".vcf", true) == true -> "contacts"
                            else -> "Sheet1"
                        },
                    )
                }
        }
    }

    val cacheKey = "$selectedSheet|$parseCap"
    LaunchedEffect(contentKey, fileName, mimeType, selectedSheet, parseCap, sheetNames) {
        sheetCache[cacheKey]?.let {
            loading = false
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        val result = withContext(Dispatchers.Default) {
            EazyFileParser.parse(
                bytes = bytes,
                fileName = fileName,
                mimeType = mimeType,
                options = options.copy(
                    allSheets = false,
                    sheetIndex = selectedSheet,
                    sheetName = sheetNames.getOrNull(selectedSheet),
                    maxRows = parseCap,
                    isActive = { isActive },
                ),
            )
        }
        ensureActive()
        when (result) {
            is ParseResult.Success -> {
                if (!sheetCache.containsKey(cacheKey)) {
                    cacheOrder.remove(cacheKey)
                    cacheOrder.add(cacheKey)
                    while (cacheOrder.size > MaxSheetCacheEntries) {
                        sheetCache.remove(cacheOrder.removeAt(0))
                    }
                }
                sheetCache.keys.filter {
                    it.startsWith("$selectedSheet|") && it != cacheKey
                }.forEach {
                    sheetCache.remove(it)
                    cacheOrder.remove(it)
                }
                sheetCache[cacheKey] = result.value
                onParsed?.invoke(result.value)
            }
            is ParseResult.Error -> {
                error = result.message
                errorCode = result.code
                onError?.invoke(result.message)
            }
        }
        loading = false
    }

    // On-demand warm only (first expand / near-end) — avoids double-parse on every open.
    LaunchedEffect(contentKey, selectedSheet, sheetNames, prefetchRequested) {
        if (!prefetchRequested) return@LaunchedEffect
        val warmKey = "$selectedSheet|$hardMax"
        if (sheetCache.containsKey(warmKey) || parseCap >= hardMax) return@LaunchedEffect
        val warmed = withContext(Dispatchers.Default) {
            EazyFileParser.parse(
                bytes = bytes,
                fileName = fileName,
                mimeType = mimeType,
                options = options.copy(
                    allSheets = false,
                    sheetIndex = selectedSheet,
                    sheetName = sheetNames.getOrNull(selectedSheet),
                    maxRows = hardMax,
                    isActive = { isActive },
                ),
            )
        }
        if (warmed is ParseResult.Success) {
            if (!sheetCache.containsKey(warmKey)) {
                cacheOrder.remove(warmKey)
                cacheOrder.add(warmKey)
                while (cacheOrder.size > MaxSheetCacheEntries) {
                    val old = cacheOrder.removeAt(0)
                    if (old == "$selectedSheet|$parseCap") {
                        cacheOrder.add(old)
                        if (cacheOrder.size <= MaxSheetCacheEntries) break
                        continue
                    }
                    sheetCache.remove(old)
                }
            }
            sheetCache[warmKey] = warmed.value
        }
    }

    val full = sheetCache[cacheKey]
    val data = full?.takeDataRows(visibleRows)
    val loaded = full?.loadedDataRows ?: full?.rows?.size ?: 0
    val warmReady = sheetCache.containsKey("$selectedSheet|$hardMax")
    val canExpandParse = full?.truncated == true && parseCap < hardMax
    val showLoadMore = full != null && (visibleRows < loaded || canExpandParse)
    val sheetEdits = editsBySheet.getOrPut(selectedSheet) { mutableStateMapOf() }

    fun requestMore() {
        when {
            visibleRows < loaded ->
                visibleRows = (visibleRows + LoadMoreStep).coerceAtMost(loaded)
            canExpandParse && warmReady -> {
                parseCap = hardMax
                val warmRows = sheetCache["$selectedSheet|$hardMax"]?.loadedDataRows
                    ?: sheetCache["$selectedSheet|$hardMax"]?.rows?.size
                    ?: hardMax
                visibleRows = (visibleRows + LoadMoreStep).coerceAtMost(warmRows)
            }
            canExpandParse -> {
                // Kick on-demand prefetch; also bump parseCap for immediate progress.
                prefetchRequested = true
                val next = (parseCap + LoadMoreStep).coerceAtMost(hardMax)
                parseCap = next
                visibleRows = next
            }
        }
    }

    when {
        loading && data == null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        error != null && data == null -> {
            EmptyStateView(
                title = when (errorCode) {
                    "ENCRYPTED" -> "Password-protected file"
                    "TOO_LARGE" -> "File / sheet too large"
                    "UNSUPPORTED" -> "Unsupported file / compression"
                    else -> "Could not open file"
                },
                subtitle = error,
                modifier = modifier.fillMaxSize(),
            )
        }
        else -> {
            Column(modifier.fillMaxSize()) {
                if (sheetNames.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedSheet.coerceIn(0, sheetNames.lastIndex.coerceAtLeast(0)),
                        edgePadding = 12.dp,
                        divider = {},
                    ) {
                        sheetNames.forEachIndexed { index, name ->
                            Tab(
                                selected = index == selectedSheet,
                                onClick = { selectedSheet = index },
                                text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
                if (loading) {
                    Text(
                        text = "Loading sheet…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                if (data != null) {
                    if (showLoadMore) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = when {
                                    visibleRows < loaded ->
                                        "Showing $visibleRows of $loaded loaded rows."
                                    canExpandParse && warmReady ->
                                        "Showing $visibleRows rows — more ready."
                                    canExpandParse && prefetchRequested ->
                                        "Showing $visibleRows rows — loading more…"
                                    canExpandParse ->
                                        "Showing $visibleRows rows — tap to load more."
                                    full.truncated ->
                                        "Showing $visibleRows rows (parser safety limit)."
                                    else -> "Showing $visibleRows rows."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                            )
                            if (visibleRows < loaded || canExpandParse) {
                                TextButton(onClick = { requestMore() }) {
                                    Text("Show +$LoadMoreStep")
                                }
                            }
                        }
                    }
                    FileDataViewer(
                        data = data,
                        modifier = Modifier.weight(1f),
                        showSearch = showSearch,
                        showRowNumbers = showRowNumbers,
                        cellMinWidth = cellMinWidth,
                        showSheetTabs = false,
                        onExportCsv = onExportCsv,
                        onExportJson = onExportJson,
                        editable = editable,
                        onCellEdit = onCellEdit,
                        onNearEnd = { requestMore() },
                        edits = sheetEdits,
                        editSheetIndex = selectedSheet,
                    )
                }
            }
        }
    }
}

/**
 * Path-based viewer: re-reads from disk per sheet/rowCap (does not keep a long-lived [ByteArray]
 * in Compose state). Better for large files on disk.
 */
@Composable
fun FileDataViewerFromPath(
    path: String,
    modifier: Modifier = Modifier,
    fileName: String? = null,
    mimeType: String? = null,
    options: FileParseOptions = FileParseOptions(
        allSheets = false,
        maxRows = DefaultViewerMaxRows,
        includeRowId = true,
        autoDetectHeader = true,
        skipHiddenRows = true,
    ),
    showSearch: Boolean = true,
    showRowNumbers: Boolean = true,
    cellMinWidth: Dp = 112.dp,
    onParsed: ((ParseSuccess) -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
    onExportCsv: ((String) -> Unit)? = null,
    onExportJson: ((String) -> Unit)? = null,
    editable: Boolean = false,
    onCellEdit: ((rowIndex: Int, column: String, newValue: String) -> Unit)? = null,
) {
    val hardMax = viewerHardMax(options)
    var sheetNames by remember(path) { mutableStateOf<List<String>>(emptyList()) }
    var selectedSheet by remember(path) { mutableIntStateOf(0) }
    var parseCap by remember(path) {
        mutableIntStateOf((options.maxRows ?: DefaultViewerMaxRows).coerceAtMost(hardMax))
    }
    var visibleRows by remember(path) {
        mutableIntStateOf(options.maxRows ?: DefaultViewerMaxRows)
    }
    val sheetCache = remember(path) { mutableStateMapOf<String, ParseSuccess>() }
    val cacheOrder = remember(path) { mutableListOf<String>() }
    val editsBySheet = remember(path) { mutableStateMapOf<Int, MutableMap<String, String>>() }
    var prefetchRequested by remember(path) { mutableStateOf(false) }
    var loading by remember(path) { mutableStateOf(true) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var errorCode by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        sheetNames = withContext(Dispatchers.Default) {
            EazyFileParser.listExcelSheetNamesFromPath(path) { isActive }
                .ifEmpty {
                    listOf(fileName?.substringBeforeLast('.') ?: "Sheet1")
                }
        }
    }

    val cacheKey = "$selectedSheet|$parseCap"
    LaunchedEffect(path, fileName, mimeType, selectedSheet, parseCap, sheetNames) {
        sheetCache[cacheKey]?.let {
            loading = false
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        val result = withContext(Dispatchers.Default) {
            EazyFileParser.parseFile(
                path = path,
                fileName = fileName,
                mimeType = mimeType,
                options = options.copy(
                    allSheets = false,
                    sheetIndex = selectedSheet,
                    sheetName = sheetNames.getOrNull(selectedSheet),
                    maxRows = parseCap,
                    isActive = { isActive },
                ),
            )
        }
        ensureActive()
        when (result) {
            is ParseResult.Success -> {
                if (!sheetCache.containsKey(cacheKey)) {
                    cacheOrder.remove(cacheKey)
                    cacheOrder.add(cacheKey)
                    while (cacheOrder.size > MaxSheetCacheEntries) {
                        sheetCache.remove(cacheOrder.removeAt(0))
                    }
                }
                sheetCache.keys.filter {
                    it.startsWith("$selectedSheet|") && it != cacheKey
                }.forEach {
                    sheetCache.remove(it)
                    cacheOrder.remove(it)
                }
                sheetCache[cacheKey] = result.value
                onParsed?.invoke(result.value)
            }
            is ParseResult.Error -> {
                error = result.message
                errorCode = result.code
                onError?.invoke(result.message)
            }
        }
        loading = false
    }

    LaunchedEffect(path, selectedSheet, sheetNames, prefetchRequested) {
        if (!prefetchRequested) return@LaunchedEffect
        val warmKey = "$selectedSheet|$hardMax"
        if (sheetCache.containsKey(warmKey) || parseCap >= hardMax) return@LaunchedEffect
        val warmed = withContext(Dispatchers.Default) {
            EazyFileParser.parseFile(
                path = path,
                fileName = fileName,
                mimeType = mimeType,
                options = options.copy(
                    allSheets = false,
                    sheetIndex = selectedSheet,
                    sheetName = sheetNames.getOrNull(selectedSheet),
                    maxRows = hardMax,
                    isActive = { isActive },
                ),
            )
        }
        if (warmed is ParseResult.Success) {
            if (!sheetCache.containsKey(warmKey)) {
                cacheOrder.remove(warmKey)
                cacheOrder.add(warmKey)
                while (cacheOrder.size > MaxSheetCacheEntries) {
                    val old = cacheOrder.removeAt(0)
                    if (old == "$selectedSheet|$parseCap") {
                        cacheOrder.add(old)
                        if (cacheOrder.size <= MaxSheetCacheEntries) break
                        continue
                    }
                    sheetCache.remove(old)
                }
            }
            sheetCache[warmKey] = warmed.value
        }
    }

    val full = sheetCache[cacheKey]
    val data = full?.takeDataRows(visibleRows)
    val loaded = full?.loadedDataRows ?: full?.rows?.size ?: 0
    val warmReady = sheetCache.containsKey("$selectedSheet|$hardMax")
    val canExpandParse = full?.truncated == true && parseCap < hardMax
    val showLoadMore = full != null && (visibleRows < loaded || canExpandParse)
    val sheetEdits = editsBySheet.getOrPut(selectedSheet) { mutableStateMapOf() }
    fun requestMore() {
        when {
            visibleRows < loaded ->
                visibleRows = (visibleRows + LoadMoreStep).coerceAtMost(loaded)
            canExpandParse && warmReady -> {
                parseCap = hardMax
                val warmRows = sheetCache["$selectedSheet|$hardMax"]?.loadedDataRows
                    ?: sheetCache["$selectedSheet|$hardMax"]?.rows?.size
                    ?: hardMax
                visibleRows = (visibleRows + LoadMoreStep).coerceAtMost(warmRows)
            }
            canExpandParse -> {
                prefetchRequested = true
                val next = (parseCap + LoadMoreStep).coerceAtMost(hardMax)
                parseCap = next
                visibleRows = next
            }
        }
    }
    when {
        loading && data == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null && data == null -> EmptyStateView(
            title = when (errorCode) {
                "ENCRYPTED" -> "Password-protected file"
                "TOO_LARGE" -> "File / sheet too large"
                "UNSUPPORTED" -> "Unsupported file / compression"
                else -> "Could not open file"
            },
            subtitle = error,
            modifier = modifier.fillMaxSize(),
        )
        data != null -> Column(modifier.fillMaxSize()) {
            if (sheetNames.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = selectedSheet.coerceIn(0, sheetNames.lastIndex.coerceAtLeast(0)),
                    edgePadding = 12.dp,
                    divider = {},
                ) {
                    sheetNames.forEachIndexed { index, name ->
                        Tab(
                            selected = index == selectedSheet,
                            onClick = { selectedSheet = index },
                            text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            if (showLoadMore) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when {
                            visibleRows < loaded -> "Showing $visibleRows of $loaded loaded rows."
                            canExpandParse && warmReady -> "Showing $visibleRows rows — more ready."
                            canExpandParse && prefetchRequested -> "Showing $visibleRows rows — loading more…"
                            canExpandParse -> "Showing $visibleRows rows — tap to load more."
                            full.truncated -> "Showing $visibleRows rows (parser safety limit)."
                            else -> "Showing $visibleRows rows."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (visibleRows < loaded || canExpandParse) {
                        TextButton(onClick = { requestMore() }) { Text("Show +$LoadMoreStep") }
                    }
                }
            }
            FileDataViewer(
                data = data,
                modifier = Modifier.weight(1f),
                showSearch = showSearch,
                showRowNumbers = showRowNumbers,
                cellMinWidth = cellMinWidth,
                showSheetTabs = false,
                onExportCsv = onExportCsv,
                onExportJson = onExportJson,
                editable = editable,
                onCellEdit = onCellEdit,
                onNearEnd = { requestMore() },
                edits = sheetEdits,
                editSheetIndex = selectedSheet,
            )
        }
    }
}

@Composable
fun FileDataViewer(
    data: ParseSuccess,
    modifier: Modifier = Modifier,
    showSearch: Boolean = true,
    showRowNumbers: Boolean = true,
    cellMinWidth: Dp = 112.dp,
    showSheetTabs: Boolean = true,
    onExportCsv: ((String) -> Unit)? = null,
    onExportJson: ((String) -> Unit)? = null,
    editable: Boolean = false,
    onCellEdit: ((rowIndex: Int, column: String, newValue: String) -> Unit)? = null,
    onNearEnd: (() -> Unit)? = null,
    /**
     * Optional external edit map (survives parseCap / takeDataRows refresh).
     * Keys: `"rowIndex|column"`. When null, edits are kept in a per-sheet map that
     * does **not** reset when [data] identity changes.
     */
    edits: MutableMap<String, String>? = null,
    /** When using external edits for a single-sheet payload, which logical sheet they belong to. */
    editSheetIndex: Int = 0,
) {
    var sheetIndex by remember { mutableIntStateOf(0) }
    val sheets = data.sheets
    val safeIndex = sheetIndex.coerceIn(0, (sheets.size - 1).coerceAtLeast(0))
    val sheet = sheets.getOrNull(safeIndex)
    val sheetEditable = editable && sheet?.name != "_meta"
    val clipboard = remember { ClipboardManager() }
    val share = remember { com.aj.shared.share.ShareManager() }
    // Per logical sheet — survives data refresh from progressive load / windowing.
    val editsBySheet = remember { mutableStateMapOf<Int, MutableMap<String, String>>() }
    val activeEdits = edits ?: editsBySheet.getOrPut(safeIndex) { mutableStateMapOf() }

    fun editedData(): ParseSuccess {
        if (edits != null) {
            // Parent owns a single-sheet window; edits apply to sheet 0 of [data].
            return data.withCellEdits(0, edits)
        }
        var out = data
        for ((si, map) in editsBySheet) {
            if (map.isNotEmpty() && si in out.sheets.indices) {
                out = out.withCellEdits(si, map)
            }
        }
        return out
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.sourceName ?: data.format.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = buildString {
                    append("${sheet?.rows?.size ?: 0} rows")
                    if (data.truncated) append(" · truncated")
                    if (activeEdits.isNotEmpty()) append(" · ${activeEdits.size} edits")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                onClick = {
                    val csv = editedData().toCsvString(safeIndex)
                    clipboard.copy(csv, "csv")
                    onExportCsv?.invoke(csv)
                },
            ) { Text("Copy CSV") }
            TextButton(
                onClick = {
                    val json = editedData().toJsonString(pretty = false)
                    clipboard.copy(json, "json")
                    onExportJson?.invoke(json)
                },
            ) { Text("Copy JSON") }
            TextButton(
                onClick = {
                    val csv = editedData().toCsvString(safeIndex)
                    share.shareText(csv)
                    onExportCsv?.invoke(csv)
                },
            ) { Text("Share CSV") }
            TextButton(
                onClick = {
                    val csv = editedData().toCsvString(safeIndex)
                    val name = (data.sourceName ?: "export").substringBeforeLast('.') + ".csv"
                    share.shareFile(csv.encodeToByteArray(), name, "text/csv")
                    onExportCsv?.invoke(csv)
                },
            ) { Text("Save CSV") }
            TextButton(
                onClick = {
                    val xlsx = EazyFileParser.exportXlsx(editedData(), sheetIndex = -1)
                    val name = (data.sourceName ?: "export").substringBeforeLast('.') + ".xlsx"
                    share.shareFile(
                        xlsx,
                        name,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    )
                },
            ) { Text("Save XLSX") }
        }

        if (data.warnings.isNotEmpty()) {
            Text(
                text = data.warnings.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (data.charts.isNotEmpty()) {
            ChartPreviewStrip(charts = data.charts)
        }

        if (showSheetTabs && sheets.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = safeIndex,
                edgePadding = 12.dp,
                divider = {},
            ) {
                sheets.forEachIndexed { index, s ->
                    Tab(
                        selected = index == safeIndex,
                        onClick = { sheetIndex = index },
                        text = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        if (sheet == null) {
            EmptyStateView(
                title = "No data",
                subtitle = "This file has no rows to show.",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ParsedSheetTable(
                sheet = sheet,
                showSearch = showSearch,
                showRowNumbers = showRowNumbers,
                cellMinWidth = cellMinWidth,
                modifier = Modifier.weight(1f),
                truncatedHint = data.truncated,
                editable = sheetEditable,
                edits = activeEdits,
                onCellEdit = onCellEdit,
                onNearEnd = onNearEnd,
                onSearchActive = if (data.truncated) onNearEnd else null,
            )
        }
    }
}

private enum class SortDir { Asc, Desc }

@OptIn(FlowPreview::class)
@Composable
fun ParsedSheetTable(
    sheet: ParsedSheet,
    modifier: Modifier = Modifier,
    showSearch: Boolean = true,
    showRowNumbers: Boolean = true,
    cellMinWidth: Dp = 112.dp,
    maxVisibleColumns: Int = DefaultVisibleColumns,
    /** How many leading data columns stay fixed while the rest scroll (0–5). */
    freezeColumns: Int = 1,
    truncatedHint: Boolean = false,
    editable: Boolean = false,
    edits: MutableMap<String, String>? = null,
    onCellEdit: ((rowIndex: Int, column: String, newValue: String) -> Unit)? = null,
    onNearEnd: (() -> Unit)? = null,
    /** When truncated, searching asks parent to load more rows from file. */
    onSearchActive: (() -> Unit)? = null,
) {
    var queryInput by remember(sheet) { mutableStateOf("") }
    var query by remember(sheet) { mutableStateOf("") }
    var activeColumn by remember(sheet) { mutableStateOf<String?>(null) }
    var colWindowStart by remember(sheet) { mutableIntStateOf(0) }
    var sortCol by remember(sheet) { mutableStateOf<String?>(null) }
    var sortDir by remember(sheet) { mutableStateOf(SortDir.Asc) }
    var showAllColumns by remember(sheet) { mutableStateOf(false) }
    val localEdits = remember(sheet) { mutableStateMapOf<String, String>() }
    val editMap = edits ?: localEdits
    val undoStack = remember(sheet) { mutableListOf<Pair<String, String?>>() }
    var undoDepth by remember(sheet) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(queryInput) {
        snapshotFlow { queryInput }
            .debounce(250)
            .distinctUntilChanged()
            .collect {
                query = it
                if (it.isNotBlank()) onSearchActive?.invoke()
            }
    }

    val allColumns = remember(sheet, showRowNumbers) {
        if (showRowNumbers && sheet.columns.firstOrNull() == "id") sheet.columns.drop(1)
        else sheet.columns
    }
    val visibleColumns = if (showAllColumns) {
        allColumns
    } else {
        val end = (colWindowStart + maxVisibleColumns).coerceAtMost(allColumns.size)
        allColumns.subList(colWindowStart, end)
    }
    val colEnd = colWindowStart + visibleColumns.size
    val effectiveCellWidth = remember(allColumns.size, cellMinWidth, showAllColumns) {
        when {
            showAllColumns && allColumns.size > 40 -> 72.dp
            allColumns.size > 40 -> 88.dp
            allColumns.size > 20 -> 96.dp
            else -> cellMinWidth
        }
    }

    val filtered = remember(sheet, query, activeColumn, sortCol, sortDir) {
        val q = query.trim()
        var indexed = sheet.rows.mapIndexed { i, row -> i to row }
        if (q.isNotEmpty()) {
            indexed = indexed.filter { (_, row) ->
                if (activeColumn != null) {
                    row[activeColumn].orEmpty().contains(q, ignoreCase = true)
                } else {
                    row.values.any { it.contains(q, ignoreCase = true) }
                }
            }
        }
        val sc = sortCol
        if (sc != null) {
            indexed = indexed.sortedWith { a, b ->
                val av = a.second[sc].orEmpty()
                val bv = b.second[sc].orEmpty()
                val an = av.toDoubleOrNull()
                val bn = bv.toDoubleOrNull()
                val cmp = when {
                    an != null && bn != null -> an.compareTo(bn)
                    else -> av.compareTo(bv, ignoreCase = true)
                }
                if (sortDir == SortDir.Asc) cmp else -cmp
            }
        }
        indexed
    }

    LaunchedEffect(listState, filtered.size, onNearEnd) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 3) onNearEnd?.invoke()
        }
    }

    val rowNumberWidth = 56.dp

    Column(modifier = modifier.fillMaxSize()) {
        if (showSearch) {
            SearchBar(
                query = queryInput,
                onQueryChange = { queryInput = it },
                placeholder = if (truncatedHint) {
                    "Search loaded rows (file may be truncated)"
                } else {
                    "Search in table"
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (truncatedHint && query.isNotBlank()) {
                Text(
                    text = "Search runs on currently loaded rows only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (allColumns.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = activeColumn == null,
                        onClick = { activeColumn = null },
                        label = { Text("All columns") },
                    )
                    allColumns.forEach { col ->
                        FilterChip(
                            selected = activeColumn == col,
                            onClick = { activeColumn = col },
                            label = { Text(col) },
                        )
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!showAllColumns && allColumns.size > maxVisibleColumns) {
                    TextButton(
                        onClick = { colWindowStart = (colWindowStart - maxVisibleColumns).coerceAtLeast(0) },
                        enabled = colWindowStart > 0,
                    ) { Text("◀") }
                    Text(
                        text = "Cols ${colWindowStart + 1}–$colEnd / ${allColumns.size}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    TextButton(
                        onClick = {
                            colWindowStart = (colWindowStart + maxVisibleColumns)
                                .coerceAtMost((allColumns.size - maxVisibleColumns).coerceAtLeast(0))
                        },
                        enabled = colEnd < allColumns.size,
                    ) { Text("▶") }
                }
                TextButton(onClick = { showAllColumns = !showAllColumns }) {
                    Text(if (showAllColumns) "Window columns" else "Show all columns")
                }
                if (editable) {
                    TextButton(
                        onClick = {
                            val last = undoStack.removeLastOrNull() ?: return@TextButton
                            val (key, prev) = last
                            if (prev == null) editMap.remove(key) else editMap[key] = prev
                            undoDepth = undoStack.size
                        },
                        enabled = undoDepth > 0,
                    ) { Text("Undo") }
                }
            }
            Text(
                text = buildString {
                    append(if (query.isBlank()) "${filtered.size} rows · ${allColumns.size} columns" else "${filtered.size} of ${sheet.rows.size} rows")
                    if (sortCol != null) append(" · sort: $sortCol ${if (sortDir == SortDir.Asc) "↑" else "↓"}")
                    append(" · tap header to sort")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (filtered.isEmpty()) {
            EmptyStateView(
                title = if (sheet.rows.isEmpty()) "Empty sheet" else "No matches",
                subtitle = if (sheet.rows.isEmpty()) "\"${sheet.name}\" has no data rows." else "Try another search.",
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            return
        }

        val hScroll = rememberScrollState()
        var scrollX by remember { mutableStateOf(0f) }
        LaunchedEffect(hScroll) {
            snapshotFlow { hScroll.value }.collect { scrollX = it.toFloat() }
        }
        // Freeze panes lite: keep # + first N visible columns fixed while the rest scroll.
        val freezeCount = freezeColumns.coerceIn(0, 5)
        val freezeFirstCol = visibleColumns.isNotEmpty() && !showAllColumns && freezeCount > 0
        val frozenCols = if (freezeFirstCol) visibleColumns.take(freezeCount) else emptyList()
        val scrollCols = if (freezeFirstCol) visibleColumns.drop(freezeCount) else visibleColumns
        val scrollTableWidth = remember(scrollCols, effectiveCellWidth) {
            var w = 0.dp
            scrollCols.forEach { w += effectiveCellWidth }
            w.coerceAtLeast(1.dp)
        }
        val shape = RoundedCornerShape(8.dp)
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .weight(1f)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                ) {
                    if (showRowNumbers) HeaderCell("#", cellMinWidth = rowNumberWidth)
                    frozenCols.forEach { col ->
                        HeaderCell(
                            text = buildString {
                                append(col)
                                if (sortCol == col) append(if (sortDir == SortDir.Asc) " ↑" else " ↓")
                            },
                            cellMinWidth = effectiveCellWidth,
                            onClick = {
                                if (sortCol == col) {
                                    sortDir = if (sortDir == SortDir.Asc) SortDir.Desc else SortDir.Asc
                                } else {
                                    sortCol = col
                                    sortDir = SortDir.Asc
                                }
                            },
                        )
                    }
                    Row(Modifier.horizontalScroll(hScroll)) {
                        scrollCols.forEach { col ->
                            HeaderCell(
                                text = buildString {
                                    append(col)
                                    if (sortCol == col) append(if (sortDir == SortDir.Asc) " ↑" else " ↓")
                                },
                                cellMinWidth = effectiveCellWidth,
                                onClick = {
                                    if (sortCol == col) {
                                        sortDir = if (sortDir == SortDir.Asc) SortDir.Desc else SortDir.Asc
                                    } else {
                                        sortCol = col
                                        sortDir = SortDir.Asc
                                    }
                                },
                            )
                        }
                    }
                }
                HorizontalDivider()
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        itemsIndexed(items = filtered) { displayIndex, (origIndex, row) ->
                            val bg = if (displayIndex % 2 == 0) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bg)
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                            ) {
                                if (showRowNumbers) {
                                    BodyCell(
                                        text = row["id"] ?: (origIndex + 1).toString(),
                                        cellMinWidth = rowNumberWidth,
                                        muted = true,
                                    )
                                }
                                frozenCols.forEach { col ->
                                    val key = "$origIndex|$col"
                                    val text = editMap[key] ?: row[col].orEmpty()
                                    CellValue(
                                        text = text,
                                        editable = editable,
                                        cellMinWidth = effectiveCellWidth,
                                        onChange = { v ->
                                            editMap.putWithUndo(key, v, undoStack) { undoDepth = it }
                                            onCellEdit?.invoke(origIndex, col, v)
                                        },
                                    )
                                }
                                Box(
                                    Modifier
                                        .weight(1f, fill = true)
                                        .clipToBounds(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .graphicsLayer { translationX = -scrollX }
                                            .requiredWidth(scrollTableWidth),
                                    ) {
                                        scrollCols.forEach { col ->
                                            val key = "$origIndex|$col"
                                            val text = editMap[key] ?: row[col].orEmpty()
                                            CellValue(
                                                text = text,
                                                editable = editable,
                                                cellMinWidth = effectiveCellWidth,
                                                onChange = { v ->
                                                    editMap.putWithUndo(key, v, undoStack) { undoDepth = it }
                                                    onCellEdit?.invoke(origIndex, col, v)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CellValue(
    text: String,
    editable: Boolean,
    cellMinWidth: Dp,
    onChange: (String) -> Unit,
) {
    if (editable) {
        BasicTextField(
            value = text,
            onValueChange = onChange,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
            ),
            modifier = Modifier
                .width(cellMinWidth)
                .padding(horizontal = 8.dp),
            singleLine = true,
        )
    } else {
        BodyCell(text = text, cellMinWidth = cellMinWidth)
    }
}

private fun MutableMap<String, String>.putWithUndo(
    key: String,
    newValue: String,
    undoStack: MutableList<Pair<String, String?>>,
    onDepth: (Int) -> Unit,
) {
    val prev = this[key]
    if (prev == newValue) return
    // Coalesce rapid typing on the same cell into one undo step.
    if (undoStack.lastOrNull()?.first != key) {
        undoStack += key to prev
        if (undoStack.size > 100) undoStack.removeAt(0)
        onDepth(undoStack.size)
    }
    this[key] = newValue
}

@Composable
private fun HeaderCell(
    text: String,
    cellMinWidth: Dp,
    onClick: (() -> Unit)? = null,
) {
    Text(
        text = text,
        modifier = Modifier
            .width(cellMinWidth)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun BodyCell(
    text: String,
    cellMinWidth: Dp,
    muted: Boolean = false,
) {
    Text(
        text = text.ifEmpty { "—" },
        modifier = Modifier
            .width(cellMinWidth)
            .padding(horizontal = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = if (muted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ChartPreviewStrip(charts: List<ParsedChart>) {
    val scroll = rememberScrollState()
    val barColor = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            text = "Charts",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            charts.take(4).forEach { chart ->
                val vals = chart.values.take(24)
                val max = vals.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                Column(Modifier.width(200.dp)) {
                    Text(
                        text = chart.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    ) {
                        if (vals.isEmpty()) return@Canvas
                        val gap = 3f
                        val barW = ((size.width - gap * (vals.size + 1)) / vals.size).coerceAtLeast(2f)
                        vals.forEachIndexed { i, v ->
                            val h = ((v / max) * (size.height - 8f)).toFloat().coerceAtLeast(1f)
                            val x = gap + i * (barW + gap)
                            drawRect(
                                color = barColor,
                                topLeft = Offset(x, size.height - h - 4f),
                                size = Size(barW, h),
                            )
                        }
                    }
                }
            }
        }
    }
}
