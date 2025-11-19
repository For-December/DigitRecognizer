# 本地模型调试指南

## 问题: "Model not initialized"

### 已完成的修复

1. ✅ 添加了详细的日志输出
2. ✅ 添加了错误信息捕获和显示
3. ✅ 添加了模型输入输出形状打印

### 查看日志

现在运行应用时，请查看 **Logcat** 中的日志：

1. 在 Android Studio 中打开 Logcat
2. 过滤标签: `LocalModelPredictor`
3. 查找以下信息：

#### 成功的日志应该是：
```
D/LocalModelPredictor: 开始加载模型...
D/LocalModelPredictor: 模型文件加载成功，大小: XXXXX bytes
D/LocalModelPredictor: TensorFlow Lite Interpreter 初始化成功
D/LocalModelPredictor: 输入形状: [1, 28, 28, 1]
D/LocalModelPredictor: 输出形状: [1, 10]
```

#### 如果出错，会看到：
```
E/LocalModelPredictor: 模型加载失败: [具体错误信息]
```

### 常见错误原因和解决方法

#### 1. 文件不存在
**错误**: `FileNotFoundException: digit_recognition_model.tflite`
**解决**:
```bash
# 确认文件存在
ls -la app/src/main/assets/digit_recognition_model.tflite

# 如果不存在，将文件复制到正确位置
cp your_model.tflite app/src/main/assets/digit_recognition_model.tflite

# 清理并重新构建
./gradlew clean assembleDebug
```

#### 2. 模型格式不兼容
**错误**: `Could not create TensorFlow Lite Interpreter`
**原因**: 模型可能不是有效的 TFLite 格式，或者版本不兼容
**解决**:
- 确认模型文件是 `.tflite` 格式
- 检查模型是否使用了不支持的操作
- 尝试使用 TensorFlow Lite Converter 重新转换模型

#### 3. 输入输出形状不匹配
**检查**: 查看日志中的输入输出形状
**预期**:
- 输入: `[1, 28, 28, 1]` 或 `[1, 28, 28]`
- 输出: `[1, 10]`

如果形状不匹配，需要修改 `preprocessImage` 函数。

### 调试步骤

#### 步骤 1: 检查文件是否真的在 assets 中
```bash
cd /Users/fy/AndroidStudioProjects/DigitRecognizer
find . -name "digit_recognition_model.tflite" -type f
```

应该看到:
```
./app/src/main/assets/digit_recognition_model.tflite
```

#### 步骤 2: 检查文件大小
```bash
ls -lh app/src/main/assets/digit_recognition_model.tflite
```

一个典型的 MNIST 模型应该是 **几百 KB 到几 MB**。

如果文件很小（< 10KB），可能是损坏的。

#### 步骤 3: 清理并重新构建
```bash
# 停止 Gradle
./gradlew --stop

# 清理项目
./gradlew clean

# 重新构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

#### 步骤 4: 运行应用并查看 Logcat
1. 运行应用
2. 选择 "本地模型"
3. 尝试识别
4. 查看 Logcat 中的完整错误信息

### 如果模型输入形状不同

如果你的模型期望不同的输入格式，请告诉我输入形状，我会修改代码。

常见的输入格式：
- `[1, 28, 28, 1]` - 单通道灰度图（当前实现）
- `[1, 28, 28]` - 没有通道维度
- `[1, 1, 28, 28]` - 批次和通道维度调换
- `[1, 784]` - 展平的向量

### 测试模型文件

你可以用 Python 测试模型文件是否有效：

```python
import tensorflow as tf
import numpy as np

# 加载模型
interpreter = tf.lite.Interpreter(model_path="digit_recognition_model.tflite")
interpreter.allocate_tensors()

# 获取输入输出详情
input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print("输入详情:")
print(f"  形状: {input_details[0]['shape']}")
print(f"  类型: {input_details[0]['dtype']}")

print("\n输出详情:")
print(f"  形状: {output_details[0]['shape']}")
print(f"  类型: {output_details[0]['dtype']}")

# 测试推理
test_input = np.random.random(input_details[0]['shape']).astype(np.float32)
interpreter.set_tensor(input_details[0]['index'], test_input)
interpreter.invoke()
output = interpreter.get_tensor(output_details[0]['index'])

print(f"\n测试推理成功！输出形状: {output.shape}")
print(f"输出: {output}")
```

### 下一步

1. **运行应用**
2. **查看 Logcat** - 找到 `LocalModelPredictor` 标签
3. **复制完整的错误信息** 告诉我
4. 我会根据具体错误帮你解决问题

---

## 快速诊断命令

在 Android Studio 终端中运行：

```bash
# 检查文件
ls -lh app/src/main/assets/digit_recognition_model.tflite

# 清理重建
./gradlew clean assembleDebug

# 查看构建日志
./gradlew assembleDebug --info | grep -i "assets"
```

如果看到类似 "copying digit_recognition_model.tflite to APK" 的信息，说明文件正确打包了。

