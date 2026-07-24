# 第一阶段使用 Node 构建 Vue 静态资源，最终 Nginx 镜像里不保留 node_modules。
FROM node:22-alpine AS build

# 前端构建目录。
WORKDIR /web

# 先复制依赖声明，利用 Docker 缓存减少重复 npm ci 的次数。
COPY package*.json ./
# 按 lock 文件安装依赖，保证服务器构建结果和本地依赖版本一致。
RUN npm ci

# 复制前端源码并执行正式构建，生成 dist。
COPY . .
RUN npm run build

# 第二阶段使用 Nginx 托管 dist，并承担 /api 反向代理和限流。
FROM nginx:1.27-alpine

# 覆盖默认站点配置，启用 SPA 路由、接口代理、限流和访问日志。
COPY nginx.conf /etc/nginx/conf.d/default.conf
# 只把构建产物复制到 Nginx 静态目录，不把源码放进运行容器。
COPY --from=build /web/dist /usr/share/nginx/html

# Web 容器内部监听 80/443，由 docker-compose 映射到宿主机 HTTP/HTTPS 端口。
EXPOSE 80
EXPOSE 443
