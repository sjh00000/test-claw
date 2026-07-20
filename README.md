# Text Generation Studio

Vue3 + Spring Boot 的文生图 / 文生视频工作台。

## 功能范围

- 用户登录，首次登录自动创建用户；用户信息持久化到 MySQL。
- 文生图独立使用 image-provider，支持无参考图纯文生图，也支持上传参考图后按图编辑生成。
- 文生视频独立使用 Seedance，支持无参考图纯文生视频，也支持上传参考图后多模态参考生成。
- 不再创建本地生成会话，不再串联“参考图 -> 关键帧 -> 视频”的流程。

## MySQL 配置

后端使用 MySQL 持久化用户信息。默认连接本机 MySQL 的 `keyframe_video_studio` 库：

```properties
MYSQL_URL=jdbc:mysql://localhost:3306/keyframe_video_studio?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=root
MYSQL_PASSWORD=
```

完整建库建表 SQL 在 `sql/keyframe_video_studio.sql`。应用启动时也会按 `src/main/resources/schema.sql` 幂等确认 `app_user` 表存在。单表用户读写使用 MyBatis-Plus；后续如果出现关联查询，再通过 MyBatis Mapper XML 承载 SQL。厂商 `api-key` 只随本次请求传给后端使用，不写入用户表。

如果本机 MySQL 账号不是 `root` 空密码，只需要改环境变量：

```powershell
$env:MYSQL_USERNAME="你的账号"
$env:MYSQL_PASSWORD="你的密码"
```

## 页面厂商配置

登录后在页面顶部填写：

- image-provider：`base-url`、`api-key`、`model`
- Seedance：`base-url`、`api-key`、`model`

image-provider 真实调用采用纯文生图接口或带 `image[]` 的 multipart 编辑接口。Seedance 当前按多模态参考生视频场景提交，参考图为空时只提交文本提示词。

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

- `POST /api/auth/login`：登录或首次创建用户。
- `POST /api/generation/images`：文生图，参考图可选。
- `POST /api/generation/videos`：文生视频，参考图可选。
- `POST /api/generation/videos/status`：按 Seedance 任务 ID 查询视频状态。

所有接口统一返回：

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```
