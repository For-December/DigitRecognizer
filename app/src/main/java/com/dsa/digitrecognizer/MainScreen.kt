package com.dsa.digitrecognizer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.io.File
import android.graphics.Color as AndroidColor

// 统一的图片预处理函数
fun preprocessBitmap(bitmap: Bitmap, threshold: Int = 220): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val processedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    var blackPixels = 0
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = AndroidColor.red(pixel)
        val g = AndroidColor.green(pixel)
        val b = AndroidColor.blue(pixel)
        val gray = (r + g + b) / 3

        // 二值化：灰度值 > 阈值设为白色(255)，否则设为黑色(0)
        val binaryValue = if (gray > threshold) 255 else 0
        if (binaryValue == 0) blackPixels++
        pixels[i] = AndroidColor.rgb(binaryValue, binaryValue, binaryValue)
    }

    android.util.Log.d("MainScreen", "预处理完成: ${width}x${height}, 黑色像素: $blackPixels/${pixels.size}")
    processedBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return processedBitmap
}

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedInputMethod by remember { mutableStateOf<InputMethod>(InputMethod.HANDWRITE) }
    var selectedModelType by remember { mutableStateOf<ModelType>(ModelType.LOCAL) }
    var paths by remember { mutableStateOf<List<PathWrapper>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(Pair(1f, 1f)) }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var predictionResult by remember { mutableStateOf<PredictionResult?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    // 权限请求
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "需要相机权限", Toast.LENGTH_SHORT).show()
        }
    }

    // 拍照
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            val bitmap = ImageUtils.getBitmapFromUri(context, photoUri!!)
            selectedImage = bitmap
            paths = emptyList()
        }
    }

    // 选择图片
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = ImageUtils.getBitmapFromUri(context, it)
            selectedImage = bitmap
            paths = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "手写数字识别",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 20.dp)
        )

        // 模型选择
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("选择模型类型", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = selectedModelType == ModelType.LOCAL,
                        onClick = { selectedModelType = ModelType.LOCAL },
                        label = { Text("本地模型") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedModelType == ModelType.REMOTE,
                        onClick = { selectedModelType = ModelType.REMOTE },
                        label = { Text("在线API") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 输入方式选择
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("选择输入方式", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = selectedInputMethod == InputMethod.HANDWRITE,
                        onClick = {
                            selectedInputMethod = InputMethod.HANDWRITE
                            selectedImage = null
                            predictionResult = null
                        },
                        label = { Text("手写") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedInputMethod == InputMethod.PHOTO,
                        onClick = {
                            selectedInputMethod = InputMethod.PHOTO
                            paths = emptyList()
                            predictionResult = null
                        },
                        label = { Text("拍照/相册") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 输入区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (selectedInputMethod == InputMethod.HANDWRITE) {
                    Text("在下方绘制数字", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                    ) {
                        DrawingCanvas(
                            paths = paths,
                            onAddStroke = { stroke ->
                                paths = paths + stroke
                                predictionResult = null
                            },
                            onCanvasSizeChanged = { width, height ->
                                canvasSize = Pair(width, height)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            paths = emptyList()
                            predictionResult = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("清除")
                    }
                } else {
                    if (selectedImage != null) {
                        Image(
                            bitmap = selectedImage!!.asImageBitmap(),
                            contentDescription = "Selected Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                                .background(Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("未选择图片", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    val photoFile = File(context.cacheDir, "temp_photo.jpg")
                                    photoUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        photoFile
                                    )
                                    takePictureLauncher.launch(photoUri)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("拍照")
                        }

                        Button(
                            onClick = {
                                pickImageLauncher.launch("image/*")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("相册")
                        }
                    }
                }
            }
        }

        // 识别按钮
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    try {
                        val bitmap = if (selectedInputMethod == InputMethod.HANDWRITE) {
                            android.util.Log.d("MainScreen", "Recognition started: paths.size = ${paths.size}")
                            if (paths.isEmpty()) {
                                Toast.makeText(context, "请先绘制数字", Toast.LENGTH_SHORT).show()
                                isLoading = false
                                return@launch
                            }
                            convertDrawingToBitmap(paths, canvasSize.first, canvasSize.second)
                        } else {
                            selectedImage ?: run {
                                Toast.makeText(context, "请先选择图片", Toast.LENGTH_SHORT).show()
                                isLoading = false
                                return@launch
                            }
                        }

                        android.util.Log.d("MainScreen", "原始图片: ${bitmap.width}x${bitmap.height}")

                        // 统一预处理：二值化
                        val processedBitmap = preprocessBitmap(bitmap, 220)

                        val result = if (selectedModelType == ModelType.LOCAL) {
                            viewModel.predictLocal(context, processedBitmap)
                        } else {
                            // 远程 API 需要保存临时文件
                            val imageFile = ImageUtils.saveBitmapToFile(context, processedBitmap)
                            viewModel.predictRemote(imageFile)
                        }

                        android.util.Log.d("MainScreen", "Prediction result: digit=${result.digit}, confidence=${result.confidence}")
                        predictionResult = result
                        showResultDialog = true
                    } catch (e: Exception) {
                        Toast.makeText(context, "识别失败: ${e.message}", Toast.LENGTH_LONG).show()
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("识别", fontSize = 18.sp)
            }
        }
    }

    // 结果对话框
    if (showResultDialog && predictionResult != null) {
        ResultDialog(
            result = predictionResult!!,
            onDismiss = { showResultDialog = false }
        )
    }
}

enum class InputMethod {
    HANDWRITE, PHOTO
}

enum class ModelType {
    LOCAL, REMOTE
}

@Composable
fun ResultDialog(
    result: PredictionResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "识别结果",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 主要结果
                Text(
                    text = "${result.digit}",
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
                Text(
                    text = "置信度: %.2f%%".format(result.confidence),
                    fontSize = 18.sp,
                    color = Color(0xFF1565C0),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // 所有概率
                Text(
                    "所有概率分布",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                result.probabilities.forEachIndexed { index, prob ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "数字 $index:",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            modifier = Modifier.width(60.dp)
                        )
                        LinearProgressIndicator(
                            progress = { prob / 100f },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                        )
                        Text(
                            "%.1f%%".format(prob),
                            fontSize = 12.sp,
                            modifier = Modifier.width(50.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定", fontSize = 16.sp)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}
