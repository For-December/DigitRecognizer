# 调试图片保存位置说明

## 问题
之前的调试图片保存在应用的内部缓存目录：
```
/data/data/com.dsa.digitrecognizer/cache/debug_xxx.png
```
这个目录是应用的私有目录，普通文件管理器无法访问。

## 解决方案
现在调试图片会保存到两个位置：

### 1. 内部缓存（给应用和API使用）
- **路径**: `/data/data/com.dsa.digitrecognizer/cache/`
- **用途**: 应用内部使用、发送到远程API
- **访问**: 无法直接访问（需要root或adb）

### 2. 相册（方便查看）✅
- **路径**: `Pictures/DigitRecognizer/`
- **用途**: 方便用户直接查看调试图片
- **访问**: 可以在相册应用中直接查看
- **提示**: 识别后会显示"调试图片已保存到相册"

## 如何查看调试图片

### 方法1: 打开相册应用（推荐）
1. 打开手机的"相册"或"图库"应用
2. 找到"DigitRecognizer"相册
3. 查看最新保存的 `debug_xxx.png` 图片

### 方法2: 使用文件管理器
1. 打开文件管理器
2. 进入 `Pictures/DigitRecognizer/` 目录
3. 查看调试图片

### 方法3: 使用 ADB 提取（需要电脑）
如果需要提取内部缓存的图片：

```bash
# 1. 列出缓存目录中的文件
adb shell "run-as com.dsa.digitrecognizer ls /data/data/com.dsa.digitrecognizer/cache/"

# 2. 提取特定文件到电脑
adb shell "run-as com.dsa.digitrecognizer cat /data/data/com.dsa.digitrecognizer/cache/debug_xxx.png" > debug.png
```

## 调试图片的用途

调试图片显示的是经过预处理（二值化）后的图片，可以帮助你：

1. **验证二值化效果**: 查看图片是否正确转换为黑白
2. **检查图片质量**: 数字是否清晰、是否有噪点
3. **对比识别结果**: 如果识别错误，可以看看是图片问题还是模型问题

## 调试图片特点

- **格式**: PNG（无损压缩）
- **处理**: 已经过二值化（阈值220）
- **内容**: 黑色笔画 + 白色背景
- **命名**: `debug_时间戳.png`

## 权限说明

应用需要以下权限来保存图片到相册：
- Android 10+ (API 29+): 不需要额外权限，使用 MediaStore API
- Android 9 及以下: 需要 WRITE_EXTERNAL_STORAGE 权限

权限已经在 AndroidManifest.xml 中正确配置。

## 示例

当你进行识别后，你会看到：
1. Toast 提示："调试图片已保存到相册"
2. 在相册中可以找到 `Pictures/DigitRecognizer/debug_xxx.png`
3. 打开图片可以看到二值化后的黑白数字图片

这样你就可以方便地检查预处理效果，判断识别问题的根源了！

