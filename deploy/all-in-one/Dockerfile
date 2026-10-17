# LedgerPilot all-in-one 演示镜像：一个容器跑全栈（AI + 后端(H2 演示库) + nginx 托管前端）
# 面向「一条命令试用」场景；生产部署请用 deploy/docker-compose.yml（组件分容器）。
# 构建（在仓库根）：docker build -f deploy/all-in-one/Dockerfile -t ledgerpilot/all-in-one .
FROM node:22-alpine AS front
WORKDIR /src
COPY apps/front/package.json apps/front/pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile --registry=https://registry.npmmirror.com
COPY apps/front/ .
RUN pnpm build

FROM maven:3.8-eclipse-temurin-17 AS backend
WORKDIR /src
COPY apps/backend/ .
RUN mvn -q -pl ledgerpilot-admin -am package -DskipTests

FROM python:3.11-slim
RUN apt-get update && apt-get install -y --no-install-recommends nginx supervisor \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/ledgerpilot

# AI 服务（含生产依赖）
COPY apps/ai/requirements.txt ai/
RUN pip install --no-cache-dir -r ai/requirements.txt
COPY apps/ai/ ai/

# 后端 jar
COPY --from=backend /src/ledgerpilot-admin/target/ledgerpilot-admin.jar backend/app.jar

# 前端产物 + nginx 配置（同源反代 /api、/ai-api）
COPY --from=front /src/dist /usr/share/nginx/html
COPY deploy/all-in-one/nginx.conf /etc/nginx/conf.d/default.conf
COPY deploy/all-in-one/supervisord.conf /etc/supervisor/conf.d/supervisord.conf

EXPOSE 3000
ENV JWT_SECRET=change-me-demo-only-32-chars-minimum \
    LEDGERPILOT_API_TOKEN=all-in-one-demo
CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
