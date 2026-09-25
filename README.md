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

## Архитектура

Система построена как модульный монолит на Spring Boot 4.1 с разделением по доменам: сценарный движок (scenario), геймификация (gamification) и обучающая обратная связь (feedback). Сценарии — графы узлов и выборов, сохранённые в PostgreSQL; данные загружаются идемпотентно из JSON-seed при запуске приложения.

### Компонентная архитектура

```mermaid
graph LR
    Browser["🌐 Браузер<br/>(React 18)"]
    Backend["🚀 Spring Boot 4.1<br/>Java 25"]
    Scenario["Scenario<br/>(граф, API)"]
    Gamification["Gamification<br/>(очки, ачивки)"]
    Feedback["Feedback<br/>(разбор)"]
    Config["Config"]
    DB["🐘 PostgreSQL 17"]
    Seed["📄 JSON seed<br/>(scenarios/)"]
    Event["📡 ScenarioCompletedEvent<br/>(in-process event)"]
    
    Browser -->|REST, X-Player-Id| Backend
    Backend --> Config
    Backend --> Scenario
    Backend --> Gamification
    Backend --> Feedback
    Scenario -->|создаёт граф| DB
    Scenario -->|запускает| Seed
    Scenario -->|публикует| Event
    Event -->|слушает| Gamification
    Event -->|слушает| Feedback
    Gamification -->|читает| DB
    Feedback -->|читает| DB
    Backend -->|Swagger UI| Browser
```

**Описание**: фронтенд отправляет запросы на REST API Backend через общий origin; каждый запрос включает заголовок `X-Player-Id` для простой идентификации игрока (без Spring Security). Backend раздаёт и фронтенд-статику (React компоненты в браузер) и API. Сценарный движок управляет графом узлов и выборов, загружая их из JSON-файлов при старте; при завершении сценария публикует доменное событие `ScenarioCompletedEvent` в памяти (in-process), на которое отписаны gamification и feedback. Gamification начисляет очки и ачивки, feedback строит разбор решений по истории выборов. Все данные в PostgreSQL, миграции через Liquibase.

### Сценарий прохождения

```mermaid
sequenceDiagram
    Player->>API: GET /scenarios<br/>(список)
    API-->>Player: ScenarioSummaryResponse[]
    Player->>API: POST /scenarios/{id}/progress<br/>(старт)
    API->>DB: create user_progress
    API-->>Player: ProgressStateResponse<br/>(узел, выборы, таймер)
    Player->>API: POST /progress/{id}/choices/{choiceId}<br/>(выбор)
    API->>DB: check node_deadline_at
    alt Таймер истёк
        API->>DB: apply defaultChoice
        API-->>Player: ChoiceAppliedResponse<br/>(wasTimeout=true)
    else Таймер активен
        API->>DB: apply choiceId
        API-->>Player: ChoiceAppliedResponse<br/>(loyaltyDelta, safetyDelta)
    end
    API->>DB: insert scenario_choice_history
    Player->>API: POST /progress/{id}/choices/{...} (повтор)
    API-->>Player: nextNode
    alt Терминальный узел
        API->>DB: user_progress.status=COMPLETED
        API->>Event: publish ScenarioCompletedEvent
        Event->>Gamification: добавить очки, ачивки
        Event->>Feedback: готово к разбору
    end
    Player->>API: GET /feedback/debrief/{id}<br/>(разбор)
    API-->>Player: DebriefResponse<br/>(таймлайн, ролевые шаги)
    Player->>API: GET /gamification/profile/{playerId}
    API-->>Player: ProfileResponse<br/>(счёт, блоки компетенций)
```

**Детали**: игрок открывает список сценариев, выбирает один и начинает его (отправляя `X-Player-Id` в заголовке). Сервер создаёт запись `user_progress` и возвращает первый узел с доступными выборами. На каждый выбор сервер проверяет, не истёк ли сервисный дедлайн (`node_deadline_at`); если истёк — применяется `defaultChoice` узла (поведение при таймауте). При терминальном узле прохождение переходит в `COMPLETED`, публикуется событие, и gamification/feedback получают уведомление. Затем игрок может посмотреть разбор решений (какие шаги ролевой модели соблюдены/пропущены, какие были лучшие альтернативы) и обновить профиль.

## API и пользовательский сценарий

### Таблица эндпоинтов

