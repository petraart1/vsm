# Multi-stage: собираем статику Vite-приложения, затем раздаём её отдельным nginx-контейнером.
# Build context — frontend/ (design/tokens.json не нужен: сгенерированный src/styles/tokens.css
# уже закоммичен в репозиторий, prebuild-скрипт просто ничего не сделает без него).

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
