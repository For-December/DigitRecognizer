package com.dsa.digitrecognizer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

data class PathWrapper(
    val path: Path,
    val color: Color,
    val strokeWidth: Float
)

@Composable
fun DrawingCanvas(
    paths: List<PathWrapper>,
    onPathUpdate: (List<PathWrapper>) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPath by remember { mutableStateOf<Path?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentPath = Path().apply {
                            moveTo(offset.x, offset.y)
                        }
                    },
                    onDrag = { change, _ ->
                        currentPath?.lineTo(change.position.x, change.position.y)
                    },
                    onDragEnd = {
                        currentPath?.let { path ->
                            onPathUpdate(
                                paths + PathWrapper(
                                    path = path,
                                    color = Color.Black,
                                    strokeWidth = 30f
                                )
                            )
                        }
                        currentPath = null
                    }
                )
            }
    ) {
        // Draw white background
        drawRect(color = Color.White)

        // Draw saved paths
        paths.forEach { pathWrapper ->
            drawPath(
                path = pathWrapper.path,
                color = pathWrapper.color,
                style = Stroke(width = pathWrapper.strokeWidth)
            )
        }

        // Draw current path
        currentPath?.let { path ->
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 30f)
            )
        }
    }
}

fun convertDrawingToBitmap(paths: List<PathWrapper>, width: Int = 280, height: Int = 280): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // White background
    canvas.drawColor(android.graphics.Color.WHITE)

    // Draw paths
    val paint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }

    paths.forEach { pathWrapper ->
        paint.color = pathWrapper.color.toArgb()
        paint.strokeWidth = pathWrapper.strokeWidth
        canvas.drawPath(pathWrapper.path.asAndroidPath(), paint)
    }

    return bitmap
}

