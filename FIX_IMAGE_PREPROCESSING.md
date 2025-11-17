# 修复图片预处理问题

## 问题描述
从图库选择的照片没有经过二值化处理就被发送到模型，导致识别效果不佳。而手写的图片却被二值化了两次，可能导致信息丢失。

## 问题分析

### 之前的处理流程
1. **手写输入**：
   - `convertDrawingToBitmap` 中进行二值化（阈值220）
   - 如果使用本地模型，`LocalModelPredictor.predict` 再次二值化
   - 结果：二值化两次❌

2. **图库照片**：
   - 本地模型：`LocalModelPredictor.predict` 中二值化一次 ✅
   - 远程API：直接发送原图，没有二值化 ❌

### 不一致的问题
- 同一张图片，在本地和远程可能得到不同的识别结果
- 手写的图片被过度处理
- 照片在远程API时没有预处理

## 解决方案

### 统一预处理流程
在 `MainScreen` 中添加统一的预处理函数，对所有图片（手写、照片）在发送到模型前进行统一处理：

```kotlin
fun preprocessBitmap(bitmap: Bitmap, threshold: Int = 220): Bitmap {
    // 二值化处理
    val width = bitmap.width
    val height = bitmap.height
    val processedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = AndroidColor.red(pixel)
        val g = AndroidColor.green(pixel)
        val b = AndroidColor.blue(pixel)
        val gray = (r + g + b) / 3
        
        // 二值化：灰度值 > 阈值设为白色，否则设为黑色
        val binaryValue = if (gray > threshold) 255 else 0
        pixels[i] = AndroidColor.rgb(binaryValue, binaryValue, binaryValue)
    }

    processedBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return processedBitmap
}
```

### 现在的处理流程

```
┌─────────────┐     ┌─────────────┐
│  手写输入   │     │  图库照片   │
└──────┬──────┘     └──────┬──────┘
       │                   │
       ▼                   ▼
  convertDrawing      selectedImage
   ToBitmap()              │
       │                   │
       └────────┬──────────┘
                │
                ▼
         preprocessBitmap()  ← 统一二值化（阈值220）
                │
                ▼
         保存调试图片
                │
       ┌────────┴────────┐
       │                 │
       ▼                 ▼
   本地模型          远程API
   (已预处理)        (已预处理)
```

## 修改内容

### 1. DrawingView.kt
- ✅ 移除 `convertDrawingToBitmap` 中的二值化处理
- ✅ 只负责路径到 Bitmap 的转换和缩放

### 2. MainScreen.kt
- ✅ 添加 `preprocessBitmap` 函数
- ✅ 在识别前统一调用预处理
- ✅ 保存预处理后的图片用于调试

### 3. LocalModelPredictor.kt
- ✅ 移除 `applyBinarization` 函数
- ✅ 移除 `predict` 方法中的二值化步骤
- ✅ 直接处理已经预处理过的图片

## 优势

1. **一致性**：所有图片（手写、照片）都经过相同的预处理流程
2. **本地和远程一致**：本地模型和远程API看到的是相同的图片
3. **单一职责**：
   - DrawingView：只负责绘图和转换
   - MainScreen：负责预处理
   - LocalModelPredictor：只负责模型推理
4. **易于调试**：可以检查预处理后的图片
5. **易于调整**：只需在一个地方修改预处理参数

## 测试建议

1. 测试手写数字识别
2. 测试从相册选择照片识别
3. 测试拍照识别
4. 对比本地模型和远程API的结果（应该非常接近）
5. 检查保存的调试图片，确认二值化效果

## 后续优化建议

如果识别效果仍不理想，可以考虑：
- 调整二值化阈值（当前220）
- 添加图像增强（对比度、亮度调整）
- 添加去噪处理
- 添加自动裁剪功能（去除空白边缘）

