#!/bin/sh
# Usage: expense-reimbursement.sh <DOCKER_USERNAME> <DOCKER_PASSWORD> [VERSION]
#
# 由 .github/workflows/ci.yml 的 deploy job 远程调用：
#   ssh deploy@vps -- cd /home/deploy/expense-reimbursement
#                    && sh expense-reimbursement.sh $DOCKER_USERNAME $DOCKER_PASSWORD $VERSION
#
# agent 家族部署形态（docs/families/agent.md，2026-10-06 分配 5306/5406），
# 本仓与家族 uvicorn 形态的差异——Spring Boot + PG，三容器：
#   db  容器（postgres:16-alpine，不暴 host 端口，docker network 内互联）
#   api 容器（fat jar 监听容器 8080）→ host 127.0.0.1:5406（单层映射）
#   web 容器（nginx 静态，容器内 :80）→ host 127.0.0.1:5306
#   vhost expense.xiangru.uk：/ → web 容器，/api/ → api 容器
#
# 与家族后端仓 deploy 脚本的差异：
#   - 三容器 + docker network + pgdata 卷（$BASE/data/pg）
#   - PG 密码首启自举随机生成落 env-file（umask 077，不入库不进镜像）
#   - 探活 /actuator/health（200）+ web 静态首页（200）
#   - ANTHROPIC_API_KEY 是启动硬依赖（AnthropicOpinionClient 构造器
#     fromEnv() 缺 key 应用起不来），值取自 secret LLM_API_KEY（MiniMax key）。
#     语义边界（如实标注）：anthropic-java SDK 无 base-url env、model 硬编码，
#     该 key 打不到 MiniMax——prod 的 /opinion 端点会 401，
#     核心流转（提交/审批/付款/OCR）不受影响。
#   - OCR：APP_OCR_ENGINE=tesseract（镜像已装 tesseract-ocr + chi_sim）
#
# 前置: deploy 用户需在 docker 组中(sudo usermod -aG docker deploy)；
#       sudoers 放 nginx + systemctl reload + !requiretty（家族既有）。

set -eu

USERNAME="${1:-}"
PASSWORD="${2:-}"
VERSION="${3:-latest}"
IMAGE_API="${USERNAME}/expense-reimbursement-api:${VERSION}"
IMAGE_WEB="${USERNAME}/expense-reimbursement-web:${VERSION}"
IMAGE_DB="postgres:16-alpine"
BASE="/home/deploy/expense-reimbursement"
API_PORT=5406
WEB_PORT=5306
CONTAINER_API="expense-reimbursement-api"
CONTAINER_WEB="expense-reimbursement-web"
CONTAINER_DB="expense-reimbursement-db"
NETWORK="expense-reimbursement-net"

NGINX_DOMAIN="${NGINX_DOMAIN:-expense.xiangru.uk}"
NGINX_CERT_BASENAME="${NGINX_CERT_BASENAME:-xiangru-uk}"

if [ -z "$USERNAME" ] || [ -z "$PASSWORD" ]; then
  echo "Usage: $0 <DOCKER_USERNAME> <DOCKER_PASSWORD> [VERSION]" >&2
  exit 2
fi

# ANTHROPIC_API_KEY 是 Spring 启动硬依赖（缺 key 应用起不来）——唯一 secret，
# 值 = secret LLM_API_KEY（MiniMax key，见头注释语义边界）。fail-fast（禁兜底）。
ENV_FILE="$BASE/expense-reimbursement.env"
have_real_key() {
  [ -f "$ENV_FILE" ] \
    && grep -q '^ANTHROPIC_API_KEY=sk-' "$ENV_FILE" \
    && ! grep -q '^ANTHROPIC_API_KEY=sk-xxxxxxxx$' "$ENV_FILE"
}
if [ -z "${LLM_API_KEY:-}" ] && ! have_real_key; then
  echo "ERROR: LLM_API_KEY secret required（映射 env-file ANTHROPIC_API_KEY，Spring 启动硬依赖；GitHub Secrets → ci.yml envs → 本脚本）" >&2
  exit 1
