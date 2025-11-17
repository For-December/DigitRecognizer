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
import androidx.core.graphics.scale

class LocalModelPredictor(context: Context) {
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

        // 缩放到28x28（图片已在MainScreen中二值化）
        val resizedBitmap = bitmap.scale(28, 28)
        Log.d(TAG, "图片已缩放到 28x28")

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


    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(4 * 28 * 28 * 1)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(28 * 28)
        bitmap.getPixels(intValues, 0, 28, 0, 0, 28, 28)
        val threshold = 220 // 与 Python 一致

        for (pixelValue in intValues) {
            // RGB → 灰度
            val r = Color.red(pixelValue)
            val g = Color.green(pixelValue)
            val b = Color.blue(pixelValue)
            val gray = (r + g + b) / 3.0f

            // 新增二值化（与 Python 一致）
            val binary = if (gray < threshold) 1.0f else 0.0f

            // 移除颜色反转（因为 Python 不反转）
            inputBuffer.putFloat(binary)
        }

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

