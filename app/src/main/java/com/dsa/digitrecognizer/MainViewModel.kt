package com.dsa.digitrecognizer

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel : ViewModel() {
    private var localPredictor: LocalModelPredictor? = null
    private val remotePredictor = RemoteModelPredictor()

    suspend fun predictLocal(context: Context, bitmap: Bitmap): PredictionResult {
        return withContext(Dispatchers.Default) {
            if (localPredictor == null) {
                localPredictor = LocalModelPredictor(context)
            }
            localPredictor!!.predict(bitmap)
        }
    }

    suspend fun predictRemote(imageFile: File): PredictionResult {
        return withContext(Dispatchers.IO) {
            remotePredictor.predict(imageFile)
        }
    }

    override fun onCleared() {
        super.onCleared()
        localPredictor?.close()
    }
}

