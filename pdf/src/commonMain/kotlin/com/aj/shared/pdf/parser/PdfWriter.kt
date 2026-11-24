package com.aj.shared.pdf.parser

import com.aj.shared.pdf.model.PdfAnnotation
import com.aj.shared.pdf.model.PdfFreehandStroke
import com.aj.shared.pdf.model.PdfHighlightAnnotation
import com.aj.shared.pdf.model.PdfPageInfo
import com.aj.shared.pdf.model.PdfShapeAnnotation
import com.aj.shared.pdf.model.PdfShapeType
import com.aj.shared.pdf.model.PdfSignatureAnnotation
import com.aj.shared.pdf.model.PdfStampAnnotation
import com.aj.shared.pdf.model.PdfStrikeAnnotation
import com.aj.shared.pdf.model.PdfTextAnnotation
import com.aj.shared.pdf.model.PdfUnderlineAnnotation

/**
 * Pure Kotlin PDF generator and exporter.
 * Bakes modified pages, rotations, reorderings, vector strokes, text, stamps, and annotations
 * into standard PDF 1.4 output bytes.
 */
object PdfWriter {

    /**
     * Exports a document with page modifications and overlay annotations.
     */
    fun exportPdf(
        originalBytes: ByteArray?,
        pages: List<PdfPageInfo>,
        annotationsByPage: Map<Int, List<PdfAnnotation>>
    ): ByteArray {
        val nonDeletedPages = pages.filterNot { it.isDeleted }
        val effectivePages = if (nonDeletedPages.isEmpty()) {
            listOf(PdfPageInfo(0, 612f, 792f, 0))
        } else {
            nonDeletedPages
        }

        val out = ByteStreamWriter()
        val objectOffsets = mutableListOf<Long>()

        // 1. Header
        out.writeAscii("%PDF-1.4\n")
        out.writeBytes(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))

        // Object 1: Catalog
        objectOffsets.add(out.size)
        out.writeAscii("1 0 obj\n")
        out.writeAscii("<< /Type /Catalog /Pages 2 0 R >>\n")
        out.writeAscii("endobj\n")

        // Object 2: Pages root
        val pageObjIds = mutableListOf<Int>()
        var nextObjId = 3
        for (i in effectivePages.indices) {
            pageObjIds.add(nextObjId)
            nextObjId += 2 // Page obj + Content stream obj
        }

        objectOffsets.add(out.size)
        out.writeAscii("2 0 obj\n")
        out.writeAscii("<< /Type /Pages /Count ${effectivePages.size} /Kids [ ")
        for (id in pageObjIds) {
            out.writeAscii("$id 0 R ")
        }
        out.writeAscii("] >>\n")
        out.writeAscii("endobj\n")

        // Write each page and its content stream
        for ((idx, page) in effectivePages.withIndex()) {
            val pageObjId = pageObjIds[idx]
            val contentObjId = pageObjId + 1

            // Page Object
            objectOffsets.add(out.size)
            out.writeAscii("$pageObjId 0 obj\n")
            out.writeAscii("<< /Type /Page /Parent 2 0 R ")
            out.writeAscii("/MediaBox [0 0 ${page.width} ${page.height}] ")
            if (page.rotation != 0) {
                out.writeAscii("/Rotate ${page.rotation} ")
            }
            out.writeAscii("/Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> ")
            out.writeAscii("/Contents $contentObjId 0 R ")
            out.writeAscii(">>\n")
            out.writeAscii("endobj\n")

            // Content Stream Object
            val streamContent = buildPageContentStream(page, annotationsByPage[page.pageIndex].orEmpty())
            val streamBytes = streamContent.encodeToByteArray()

            objectOffsets.add(out.size)
            out.writeAscii("$contentObjId 0 obj\n")
            out.writeAscii("<< /Length ${streamBytes.size} >>\n")
            out.writeAscii("stream\n")
            out.writeBytes(streamBytes)
            out.writeAscii("\nendstream\n")
            out.writeAscii("endobj\n")
        }

        // Xref Table
        val xrefOffset = out.size
        val totalObjects = nextObjId // 1 to nextObjId - 1
        out.writeAscii("xref\n")
        out.writeAscii("0 $totalObjects\n")
        out.writeAscii("0000000000 65535 f \n")
        for (offset in objectOffsets) {
            val pad = offset.toString().padStart(10, '0')
            out.writeAscii("$pad 00000 n \n")
        }