fi

gen_password() {
  tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 28
}

# env-file 自举（首启）：POSTGRES_* 给 db 容器，DATABASE_* 给 api（application.yml 占位符），
# PG 密码只生成一次落 env-file，两处同名值保持一致
if [ ! -f "$ENV_FILE" ]; then
  echo "→ bootstrapping $ENV_FILE (PG password auto-generated, persisted here only)"
  umask 077
  PG_PASSWORD="$(gen_password)"
  {
    printf 'POSTGRES_USER=expense\n'
    printf 'POSTGRES_PASSWORD=%s\n' "$PG_PASSWORD"
    printf 'POSTGRES_DB=expense\n'
    printf 'DATABASE_URL=jdbc:postgresql://%s:5432/expense\n' "$CONTAINER_DB"
    printf 'DATABASE_USER=expense\n'
    printf 'DATABASE_PASSWORD=%s\n' "$PG_PASSWORD"
    printf 'APP_OCR_ENGINE=tesseract\n'
    printf 'ANTHROPIC_API_KEY=%s\n' "$LLM_API_KEY"
  } > "$ENV_FILE"
  unset PG_PASSWORD
  chown deploy:deploy "$ENV_FILE" 2>/dev/null || true
  chmod 600 "$ENV_FILE"
fi

# 存量 env-file 补键（deploy-script-append-if-missing 教训：append 不覆盖已有行）
if [ -f "$ENV_FILE" ]; then
  append_if_missing() {
    key="$1"; val="$2"
    if ! grep -q "^${key}=" "$ENV_FILE"; then
      echo "→ append ${key} to existing $ENV_FILE"
      umask 077
      printf '%s=%s\n' "$key" "$val" >> "$ENV_FILE"
    fi
  }
  append_if_missing POSTGRES_USER 'expense'
  append_if_missing POSTGRES_DB 'expense'
  append_if_missing DATABASE_URL "jdbc:postgresql://${CONTAINER_DB}:5432/expense"
  append_if_missing DATABASE_USER 'expense'
  append_if_missing APP_OCR_ENGINE 'tesseract'
  # 密钥类：DATABASE_PASSWORD 与 POSTGRES_PASSWORD 必须一致（api 连库凭据），
  # 缺一个时两边同刷为对方值
  if ! grep -q '^POSTGRES_PASSWORD=..*' "$ENV_FILE" && grep -q '^DATABASE_PASSWORD=..*' "$ENV_FILE"; then
    append_if_missing POSTGRES_PASSWORD "$(grep '^DATABASE_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
  fi
  if ! grep -q '^DATABASE_PASSWORD=..*' "$ENV_FILE" && grep -q '^POSTGRES_PASSWORD=..*' "$ENV_FILE"; then
    append_if_missing DATABASE_PASSWORD "$(grep '^POSTGRES_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
  fi

  # 密钥类双模：缺/空/占位才覆盖（运维手工换的真 key 保留）
  upsert_if_placeholder() {
    key="$1"; val="$2"
    if ! grep -q "^${key}=..*" "$ENV_FILE" \
       || grep -q "^${key}=$" "$ENV_FILE" \
       || grep -q "^${key}=CHANGE_ME$" "$ENV_FILE" \
       || grep -q "^${key}=sk-xxxxxxxx$" "$ENV_FILE"; then
      echo "→ upsert ${key} to existing $ENV_FILE"
      sed -i "s#^${key}=.*#${key}=${val}#" "$ENV_FILE"
    fi
  }
  if [ -n "${LLM_API_KEY:-}" ]; then
    upsert_if_placeholder ANTHROPIC_API_KEY "$LLM_API_KEY"
  fi
fi

# 数据卷（PG data；db 容器重建不丢数据）
mkdir -p "$BASE/data/pg"

