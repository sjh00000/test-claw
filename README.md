# Keyframe Video Studio

Vue3 + Spring Boot + Spring AI Alibaba 的关键帧图生视频工作台。

## 功能范围

- 用户按三步操作：先生成或上传主体参考图，再生成或上传关键帧图，最后生成视频。
- 可先调用 image-2 生成主体/角色参考图。
- 后端逐张调用自定义厂商 image-2 edits 接口生成关键帧图。
- 后端按 Seedance 2.0 多模态参考生视频规则，把关键帧结果作为 `reference_image` 提交给 Seedance。
- 前端展示关键帧生成结果、视频任务状态和最终视频地址。

## 环境变量

```powershell
$env:GPT_IMAGE2_BASE_URL="https://api.euzhi.com"
$env:GPT_IMAGE2_API_KEY="your-euzhi-api-key"
$env:GPT_IMAGE2_MODEL="gpt-image-2"
$env:GPT_IMAGE2_EDIT_ENDPOINT="/v1/images/edits"
$env:GPT_IMAGE2_GENERATION_ENDPOINT="/v1/images/generations"
$env:GPT_IMAGE2_SIZE="1024x1024"
$env:GPT_IMAGE2_QUALITY="medium"

$env:SEEDANCE_BASE_URL="https://your-seedance-provider.example.com"
$env:SEEDANCE_API_KEY="your-seedance-api-key"
$env:SEEDANCE_MODEL="doubao-seedance-2-0-260128"
$env:SEEDANCE_FAST_MODEL="doubao-seedance-2-0-fast-260128"
```

未配置 image2 key 时，后端会返回开发占位图，方便先联调前端流程。真实调用采用 multipart 表单：`model`、`prompt`、`size`、`quality` 和 `image[]` 参考图文件；这组主体参考图在一个会话内被所有关键帧共用。
image-2 返回优先读取 `data[0].url`；如果只有 `data[0].b64_json`，当前会转成 `data:image/png;base64,...` 直接传给前端和 Seedance。生产接对象存储后，可在 `ImageProviderClient` 的返回解析处统一改为转存 URL。

Seedance 2.0 当前按多模态参考生视频场景提交，图片 role 统一为 `reference_image`，不和首帧/尾帧模式混用。分辨率支持 `480p`、`720p`，比例支持 `adaptive`、`16:9`、`4:3`、`1:1`、`3:4`、`9:16`、`21:9`，时长支持 `-1` 或 `4~15` 秒。

## 本地启动

后端：

```powershell
mvn spring-boot:run
```

前端：

```powershell
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。

## API

- `POST /api/generation-sessions/reference-images`：生成主体/角色参考图。
- `POST /api/generation-sessions`：创建会话。
- `POST /api/generation-sessions/{sessionId}/keyframes`：逐张生成关键帧。
- `POST /api/generation-sessions/{sessionId}/video`：提交 Seedance 视频任务。
- `POST /api/generation-sessions/video`：使用已上传或已选择的关键帧图直接提交 Seedance 视频任务。
- `POST /api/generation-sessions/{sessionId}/video/status`：刷新 Seedance 任务状态。
- `GET /api/generation-sessions/{sessionId}`：查询会话。

所有接口统一返回：

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```
