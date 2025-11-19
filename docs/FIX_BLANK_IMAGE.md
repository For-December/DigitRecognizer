# 修复空白图片问题

## 问题描述
后台收到的图片是空白的，无法进行识别。

## 根本原因
当用户在手写画布上绘制数字时，路径（Path）的坐标是基于屏幕上Canvas的实际大小（例如 1000x800 像素）。但是在转换为 Bitmap 时，我们创建的是一个固定大小的 280x280 像素的 Bitmap，而没有对路径进行缩放。

这导致绘制的笔画坐标超出了 280x280 的范围，所以生成的 Bitmap 是空白的。

## 解决方案

### 1. 修改 DrawingView.kt
- **添加 canvas 尺寸回调**: 在 `DrawingCanvas` 中添加 `onCanvasSizeChanged` 回调，用于报告实际的 Canvas 尺寸
- **修改 convertDrawingToBitmap**: 添加 `canvasWidth` 和 `canvasHeight` 参数，并在绘制时计算缩放比例
- **路径缩放**: 使用 `Matrix.setScale()` 对路径进行缩放，确保笔画在目标 Bitmap 尺寸内正确显示

### 2. 修改 MainScreen.kt
- **存储 canvas 尺寸**: 添加 `canvasSize` 状态变量来存储 Canvas 的实际尺寸
- **传递 canvas 尺寸**: 在调用 `convertDrawingToBitmap` 时传递实际的 Canvas 尺寸
- **添加调试日志**: 保存生成的 Bitmap 到文件，方便检查

### 3. 修改 ImageUtils.kt
- **改用 PNG 格式**: 将保存格式从 JPEG 改为 PNG，避免压缩造成的质量损失
- **添加调试日志**: 输出像素统计信息，便于确认图片是否包含内容

## 关键代码变更

### DrawingView.kt
```kotlin
fun convertDrawingToBitmap(
    paths: List<PathWrapper>, 
    canvasWidth: Float,      // 新增：Canvas 实际宽度
    canvasHeight: Float,     // 新增：Canvas 实际高度
    width: Int = 280, 
    height: Int = 280
): Bitmap {
    // 计算缩放比例
    val scaleX = width.toFloat() / canvasWidth
    val scaleY = height.toFloat() / canvasHeight
    
    // 对每个路径应用缩放
    paths.forEach { pathWrapper ->
        val scaledPath = android.graphics.Path(pathWrapper.path.asAndroidPath())
        val matrix = android.graphics.Matrix()
        matrix.setScale(scaleX, scaleY)
        scaledPath.transform(matrix)
        canvas.drawPath(scaledPath, paint)
    }
}
```

### MainScreen.kt
```kotlin
// 存储 Canvas 尺寸
var canvasSize by remember { mutableStateOf(Pair(1f, 1f)) }

// 在 DrawingCanvas 中获取尺寸
DrawingCanvas(
    paths = paths,
    onPathUpdate = { paths = it },
    onCanvasSizeChanged = { width, height ->
        canvasSize = Pair(width, height)
    }
)

// 转换时传递尺寸
convertDrawingToBitmap(paths, canvasSize.first, canvasSize.second)
```

## 验证方法
1. 检查 Logcat 中的日志，查看：
   - Canvas 尺寸和缩放比例
   - Bitmap 中黑色像素的数量
   - 保存的文件路径
   
2. 从设备中提取调试图片文件进行检查：
   ```bash
   adb shell "run-as com.dsa.digitrecognizer ls /data/data/com.dsa.digitrecognizer/cache/"
   adb shell "run-as com.dsa.digitrecognizer cat /data/data/com.dsa.digitrecognizer/cache/debug_*.png" > debug.png
   ```

## 测试结果
- ✅ 绘制的数字现在可以正确显示在生成的 Bitmap 中
- ✅ 本地模型和远程 API 都能收到包含内容的图片
- ✅ 识别准确率显著提升

