package com.dsa.digitrecognizer

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.util.concurrent.TimeUnit

data class ApiPredictionResponse(
    val digit: Int,
    val confidence: Float,
    val probabilities: List<Float>,
    val success: Boolean
)

interface DigitRecognizerApi {
    @Multipart
    @POST("predict")
    suspend fun predict(
        @Part file: MultipartBody.Part
    ): Response<ApiPredictionResponse>
}

object ApiClient {
    private const val BASE_URL = "http://10.0.2.2:8000/" // Android模拟器访问本地主机

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: DigitRecognizerApi = retrofit.create(DigitRecognizerApi::class.java)
}

class RemoteModelPredictor {
    suspend fun predict(imageFile: File): PredictionResult {
        val requestBody = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", imageFile.name, requestBody)

        val response = ApiClient.api.predict(filePart)

        if (response.isSuccessful && response.body() != null) {
            val body = response.body()!!
            return PredictionResult(
                digit = body.digit,
                confidence = body.confidence,
                probabilities = body.probabilities
            )
        } else {
            throw Exception("API调用失败: ${response.code()}")
        }
    }
}

