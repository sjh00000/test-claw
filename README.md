# Spring AI Alibaba Demo

这是一个基于 Spring Boot 3 + Spring AI Alibaba + DashScope 的最小可运行示例。

## 功能

- `GET /api/chat/health`：服务健康检查
- `GET /api/chat/simple?message=...`：快速聊天接口
- `POST /api/chat`：标准 JSON 聊天接口
- `GET /`：内置静态体验页

## 本地启动

### 1. 配置 API Key

PowerShell:

```powershell
$env:AI_DASHSCOPE_API_KEY="你的 DashScope Key"
```

可选环境变量：

```powershell
$env:DASHSCOPE_MODEL="qwen-plus"
$env:DASHSCOPE_TEMPERATURE="0.7"
$env:DASHSCOPE_TOP_P="0.8"
```

### 2. 启动应用

```powershell
mvn spring-boot:run
```

### 3. 访问 Demo

- 页面体验：http://localhost:8080/
- 健康检查：http://localhost:8080/api/chat/health

## 调用示例

### GET

```powershell
curl "http://localhost:8080/api/chat/simple?message=请介绍一下Spring%20AI%20Alibaba"
```

### POST

```powershell
curl -Method POST "http://localhost:8080/api/chat" `
  -ContentType "application/json" `
  -Body '{"message":"帮我用三点介绍 Spring AI Alibaba"}'
```

## 关键依赖

```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
    <version>1.1.0.0</version>
</dependency>
```

## 说明

- 默认模型为 `qwen-plus`
- `application.yml` 使用 `AI_DASHSCOPE_API_KEY` 读取 DashScope 凭证
- 若未配置 AI_DASHSCOPE_API_KEY，应用会在启动阶段直接失败
