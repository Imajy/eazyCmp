package com.aj.shared.pdf.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import com.aj.shared.pdf.model.PdfAnnotation
import com.aj.shared.pdf.model.PdfDocumentInfo
import com.aj.shared.pdf.model.PdfEditAction
import com.aj.shared.pdf.model.PdfFreehandStroke
import com.aj.shared.pdf.model.PdfPageInfo
import com.aj.shared.pdf.model.PdfPageOperation
import com.aj.shared.pdf.model.PdfPoint
import com.aj.shared.pdf.model.PdfRect
import com.aj.shared.pdf.model.PdfShapeAnnotation
import com.aj.shared.pdf.model.PdfShapeType
import com.aj.shared.pdf.model.PdfSignatureAnnotation
import com.aj.shared.pdf.model.PdfStampAnnotation
import com.aj.shared.pdf.model.PdfStampType
import com.aj.shared.pdf.model.PdfTextAnnotation
import com.aj.shared.pdf.model.PdfUndoRedoManager
import com.aj.shared.pdf.parser.PdfReader
import com.aj.shared.pdf.parser.PdfWriter
import com.aj.shared.pdf.render.PdfPageRenderer
import com.aj.shared.pdf.render.PdfRenderDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editor tools available in the toolbar.
 */
enum class PdfEditorTool(val label: String, val icon: String) {
    HAND("Hand", "🖐️"),
    PEN("Pen", "✏️"),
    HIGHLIGHTER("Highlighter", "🖍️"),
    ERASER("Eraser", "🧹"),
    TEXT("Text", "🔤"),
    SIGNATURE("Signature", "✒️"),
    STAMP("Stamp", "🏷️"),
    SHAPE("Shape", "📐")
}

/**
 * State holder for editing a PDF document.
 */
