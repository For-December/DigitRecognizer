package com.dsa.digitrecognizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object ImageUtils {
    fun saveBitmapToFile(context: Context, bitmap: Bitmap, fileName: String = "temp_image.jpg"): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        android.util.Log.d("ImageUtils", "Saved bitmap to ${file.absolutePath}, size: ${file.length()} bytes")

        // Log some pixel info to verify it's not blank
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val blackPixels = pixels.count {
            val r = android.graphics.Color.red(it)
            val g = android.graphics.Color.green(it)
            val b = android.graphics.Color.blue(it)
            val gray = (r + g + b) / 3
            gray < 128
        }
        android.util.Log.d("ImageUtils", "Bitmap stats: ${bitmap.width}x${bitmap.height}, black pixels: $blackPixels/${pixels.size}")

        return file
    }

    fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 保存图片到相册（Pictures/DigitRecognizer 目录）
     * 可以在相册中直接查看
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/DigitRecognizer")
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                android.util.Log.d("ImageUtils", "Saved to gallery: $fileName")
            }

            uri
        } catch (e: Exception) {
            android.util.Log.e("ImageUtils", "Failed to save to gallery", e)
            e.printStackTrace()
            null
        }
    }
}

