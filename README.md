# Text Generation Studio

Vue3 + Spring Boot 的文生图 / 文生视频工作台。

## 功能范围

- 用户登录，首次登录自动创建用户；登录成功返回 24 小时 `accessToken`，用户信息持久化到 MySQL。
- 文生图独立使用 image-provider，支持无参考图纯文生图，也支持上传参考图后按图编辑生成。
- 文生视频独立使用 Seedance，支持无参考图纯文生视频，也支持上传参考图后多模态参考生成。
- 管理员后台维护图片/视频剩余次数和模型服务配置。
- 不再创建本地生成会话，不再串联“参考图 -> 关键帧 -> 视频”的流程。

## MySQL 配置

后端使用 MySQL 持久化用户信息。默认连接本机 MySQL 的 `keyframe_video_studio` 库：

```properties
MYSQL_URL=jdbc:mysql://localhost:3306/keyframe_video_studio?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=root
MYSQL_PASSWORD=
```

完整建库建表 SQL 在 `sql/keyframe_video_studio.sql`。已有库升级可执行 `sql/admin_features_migration.sql`，该脚本会重建 `app_user` 和 `operation_log`，旧用户与旧生成日志会被清空，模型配置表保留。应用启动时也会按 `src/main/resources/schema.sql` 确认 `app_user`、`operation_log`、`model_config` 表存在。用户密码按当前产品要求明文保存到 `password` 字段。单表用户读写、模型配置和生成操作日志写入使用 MyBatis-Plus；后续如果出现关联查询，再通过 MyBatis Mapper XML 承载 SQL。

当前本地 MySQL 没有注册成 Windows 服务。如果重启后连接出现 `Can't connect to MySQL server on 'localhost:3306' (10061)`，先执行：

```powershell
.\scripts\start-mysql.ps1
```

如果本机 MySQL 账号不是 `root` 空密码，只需要改环境变量：

```powershell
$env:MYSQL_USERNAME="你的账号"
$env:MYSQL_PASSWORD="你的密码"
```

## 管理员配置

管理员固定为用户名 `sjh`，不再支持页面设置其他管理员。管理员登录后可在右上角进入：

- 用户设置：查看用户、配置图片/视频剩余次数。
- 使用日志：仅记录文生图、文生视频调用，可按用户、生成类型和状态筛选。
- 模型配置：维护图片服务和视频服务的服务地址、密钥、模型名称。

普通用户不再配置服务地址、密钥或模型。生成请求会由后端读取管理员保存的模型配置。

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
- `POST /api/admin/users`：管理员查看用户。
- `POST /api/admin/users/update`：管理员保存用户图片/视频剩余次数。
- `POST /api/admin/logs`：管理员筛选文生图、文生视频使用日志。
- `POST /api/admin/model-configs`：管理员查看模型配置。
- `POST /api/admin/model-configs/save`：管理员保存模型配置。

除 `POST /api/auth/login` 外，其余接口都需要在请求头携带 `Authorization: Bearer <accessToken>`。后端过滤器会解析 token 并把当前用户放入 ThreadLocal，业务接口不再从请求体接收当前登录用户 ID。

所有接口统一返回：

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```
