# 多阶段：node 构建 → nginx 托管（含 /api、/ai-api 反代与 SPA 回退）
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile --registry=https://registry.npmmirror.com
COPY . .
RUN pnpm build

FROM nginx:1.27-alpine
COPY deploy/nginx.conf.template /etc/nginx/templates/default.conf.template
ENV BACKEND_UPSTREAM=http://backend:8080 AI_UPSTREAM=http://ai:8000 AI_SERVICE_TOKEN=
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
