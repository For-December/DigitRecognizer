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

data class DrawingData(
    val paths: List<PathWrapper>,
    val canvasWidth: Float,
    val canvasHeight: Float
)

@Composable
fun DrawingCanvas(
    paths: List<PathWrapper>,
    onPathUpdate: (List<PathWrapper>) -> Unit,
    onCanvasSizeChanged: (Float, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var touchCount by remember { mutableIntStateOf(0) }

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
                        touchCount++
                    },
                    onDrag = { change, _ ->
                        currentPath?.lineTo(change.position.x, change.position.y)
                        touchCount++
                        change.consume()
                    },
                    onDragEnd = {
                        currentPath?.let { path ->
                            val newPaths = paths + PathWrapper(
                                path = path,
                                color = Color.Black,
                                strokeWidth = 30f
                            )
                            onPathUpdate(newPaths)
                        }
                        currentPath = null
                        touchCount++
                    }
                )
            }
    ) {
        // Report canvas size
        onCanvasSizeChanged(size.width, size.height)

        // This will trigger recomposition
        touchCount.let { }

        // Draw white background
        drawRect(color = Color.White)

        // Draw saved paths
        paths.forEach { pathWrapper ->
            drawPath(
                path = pathWrapper.path,
                color = pathWrapper.color,
                style = Stroke(width = pathWrapper.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // Draw current path being drawn
        currentPath?.let { path ->
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 30f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

fun convertDrawingToBitmap(
    paths: List<PathWrapper>,
    canvasWidth: Float,
    canvasHeight: Float,
    width: Int = 280,
    height: Int = 280
): Bitmap {
    android.util.Log.d("DrawingView", "convertDrawingToBitmap: paths.size = ${paths.size}, canvas=${canvasWidth}x${canvasHeight}, bitmap=${width}x${height}")

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // White background
    canvas.drawColor(android.graphics.Color.WHITE)

    // Calculate scale factors
    val scaleX = width.toFloat() / canvasWidth
    val scaleY = height.toFloat() / canvasHeight
    android.util.Log.d("DrawingView", "Scale factors: scaleX=$scaleX, scaleY=$scaleY")

    // Draw paths with scaling
    val paint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }

    paths.forEach { pathWrapper ->
        paint.color = pathWrapper.color.toArgb()
        paint.strokeWidth = pathWrapper.strokeWidth * scaleX // Scale stroke width too

        // Create a scaled path
        val scaledPath = android.graphics.Path(pathWrapper.path.asAndroidPath())
        val matrix = android.graphics.Matrix()
        matrix.setScale(scaleX, scaleY)
        scaledPath.transform(matrix)

        canvas.drawPath(scaledPath, paint)
    }

    // Apply binarization with threshold 220
    val binarizedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    // Count black and white pixels for debugging
    var blackPixels = 0
    var whitePixels = 0

    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = android.graphics.Color.red(pixel)
        val g = android.graphics.Color.green(pixel)
        val b = android.graphics.Color.blue(pixel)
        val gray = (r + g + b) / 3

        // Binarization: if gray value > 220, set to white (255), else black (0)
        val binaryValue = if (gray > 220) 255 else 0
        if (binaryValue == 0) blackPixels++ else whitePixels++
        pixels[i] = android.graphics.Color.rgb(binaryValue, binaryValue, binaryValue)
    }

    android.util.Log.d("DrawingView", "Binarization: black=$blackPixels, white=$whitePixels, ratio=${blackPixels.toFloat()/(blackPixels+whitePixels)}")

    binarizedBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return binarizedBitmap
}

