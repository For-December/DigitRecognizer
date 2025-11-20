package com.dsa.digitrecognizer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// Store each stroke as an immutable list of points instead of a mutable Path
data class PathWrapper(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@Composable
fun DrawingCanvas(
    paths: List<PathWrapper>,
    onPathsChanged: (List<PathWrapper>) -> Unit,
    modifier: Modifier = Modifier,
    onCanvasSizeChanged: (Float, Float) -> Unit = { _, _ -> }
) {
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var touchCount by remember { mutableIntStateOf(0) }
    var lastReportedSize by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    // Internal mutable list for reliable drawing
    val internalPaths = remember { mutableStateListOf<PathWrapper>() }

    // When parent `paths` changes (external clear/restore), sync internalPaths
    LaunchedEffect(paths) {
        if (paths != internalPaths.toList()) {
            android.util.Log.d("DrawingView", "Syncing internalPaths from parent: parent=${paths.size}, internal=${internalPaths.size}")
            internalPaths.clear()
            internalPaths.addAll(paths)
        }
    }

    // latest callbacks/values for pointerInput
    val latestOnPathsChanged = rememberUpdatedState(onPathsChanged)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentPoints = listOf(offset)
                        touchCount++
                    },
                    onDrag = { change, _ ->
                        currentPoints = currentPoints + change.position
                        touchCount++
                        change.consume()
                    },
                    onDragEnd = {
                        if (currentPoints.isNotEmpty()) {
                            val stroke = PathWrapper(points = currentPoints, color = Color.Black, strokeWidth = 30f)
                            internalPaths.add(stroke)
                            android.util.Log.d("DrawingView", "Added stroke, internalPaths.size=${internalPaths.size}")
                            // notify parent with authoritative list
                            latestOnPathsChanged.value(internalPaths.toList())
                        }
                        currentPoints = emptyList()
                        touchCount++
                    }
                )
            }
    ) {
        // Report canvas size only when it changes
        val currentSize = Pair(size.width, size.height)
        if (lastReportedSize != currentSize) {
            lastReportedSize = currentSize
            onCanvasSizeChanged(size.width, size.height)
        }

        // Trigger recomposition when touchCount changes
        touchCount.let { }

        // Draw white background
        drawRect(color = Color.White)

        // Build smoothed Path
        fun buildSmoothPathFromPoints(pts: List<Offset>): Path {
            val p = Path()
            if (pts.isEmpty()) return p
            if (pts.size == 1) {
                p.moveTo(pts[0].x, pts[0].y)
                p.lineTo(pts[0].x, pts[0].y)
                return p
            }
            p.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val prev = pts[i - 1]
                val curr = pts[i]
                val midX = (prev.x + curr.x) / 2f
                val midY = (prev.y + curr.y) / 2f
                p.quadraticTo(prev.x, prev.y, midX, midY)
            }
            val last = pts.last()
            p.lineTo(last.x, last.y)
            return p
        }

        // Draw saved paths from internalPaths
        internalPaths.forEach { pw ->
            val p = buildSmoothPathFromPoints(pw.points)
            drawPath(p, color = pw.color, style = Stroke(width = pw.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Draw current in-progress stroke
        if (currentPoints.isNotEmpty()) {
            val p = buildSmoothPathFromPoints(currentPoints)
            drawPath(p, color = Color.Black, style = Stroke(width = 30f, cap = StrokeCap.Round, join = StrokeJoin.Round))
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

    // Draw paths with scaling and smoothing using Android Path.quadTo equivalents
    val paint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }

    paths.forEach { pathWrapper ->
        paint.color = pathWrapper.color.toArgb()
        paint.strokeWidth = pathWrapper.strokeWidth * scaleX // Scale stroke width too

        // Build an android.graphics.Path from points with quadratic smoothing
        val androidPath = android.graphics.Path()
        val pts = pathWrapper.points
        if (pts.isNotEmpty()) {
            if (pts.size == 1) {
                androidPath.moveTo(pts[0].x * scaleX, pts[0].y * scaleY)
                androidPath.lineTo(pts[0].x * scaleX, pts[0].y * scaleY)
            } else {
                androidPath.moveTo(pts[0].x * scaleX, pts[0].y * scaleY)
                for (i in 1 until pts.size) {
                    val prev = pts[i - 1]
                    val curr = pts[i]
                    val midX = (prev.x + curr.x) / 2f * scaleX
                    val midY = (prev.y + curr.y) / 2f * scaleY
                    androidPath.quadTo(prev.x * scaleX, prev.y * scaleY, midX, midY)
                }
                val last = pts.last()
                androidPath.lineTo(last.x * scaleX, last.y * scaleY)
            }
        }

        canvas.drawPath(androidPath, paint)
    }

    android.util.Log.d("DrawingView", "Bitmap conversion complete")
    return bitmap
}
