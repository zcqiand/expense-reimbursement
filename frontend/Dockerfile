# 前端 Dockerfile——两阶段构建。
#
# 阶段一（builder）：node 镜像跑 npm ci + vite build，产出 dist/ 静态文件
# 阶段二（runtime）：nginx 镜像 serve 静态文件，体积小、零 Node 运行时
#
# 这是第 15 章 CI/CD 章节会进一步讲解的"前端构建产物镜像化"实践。
#
# agent 家族部署形态（docs/families/agent.md，2026-10-06 起 5306/5406）：
# CI deploy job build & push → VPS 三容器中的 web 容器（host 127.0.0.1:5306）。

# ============= builder =============
FROM node:20-alpine AS builder

WORKDIR /build

# 硬约束：npm 依赖一律走 npmmirror（suite root CLAUDE.md §2）
RUN npm config set registry https://registry.npmmirror.com

# 先拷 package.json + lock 单独装依赖，复用 docker 层缓存
# （npm ci 按 lock 精确安装——lock 已入库，install 的宽解析不需要了）
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# 再拷源码并构建
COPY . .
RUN npm run build

# ============= runtime =============
FROM nginx:alpine

COPY --from=builder /build/dist /usr/share/nginx/html

# nginx 默认配置会把所有路由 fallback 到 index.html（SPA 需要）
# 这里写入最小 nginx.conf
RUN printf 'server {\n\
  listen 80;\n\
  root /usr/share/nginx/html;\n\
  location / { try_files $uri /index.html; }\n\
}\n' > /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
    CMD wget -q -O - http://localhost/ | grep -q "root" || exit 1