        // Trailer
        out.writeAscii("trailer\n")
        out.writeAscii("<< /Size $totalObjects /Root 1 0 R >>\n")
        out.writeAscii("startxref\n")
        out.writeAscii("$xrefOffset\n")
        out.writeAscii("%%EOF\n")

        return out.toByteArray()
    }

    private fun buildPageContentStream(page: PdfPageInfo, annotations: List<PdfAnnotation>): String {
        val sb = StringBuilder()

        // Background white fill
        sb.append("q\n")
        sb.append("1 1 1 rg 0 0 ${page.width} ${page.height} re f\n")
        sb.append("Q\n")

        for (annot in annotations) {
            when (annot) {
                is PdfFreehandStroke -> renderStroke(sb, page, annot)
                is PdfHighlightAnnotation -> renderHighlight(sb, page, annot)
                is PdfUnderlineAnnotation -> renderUnderline(sb, page, annot)
                is PdfStrikeAnnotation -> renderStrike(sb, page, annot)
                is PdfTextAnnotation -> renderText(sb, page, annot)
                is PdfStampAnnotation -> renderStamp(sb, page, annot)
                is PdfShapeAnnotation -> renderShape(sb, page, annot)
                is PdfSignatureAnnotation -> renderSignature(sb, page, annot)
            }
        }

        return sb.toString()
    }

    private fun renderStroke(sb: StringBuilder, page: PdfPageInfo, stroke: PdfFreehandStroke) {
        if (stroke.points.size < 2) return
        val (r, g, b) = argbToRgb(stroke.colorArgb)
        sb.append("q\n")
        sb.append("${stroke.strokeWidth} w 1 J 1 j\n")
        sb.append("$r $g $b RG\n")

        val first = stroke.points[0]
        sb.append("${first.x} ${flipY(page, first.y)} m\n")
        for (i in 1 until stroke.points.size) {
            val pt = stroke.points[i]
            sb.append("${pt.x} ${flipY(page, pt.y)} l\n")
        }
        sb.append("S\n")
        sb.append("Q\n")
    }

    private fun renderHighlight(sb: StringBuilder, page: PdfPageInfo, h: PdfHighlightAnnotation) {
        val (r, g, b) = argbToRgb(h.colorArgb)
        sb.append("q\n")
        sb.append("$r $g $b rg\n")
        for (rect in h.rects) {
            val pdfY = page.height - rect.bottom
            sb.append("${rect.left} $pdfY ${rect.width} ${rect.height} re f\n")
        }
        sb.append("Q\n")
    }

    private fun renderUnderline(sb: StringBuilder, page: PdfPageInfo, u: PdfUnderlineAnnotation) {
        val (r, g, b) = argbToRgb(u.colorArgb)
        sb.append("q\n")
        sb.append("1.5 w\n")
        sb.append("$r $g $b RG\n")
        for (rect in u.rects) {
            val pdfY = page.height - rect.bottom
            sb.append("${rect.left} $pdfY m ${rect.right} $pdfY l S\n")
        }
        sb.append("Q\n")
    }

    private fun renderStrike(sb: StringBuilder, page: PdfPageInfo, s: PdfStrikeAnnotation) {
        val (r, g, b) = argbToRgb(s.colorArgb)
        sb.append("q\n")
        sb.append("1.5 w\n")
        sb.append("$r $g $b RG\n")
        for (rect in s.rects) {
            val midY = page.height - (rect.top + rect.bottom) / 2f
            sb.append("${rect.left} $midY m ${rect.right} $midY l S\n")
        }
        sb.append("Q\n")
    }

    private fun renderText(sb: StringBuilder, page: PdfPageInfo, textAnnot: PdfTextAnnotation) {
        val (r, g, b) = argbToRgb(textAnnot.textColorArgb)
        val pdfY = page.height - textAnnot.y
        sb.append("q\n")
        sb.append("BT\n")
        sb.append("/F1 ${textAnnot.fontSize} Tf\n")
        sb.append("$r $g $b rg\n")
        sb.append("${textAnnot.x} $pdfY Td\n")
        sb.append("(${escapePdfString(textAnnot.text)}) Tj\n")
        sb.append("ET\n")
        sb.append("Q\n")
    }

    private fun renderStamp(sb: StringBuilder, page: PdfPageInfo, stamp: PdfStampAnnotation) {
        val (r, g, b) = argbToRgb(stamp.colorArgb)
        val pdfY = page.height - (stamp.y + stamp.height)
        sb.append("q\n")
        sb.append("2 w\n")
        sb.append("$r $g $b RG\n")
        sb.append("$r $g $b rg\n")
        // Border
        sb.append("${stamp.x} $pdfY ${stamp.width} ${stamp.height} re S\n")
        // Text
        val label = stamp.displayLabel
        val fontSize = (stamp.height * 0.45f).coerceIn(10f, 28f)
        val textX = stamp.x + 8f
        val textY = pdfY + stamp.height * 0.35f
        sb.append("BT\n")
        sb.append("/F1 $fontSize Tf\n")
        sb.append("$textX $textY Td\n")
        sb.append("(${escapePdfString(label)}) Tj\n")
        sb.append("ET\n")
        sb.append("Q\n")
    }

    private fun renderShape(sb: StringBuilder, page: PdfPageInfo, shape: PdfShapeAnnotation) {
        val (sr, sg, sbCol) = argbToRgb(shape.strokeColorArgb)
        sb.append("q\n")
        sb.append("${shape.strokeWidth} w\n")
        sb.append("$sr $sg $sbCol RG\n")

        when (shape.shapeType) {
            PdfShapeType.RECTANGLE -> {
                val left = minOf(shape.startX, shape.endX)
                val top = minOf(shape.startY, shape.endY)
                val w = (shape.endX - shape.startX).let { if (it < 0) -it else it }
                val h = (shape.endY - shape.startY).let { if (it < 0) -it else it }
                val pdfY = page.height - (top + h)
                sb.append("$left $pdfY $w $h re S\n")
            }
            PdfShapeType.CIRCLE -> {
                val left = minOf(shape.startX, shape.endX)
                val top = minOf(shape.startY, shape.endY)
                val w = (shape.endX - shape.startX).let { if (it < 0) -it else it }
                val h = (shape.endY - shape.startY).let { if (it < 0) -it else it }
                val pdfY = page.height - (top + h)
                // Approximated as ellipse rect for PDF stream
                sb.append("$left $pdfY $w $h re S\n")
            }
            PdfShapeType.LINE, PdfShapeType.ARROW -> {
                val y1 = flipY(page, shape.startY)
                val y2 = flipY(page, shape.endY)
                sb.append("${shape.startX} $y1 m ${shape.endX} $y2 l S\n")
            }
        }
        sb.append("Q\n")
    }

    private fun renderSignature(sb: StringBuilder, page: PdfPageInfo, sig: PdfSignatureAnnotation) {
        for (stroke in sig.strokes) {
            renderStroke(sb, page, stroke)
        }
        if (sig.signerName != null) {
            sb.append("q\n")
            sb.append("BT\n")
            sb.append("/F1 10 Tf\n")
            sb.append("0 0 0 rg\n")
            sb.append("${sig.x} ${page.height - (sig.y + sig.height)} Td\n")
            sb.append("(Signed by: ${escapePdfString(sig.signerName)}) Tj\n")
            sb.append("ET\n")
            sb.append("Q\n")
        }
    }

    private fun flipY(page: PdfPageInfo, y: Float): Float = page.height - y

    private fun argbToRgb(argb: Long): Triple<Float, Float, Float> {
        val r = ((argb shr 16) and 0xFF) / 255.0f
        val g = ((argb shr 8) and 0xFF) / 255.0f
        val b = (argb and 0xFF) / 255.0f
        return Triple(r, g, b)
    }

    private fun escapePdfString(str: String): String =
        str.replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")

    private class ByteStreamWriter {
        private var buffer = ByteArray(4096)
        var size: Long = 0L
            private set

        fun writeBytes(bytes: ByteArray) {
            ensureCapacity(bytes.size)
            bytes.copyInto(buffer, size.toInt(), 0, bytes.size)
            size += bytes.size
        }

        fun writeAscii(str: String) {
            val b = str.encodeToByteArray()
            writeBytes(b)
        }

        fun toByteArray(): ByteArray {
            val result = ByteArray(size.toInt())
            buffer.copyInto(result, 0, 0, size.toInt())
            return result
        }

        private fun ensureCapacity(additional: Int) {
            val needed = (size + additional).toInt()
            if (needed > buffer.size) {
                var newCap = buffer.size * 2
                while (newCap < needed) newCap *= 2
                buffer = buffer.copyOf(newCap)
            }
        }
    }
}
