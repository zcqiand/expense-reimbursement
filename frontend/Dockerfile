# 前端 Dockerfile——两阶段构建。
#
# 阶段一（builder）：node 镜像跑 npm install + vite build，产出 dist/ 静态文件
# 阶段二（runtime）：nginx 镜像 serve 静态文件，体积小、零 Node 运行时
#
# 这是第 15 章 CI/CD 章节会进一步讲解的"前端构建产物镜像化"实践。

# ============= builder =============
FROM node:20-alpine AS builder

WORKDIR /build

# 先拷 package.json 单独装依赖，复用 docker 层缓存
COPY package.json package-lock.json* ./
RUN npm install

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
