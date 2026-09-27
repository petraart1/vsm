# Контракт data/domain-слоя для UI

Публичные методы репозиториев (`ru.vsm.mobile.domain.repository.*`), доступные через
`AppContainer`. Каждый репозиторий имеет реальную реализацию (`data/repository/*Impl.kt`, ходит в
backend) и фейковую (`domain/fake/Fake*.kt`, in-memory — выбирается `AppContainer(useFakes = true)`).

Идентификация игрока — `playerId: String` (UUID), передаётся вызывающим кодом явно в каждый метод,
который его требует (см. `PlayerRepository.getOrCreatePlayerId()` — анонимный id устройства).
Авторизованная сессия (`AuthRepository`) добавляет `Authorization: Bearer` поверх — backend сам
приоритезирует токен над `playerId`, вызывающему коду ничего дополнительно передавать не нужно.

Все методы возвращают `Result<T>`; ошибки — `ru.vsm.mobile.domain.error.DomainError` (см. файл).

## PlayerRepository

- `getOrCreatePlayerId(): String` — анонимный id устройства.

## AuthRepository

- `currentUser: Flow<AuthUser?>`, `isAuthorized: Flow<Boolean>`
- `register(login, email, password, displayName?): Result<AuthUser>`
- `login(login, password): Result<AuthUser>`
- `logout()`
- `esiaAuthorizeUrl(redirectUri): String` — URL демо-входа через Госуслуги (открыть в веб-вьюхе)
- `loginWithEsia(code): Result<AuthUser>` — обмен кода из редиректа `esiaAuthorizeUrl` на сессию

Модель: `AuthUser(id, login, displayName?, role: UserRole)`.

## ScenarioRepository

- `list(block?): Result<List<ScenarioSummary>>`
- `start(scenarioId, playerId): Result<ScenarioProgress>`
- `getProgress(progressId, playerId): Result<ScenarioProgress>`
- `choose(progressId, choiceId, playerId): Result<ChoiceResult>`
- `timeout(progressId, playerId): Result<ChoiceResult>`
- `liveEvents(progressId, playerId, token?): Flow<LiveProgressEvent>` — WS таймер/шкалы
- `chooseOrQueue(progressId, choiceId, playerId): Result<ChoiceOutcome>` — офлайн-устойчивый `choose`
- `timeoutOrQueue(progressId, playerId): Result<ChoiceOutcome>`
- `pendingOfflineCount(): Flow<Int>`

Модели: `ScenarioSummary`, `ScenarioProgress`, `ChoiceResult`, `ChoiceOutcome`, `LiveProgressEvent`.

## ExamRepository

- `start(playerId, carClass?, size?): Result<Exam>`
- `startCurrent(examId, playerId): Result<ScenarioProgress>` — далее обычный `ScenarioRepository`
- `get(examId, playerId): Result<Exam>`

Модель: `Exam` (включает `CarClass`, статус, оценку).

## FeedbackRepository

- `getDebrief(userProgressId, playerId): Result<Debrief>` — разбор одного прохождения
- `getCompetencies(playerId): Result<CompetencyAnalytics>` — агрегат по всем прохождениям

Модели: `Debrief`, `DebriefStep`, `KeyMoment`, `CompetencyAnalytics`, `BlockCompetencyStats`,
`RoleStepCompliance`, `NormViolation`, `ScenarioRecommendation`.

## GamificationRepository

- `getProfile(playerId): Result<Profile>` — счёт, уровень (`level`/`levelTitle`/`levelProgress`/
  `pointsToNextLevel`), прогресс по блокам, недавние ачивки
- `getLeaderboard(limit = 20, playerId? = null): Result<Leaderboard>` — топ + `me` (карточка "ваше
  место", `null` если игрок неопознан или ещё не участвует)
- `getTeamLeaderboard(): Result<List<TeamLeaderboardEntry>>` — публичный, без playerId
- `getAchievements(playerId? = null): Result<List<Achievement>>` — без playerId все `earned = false`
- `getCustomAwards(playerId? = null): Result<List<CustomAward>>` — награды администратора
- `getChallenges(playerId? = null): Result<List<Challenge>>` — челленджи месяца с прогрессом
- `putShowcase(playerId, finish, items: List<ShowcaseItem>): Result<Showcase>` — замена своей витрины (≤6 наград)
- `getShowcase(publicId): Result<Showcase>` — публичная витрина коллеги (`publicId` из `LeaderboardEntry`)
- `getTeams(): Result<List<Team>>`
- `joinTeam(teamId, playerId): Result<Team>` — повторный вызов с другим id = смена команды
- `getNotifications(playerId, unreadOnly = false): Result<List<Notification>>`
- `markRead(notificationId, playerId): Result<Notification>`
- `markAllRead(playerId): Result<Int>`

Модели:
- `Profile(playerId, displayName, totalScore, scenariosCompleted, totalScenariosAvailable, blockProgress, recentAchievements, leaderboardRank?, level, levelTitle, levelProgress, pointsToNextLevel?)`
- `LeaderboardEntry(rank, publicId, displayName, totalScore, scenariosCompleted, me, level, levelTitle)` — **без** реального `playerId`, только `publicId`
- `Leaderboard(top, me?)`
- `TeamLeaderboardEntry(rank, teamId, code, name, depot, memberCount, totalScore, averageScore, totalPlaythroughs, averageSafety)`
- `Achievement(code, title, description, category, earned, earnedAt?)`
- `CustomAward(id, code, title, description, shape, glyph?, verifiedOnly, earned, earnedAt?)`
- `Challenge(code, title, description, goalType, targetBlock?, targetCount, safetyThreshold?, rewardPoints, startsAt, endsAt, current, completed, completedAt?)`
- `Team(id, code, name, depot, memberCount)`
- `ShowcaseItem(id, title, shape, glyph?, text?)`, `Showcase(finish, items)`
- `Notification(id, type: NotificationType, title, body, createdAt, readAt?)` + `unread: Boolean`
- `NotificationType` enum: `ACHIEVEMENT_UNLOCKED, NEW_PERSONAL_BEST, LEADERBOARD_RANK_UP,
  RECOMMENDED_SCENARIO, CHALLENGE_COMPLETED, TEAM_RANK_UP, EXAM_COMPLETED, NEW_SCENARIO,
  NEW_CHALLENGE, POINTS_EXPIRING, POINTS_EXPIRED, LEVEL_UP, UNKNOWN` — `UNKNOWN` это локальный
  запасной вариант для типа, ещё не известного клиенту (безопасный fallback, не 3-е состояние на backend)

## Прочие enum'ы (`domain/model/Enums.kt`)

`NodeType`, `ScenarioOutcome`, `ProgressStatus`, `RoleStep`, `RecommendationReason`, `UserRole`,
`CarClass`, `NotificationType`.