# docker network（幂等；db/api 同网互联，api 用容器名当 PG 主机名）
docker network inspect "$NETWORK" >/dev/null 2>&1 || docker network create "$NETWORK"

# nginx vhost 重渲染（每次 deploy 都跑；模板总从 main 拉最新——
# VPS 本地老模板会渲染出老端口全家族 502，2026-09-03 事故纪律）
NGINX_SITES_AVAILABLE="/etc/nginx/sites-available"
NGINX_SITES_ENABLED="/etc/nginx/sites-enabled"
NGINX_VHOST_FILE="${NGINX_SITES_AVAILABLE}/${NGINX_DOMAIN}"
NGINX_VHOST_LINK="${NGINX_SITES_ENABLED}/${NGINX_DOMAIN}"
NGINX_TEMPLATE="${BASE}/nginx-vps.conf.example"

echo "→ fetching nginx-vps.conf.example template (always fresh from main)"
curl -fsSL "https://raw.githubusercontent.com/zcqiand/expense-reimbursement/refs/heads/main/deploy/nginx-vps.conf.example" -o "${NGINX_TEMPLATE}"

# 渲染到临时文件 —— sed 顺序：cert 归一化规则必须排在 <domain> 通配之前
# （先替换 <domain> 会把 cert 路径占位符一并吃掉，2026-09-03 事故根因）。
TMP_VHOST="$(mktemp -t vpstpl.XXXXXX)"
sed \
  -e "s|/etc/nginx/ssl/<domain>\.crt|/etc/nginx/ssl/${NGINX_CERT_BASENAME}.cert|g" \
  -e "s|/etc/nginx/ssl/<domain>\.key|/etc/nginx/ssl/${NGINX_CERT_BASENAME}.key|g" \
  -e "s|<domain>|${NGINX_DOMAIN}|g" \
  "${NGINX_TEMPLATE}" > "${TMP_VHOST}"

if [ -e "${NGINX_VHOST_FILE}" ] && diff -q "${TMP_VHOST}" "${NGINX_VHOST_FILE}" >/dev/null 2>&1; then
  echo "→ nginx vhost ${NGINX_VHOST_FILE} unchanged, skip"
  rm -f "${TMP_VHOST}"
else
  echo "→ rendering nginx vhost ${NGINX_VHOST_FILE} (domain=${NGINX_DOMAIN} cert=${NGINX_CERT_BASENAME})"
  if [ -w "${NGINX_SITES_AVAILABLE}" ]; then
    cp "${TMP_VHOST}" "${NGINX_VHOST_FILE}"
  else
    sudo cp "${TMP_VHOST}" "${NGINX_VHOST_FILE}" \
      || { echo "ERROR: sudo cp ${NGINX_VHOST_FILE} failed"; rm -f "${TMP_VHOST}"; exit 1; }
  fi
  if [ -w "${NGINX_SITES_ENABLED}" ]; then
    ln -sf "${NGINX_VHOST_FILE}" "${NGINX_VHOST_LINK}"
  else
    sudo ln -sf "${NGINX_VHOST_FILE}" "${NGINX_VHOST_LINK}" \
      || { echo "ERROR: sudo ln ${NGINX_VHOST_LINK} failed"; rm -f "${TMP_VHOST}"; exit 1; }
  fi
  rm -f "${TMP_VHOST}"
  echo "→ nginx -t"
  sudo nginx -t
  echo "→ systemctl reload nginx"
  sudo systemctl reload nginx
  echo "✓ nginx reloaded"
fi

echo "→ image: $IMAGE_API / $IMAGE_WEB / $IMAGE_DB"
echo "→ docker login"
printf '%s' "$PASSWORD" | docker login -u "$USERNAME" --password-stdin

echo "→ docker pull (api + web + db)"
docker pull "$IMAGE_API"
docker pull "$IMAGE_WEB"
docker pull "$IMAGE_DB"

