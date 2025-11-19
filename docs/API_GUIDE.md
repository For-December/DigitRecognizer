# API 使用说明

## 接口信息

### 端点
```
POST http://localhost:8000/predict
```

### 请求格式
- Content-Type: `multipart/form-data`
- 参数名: `file`
- 文件类型: 图片文件 (JPEG, PNG 等)

### 响应格式
```json
{
  "digit": 7,
  "confidence": 98.5,
  "probabilities": [
    2.3,    // 数字 0 的概率
    0.5,    // 数字 1 的概率
    1.2,    // 数字 2 的概率
    0.8,    // 数字 3 的概率
    0.3,    // 数字 4 的概率
    1.5,    // 数字 5 的概率
    0.9,    // 数字 6 的概率
    98.5,   // 数字 7 的概率 ⭐
    2.1,    // 数字 8 的概率
    1.4     // 数字 9 的概率
  ],
  "success": true
}
```

## Android 应用配置

### 模拟器
Android 模拟器使用特殊 IP 地址 `10.0.2.2` 来访问主机的 `localhost`。

应用中的配置（已设置）：
```kotlin
// ApiService.kt
private const val BASE_URL = "http://10.0.2.2:8000/"
```

### 真机测试
如果在真实设备上测试，需要修改配置：

1. **获取电脑 IP 地址**
   ```bash
   # macOS/Linux
   ifconfig | grep "inet "
   
   # Windows
   ipconfig
   ```
   
   示例输出: `192.168.1.100`

2. **修改 BASE_URL**
   在 `app/src/main/java/com/dsa/digitrecognizer/ApiService.kt` 中：
   ```kotlin
   private const val BASE_URL = "http://192.168.1.100:8000/"
   ```

3. **确保防火墙允许访问**
   ```bash
   # macOS - 允许 Python 接受传入连接
   # 在系统偏好设置 > 安全性与隐私 > 防火墙 中配置
   
   # Linux
   sudo ufw allow 8000
   ```

## API 测试

### 使用 curl 测试
```bash
# 测试 API 是否正常工作
curl -X POST http://localhost:8000/predict \
  -F "file=@test_digit.jpg"
```

预期响应：
```json
{
  "digit": 7,
  "confidence": 98.5,
  "probabilities": [2.3, 0.5, ...],
  "success": true
}
```

### 使用 Python 测试
```python
import requests

url = "http://localhost:8000/predict"
files = {"file": open("test_digit.jpg", "rb")}

response = requests.post(url, files=files)
print(response.json())
```

### 使用 Postman 测试
1. 创建新请求
2. 方法: POST
3. URL: `http://localhost:8000/predict`
4. Body > form-data
5. Key: `file` (类型选择 File)
6. Value: 选择图片文件
7. 点击 Send

## 错误处理

### 常见错误

#### 1. Connection refused
```
java.net.ConnectException: Failed to connect to /10.0.2.2:8000
```
**原因**: API 服务未运行
**解决**: 启动 Python API 服务

#### 2. Timeout
```
java.net.SocketTimeoutException: timeout
```
**原因**: 请求超时
**解决**: 
- 检查网络连接
- 增加超时时间（已设置为 30 秒）
- 检查服务器性能

#### 3. 500 Internal Server Error
```json
{
  "detail": "预测失败: ..."
}
```
**原因**: 服务器处理失败
**解决**: 
- 检查图片格式是否正确
- 查看服务器日志
- 确认模型文件加载正常

## API 服务器示例

你的 Python API 服务器应该类似：

```python
from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
import numpy as np
import tensorflow as tf
from PIL import Image
import io

app = FastAPI()

# 加载模型
model = tf.keras.models.load_model('digit_model.h5')

def preprocess_image(image_bytes):
    # 读取图片
    image = Image.open(io.BytesIO(image_bytes))
    
    # 转换为灰度
    image = image.convert('L')
    
    # 调整大小到 28x28
    image = image.resize((28, 28))
    
    # 转换为数组并归一化
    img_array = np.array(image) / 255.0
    
    # 反转颜色（如果需要）
    img_array = 1 - img_array
    
    # 重塑为模型输入格式
    img_array = img_array.reshape(1, 28, 28, 1)
    
    return img_array

@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    try:
        # 读取图片
        contents = await file.read()
        
        # 预处理
        img_input = preprocess_image(contents)
        
        # 预测
        predictions = model.predict(img_input, verbose=0)
        digit = int(np.argmax(predictions))
        confidence = float(np.max(predictions) * 100)
        probabilities = (predictions[0] * 100).tolist()
        
        return JSONResponse(
            content={
                "digit": digit,
                "confidence": confidence,
                "probabilities": probabilities,
                "success": True,
            }
        )
    
    except Exception as e:
        return JSONResponse(
            status_code=500,
            content={"detail": f"预测失败: {str(e)}"}
        )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

## 性能优化建议

### 服务器端
- 使用模型缓存，避免重复加载
- 考虑使用异步处理
- 启用 gzip 压缩
- 添加请求限流

### 客户端（Android）
- 压缩图片大小再上传
- 添加重试机制
- 显示上传进度
- 缓存识别结果

## 安全建议

如果部署到生产环境：

1. **使用 HTTPS**
   ```kotlin
   private const val BASE_URL = "https://your-domain.com/"
   ```

2. **添加认证**
   ```kotlin
   @POST("predict")
   suspend fun predict(
       @Header("Authorization") token: String,
       @Part file: MultipartBody.Part
   ): Response<ApiPredictionResponse>
   ```

3. **限制文件大小**
   ```python
   @app.post("/predict")
   async def predict(file: UploadFile = File(..., max_length=5*1024*1024)):  # 5MB
   ```

4. **验证文件类型**
   ```python
   allowed_types = ["image/jpeg", "image/png", "image/jpg"]
   if file.content_type not in allowed_types:
       raise HTTPException(400, "Invalid file type")
   ```

## 调试技巧

### 启用详细日志
在 `ApiService.kt` 中添加日志拦截器：

```kotlin
import okhttp3.logging.HttpLoggingInterceptor

private val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}

private val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(loggingInterceptor)  // 添加这行
    .connectTimeout(30, TimeUnit.SECONDS)
    // ...
    .build()
```

### 查看网络请求
使用 Android Studio 的 Network Profiler：
1. 运行应用
2. 打开 Profiler 标签
3. 选择 Network
4. 观察请求和响应详情

---

**需要帮助？** 检查服务器日志和 Android Logcat 输出

