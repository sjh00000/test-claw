# Text Generation Studio

Vue3 + Spring Boot 的文生图 / 文生视频工作台。

## 功能范围

- 用户登录，首次登录自动创建用户；登录成功返回 24 小时 `accessToken`，用户信息持久化到 MySQL。
- 文生图独立使用 image-provider，支持无参考图纯文生图，也支持上传参考图后按图编辑生成。
- 文生视频独立使用 Seedance，支持无参考图纯文生视频，也支持上传参考图后多模态参考生成。
- 管理员后台维护图片/视频剩余次数和模型服务配置。
- 不再创建本地生成会话，不再串联“参考图 -> 关键帧 -> 视频”的流程。

## 应用配置

后端真实配置拆成公共、dev、prod 三份，并都已加入 `.gitignore`，不要提交到 Git。仓库只保留对应的 `.example.yml` 示例；首次拉取项目后复制一份并填写真实值：

```powershell
Copy-Item src/main/resources/application.example.yml src/main/resources/application.yml
Copy-Item src/main/resources/application-dev.example.yml src/main/resources/application-dev.yml
Copy-Item src/main/resources/application-prod.example.yml src/main/resources/application-prod.yml
```

`application.yml` 放公共配置和默认激活 `dev`；`application-dev.yml` 放本机 MySQL/Redis 连接；`application-prod.yml` 放 Docker/服务器 MySQL/Redis 连接。需要填写的真实值包括 MySQL 账号密码、JWT 签名密钥、OSS AccessKey 和 Bucket 信息。图片/视频厂商的 `base-url`、`api-key`、`model` 不再写入 yml，由管理员在后台“模型配置”页面维护并落库。

应用启动时会按 `src/main/resources/schema.sql` 确认 `app_user`、`operation_log`、`generation_task`、`model_config` 表存在。用户密码按当前产品要求明文保存到 `password` 字段。单表用户读写、模型配置和生成操作日志写入使用 MyBatis-Plus；后续如果出现关联查询，再通过 MyBatis Mapper XML 承载 SQL。

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

## Docker 部署

Docker 部署使用 `docker-compose.yml` 编排 `web`、`app`、`mysql`、`redis` 四个服务。后端容器会只读挂载 `src/main/resources/application.yml` 和 `src/main/resources/application-prod.yml` 到 `/app/config/`，并启用 `prod` profile；应用密钥仍然只写在 yml 文件里。

`.env` 只保留 Docker 初始化 MySQL 容器和端口映射所需的少量参数，不再保存 JWT、OSS 或厂商模型密钥；模板见 `.env.example`。

```bash
cp .env.example .env
cp src/main/resources/application.example.yml src/main/resources/application.yml
cp src/main/resources/application-prod.example.yml src/main/resources/application-prod.yml
docker compose up -d --build
```

容器职责：

- `web`：Nginx 静态前端和 `/api` 反向代理，包含生成接口限流。
- `app`：Spring Boot 后端，只在 Docker 网络内暴露 `8080`。
- `mysql`：持久化业务数据，数据保存在 Docker volume。
- `redis`：生成任务提交防重分布式锁，数据保存在 Docker volume。

常用命令：

```bash
docker compose ps
docker compose logs -f app
docker compose restart app
```

当前部署流转：

- 本地准备代码和真实配置文件，真实 `application.yml`、`application-dev.yml`、`application-prod.yml`、`.env` 不提交 Git。
- 上传代码到服务器部署目录，服务器部署目录也要有自己的 `src/main/resources/application.yml`、`src/main/resources/application-prod.yml` 和 `.env`。
- 在服务器执行镜像构建，后端镜像由 `Dockerfile` 构建，前端镜像由 `frontend/Dockerfile` 构建。
- 运行容器时，`app` 容器读取挂载进去的 `/app/config/application.yml` 和 `/app/config/application-prod.yml`，`web` 容器暴露 80 并反向代理 `/api` 到后端。
- 后续只改 yml 配置时，通常重启后端容器即可；改代码才需要重新构建镜像。

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
