# Spring Boot 后端 Dockerfile —— 多阶段构建。
#
# 阶段一（builder）：用 maven 镜像跑 mvn package，生成 fat jar
# 阶段二（runtime）：用 JRE 镜像，只拷 fat jar 与必要文件——镜像更小、攻击面更小
#
# 这是第 15 章 CI/CD 章节会进一步讲解的"多阶段构建"实践。
#
# agent 家族部署形态（docs/families/agent.md，2026-10-06 起 5306/5406）：
# CI deploy job build & push（latest + tag 双份）→ VPS deploy/expense-reimbursement.sh
# 三容器（pg + api + web）。application.yml server.port 硬编码 8080 非 env 可配，
# host 侧单层映射 127.0.0.1:5406 → 容器 8080。

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

# OCR：app.ocr.engine=tesseract 时 TesseractOcrClient 调容器内 tesseract CLI
# （chi_sim+eng 双语；识别失败降级 FAILED 记录，不阻断报销流转）。
# alpine 的 tesseract 语言数据是独立包，eng/chi_sim 都要显式装。
RUN apk add --no-cache tesseract-ocr tesseract-ocr-eng tesseract-ocr-chi-sim

# 创建非 root 用户跑应用——安全基线
RUN addgroup -S app && adduser -S -G app app
USER app

WORKDIR /app

COPY --from=builder /build/target/expense-reimbursement-backend.jar app.jar

EXPOSE 8080

# 由 actuator/health 做容器健康检查——docker-compose.yml 与 deploy 脚本 host 侧探活双保险
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -q -O - http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
