package com.aj.shared.pdf.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aj.shared.pdf.model.PdfFreehandStroke
import com.aj.shared.pdf.model.PdfHighlightAnnotation
import com.aj.shared.pdf.model.PdfPageInfo
import com.aj.shared.pdf.model.PdfPoint
import com.aj.shared.pdf.model.PdfShapeAnnotation
import com.aj.shared.pdf.model.PdfShapeType
import com.aj.shared.pdf.model.PdfSignatureAnnotation
import com.aj.shared.pdf.model.PdfStampAnnotation
import com.aj.shared.pdf.model.PdfTextAnnotation
import com.aj.shared.pdf.state.PdfEditorState
import com.aj.shared.pdf.state.PdfEditorTool
import com.aj.shared.pdf.state.rememberPdfEditorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-featured PDF Editor Composable for Kotlin Multiplatform.
 * Allows drawing with pen/highlighter, adding custom text notes, inserting signatures,
 * placing stamps, drawing geometric shapes, reordering/rotating/deleting pages,
 * full undo/redo, and exporting the modified document.
 */
@Composable
fun PdfEditor(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    fileName: String? = null,
    password: String? = null,
    state: PdfEditorState = rememberPdfEditorState(),
    onSave: ((ByteArray) -> Unit)? = null,
    onExport: ((fileBytes: ByteArray, fileName: String) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(bytes, password) {
        state.load(bytes, password)
    }

    DisposableEffect(Unit) {
        onDispose {
            state.release()
        }
    }

    // Color palette options
    val colorPalette: List<Pair<Long, Color>> = listOf(
        0xFF000000L to Color.Black,
        0xFF1E88E5L to Color(0xFF1E88E5), // Blue
        0xFFE53935L to Color(0xFFE53935), // Red
        0xFF43A047L to Color(0xFF43A047), // Green
        0xFFFDD835L to Color(0xFFFDD835), // Yellow
        0xFF8E24AAL to Color(0xFF8E24AA)  // Purple
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5))
    ) {
        // --- Top App Bar ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Title
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = fileName ?: state.documentInfo?.title ?: "Edit PDF",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.nonDeletedPages.isNotEmpty()) {
                        Text(
                            text = "Page ${state.currentPageIndex + 1} of ${state.nonDeletedPages.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                // Top Actions: Undo, Redo, Page Manager, Save
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { state.undo() },
                        enabled = state.canUndo
                    ) {
                        Text("↩️ Undo", fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = { state.redo() },
                        enabled = state.canRedo
                    ) {
                        Text("↪️ Redo", fontSize = 12.sp)
                    }

                    TextButton(onClick = { state.showPageManager = true }) {
                        Text("📑 Pages", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                state.isExporting = true
                                val exported = withContext(Dispatchers.Default) {
                                    state.exportPdf()
                                }
                                state.isExporting = false
                                onSave?.invoke(exported)
                                onExport?.invoke(exported, fileName ?: "document_edited.pdf")
                            }
                        },
                        enabled = !state.isLoading && !state.isExporting,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        if (state.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // --- Main Editing Canvas Area ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                state.currentPage != null -> {
                    PdfEditorPageView(
                        page = state.currentPage!!,
                        state = state
                    )
                }
                else -> {
                    Text("No pages available in document", color = Color.Gray)
                }
            }

            // Quick page indicator at bottom
            if (state.nonDeletedPages.size > 1) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xDDFFFFFF),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (state.currentPageIndex > 0) {
                                    state.currentPageIndex--
                                    state.renderPageBitmap(state.currentPageIndex)
                                }
                            },
                            enabled = state.currentPageIndex > 0
                        ) {
                            Text("◀", fontSize = 12.sp)
                        }
                        Text(
                            text = "${state.currentPageIndex + 1} / ${state.nonDeletedPages.size}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        TextButton(
                            onClick = {
                                if (state.currentPageIndex < state.nonDeletedPages.size - 1) {
                                    state.currentPageIndex++
                                    state.renderPageBitmap(state.currentPageIndex)
                                }
                            },
                            enabled = state.currentPageIndex < state.nonDeletedPages.size - 1
                        ) {
                            Text("▶", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- Tool Settings Bar (Colors / Stroke Width) ---
        AnimatedVisibility(
            visible = state.activeTool == PdfEditorTool.PEN ||
                      state.activeTool == PdfEditorTool.HIGHLIGHTER ||
                      state.activeTool == PdfEditorTool.SHAPE
        ) {
            Surface(
                color = Color(0xFFE8ECEF),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Color swatches
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colorPalette.forEach { (argb, color) ->
                            val isSelected = if (state.activeTool == PdfEditorTool.HIGHLIGHTER) {
                                (state.highlighterColor and 0x00FFFFFFL) == (argb and 0x00FFFFFFL)
                            } else {
                                state.penColor == argb
                            }
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable {
                                        if (state.activeTool == PdfEditorTool.HIGHLIGHTER) {
                                            state.highlighterColor = (0x66000000L or (argb and 0x00FFFFFFL))
                                        } else {
                                            state.penColor = argb
                                        }
                                    }
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    // Stroke width toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Size: ", style = MaterialTheme.typography.bodySmall)
                        val sizes = listOf(2f to "S", 4f to "M", 8f to "L")
                        sizes.forEach { (w, label) ->
                            val isSelected = state.penStrokeWidth == w
                            TextButton(
                                onClick = { state.penStrokeWidth = w },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Bottom Main Tools Dock ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(PdfEditorTool.values()) { tool ->
                    val isSelected = state.activeTool == tool
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                state.activeTool = tool
                                when (tool) {
                                    PdfEditorTool.SIGNATURE -> state.showSignaturePad = true
                                    PdfEditorTool.STAMP -> state.showStampPicker = true
                                    else -> {}
                                }
                            }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = tool.icon, fontSize = 18.sp)
                        Text(
                            text = tool.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    // Signature Pad Dialog
    if (state.showSignaturePad) {
        PdfSignaturePadDialog(
            onDismiss = { state.showSignaturePad = false },
            onSignatureCaptured = { strokes ->
                state.showSignaturePad = false
                val page = state.currentPage ?: return@PdfSignaturePadDialog
                val sig = PdfSignatureAnnotation(
                    id = "sig_${page.pageIndex}",
                    pageIndex = page.pageIndex,
                    x = (page.width * 0.35f),
                    y = (page.height * 0.75f),
                    strokes = strokes
                )
                state.addAnnotation(sig)
            }
        )
    }

    // Stamp Picker Dialog
    if (state.showStampPicker) {
        PdfStampPickerDialog(
            onDismiss = { state.showStampPicker = false },
            onStampSelected = { stampType, customText ->
                state.showStampPicker = false
                val page = state.currentPage ?: return@PdfStampPickerDialog
                val stamp = PdfStampAnnotation(
                    id = "stamp_${page.pageIndex}",
                    pageIndex = page.pageIndex,
                    x = (page.width * 0.35f),
                    y = (page.height * 0.4f),
                    stampType = stampType,
                    customText = customText
                )
                state.addAnnotation(stamp)
            }
        )
    }

    // Page Manager Dialog
    if (state.showPageManager) {
        PdfPageManagerDialog(
            state = state,
            onDismiss = { state.showPageManager = false }
        )
    }

    // Text Entry Dialog
    if (state.showTextEntryDialog) {
        var enteredText by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { state.showTextEntryDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Add Text Note", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = enteredText,
                        onValueChange = { enteredText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { inner ->
                            if (enteredText.isEmpty()) {
                                Text("Type text here...", color = Color.Gray, fontSize = 14.sp)
                            }
                            inner()
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { state.showTextEntryDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val loc = state.textEntryPendingLocation ?: Offset(50f, 100f)
                                state.showTextEntryDialog = false
                                state.addAnnotation(
                                    PdfTextAnnotation(
                                        id = "text_${state.currentPageIndex}_${loc.x}",
                                        pageIndex = state.currentPageIndex,
                                        x = loc.x,
                                        y = loc.y,
                                        text = enteredText,
                                        fontSize = state.textFontSize,
                                        textColorArgb = state.textColor
                                    )
                                )
                            },
                            enabled = enteredText.isNotBlank()
                        ) {
                            Text("Add")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Page View inside Editor with gesture drawing, shapes, and annotation overlays.
 */
@Composable
private fun PdfEditorPageView(
    page: PdfPageInfo,
    state: PdfEditorState
) {
    LaunchedEffect(page.pageIndex) {
        state.renderPageBitmap(page.pageIndex, scale = 1.5f)
    }

    val bitmap = state.pageBitmaps[page.pageIndex]
    val annotations = state.annotationsByPage[page.pageIndex].orEmpty()

    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Shape drawing in progress
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapeEnd by remember { mutableStateOf<Offset?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .aspectRatio(page.aspectRatio.coerceIn(0.5f, 1.8f))
            .graphicsLayer {
                scaleX = zoomScale
                scaleY = zoomScale
                translationX = panOffset.x
                translationY = panOffset.y
                rotationZ = page.rotation.toFloat()
            }
            .shadow(6.dp, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.activeTool) {
                    when (state.activeTool) {
                        PdfEditorTool.HAND -> {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(0.8f, 4.0f)
                                if (zoomScale > 1.0f) {
                                    panOffset += pan
                                } else {
                                    panOffset = Offset.Zero
                                }
                            }
                        }
                        PdfEditorTool.PEN, PdfEditorTool.HIGHLIGHTER -> {
                            val isHighlighter = state.activeTool == PdfEditorTool.HIGHLIGHTER
                            detectDragGestures(
                                onDragStart = { offset ->
                                    state.currentStrokePoints.add(PdfPoint.fromOffset(offset))
                                },
                                onDrag = { change, _ ->
                                    state.currentStrokePoints.add(PdfPoint.fromOffset(change.position))
                                },
                                onDragEnd = {
                                    state.commitCurrentStroke(isHighlighter)
                                },
                                onDragCancel = {
                                    state.currentStrokePoints.clear()
                                }
                            )
                        }
                        PdfEditorTool.ERASER -> {
                            detectDragGestures { change, _ ->
                                state.eraseNear(change.position)
                            }
                        }
                        PdfEditorTool.TEXT -> {
                            detectTapGestures { offset ->
                                state.textEntryPendingLocation = offset
                                state.showTextEntryDialog = true
                            }
                        }
                        PdfEditorTool.SHAPE -> {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    shapeStart = offset
                                    shapeEnd = offset
                                },
                                onDrag = { change, _ ->
                                    shapeEnd = change.position
                                },
                                onDragEnd = {
                                    val start = shapeStart
                                    val end = shapeEnd
                                    if (start != null && end != null) {
                                        state.addAnnotation(
                                            PdfShapeAnnotation(
                                                id = "shape_${page.pageIndex}",
                                                pageIndex = page.pageIndex,
                                                shapeType = state.selectedShapeType,
                                                startX = start.x,
                                                startY = start.y,
                                                endX = end.x,
                                                endY = end.y,
                                                strokeWidth = state.penStrokeWidth,
                                                strokeColorArgb = state.penColor
                                            )
                                        )
                                    }
                                    shapeStart = null
                                    shapeEnd = null
                                },
                                onDragCancel = {
                                    shapeStart = null
                                    shapeEnd = null
                                }
                            )
                        }
                        else -> {}
                    }
                }
        ) {
            // 1. Rendered PDF page background
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "PDF Page ${page.pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            }

            // 2. Overlay Canvas for Annotations & Realtime Strokes
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Committed annotations
                for (annot in annotations) {
                    when (annot) {
                        is PdfFreehandStroke -> {
                            if (annot.points.size >= 2) {
                                val path = Path().apply {
                                    moveTo(annot.points[0].x, annot.points[0].y)
                                    for (i in 1 until annot.points.size) {
                                        lineTo(annot.points[i].x, annot.points[i].y)
                                    }
                                }
                                val color = Color(annot.colorArgb).copy(alpha = annot.alpha)
                                drawPath(
                                    path = path,
                                    color = color,
                                    style = Stroke(
                                        width = annot.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                        is PdfHighlightAnnotation -> {
                            for (rect in annot.rects) {
                                drawRect(
                                    color = Color(annot.colorArgb),
                                    topLeft = Offset(rect.left, rect.top),
                                    size = Size(rect.width, rect.height)
                                )
                            }
                        }
                        is PdfShapeAnnotation -> {
                            val color = Color(annot.strokeColorArgb)
                            when (annot.shapeType) {
                                PdfShapeType.RECTANGLE -> {
                                    val left = minOf(annot.startX, annot.endX)
                                    val top = minOf(annot.startY, annot.endY)
                                    val w = kotlin.math.abs(annot.endX - annot.startX)
                                    val h = kotlin.math.abs(annot.endY - annot.startY)
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(left, top),
                                        size = Size(w, h),
                                        style = Stroke(width = annot.strokeWidth)
                                    )
                                }
                                PdfShapeType.CIRCLE -> {
                                    val center = Offset(
                                        (annot.startX + annot.endX) / 2f,
                                        (annot.startY + annot.endY) / 2f
                                    )
                                    val radius = kotlin.math.hypot(
                                        annot.endX - annot.startX,
                                        annot.endY - annot.startY
                                    ) / 2f
                                    drawCircle(
                                        color = color,
                                        radius = radius,
                                        center = center,
                                        style = Stroke(width = annot.strokeWidth)
                                    )
                                }
                                PdfShapeType.LINE, PdfShapeType.ARROW -> {
                                    drawLine(
                                        color = color,
                                        start = Offset(annot.startX, annot.startY),
                                        end = Offset(annot.endX, annot.endY),
                                        strokeWidth = annot.strokeWidth,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                        is PdfSignatureAnnotation -> {
                            for (stroke in annot.strokes) {
                                if (stroke.points.size >= 2) {
                                    val path = Path().apply {
                                        moveTo(annot.x + stroke.points[0].x, annot.y + stroke.points[0].y)
                                        for (i in 1 until stroke.points.size) {
                                            lineTo(annot.x + stroke.points[i].x, annot.y + stroke.points[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(stroke.colorArgb),
                                        style = Stroke(width = stroke.strokeWidth, cap = StrokeCap.Round)
                                    )
                                }
                            }
                        }
                        else -> {}
                    }
                }

                // Active live stroke being drawn right now
                if (state.currentStrokePoints.size >= 2) {
                    val activePath = Path().apply {
                        moveTo(state.currentStrokePoints[0].x, state.currentStrokePoints[0].y)
                        for (i in 1 until state.currentStrokePoints.size) {
                            lineTo(state.currentStrokePoints[i].x, state.currentStrokePoints[i].y)
                        }
                    }
                    val isHighlighter = state.activeTool == PdfEditorTool.HIGHLIGHTER
                    val strokeColor = if (isHighlighter) {
                        Color(state.highlighterColor).copy(alpha = 0.4f)
                    } else {
                        Color(state.penColor)
                    }
                    val strokeW = if (isHighlighter) state.highlighterStrokeWidth else state.penStrokeWidth
                    drawPath(
                        path = activePath,
                        color = strokeColor,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // Active shape preview being dragged
                val start = shapeStart
                val end = shapeEnd
                if (start != null && end != null) {
                    val left = minOf(start.x, end.x)
                    val top = minOf(start.y, end.y)
                    val w = kotlin.math.abs(end.x - start.x)
                    val h = kotlin.math.abs(end.y - start.y)
                    drawRect(
                        color = Color(state.penColor),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        style = Stroke(width = state.penStrokeWidth)
                    )
                }
            }

            // 3. Stamp & Text overlays as Compose widgets on top of Canvas
            for (annot in annotations) {
                when (annot) {
                    is PdfStampAnnotation -> {
                        val stampColor = Color(annot.colorArgb)
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = annot.x
                                    translationY = annot.y
                                    rotationZ = annot.rotationDegrees
                                }
                                .border(2.5.dp, stampColor, RoundedCornerShape(4.dp))
                                .background(stampColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = annot.displayLabel,
                                color = stampColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }
                    }
                    is PdfTextAnnotation -> {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = annot.x
                                    translationY = annot.y
                                }
                                .background(
                                    Color(annot.backgroundColorArgb).copy(alpha = 0.85f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(4.dp)
                        ) {
                            Text(
                                text = annot.text,
                                color = Color(annot.textColorArgb),
                                fontSize = annot.fontSize.sp,
                                fontWeight = if (annot.isBold) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
