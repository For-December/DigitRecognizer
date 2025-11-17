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

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedInputMethod by remember { mutableStateOf<InputMethod>(InputMethod.HANDWRITE) }
    var selectedModelType by remember { mutableStateOf<ModelType>(ModelType.LOCAL) }
    var paths by remember { mutableStateOf<List<PathWrapper>>(emptyList()) }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var predictionResult by remember { mutableStateOf<PredictionResult?>(null) }
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
                            onPathUpdate = {
                                paths = it
                                predictionResult = null
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
                            if (paths.isEmpty()) {
                                Toast.makeText(context, "请先绘制数字", Toast.LENGTH_SHORT).show()
                                isLoading = false
                                return@launch
                            }
                            convertDrawingToBitmap(paths)
                        } else {
                            selectedImage ?: run {
                                Toast.makeText(context, "请先选择图片", Toast.LENGTH_SHORT).show()
                                isLoading = false
                                return@launch
                            }
                        }

                        val result = if (selectedModelType == ModelType.LOCAL) {
                            viewModel.predictLocal(context, bitmap)
                        } else {
                            val imageFile = ImageUtils.saveBitmapToFile(context, bitmap)
                            viewModel.predictRemote(imageFile)
                        }

                        predictionResult = result
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

        // 结果显示
        predictionResult?.let { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "识别结果",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${result.digit}",
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)
                    )
                    Text(
                        text = "置信度: %.2f%%".format(result.confidence),
                        fontSize = 18.sp,
                        color = Color(0xFF1565C0)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("所有概率", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    result.probabilities.forEachIndexed { index, prob ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("数字 $index:", fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(
                                    progress = prob / 100f,
                                    modifier = Modifier
                                        .width(150.dp)
                                        .padding(end = 8.dp)
                                )
                                Text("%.2f%%".format(prob), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class InputMethod {
    HANDWRITE, PHOTO
}

enum class ModelType {
    LOCAL, REMOTE
}

