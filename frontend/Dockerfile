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
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Непривилегированный порт (8080) и явный chown позволяют работать под встроенным
# пользователем nginx вместо root.
RUN touch /var/run/nginx.pid && \
    chown -R nginx:nginx /var/cache/nginx /var/run/nginx.pid /usr/share/nginx/html /etc/nginx/conf.d

USER nginx
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1
