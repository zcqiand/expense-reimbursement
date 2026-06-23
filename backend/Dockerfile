# Spring Boot 后端 Dockerfile —— 多阶段构建。
#
# 阶段一（builder）：用 maven 镜像跑 mvn package，生成 fat jar
# 阶段二（runtime）：用 JRE 镜像，只拷 fat jar 与必要文件——镜像更小、攻击面更小
#
# 这是第 15 章 CI/CD 章节会进一步讲解的"多阶段构建"实践。

# ============= builder =============
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 先拷 pom.xml 单独 resolve 依赖，让 docker 层缓存能复用——只改源码不重新下载依赖
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# 再拷源码并编译
COPY src ./src
RUN mvn -B -q package -DskipTests

# ============= runtime =============
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 创建非 root 用户跑应用——安全基线
RUN addgroup -S app && adduser -S -G app app
USER app

COPY --from=builder /build/target/expense-reimbursement-backend.jar app.jar

EXPOSE 8080

# 由 actuator/health 做容器健康检查——docker-compose.yml 会用它
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -q -O - http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
