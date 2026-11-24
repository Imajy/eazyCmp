package com.aj.shared.pdf.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Metadata and summary information for a PDF document.
 */
@Serializable
data class PdfDocumentInfo(
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val keywords: String? = null,
    val creator: String? = null,
    val producer: String? = null,
    val creationDate: String? = null,
    val modificationDate: String? = null,
    val pageCount: Int = 0,
    val version: String = "1.7",
    val isEncrypted: Boolean = false,
    val fileSize: Long = 0L
)

/**
 * Information about a single page in a PDF.
 */
@Serializable
data class PdfPageInfo(
    val pageIndex: Int,
    val width: Float,
    val height: Float,
    val rotation: Int = 0,
    val isDeleted: Boolean = false
) {
    val aspectRatio: Float
        get() = if (height > 0f) width / height else 0.7727f

    val isLandscape: Boolean
        get() = (rotation % 180 == 0 && width > height) || (rotation % 180 != 0 && height > width)
}

/**
 * 2D point in normalized or point coordinate space.
 */
@Serializable
data class PdfPoint(
    val x: Float,
    val y: Float
) {
    fun toOffset(): Offset = Offset(x, y)

    companion object {
        fun fromOffset(offset: Offset): PdfPoint = PdfPoint(offset.x, offset.y)
    }
}

/**
 * Rectangle coordinates on a PDF page.
 */
@Serializable
data class PdfRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    fun contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom
}

/**
 * Sealed interface for all annotations added to a PDF page.
 */
@Serializable
sealed interface PdfAnnotation {
    val id: String
    val pageIndex: Int
    val createdAt: Long

    fun withPage(newPageIndex: Int): PdfAnnotation
}

/**
 * Freehand drawing or handwriting stroke on a page.
 */
@Serializable
data class PdfFreehandStroke(
    override val id: String,
    override val pageIndex: Int,
    val points: List<PdfPoint>,
    val strokeWidth: Float = 3f,
    val colorArgb: Long = 0xFF000000,
    val isHighlighter: Boolean = false,
    val alpha: Float = if (isHighlighter) 0.35f else 1.0f,
    override val createdAt: Long = 0L
) : PdfAnnotation {
    override fun withPage(newPageIndex: Int): PdfFreehandStroke = copy(pageIndex = newPageIndex)
}

/**
 * Highlight region on a page.
 */
@Serializable
data class PdfHighlightAnnotation(
    override val id: String,
    override val pageIndex: Int,
    val rects: List<PdfRect>,
    val colorArgb: Long = 0x55FFEB3B, // translucent yellow
    val note: String? = null,
    override val createdAt: Long = 0L
) : PdfAnnotation {
    override fun withPage(newPageIndex: Int): PdfHighlightAnnotation = copy(pageIndex = newPageIndex)
}

/**
 * Underline annotation on text.
 */
@Serializable
data class PdfUnderlineAnnotation(
    override val id: String,
    override val pageIndex: Int,
    val rects: List<PdfRect>,
    val colorArgb: Long = 0xFF2196F3, // blue
    override val createdAt: Long = 0L
) : PdfAnnotation {
    override fun withPage(newPageIndex: Int): PdfUnderlineAnnotation = copy(pageIndex = newPageIndex)
}

/**
 * Strikethrough annotation on text.
 */
@Serializable
data class PdfStrikeAnnotation(
    override val id: String,
    override val pageIndex: Int,
    val rects: List<PdfRect>,
    val colorArgb: Long = 0xFFE53935, // red
    override val createdAt: Long = 0L
) : PdfAnnotation {
    override fun withPage(newPageIndex: Int): PdfStrikeAnnotation = copy(pageIndex = newPageIndex)
}

/**
 * Custom text note or overlay box on a page.
 */
@Serializable
data class PdfTextAnnotation(
    override val id: String,
    override val pageIndex: Int,
    val x: Float,
    val y: Float,
    val text: String,
    val fontSize: Float = 14f,
    val textColorArgb: Long = 0xFF000000,
    val backgroundColorArgb: Long = 0x00000000,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    override val createdAt: Long = 0L
) : PdfAnnotation {
    override fun withPage(newPageIndex: Int): PdfTextAnnotation = copy(pageIndex = newPageIndex)
}

/**
 * Preset stamps for document signing / approvals.
 */
enum class PdfStampType(val label: String, val defaultColor: Long) {
    APPROVED("APPROVED", 0xFF2E7D32),
    REJECTED("REJECTED", 0xFFC62828),
    CONFIDENTIAL("CONFIDENTIAL", 0xFFD84315),
    DRAFT("DRAFT", 0xFF455A64),
    FINAL("FINAL", 0xFF1565C0),
    PAID("PAID", 0xFF00897B),
    VOID("VOID", 0xFF8E24AA),
    CUSTOM("CUSTOM", 0xFF0288D1)
}

/**
 * Stamp placed on a page.
 */
@Serializable
data class PdfStampAnnotation(
    override val id: String,
    override val pageIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float = 160f,
    val height: Float = 60f,
    val stampType: PdfStampType = PdfStampType.APPROVED,
    val customText: String? = null,
    val colorArgb: Long = stampType.defaultColor,
    val rotationDegrees: Float = -12f,
    override val createdAt: Long = 0L
) : PdfAnnotation {
    val displayLabel: String get() = customText?.takeIf { it.isNotBlank() } ?: stampType.label

    override fun withPage(newPageIndex: Int): PdfStampAnnotation = copy(pageIndex = newPageIndex)
}

/**
 * Geometric shape types for drawing diagrams or callouts.
 */
enum class PdfShapeType {
    RECTANGLE,
    CIRCLE,
    LINE,
    ARROW
}

/**
 * Shape annotation (rectangle, circle, line, arrow).
 */
@Serializable
data class PdfShapeAnnotation(
    override val id: String,
    override val pageIndex: Int,
    val shapeType: PdfShapeType,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val strokeWidth: Float = 2f,
    val strokeColorArgb: Long = 0xFF000000,
    val fillColorArgb: Long = 0x00000000,
    override val createdAt: Long = 0L
) : PdfAnnotation {
    override fun withPage(newPageIndex: Int): PdfShapeAnnotation = copy(pageIndex = newPageIndex)
}

/**
 * Digital signature placed on a page.
 */
@Serializable
data class PdfSignatureAnnotation(
    override val id: String,
    override val pageIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float = 180f,
    val height: Float = 70f,
    val strokes: List<PdfFreehandStroke> = emptyList(),
    val signerName: String? = null,
    val dateString: String? = null,
    override val createdAt: Long = 0L
) : PdfAnnotation {
    override fun withPage(newPageIndex: Int): PdfSignatureAnnotation = copy(pageIndex = newPageIndex)
}

/**
 * Search hit within a PDF document.
 */
@Serializable
data class PdfSearchMatch(
    val matchId: String,
    val pageIndex: Int,
    val textSnippet: String,
    val matchIndexInPage: Int,
    val rect: PdfRect? = null
)

/**
 * Page-level document manipulation operations.
 */
sealed interface PdfPageOperation {
    data class Rotate(val pageIndex: Int, val degreesDelta: Int) : PdfPageOperation
    data class Delete(val pageIndex: Int) : PdfPageOperation
    data class Reorder(val fromIndex: Int, val toIndex: Int) : PdfPageOperation
    data class Duplicate(val pageIndex: Int) : PdfPageOperation
    data class AddBlank(val atIndex: Int, val width: Float = 612f, val height: Float = 792f) : PdfPageOperation
}
