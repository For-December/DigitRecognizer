# 手写数字识别 Android 应用

这是一个功能完整的手写数字识别应用，支持本地模型和在线API两种识别方式。

## 功能特性

### 🎯 双模型支持
- **本地模型**: 使用 TensorFlow Lite 在设备上进行推理，速度快，无需网络
- **在线API**: 调用远程服务器 API 进行推理，可使用更强大的模型

### ✍️ 多种输入方式
- **手写输入**: 在画布上直接绘制数字
- **拍照**: 使用相机拍摄数字图片
- **相册选择**: 从相册中选择已有的数字图片

### 📊 详细结果展示
- 识别的数字（0-9）
- 识别置信度（百分比）
- 所有数字的概率分布（带进度条可视化）

## 安装配置

### 1. 模型文件放置

**重要**: 如果使用本地模型，请将 `digit_recognition_model.tflite` 文件放置在：
```
app/src/main/assets/digit_recognition_model.tflite
```

模型要求：
- 格式: TensorFlow Lite (.tflite)
- 输入: 28x28 像素灰度图像，归一化到 [0,1]
- 输出: 10个类别的概率（数字 0-9）

### 2. API 配置

如果使用在线模型，需要：

1. **启动 Python API 服务** (运行在 `localhost:8000`)
   ```bash
   # 确保你的 Python API 正在运行
   python your_api_server.py
   ```

2. **模拟器配置**: Android 模拟器会自动将 `10.0.2.2` 映射到主机的 `localhost`，无需修改代码

3. **真机配置**: 如果使用真机测试，需要修改 `ApiService.kt` 中的 `BASE_URL`：
   ```kotlin
   // 将 localhost 改为电脑的实际 IP 地址
   private const val BASE_URL = "http://192.168.x.x:8000/"
   ```

### 3. 权限说明

应用需要以下权限（已在 AndroidManifest.xml 中配置）：
- **CAMERA**: 拍照功能
- **INTERNET**: 在线API调用
- **READ_EXTERNAL_STORAGE**: 从相册选择图片

首次使用拍照功能时，应用会请求相机权限。

## 使用步骤

### 手写识别
1. 打开应用
2. 选择 "本地模型" 或 "在线API"
3. 选择 "手写" 输入方式
4. 在白色画布上绘制数字（0-9）
5. 点击 "识别" 按钮
6. 查看识别结果和概率分布
7. 如需重新绘制，点击 "清除" 按钮

### 拍照识别
1. 打开应用
2. 选择 "本地模型" 或 "在线API"
3. 选择 "拍照/相册" 输入方式
4. 点击 "拍照" 按钮，拍摄包含数字的照片
5. 点击 "识别" 按钮
6. 查看识别结果

### 相册识别
1. 打开应用
2. 选择 "本地模型" 或 "在线API"
3. 选择 "拍照/相册" 输入方式
4. 点击 "相册" 按钮，选择已有的数字图片
5. 点击 "识别" 按钮
6. 查看识别结果

## 技术栈

### 前端
- **Kotlin**: 主要开发语言
- **Jetpack Compose**: 现代化 UI 框架
- **Material Design 3**: UI 设计系统

### 机器学习
- **TensorFlow Lite**: 本地模型推理
- **TensorFlow Lite Support**: 图像预处理

### 网络
- **Retrofit 2**: HTTP 客户端
- **OkHttp 3**: 网络请求
- **Gson**: JSON 序列化/反序列化

### 架构
- **MVVM**: Model-View-ViewModel 架构
- **Coroutines**: 异步编程
- **Lifecycle**: 生命周期管理

## 项目结构

```
app/src/main/java/com/dsa/digitrecognizer/
├── MainActivity.kt           # 主Activity
├── MainScreen.kt             # 主界面UI
├── MainViewModel.kt          # ViewModel层
├── DrawingView.kt            # 手写画布组件
├── LocalModelPredictor.kt    # 本地模型推理
├── ApiService.kt             # 远程API调用
└── ImageUtils.kt             # 图像处理工具
```

## 开发笔记

### 图像预处理
- 手写输入会被转换为 280x280 的 Bitmap，然后缩放到 28x28
- 图像会从 RGB 转换为灰度，并归一化到 [0,1]
- 颜色会反转（黑底白字 -> 白底黑字）以匹配 MNIST 数据集格式

### API 接口
调用 POST `/predict` 端点，传入 `multipart/form-data` 格式的图片文件：
```kotlin
// 请求
file: UploadFile

// 响应
{
    "digit": 7,              // 识别的数字
    "confidence": 98.5,      // 置信度
    "probabilities": [...],  // 10个概率值
    "success": true          // 是否成功
}
```

## 常见问题

### Q: 本地模型识别失败
**A**: 确保 `digit_recognition_model.tflite` 文件已正确放置在 `app/src/main/assets/` 目录下

### Q: 在线API连接失败
**A**: 
- 确保 Python API 服务正在运行
- 检查防火墙设置
- 如果使用真机，确保手机和电脑在同一网络，并修改 API URL 为电脑IP

### Q: 手写识别不准确
**A**: 
- 尽量在画布中央绘制
- 确保数字清晰、大小适中
- 避免过于潦草的笔迹

### Q: 拍照后图片方向错误
**A**: 当前版本已简化处理，如遇到方向问题，可以从相册选择已旋转好的图片

## 构建和运行

```bash
# 清理项目
./gradlew clean

# 构建项目
./gradlew build

# 安装到设备/模拟器
./gradlew installDebug

# 或直接在 Android Studio 中点击 Run 按钮
```

## 未来改进

- [ ] 添加图片旋转功能
- [ ] 支持连续识别多个数字
- [ ] 添加识别历史记录
- [ ] 优化手写笔迹识别算法
- [ ] 支持更多预训练模型
- [ ] 添加深色模式支持

## 许可证

本项目仅用于学习和演示目的。

## 联系方式

如有问题或建议，欢迎提出 Issue。

---

**享受手写数字识别的乐趣！** ✨

