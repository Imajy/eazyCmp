package com.aj.shared.pdf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aj.shared.pdf.model.PdfFreehandStroke
import com.aj.shared.pdf.model.PdfPoint

/**
 * Dialog for capturing freehand digital signatures.
 */
@Composable
fun PdfSignaturePadDialog(
    onDismiss: () -> Unit,
    onSignatureCaptured: (List<PdfFreehandStroke>) -> Unit
) {
    val completedStrokes = remember { mutableStateListOf<List<PdfPoint>>() }
    val currentPoints = remember { mutableStateListOf<PdfPoint>() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sign Here",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Draw your digital signature with finger or mouse",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Drawing Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPoints.add(PdfPoint.fromOffset(offset))
                                },
                                onDrag = { change, _ ->
                                    currentPoints.add(PdfPoint.fromOffset(change.position))
                                },
                                onDragEnd = {
                                    if (currentPoints.size >= 2) {
                                        completedStrokes.add(currentPoints.toList())
                                    }
                                    currentPoints.clear()
                                },
                                onDragCancel = {
                                    currentPoints.clear()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (stroke in completedStrokes) {
                            if (stroke.size >= 2) {
                                val path = Path().apply {
                                    moveTo(stroke[0].x, stroke[0].y)
                                    for (i in 1 until stroke.size) {
                                        lineTo(stroke[i].x, stroke[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = Color.Black,
                                    style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                        if (currentPoints.size >= 2) {
                            val activePath = Path().apply {
                                moveTo(currentPoints[0].x, currentPoints[0].y)
                                for (i in 1 until currentPoints.size) {
                                    lineTo(currentPoints[i].x, currentPoints[i].y)
                                }
                            }
                            drawPath(
                                path = activePath,
                                color = Color.Black,
                                style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }

                    // Watermark hint if blank
                    if (completedStrokes.isEmpty() && currentPoints.isEmpty()) {
                        Text(
                            text = "✍️ Sign here...",
                            color = Color(0xFFCCCCCC),
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        completedStrokes.clear()
                        currentPoints.clear()
                    }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }

                    Row {
                        OutlinedButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val strokes = completedStrokes.mapIndexed { idx, pts ->
                                    PdfFreehandStroke(
                                        id = "sig_stroke_$idx",
                                        pageIndex = 0,
                                        points = pts,
                                        strokeWidth = 3.5f,
                                        colorArgb = 0xFF000000
                                    )
                                }
                                onSignatureCaptured(strokes)
                            },
                            enabled = completedStrokes.isNotEmpty()
                        ) {
                            Text("Adopt & Sign")
                        }
                    }
                }
            }
        }
    }
}
