# syntax=docker/dockerfile:1

# Multi-stage: собираем статику Vite-приложения, затем раздаём её отдельным nginx-контейнером.
# Build context — frontend/ (design/tokens.json не нужен: сгенерированный src/styles/tokens.css
# уже закоммичен в репозиторий, prebuild-скрипт просто ничего не сделает без него).

FROM node:22-alpine AS build
WORKDIR /app

# Отдельный слой + npm-кэш: пока package*.json не меняются, "npm ci" переиспользует кэш.
COPY package.json package-lock.json* ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

COPY . .
RUN npm run build

FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
# Шаблон, не готовый конфиг: образ nginx:alpine сам прогоняет *.template через envsubst в
# /etc/nginx/conf.d/*.conf при каждом старте контейнера (docker-entrypoint.d), подставляя PORT
# и BACKEND_URL из окружения — см. комментарий в файле.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

# Дефолты для локального docker compose (имя сервиса backend в той же docker-сети, порт 8080).
# На Render оба задаются платформой/render.yaml — см. README "Деплой".
ENV PORT=8080
ENV BACKEND_URL=http://backend:8080

# Непривилегированный порт и явный chown позволяют работать под встроенным пользователем nginx
# вместо root; conf.d должен быть доступен на запись — туда пишет envsubst при каждом старте.
RUN touch /var/run/nginx.pid && \
    chown -R nginx:nginx /var/cache/nginx /var/run/nginx.pid /usr/share/nginx/html /etc/nginx/conf.d

USER nginx
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD sh -c "wget --no-verbose --tries=1 --spider http://localhost:${PORT}/ || exit 1"