class PdfEditorState(
    private val coroutineScope: CoroutineScope
) {
    var originalBytes: ByteArray? = null
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isExporting by mutableStateOf(false)

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var documentInfo by mutableStateOf<PdfDocumentInfo?>(null)
        private set

    val pages = mutableStateListOf<PdfPageInfo>()
    val pageBitmaps = mutableStateMapOf<Int, ImageBitmap>()

    // Current page
    var currentPageIndex by mutableIntStateOf(0)

    // Active tool
    var activeTool by mutableStateOf(PdfEditorTool.HAND)

    // Tool configurations
    var penColor by mutableStateOf(0xFF000000L)
    var penStrokeWidth by mutableFloatStateOf(3f)
    var highlighterColor by mutableStateOf(0x66FFEB3BL)
    var highlighterStrokeWidth by mutableFloatStateOf(16f)
    var textFontSize by mutableFloatStateOf(16f)
    var textColor by mutableStateOf(0xFF000000L)
    var selectedShapeType by mutableStateOf(PdfShapeType.RECTANGLE)

    // Current drawing stroke
    val currentStrokePoints = mutableStateListOf<PdfPoint>()

    // Annotations map: pageIndex -> list of annotations
    val annotationsByPage = mutableStateMapOf<Int, MutableList<PdfAnnotation>>()

    // Undo / Redo
    val undoRedoManager = PdfUndoRedoManager()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    // Dialog flags
    var showSignaturePad by mutableStateOf(false)
    var showStampPicker by mutableStateOf(false)
    var showPageManager by mutableStateOf(false)
    var showTextEntryDialog by mutableStateOf(false)
    var textEntryPendingLocation by mutableStateOf<Offset?>(null)

    private var activeDocument: PdfRenderDocument? = null
    private var actionCounter = 0

    val currentPage: PdfPageInfo?
        get() = pages.getOrNull(currentPageIndex)

    val nonDeletedPages: List<PdfPageInfo>
        get() = pages.filterNot { it.isDeleted }

    fun load(bytes: ByteArray, password: String? = null) {
        originalBytes = bytes
        isLoading = true
        errorMessage = null
        pages.clear()
        pageBitmaps.clear()
        annotationsByPage.clear()
        undoRedoManager.clear()
        updateUndoRedoStatus()

        coroutineScope.launch {
            try {
                val reader = PdfReader(bytes)
                val info = withContext(Dispatchers.Default) { reader.readDocumentInfo() }
                documentInfo = info
                val pageInfos = withContext(Dispatchers.Default) { reader.readPageInfos() }
                pages.addAll(pageInfos)

                val renderer = PdfPageRenderer()
                val doc = renderer.open(bytes, password)
                activeDocument?.close()
                activeDocument = doc

                renderPageBitmap(0)
                isLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.message ?: "Failed to open PDF document for editing"
                isLoading = false
            }
        }
    }

    fun renderPageBitmap(pageIndex: Int, scale: Float = 1.5f) {
        if (pageBitmaps.containsKey(pageIndex)) return
        val doc = activeDocument ?: return
        if (pageIndex !in 0 until doc.pageCount) return

        coroutineScope.launch {
            try {
                val bitmap = doc.renderPage(pageIndex, scale)
                if (bitmap != null) {
                    pageBitmaps[pageIndex] = bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addAnnotation(annotation: PdfAnnotation) {
        val list = annotationsByPage.getOrPut(annotation.pageIndex) { mutableListOf() }
        list.add(annotation)
        undoRedoManager.pushAction(PdfEditAction.AddAnnotation(annotation))
        updateUndoRedoStatus()
    }

    fun removeAnnotation(annotation: PdfAnnotation) {
        val list = annotationsByPage[annotation.pageIndex] ?: return
        if (list.remove(annotation)) {
            undoRedoManager.pushAction(PdfEditAction.RemoveAnnotation(annotation))
            updateUndoRedoStatus()
        }
    }

    fun commitCurrentStroke(isHighlighter: Boolean) {
        if (currentStrokePoints.size < 2) {
            currentStrokePoints.clear()
            return
        }
        val stroke = PdfFreehandStroke(
            id = "stroke_${actionCounter++}",
            pageIndex = currentPageIndex,
            points = currentStrokePoints.toList(),
            strokeWidth = if (isHighlighter) highlighterStrokeWidth else penStrokeWidth,
            colorArgb = if (isHighlighter) highlighterColor else penColor,
            isHighlighter = isHighlighter,
            createdAt = 0L
        )
        currentStrokePoints.clear()
        addAnnotation(stroke)
    }

    fun eraseNear(point: Offset, thresholdDp: Float = 20f) {
        val list = annotationsByPage[currentPageIndex] ?: return
        val toRemove = list.firstOrNull { annot ->
            when (annot) {
                is PdfFreehandStroke -> annot.points.any { p ->
                    val dx = p.x - point.x
                    val dy = p.y - point.y
                    (dx * dx + dy * dy) < thresholdDp * thresholdDp
                }
                is PdfTextAnnotation -> {
                    point.x in annot.x..(annot.x + 150f) && point.y in annot.y..(annot.y + 30f)
                }
                is PdfStampAnnotation -> {
                    point.x in annot.x..(annot.x + annot.width) && point.y in annot.y..(annot.y + annot.height)
                }
                is PdfSignatureAnnotation -> {
                    point.x in annot.x..(annot.x + annot.width) && point.y in annot.y..(annot.y + annot.height)
                }
                is PdfShapeAnnotation -> {
                    val minX = minOf(annot.startX, annot.endX)
                    val maxX = maxOf(annot.startX, annot.endX)
                    val minY = minOf(annot.startY, annot.endY)
                    val maxY = maxOf(annot.startY, annot.endY)
                    point.x in minX..maxX && point.y in minY..maxY
                }
                else -> false
            }
        }
        if (toRemove != null) {
            removeAnnotation(toRemove)
        }
    }

    fun undo() {
        val action = undoRedoManager.popUndo() ?: return
        when (action) {
            is PdfEditAction.AddAnnotation -> {
                annotationsByPage[action.annotation.pageIndex]?.remove(action.annotation)
            }
            is PdfEditAction.RemoveAnnotation -> {
                annotationsByPage.getOrPut(action.annotation.pageIndex) { mutableListOf() }.add(action.annotation)
            }
            is PdfEditAction.ModifyAnnotation -> {
                val list = annotationsByPage[action.old.pageIndex]
                val idx = list?.indexOf(action.new) ?: -1
                if (idx != -1) {
                    list?.set(idx, action.old)
                }
            }
            is PdfEditAction.PageOperationAction -> {
                applyPageOperationInternal(action.reverse)
            }
        }
        updateUndoRedoStatus()
    }

    fun redo() {
        val action = undoRedoManager.popRedo() ?: return
        when (action) {
            is PdfEditAction.AddAnnotation -> {
                annotationsByPage.getOrPut(action.annotation.pageIndex) { mutableListOf() }.add(action.annotation)
            }
            is PdfEditAction.RemoveAnnotation -> {
                annotationsByPage[action.annotation.pageIndex]?.remove(action.annotation)
            }
            is PdfEditAction.ModifyAnnotation -> {
                val list = annotationsByPage[action.old.pageIndex]
                val idx = list?.indexOf(action.old) ?: -1
                if (idx != -1) {
                    list?.set(idx, action.new)
                }
            }
            is PdfEditAction.PageOperationAction -> {
                applyPageOperationInternal(action.forward)
            }
        }
        updateUndoRedoStatus()
    }

    // Page manipulation operations
    fun rotateCurrentPage(degrees: Int = 90) {
        val page = currentPage ?: return
        val forward = PdfPageOperation.Rotate(currentPageIndex, degrees)
        val reverse = PdfPageOperation.Rotate(currentPageIndex, -degrees)
        applyPageOperationInternal(forward)
        undoRedoManager.pushAction(PdfEditAction.PageOperationAction(forward, reverse))
        updateUndoRedoStatus()
    }

    fun deleteCurrentPage() {
        if (nonDeletedPages.size <= 1) return
        val forward = PdfPageOperation.Delete(currentPageIndex)
        val reverse = PdfPageOperation.AddBlank(currentPageIndex)
        applyPageOperationInternal(forward)
        undoRedoManager.pushAction(PdfEditAction.PageOperationAction(forward, reverse))
        updateUndoRedoStatus()
    }

    fun addBlankPage() {
        val newIdx = pages.size
        val forward = PdfPageOperation.AddBlank(newIdx)
        val reverse = PdfPageOperation.Delete(newIdx)
        applyPageOperationInternal(forward)
        undoRedoManager.pushAction(PdfEditAction.PageOperationAction(forward, reverse))
        updateUndoRedoStatus()
    }

    private fun applyPageOperationInternal(op: PdfPageOperation) {
        when (op) {
            is PdfPageOperation.Rotate -> {
                val p = pages.getOrNull(op.pageIndex)
                if (p != null) {
                    val newRot = (p.rotation + op.degreesDelta + 360) % 360
                    pages[op.pageIndex] = p.copy(rotation = newRot)
                }
            }
            is PdfPageOperation.Delete -> {
                val p = pages.getOrNull(op.pageIndex)
                if (p != null) {
                    pages[op.pageIndex] = p.copy(isDeleted = true)
                    if (currentPageIndex >= nonDeletedPages.size) {
                        currentPageIndex = (nonDeletedPages.size - 1).coerceAtLeast(0)
                    }
                }
            }
            is PdfPageOperation.AddBlank -> {
                pages.add(PdfPageInfo(op.atIndex, op.width, op.height, 0))
                currentPageIndex = op.atIndex
            }
            is PdfPageOperation.Duplicate -> {
                val p = pages.getOrNull(op.pageIndex) ?: return
                pages.add(p.copy(pageIndex = pages.size))
            }
            is PdfPageOperation.Reorder -> {
                if (op.fromIndex in pages.indices && op.toIndex in pages.indices) {
                    val item = pages.removeAt(op.fromIndex)
                    pages.add(op.toIndex, item)
                }
            }
        }
    }

    fun exportPdf(): ByteArray {
        return PdfWriter.exportPdf(
            originalBytes = originalBytes,
            pages = pages.toList(),
            annotationsByPage = annotationsByPage.mapValues { it.value.toList() }
        )
    }

    private fun updateUndoRedoStatus() {
        canUndo = undoRedoManager.canUndo
        canRedo = undoRedoManager.canRedo
    }

    fun release() {
        activeDocument?.close()
        activeDocument = null
        pageBitmaps.clear()
    }
}

@Composable
fun rememberPdfEditorState(): PdfEditorState {
    val scope = rememberCoroutineScope()
    return remember { PdfEditorState(scope) }
}