| Метод | Путь | Назначение | Параметры |
|---|---|---|---|
| **Сценарии** | | | |
| `GET` | `/api/scenarios` | Список всех сценариев | `block?` (фильтр по блоку) |
| `GET` | `/api/scenarios/{scenarioId}` | Один сценарий | — |
| **Прохождение** | | | |
| `POST` | `/api/scenarios/{scenarioId}/progress` | Начать сценарий | Header: `X-Player-Id` |
| `GET` | `/api/scenarios/progress/{progressId}` | Текущий узел | Header: `X-Player-Id` |
| `POST` | `/api/scenarios/progress/{progressId}/choices/{choiceId}` | Выбрать вариант | Header: `X-Player-Id` |
| `POST` | `/api/scenarios/progress/{progressId}/timeout` | Применить таймаут | Header: `X-Player-Id` |
| **Разбор** | | | |
| `GET` | `/api/feedback/debrief/{userProgressId}` | Разбор прохождения | — |
| **Профиль игрока** | | | |
| `GET` | `/api/gamification/profile/{playerId}` | Профиль (счёт, ачивки) | — |
| `GET` | `/api/gamification/leaderboard` | Лидерборд | `limit=20`, `playerId?` |
| `GET` | `/api/gamification/achievements` | Каталог ачивок | `playerId?` |

### Пример прохождения (curl)

Генерируем UUID для игрока (или используем существующий):

```bash
PLAYER_ID="550e8400-e29b-41d4-a716-446655440000"
API_URL="http://localhost:8080"

# 1. Список сценариев
curl -s "$API_URL/api/scenarios" | jq '.[0]'

# Запомните scenarioId первого сценария, например:
SCENARIO_ID="550e8400-e29b-41d4-a716-446655440001"

# 2. Начать сценарий
PROGRESS=$(curl -s -X POST "$API_URL/api/scenarios/$SCENARIO_ID/progress" \
  -H "X-Player-Id: $PLAYER_ID" | jq .)
PROGRESS_ID=$(echo "$PROGRESS" | jq -r '.progressId')

# 3. Получить текущий узел (с выборами)
curl -s "$API_URL/api/scenarios/progress/$PROGRESS_ID" \
  -H "X-Player-Id: $PLAYER_ID" | jq '.currentNode'

# 4. Сделать выбор (используйте choiceId из currentNode.choices)
CHOICE_ID="550e8400-e29b-41d4-a716-446655440002"
CHOICE=$(curl -s -X POST \
  "$API_URL/api/scenarios/progress/$PROGRESS_ID/choices/$CHOICE_ID" \
  -H "X-Player-Id: $PLAYER_ID" | jq .)

# 5. Повторять выборы, пока не достигнете терминального узла (status=COMPLETED)

# 6. Разбор решений
curl -s "$API_URL/api/feedback/debrief/$PROGRESS_ID" | jq '.timeline'

# 7. Профиль игрока
curl -s "$API_URL/api/gamification/profile/$PLAYER_ID" | jq '.totalScore'
```

**Swagger UI**: для интерактивного изучения API откройте `http://localhost:8080/swagger-ui.html`

## Ограничения и план развития

### Текущие ограничения в MVP

- **Аутентификация**: отсутствует. Идентификация игрока — простой заголовок `X-Player-Id` (UUID). Применимо только для дружественной сессии в одном браузере; при публичном доступе нужна реальная аутентификация и авторизация через Spring Security.
- **Таймер**: синхронизирован через серверный дедлайн (`node_deadline_at` в REST-ответе), а не через WebSocket-пуш. Клиент пересчитывает остаток каждую секунду от `Date.now()`. Это работает, но требует синхронизации часов браузер↔сервер; при большом расхождении может привести к срыву таймаута.
- **Сложность сценариев**: реализованы 8 флагманских сценариев (3–4 уровня ветвления) и 26 простых (1 развилка, 3 варианта). 17 сценариев из 51 ещё не добавлены.
- **Уведомления**: отсутствуют. Игрок не получает напоминаний о новых сценариях или персональных челленджах.
- **Аналитика компетенций**: нет глубокого анализа пробелов в знаниях. Профиль показывает raw очки по блокам, но не выделяет, какие типы ситуаций проходят плохо (например, "медицина: успешность 60%").
- **Мобильное приложение**: разрабатывается отдельно (Android, Kotlin + Compose). Web и мобильное приложение используют один backend.

### План развития

**Фаза 1 (завершение MVP)**:
- Добавить оставшиеся 17 сценариев (простые, по одной развилке на блок).
- Расширить систему ачивок (полнота ролевых шагов, включение МГН в сложность).

**Фаза 2 (UX и интеграция)**:
- Заменить REST-синхронизацию таймера на WebSocket (Reactor, `Flux`/`Sinks`).
- Добавить Spring Security с простой аутентификацией (например, OAuth2 против ВСМ-системы, если будет).
- Реализовать систему уведомлений (в-приложение и/или email, по выбору).

**Фаза 3 (аналитика и редактирование)**:
- Добавить REST-эндпоинт аналитики: "сценарии по проблемным компетенциям".
- Встроить редактор сценариев (UI для изменения графа узлов/выборов и рассеивания на лету).
- Синхронизировать мобильное приложение с backend через тот же REST API.

**Фаза 4 (масштабирование)**:
- Перейти на Kafka для асинхронных событий (при нескольких экземплярах backend).
- Добавить Redis-кэш для списка сценариев и лидерборда.
- Реализовать интеграцию с системой управления обучением ВСМ (LMS), если потребуется.
