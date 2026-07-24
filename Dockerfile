# 第一阶段使用 Maven 镜像编译后端，最终运行镜像里不保留 Maven 和源码。
FROM maven:3.9.9-eclipse-temurin-17 AS build

# Maven 构建上下文目录。
WORKDIR /workspace

# 先复制 pom.xml，Docker 可以复用依赖下载层，减少后续改源码时的构建时间。
COPY pom.xml .
# 复制后端源码参与打包；真实 application*.yml 已被 .dockerignore 排除，不会进入镜像。
COPY src ./src

# 构建 Spring Boot 可执行 jar；这里跳过测试，测试用例按项目规则通过 Postman 文件维护。
RUN mvn -B -DskipTests package

# 第二阶段只使用 JRE 运行 jar，镜像更小，也减少运行环境里的多余工具。
FROM eclipse-temurin:17-jre-alpine

# 后端应用运行目录。
WORKDIR /app

# 设置容器默认时区；JAVA_OPTS 预留给后续调整内存、GC 或诊断参数。
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

# 只把第一阶段生成的 jar 复制到运行镜像。
COPY --from=build /workspace/target/aigen-studio-0.0.1-SNAPSHOT.jar /app/app.jar

# 后端容器内部监听 8080，外部不直接暴露，由 web 容器反向代理。
EXPOSE 8080

# 支持 docker-compose 的 command 追加 Spring 启动参数，例如 --spring.profiles.active=prod。
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
