package com.aj.shared.pdf.model

/**
 * Atomic edit action that can be undone and redone.
 */
sealed interface PdfEditAction {
    data class AddAnnotation(val annotation: PdfAnnotation) : PdfEditAction
    data class RemoveAnnotation(val annotation: PdfAnnotation) : PdfEditAction
    data class ModifyAnnotation(val old: PdfAnnotation, val new: PdfAnnotation) : PdfEditAction
    data class PageOperationAction(val forward: PdfPageOperation, val reverse: PdfPageOperation) : PdfEditAction
}

/**
 * Manages the undo/redo history for PDF editing.
 */
class PdfUndoRedoManager(
    private val maxHistory: Int = 100
) {
    private val undoStack = mutableListOf<PdfEditAction>()
    private val redoStack = mutableListOf<PdfEditAction>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoDepth: Int get() = undoStack.size
    val redoDepth: Int get() = redoStack.size

    fun pushAction(action: PdfEditAction) {
        undoStack.add(action)
        if (undoStack.size > maxHistory) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun popUndo(): PdfEditAction? {
        if (undoStack.isEmpty()) return null
        val action = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(action)
        return action
    }

    fun popRedo(): PdfEditAction? {
        if (redoStack.isEmpty()) return null
        val action = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(action)
        return action
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
