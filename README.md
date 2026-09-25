# ВСМ — тренажёр проводника

Геймифицированный тренажёр для проводников высокоскоростного поезда (Москва — Санкт-Петербург): нелинейные
сценарии общения с пассажирами с таймерами, две шкалы («лояльность пассажира» / «рейтинг безопасности»),
ачивки, лидерборд, обучающий разбор решений.

- `backend/` — Spring Boot монолит (Java 25, REST на виртуальных потоках).
- `frontend/` — React 18 без сборки (UMD + Babel standalone с CDN), демо-статика раздаётся тем же backend'ом.
- `mobile/` — Android-приложение, разрабатывается отдельно.

## Запуск

### Вариант 1: всё через Docker Compose (backend + Postgres + фронт на одном origin)

```bash
docker compose -f compose.yaml up --build
```

(на машине с `DOCKER_DEFAULT_PLATFORM` в окружении — обычно `env -u DOCKER_DEFAULT_PLATFORM docker compose up --build`).

Поднимает Postgres 17 и backend-контейнер; backend сам раздаёт статику `frontend/` — открывать
`http://localhost:8080/`. Отдельно поднимать фронт не нужно.

### Вариант 2: локально, backend через `bootRun`

```bash
docker compose -f compose.yaml up -d postgres   # только Postgres
cd backend && ./gradlew bootRun                 # backend поднимет и раздаст frontend/ статикой
```

Открывать `http://localhost:8080/` — то же, что и в варианте 1, без пересборки Docker-образа при
изменениях бэкенда.

### Вариант 3: разработка фронта с live-reload (dev-сервер на 3000)

Backend поднят одним из способов выше (порт 8080). Отдельно — любой статический dev-сервер для `frontend/`
на порту 3000, например:

```bash
cd frontend && python3 -m http.server 3000
```

Запросы к `/api/**` с `http://localhost:3000` разрешены через CORS (`backend/src/main/java/ru/vsm/backend/config/WebConfig.java`,
свойство `app.cors.allowed-origins` в `backend/src/main/resources/application.properties`).

### Карта портов

| Сервис | Порт по умолчанию | Как переопределить |
|---|---|---|
| backend (REST/Swagger) | `8080` | `SERVER_PORT` (внутри контейнера/JVM) / `BACKEND_HOST_PORT` (хост-порт в `compose.yaml`) |
| frontend dev-сервер | `3000` | зависит от инструмента (например, `http.server <порт>`); при смене — добавить origin в `app.cors.allowed-origins` |
| Postgres | `5432` | `POSTGRES_HOST_PORT` (хост-порт в `compose.yaml`) |

Все три — только хост-порты (внутри Docker-сети контейнеры всегда слушают штатные 8080/5432); нужны,
только если порт уже занят на хост-машине.

### Адреса

- Приложение / фронт: `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- REST API: `http://localhost:8080/api/**` (см. Swagger UI для контрактов)
- Actuator: `http://localhost:8080/actuator/**`

### Тесты

```bash
cd backend && ./gradlew test
```

Использует Testcontainers (Postgres) — нужен доступный Docker.
