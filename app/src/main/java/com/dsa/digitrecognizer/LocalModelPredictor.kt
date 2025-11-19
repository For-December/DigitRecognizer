package com.dsa.digitrecognizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class LocalModelPredictor(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var initError: String? = null

    init {
        try {
            Log.d(TAG, "开始加载模型...")
            val modelFile = loadModelFile(context)
            Log.d(TAG, "模型文件加载成功，大小: ${modelFile.capacity()} bytes")
            interpreter = Interpreter(modelFile)
            Log.d(TAG, "TensorFlow Lite Interpreter 初始化成功")
        } catch (e: Exception) {
            val errorMsg = "模型初始化失败: ${e.message}"
            Log.e(TAG, errorMsg, e)
            initError = errorMsg
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        try {
            val fileDescriptor = context.assets.openFd("digit_recognition_model.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            Log.e(TAG, "加载模型文件失败", e)
            throw Exception("无法加载模型文件 'digit_recognition_model.tflite': ${e.message}", e)
        }
    }

    fun predict(bitmap: Bitmap): PredictionResult {
        if (interpreter == null) {
            val errorMsg = if (initError != null) {
                "模型未初始化: $initError"
            } else {
                "模型未初始化，请确保 digit_recognition_model.tflite 文件在 assets 目录中"
            }
            throw IllegalStateException(errorMsg)
        }

        Log.d(TAG, "开始识别，图片尺寸: ${bitmap.width}x${bitmap.height}")

        // 使用高质量缩放到28x28（启用插值）
        val resizedBitmap = resizeWithInterpolation(bitmap, 28, 28)
        Log.d(TAG, "图片已缩放到 28x28")
//         debugSave(resizedBitmap)

        // 缩放完再二值化一下
        val inputBuffer = preprocessImage(resizedBitmap)
        Log.d(TAG, "图片预处理完成，缓冲区大小: ${inputBuffer.capacity()}")

        // 预测
        inputBuffer.rewind() // 重置缓冲区位置
        val outputArray = Array(1) { FloatArray(10) }

        try {
            interpreter?.run(inputBuffer, outputArray)
            Log.d(TAG, "模型推理成功")
        } catch (e: Exception) {
            Log.e(TAG, "模型推理失败", e)
            throw Exception("推理失败: ${e.message}", e)
        }

        val predictions = outputArray[0]
        val maxIndex = predictions.indices.maxByOrNull { predictions[it] } ?: 0
        val confidence = predictions[maxIndex] * 100
        val probabilities = predictions.map { it * 100 }

        Log.d(TAG, "识别结果: 数字=$maxIndex, 置信度=$confidence%")

        return PredictionResult(
            digit = maxIndex,
            confidence = confidence,
            probabilities = probabilities
        )
    }

    fun debugSave(resizedBitmap : Bitmap) {
        // 保存缩放后的图片到外部存储，方便调试查看
        try {
            val externalDebugFile = java.io.File(
                context.getExternalFilesDir(null),
                "debug_model_input_28x28_${System.currentTimeMillis()}.png"
            )
            java.io.FileOutputStream(externalDebugFile).use { fos ->
                resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            Log.d(TAG, "已保存模型输入图片到: ${externalDebugFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "保存调试图片失败", e)
        }
    }

    private fun resizeWithInterpolation(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        // Coil 对已有的 Bitmap 对象不进行缩放，直接使用降级方案
        // 使用 Android 原生最高质量配置实现类似 LANCZOS 的效果
        Log.d(TAG, "使用高质量缩放: ${source.width}x${source.height} -> ${targetWidth}x${targetHeight}")
        return resizeFallback(source, targetWidth, targetHeight)
    }

    private fun resizeFallback(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        // 使用多步缩放策略模拟 LANCZOS 效果
        // 策略：先缩小到中间尺寸（50%），再缩小到目标尺寸，减少细节丢失
        val sourceWidth = source.width
        val sourceHeight = source.height

        // 如果原图尺寸不大，直接一步缩放
        if (sourceWidth <= targetWidth * 2 && sourceHeight <= targetHeight * 2) {
            return singleStepResize(source, targetWidth, targetHeight)
        }

        // 计算中间尺寸（保持宽高比，大约是目标尺寸的2倍）
        val intermediateSize = maxOf(targetWidth, targetHeight) * 2
        val intermediateWidth: Int
        val intermediateHeight: Int

        if (sourceWidth > sourceHeight) {
            intermediateWidth = intermediateSize
            intermediateHeight = (intermediateSize * sourceHeight) / sourceWidth
        } else {
            intermediateHeight = intermediateSize
            intermediateWidth = (intermediateSize * sourceWidth) / sourceHeight
        }

        // 第一步：缩放到中间尺寸
        val intermediateBitmap = singleStepResize(source, intermediateWidth, intermediateHeight)

        // 第二步：缩放到目标尺寸
        val result = singleStepResize(intermediateBitmap, targetWidth, targetHeight)

        // 释放中间 bitmap
        if (intermediateBitmap != source) {
            intermediateBitmap.recycle()
        }

        Log.d(TAG, "多步缩放: ${sourceWidth}x${sourceHeight} -> ${intermediateWidth}x${intermediateHeight} -> ${targetWidth}x${targetHeight}")
        return result
    }

    private fun singleStepResize(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        // 单步高质量缩放
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)

        val paint = android.graphics.Paint().apply {
            isFilterBitmap = true      // 启用双线性插值
            isAntiAlias = true          // 启用抗锯齿
            isDither = true             // 启用抖动
        }

        val srcRect = android.graphics.Rect(0, 0, source.width, source.height)
        val dstRect = android.graphics.Rect(0, 0, targetWidth, targetHeight)
        canvas.drawBitmap(source, srcRect, dstRect, paint)

        return result
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(4 * 28 * 28 * 1)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(28 * 28)
        bitmap.getPixels(intValues, 0, 28, 0, 0, 28, 28)
        val threshold = 220 // 与 Python 一致

        var darkPixels = 0  // 统计暗色像素
        val grayValues = mutableListOf<Float>()

        for (pixelValue in intValues) {
            // RGB → 灰度
            val r = Color.red(pixelValue)
            val g = Color.green(pixelValue)
            val b = Color.blue(pixelValue)
            val gray = (r + g + b) / 3.0f

            grayValues.add(gray)

            // 二值化（与 Python 一致）
            val binary = if (gray < threshold) {
                darkPixels++
                1.0f
            } else {
                0.0f
            }

            inputBuffer.putFloat(binary)
        }

        // 打印统计信息
        val avgGray = grayValues.average()
        val minGray = grayValues.minOrNull() ?: 0f
        val maxGray = grayValues.maxOrNull() ?: 0f
        Log.d(TAG, "灰度统计: min=${minGray.toInt()}, max=${maxGray.toInt()}, avg=${avgGray.toInt()}")
        Log.d(TAG, "二值化结果: 暗色像素(1.0)=$darkPixels/784, 亮色像素(0.0)=${784 - darkPixels}/784")

        // 打印前40个灰度值用于对比
//        val first40 = grayValues.take(40).joinToString(",") { it.toInt().toString() }
//        Log.d(TAG, "前40个灰度值: $first40")

        return inputBuffer
    }
    fun close() {
        interpreter?.close()
    }

    companion object {
        private const val TAG = "LocalModelPredictor"
    }
}

data class PredictionResult(
    val digit: Int,
    val confidence: Float,
    val probabilities: List<Float>
)