# 停启顺序：api 先停（断开连接）→ web → db；起时反向 db → 等 ready → api → web
echo "→ docker stop & rm $CONTAINER_API $CONTAINER_WEB $CONTAINER_DB"
docker stop "$CONTAINER_API" 2>/dev/null || true
docker rm "$CONTAINER_API" 2>/dev/null || true
docker stop "$CONTAINER_WEB" 2>/dev/null || true
docker rm "$CONTAINER_WEB" 2>/dev/null || true
docker stop "$CONTAINER_DB" 2>/dev/null || true
docker rm "$CONTAINER_DB" 2>/dev/null || true

echo "→ docker run db (无 host 端口，network 内互联，pgdata 卷 $BASE/data/pg)"
docker run -d \
  --name "$CONTAINER_DB" \
  --restart unless-stopped \
  --network "$NETWORK" \
  --env-file "$ENV_FILE" \
  -v "$BASE/data/pg:/var/lib/postgresql/data" \
  "$IMAGE_DB"

# 等 PG 就绪（首启要 initdb，最长 60s；pg_isready 在 db 容器内执行）
echo "→ waiting for postgres (pg_isready)"
i=0
while [ $i -lt 60 ]; do
  if docker exec "$CONTAINER_DB" pg_isready -U expense -d expense >/dev/null 2>&1; then
    echo "→ postgres ready after ${i}s"
    break
  fi
  if ! docker inspect --format='{{.State.Running}}' "$CONTAINER_DB" 2>/dev/null | grep -q true; then
    echo "→ db container not running, logs:"
    docker logs --tail 30 "$CONTAINER_DB"
    exit 1
  fi
  i=$((i+1))
  sleep 1
done
if [ $i -ge 60 ]; then
  echo "→ postgres 60s 未就绪, logs:"
  docker logs --tail 30 "$CONTAINER_DB"
  exit 1
fi

echo "→ docker run api (容器 8080 → host $API_PORT, network=$NETWORK)"
docker run -d \
  --name "$CONTAINER_API" \
  --restart unless-stopped \
  --network "$NETWORK" \
  -p "127.0.0.1:${API_PORT}:8080" \
  --env-file "$ENV_FILE" \
  "$IMAGE_API"

echo "→ docker run web (容器 :80 → host $WEB_PORT)"
docker run -d \
  --name "$CONTAINER_WEB" \
  --restart unless-stopped \
  -p "127.0.0.1:${WEB_PORT}:80" \
  "$IMAGE_WEB"

echo "→ docker image prune"
docker image prune -f

echo "→ docker ps"
docker ps --filter name="$CONTAINER_DB"
docker ps --filter name="$CONTAINER_API"
docker ps --filter name="$CONTAINER_WEB"

# 健康检查：/actuator/health 探 200（DOWN 会 503，200 即 UP）。
# 容器死亡提前终止循环，立刻报失败。
i=0
while [ $i -lt 120 ]; do
  if wget --tries=1 --timeout=3 -q "http://127.0.0.1:${API_PORT}/actuator/health" -O /dev/null 2>/dev/null; then
    echo "→ /actuator/health 200 (host 127.0.0.1:${API_PORT}) after ${i}s"
    break
  fi
  if ! docker inspect --format='{{.State.Running}}' "$CONTAINER_API" 2>/dev/null | grep -q true; then
    echo "→ api container not running, logs:"
    docker logs --tail 30 "$CONTAINER_API"
    exit 1
  fi
  i=$((i+1))
  sleep 1
done
if [ $i -ge 120 ]; then
  echo "→ /actuator/health 仍未 200（120s 上限）, logs:"
  docker logs --tail 30 "$CONTAINER_API"
  exit 1
fi

# web 静态探活
if wget --tries=1 --timeout=3 -q "http://127.0.0.1:${WEB_PORT}/" -O /dev/null 2>/dev/null; then
  echo "→ web / 200 (host 127.0.0.1:${WEB_PORT})"
else
  echo "→ web / 探活失败, logs:"
  docker logs --tail 30 "$CONTAINER_WEB"
  exit 1
fi

echo "→ deploy done at $(date -u)"
