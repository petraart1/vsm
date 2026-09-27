/**
 * Единственный слой работы с данными. Вся остальная кодовая база (экраны/компоненты)
 * обращается только сюда, никогда напрямую к fetch/localStorage/каталогу датасета.
 *
 * USE_MOCKS переключает между двумя параллельными реализациями одного и того же публичного
 * интерфейса (listScenarios/startScenario/choose/timeout/getDebrief/getProfile/getLeaderboard/
 * getAchievements) — мок ниже по файлу и реальные fetch-вызовы к backend.
 * По умолчанию false: backend готов, мок оставлен как аварийный переключатель на случай,
 * если backend недоступен во время демо.
 */

import { recordActivity, blockTitle } from "./progress.js";

export const USE_MOCKS = false;
const NETWORK_DELAY_MS = 220;

// =======================================================================
// Конфигурация: база API + playerId.
// =======================================================================

const API_BASE_STORAGE_KEY = "vsm.apiBase.v1";
const PLAYER_ID_STORAGE_KEY = "vsm.playerId.v1";

/**
 * База API: по умолчанию тот же origin, что и у фронта — в dev Vite проксирует /api на backend
 * (см. vite.config.js), в проде фронт и backend разнесены по разным origin, но фронт всё равно
 * бьёт в /api на своём собственном origin через nginx-прокси (см. frontend/nginx.conf), поэтому
 * относительный путь работает и там, и там без дополнительной настройки. Явный оverride —
 * через ?apiBase=http://host:port в URL, сохраняется в localStorage (например, если backend
 * поднят на нестандартном порту при демо).
 *
 * Когда фронт и backend не могут жить на одном origin и без прокси между ними (статический
 * хостинг вроде GitHub Pages — там нет nginx, который проксирует /api), базовый адрес backend
 * зашивается в сборку через VITE_API_BASE (см. .github/workflows/deploy-pages.yaml) и
 * используется как последний fallback, если ни ?apiBase, ни localStorage ничего не задали.
 */
function resolveApiBase() {
  try {
    const fromQuery = new URLSearchParams(window.location.search || "").get("apiBase");
    if (fromQuery) {
      try { localStorage.setItem(API_BASE_STORAGE_KEY, fromQuery); } catch (e) { /* ignore */ }
      return fromQuery.replace(/\/$/, "");
    }
  } catch (e) { /* ignore */ }
  try {
    const stored = localStorage.getItem(API_BASE_STORAGE_KEY);
    if (stored) return stored.replace(/\/$/, "");
  } catch (e) { /* ignore */ }
  const fromBuild = (typeof import.meta !== "undefined" && import.meta.env && import.meta.env.VITE_API_BASE) || "";
  return fromBuild.replace(/\/$/, "");
}

/** Базовый адрес backend (пустая строка = тот же origin, что и у фронта) — используется также
 * в ws.js, чтобы живой WebSocket-канал подключался к тому же backend, когда фронт раздаётся
 * отдельно от него (GitHub Pages). */
export const API_BASE = resolveApiBase();

function generateUuidV4() {
  if (window.crypto && typeof window.crypto.randomUUID === "function") {
    return window.crypto.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/** Один UUID на браузер, хранится в localStorage; используется как X-Player-Id и как
 * playerId в gamification-эндпоинтах — единое пространство идентификаторов на клиенте. */
export function getPlayerId() {
  const account = getAccount();
  if (account && account.id) return account.id;
  let id = null;
  try { id = localStorage.getItem(PLAYER_ID_STORAGE_KEY); } catch (e) { /* приватный режим */ }
  if (!id) {
    id = generateUuidV4();
    try { localStorage.setItem(PLAYER_ID_STORAGE_KEY, id); } catch (e) { /* ignore */ }
  }
  return id;
}

/** Тонкая обёртка над fetch: базовый URL, X-Player-Id (когда нужен), парсинг JSON/ошибок
 * в едином формате {error, message} (см. scenario/ScenarioExceptionHandler). */
function apiFetch(path, options = {}) {
  const headers = { Accept: "application/json" };
  // Личность игрока нужна почти всем эндпоинтам: профиль, ачивки, уведомления, челленджи и
  // разбор проверяют владельца (403 без заголовка), лидерборд по нему помечает строку "me".
  // Поэтому заголовок уходит всегда, кроме явного publicCall.
  if (!options.publicCall) headers["X-Player-Id"] = getPlayerId();
  const token = getToken();
  if (token && !options.noAuth) headers.Authorization = `Bearer ${token}`;
  if (options.body) headers["Content-Type"] = options.contentType || "application/json";
  return fetch(API_BASE + path, { method: options.method || "GET", headers, body: options.body }).then((res) => {
    if (res.status === 204) return null;
    return res.text().then((text) => {
      let body = null;
      if (text) {
        try { body = JSON.parse(text); } catch (e) { body = null; }
      }
      if (!res.ok) {
        const fallback = res.status >= 500 ? "Сервис временно недоступен." : "Не удалось выполнить запрос.";
        const err = new Error((body && body.message) || fallback);
        err.code = body && body.error;
        err.status = res.status;
        throw err;
      }
      return body;
    });
  }, () => {
    // Сеть недоступна, CORS или сервис не отвечает — исходное сообщение fetch техническое и на
    // английском (например «Failed to fetch»), пользователю такое не показываем.
    const err = new Error("Не удалось загрузить данные. Проверьте подключение к интернету и попробуйте ещё раз.");
    err.code = "network_error";
    throw err;
  });
}

// =======================================================================
// Локальный каталог ситуаций (public/data/situations-index.json) — используется
// ТОЛЬКО как декоративное обогащение реального списка сценариев (подпись блока на
// русском, признак эскалации из датасета), не как источник самого списка/графа.
// =======================================================================

let CATALOG = null;

function loadCatalog() {
  if (CATALOG) return Promise.resolve(CATALOG);
  return fetch(`${import.meta.env.BASE_URL}data/situations-index.json`)
    .then((r) => r.json())
    .then((json) => { CATALOG = json; return CATALOG; });
}

function blockLabelFor(catalog, blockKey) {
  const key = String(blockKey || "").toLowerCase();
  return (catalog && catalog.blocks && catalog.blocks[key]) || blockTitle(key);
}

// =======================================================================
// Локальный кэш завершённых прохождений (localStorage) — backend не даёт единого
// "список прохождений игрока" эндпоинта для scenario-list (статус "пройден"/
// последний результат), поэтому это чисто клиентская декорация, наполняется из
// getDebrief(). Ключ — id сценария как строка (UUID в реальном режиме, число в моке).
// =======================================================================

const PROGRESS_KEY = "vsm.completedScenarios.v2";

function readProgress() {
  try { return JSON.parse(localStorage.getItem(PROGRESS_KEY)) || {}; } catch (e) { return {}; }
}

function writeProgress(progress) {
  try { localStorage.setItem(PROGRESS_KEY, JSON.stringify(progress)); } catch (e) { /* ignore */ }
}

function recordCompletion(scenarioId, loyalty, safety, verdict) {
  const progress = readProgress();
  progress[String(scenarioId)] = {
    completedAt: new Date().toISOString(),
    loyalty,
    safety,
    verdict
  };
  writeProgress(progress);
}

// =======================================================================
// Ролевая модель: подписи шагов — те же строки, что отдаёт backend
// (ru.vsm.backend.feedback.dto.RoleStep#label), используются и в моке для единообразия.
// =======================================================================

const ROLE_STEP_LABELS = {
  acknowledge: "Признать ситуацию",
  rule: "Обозначить правило",
  solution: "Предложить решение",
  reassure: "Заверить"
};
const ALL_ROLE_STEP_KEYS = ["acknowledge", "rule", "solution", "reassure"];

/** Запись в журнал тренировок (график активности в профиле). Незавершённые прохождения не пишутся. */
function logActivity(debrief) {
  if (debrief.verdict === "Прохождение ещё не завершено") return;
  recordActivity({
    progressId: debrief.progressId,
    scenarioId: debrief.scenario.id,
    title: debrief.scenario.title,
    blockLabel: debrief.scenario.blockLabel,
    loyalty: debrief.finalScales.loyalty,
    safety: debrief.finalScales.safety,
    verdict: debrief.verdict
  });
}

// =======================================================================================
// ==================================  РЕАЛЬНЫЙ РЕЖИМ  ====================================
// =======================================================================================

function realNodeView(n) {
  return {
    id: n.nodeId,
    code: n.code,
    type: n.type,
    avatarInitials: n.type === "ESCALATION" ? "НП" : "ПС",
    contextNote: n.type === "ESCALATION" ? "Эскалация: начальник поезда" : null,
    situationText: n.text,
    timerSeconds: n.timerSeconds || null,
    deadlineAt: n.deadlineAt || null,
    terminal: !!n.terminal,
    terminalOutcome: n.terminalOutcome || null,
    outcomeSummary: n.outcomeSummary || null,
    choices: (n.choices || []).map((c) => ({ id: c.id, text: c.text }))
  };
}

function realReactionText(deltas, wasTimeout, outcomeSummary) {
  if (outcomeSummary) return outcomeSummary;
  if (wasTimeout) return "Время вышло — применено действие по умолчанию.";
  if (deltas.loyalty >= 0 && deltas.safety >= 0) return "Реакция на Ваше решение — положительная.";
  if (deltas.loyalty < 0 && deltas.safety < 0) return "Пассажир недоволен: решение восприняли негативно.";
  return "Реакция неоднозначная: один показатель улучшился, другой — нет.";
}

function realMapChoiceApplied(res) {
  const isFinal = res.status === "COMPLETED";
  const rawNext = res.nextNode;
  const deltas = { loyalty: res.loyaltyDelta, safety: res.safetyDelta };
  const outcomeSummary = rawNext && rawNext.terminal ? rawNext.outcomeSummary : null;
  return {
    reaction: {
      text: realReactionText(deltas, res.wasTimeout, outcomeSummary),
      deltas,
      escalation: !!(rawNext && rawNext.type === "ESCALATION"),
      hiddenPenalty: null,
      wasTimeout: !!res.wasTimeout
    },
    scales: { loyalty: res.loyaltyScore, safety: res.safetyScore },
    isFinal,
    node: rawNext && !isFinal ? realNodeView(rawNext) : null
  };
}

function realListScenarios() {
  return Promise.all([
    apiFetch("/api/scenarios", { method: "GET" }),
    loadCatalog().catch(() => null)
  ]).then(([summaries, catalog]) => {
    summaries = summaries || [];
    const progress = readProgress();
    const blocks = {};
    const situations = summaries.map((raw) => {
      const s = { ...raw, block: String(raw.block || "misc").toLowerCase() };
      const label = blockLabelFor(catalog, s.block);
      const localMeta = catalog && catalog.situations
        ? catalog.situations.filter((x) => x.id === s.situationRef)[0]
        : null;
      if (!blocks[s.block]) blocks[s.block] = { key: s.block, label, total: 0, completed: 0 };
      blocks[s.block].total += 1;
      const p = progress[String(s.id)];
      if (p) blocks[s.block].completed += 1;
      return {
        id: s.id,
        code: s.code,
        block: s.block,
        blockLabel: label,
        title: s.title,
        escalation: localMeta ? localMeta.escalation : null,
        failurePattern: localMeta ? localMeta.failure_pattern : null,
        flagship: !!s.flagship,
        status: p ? "completed" : "not_started",
        lastResult: p ? { loyalty: p.loyalty, safety: p.safety, verdict: p.verdict, completedAt: p.completedAt } : null
      };
    });
    const completedCount = situations.filter((s) => s.status === "completed").length;
    return {
      totalCount: situations.length,
      completedCount,
      blocks: Object.keys(blocks).map((k) => blocks[k]),
      situations
    };
  });
}

/** carClass — STANDARD | COMFORT | BUSINESS | FIRST: один и тот же выбор по-разному влияет на
 * лояльность в зависимости от класса вагона (портрет пассажира, см. README backend). */
function realStartScenario(scenarioId, carClass) {
  const qs = carClass ? `?carClass=${encodeURIComponent(carClass)}` : "";
  return apiFetch(`/api/scenarios/${scenarioId}`, { method: "GET" }).catch(() => null)
    .then((summary) => apiFetch(`/api/scenarios/${scenarioId}/progress${qs}`, { method: "POST", requiresPlayer: true })
      .then((progressState) => {
        if (!progressState.currentNode) return { error: "not_found" };
        return loadCatalog().catch(() => null).then((catalog) => ({
          sessionId: progressState.progressId,
          scenario: {
            id: progressState.scenarioId,
            title: summary ? summary.title : progressState.scenarioCode,
            blockLabel: summary ? blockLabelFor(catalog, summary.block) : ""
          },
          carClass: progressState.carClass || carClass || "STANDARD",
          scales: { loyalty: progressState.loyaltyScore, safety: progressState.safetyScore },
          node: realNodeView(progressState.currentNode)
        }));
      }))
    .catch(() => ({ error: "not_found" }));
}

/**
 * Преобразует сообщение WebSocket-канала (ru.vsm.backend.ws.dto.ProgressWsMessage, type
 * timeout/completed — см. README, раздел «WebSocket: живой таймер и шкалы») в ту же модель
 * представления, что и REST choose/timeout: поле currentNode на WS соответствует nextNode
 * в REST-ответе, остальной набор полей идентичен ChoiceAppliedResponse, поэтому маппинг общий.
 */
export function mapWsAppliedChoice(msg) {
  return realMapChoiceApplied({ ...msg, nextNode: msg.currentNode });
}

function realApplyChoiceRequest(progressId, pathSuffix) {
  return apiFetch(`/api/scenarios/progress/${progressId}${pathSuffix}`, { method: "POST", requiresPlayer: true })
    .then(realMapChoiceApplied, (err) => ({ error: err.code || "choice_failed" }));
}

function realChoose(progressId, choiceId) {
  return realApplyChoiceRequest(progressId, `/choices/${choiceId}`);
}

function realTimeout(progressId) {
  return realApplyChoiceRequest(progressId, "/timeout");
}

function realGetProfile(playerId) {
  return Promise.all([
    apiFetch(`/api/gamification/profile/${playerId}`, { method: "GET" }),
    loadCatalog().catch(() => null)
  ]).then(([profile, catalog]) => ({
    ...profile,
    blockProgress: (profile.blockProgress || []).map((bp) => ({ ...bp, blockLabel: blockLabelFor(catalog, bp.block) }))
  }));
}

/**
 * Лидерборд без реальных playerId (см. «Безопасность» в README backend): у строки есть только
 * publicId и признак me — «моя» строка определяется сервером по X-Player-Id запроса.
 * Нормализуем в { top, me, total }, где у каждой строки есть стабильный key.
 */
function realGetLeaderboard(opts = {}) {
  return apiFetch(`/api/gamification/leaderboard?limit=${opts.limit || 50}`, { method: "GET" }).then((d) => {
    const norm = (e) => (e ? { ...e, key: e.publicId || `${e.rank}-${e.displayName}`, me: !!e.me } : null);
    const top = (d.top || []).map(norm);
    const me = d.me ? { ...norm(d.me), me: true } : top.find((e) => e.me) || null;
    return { top, me, total: d.total || top.length };
  });
}

function realGetAchievements(playerId) {
  const qs = playerId ? `?playerId=${encodeURIComponent(playerId)}` : "";
  return apiFetch(`/api/gamification/achievements${qs}`, { method: "GET" });
}

function realGetNotifications(playerId, unreadOnly) {
  const qs = `?playerId=${encodeURIComponent(playerId)}&unreadOnly=${unreadOnly ? "true" : "false"}`;
  return apiFetch(`/api/gamification/notifications${qs}`, { method: "GET" });
}

function realMarkNotificationRead(id) {
  return apiFetch(`/api/gamification/notifications/${id}/read`, { method: "POST" });
}

function realMarkAllNotificationsRead(playerId) {
  const qs = `?playerId=${encodeURIComponent(playerId)}`;
  return apiFetch(`/api/gamification/notifications/read-all${qs}`, { method: "POST" });
}

function realGetCompetencyAnalytics(playerId) {
  return apiFetch(`/api/feedback/competencies/${playerId}`, { method: "GET" });
}

function realGetDebrief(progressId) {
  const playerId = getPlayerId();
  return apiFetch(`/api/feedback/debrief/${progressId}`, { method: "GET" }).then(
    (d) => loadCatalog().catch(() => null).then((catalog) => Promise.all([
      realGetProfile(playerId).catch(() => null),
      realGetAchievements(playerId).catch(() => null)
    ]).then(([profile, achievements]) => {
      const debrief = {
        progressId: d.userProgressId,
        scenario: { id: d.scenarioId, title: d.scenarioTitle, block: d.scenarioBlock, blockLabel: blockLabelFor(catalog, d.scenarioBlock) },
        verdict: d.verdict,
        interrupted: !!d.interrupted,
        finalScales: { loyalty: d.finalLoyaltyScore, safety: d.finalSafetyScore },
        timeline: (d.timeline || []).map((t) => ({
          sequenceIndex: t.sequenceIndex,
          nodeText: t.nodeText,
          choiceText: t.wasTimeout ? "Время вышло" : t.choiceText,
          wasTimeout: !!t.wasTimeout,
          deltas: { loyalty: t.loyaltyDelta, safety: t.safetyDelta },
          roleStepsCompleted: t.roleStepsCompleted || [],
          roleStepsSkipped: t.roleStepsSkipped || [],
          scaleConflict: !!t.scaleConflict,
          escalation: t.nodeType === "ESCALATION",
          explanation: t.explanation || null,
          hiddenCommunicationEffect: !!t.hiddenCommunicationEffect
        })),
        keyMoment: d.keyMoment
          ? {
              nodeText: d.keyMoment.nodeText,
              chosenChoiceText: d.keyMoment.chosenChoiceText,
              chosenDeltas: { loyalty: d.keyMoment.chosenLoyaltyDelta, safety: d.keyMoment.chosenSafetyDelta },
              betterChoiceText: d.keyMoment.betterChoiceText,
              betterDeltas: { loyalty: d.keyMoment.betterLoyaltyDelta, safety: d.keyMoment.betterSafetyDelta },
              adviceText: d.keyMoment.adviceText,
              betterExplanation: d.keyMoment.betterExplanation || null
            }
          : null,
        summary: d.summary,
        normReferences: d.normReferences || [],
        accrual: profile
          ? {
              totalScore: profile.totalScore,
              scenariosCompleted: profile.scenariosCompleted,
              recentAchievements: (achievements || profile.recentAchievements || []).filter((a) => a.earned)
            }
          : { totalScore: null, scenariosCompleted: null, recentAchievements: [] }
      };
      recordCompletion(debrief.scenario.id, debrief.finalScales.loyalty, debrief.finalScales.safety, debrief.verdict);
      logActivity(debrief);
      return debrief;
    })),
    () => ({ error: "debrief_unavailable" })
  );
}

// =======================================================================================
// ===================================  МОК-РЕЖИМ  ========================================
// (аварийный переключатель USE_MOCKS=true — например, если backend недоступен на демо;
// возвращает объекты той же формы, что и реальный режим выше.)
// =======================================================================================

const FLAGSHIP_IDS_MOCK = [1, 6, 9, 19, 24, 30, 33, 42];

const FULL_MOCK_GRAPHS = {
  30: {
    startNode: "n1",
    nodes: {
      n1: {
        id: "n1", avatarInitials: "ПС", contextNote: "Вагон Комфорт, тамбур у вагона-бистро",
        situationText:
          "К вам подбегает взволнованная пассажирка: она на секунду отвернулась у стойки " +
          "вагона-бистро, а её сын семи лет пропал из виду. Голос дрожит, вокруг уже " +
          "оборачиваются другие пассажиры.",
        timer: null,
        choices: [
          { id: "n1-a", text: "Я Вас понимаю, сейчас разберёмся. По правилам в такой ситуации я обязана немедленно сообщить начальнику поезда — он организует поиск по всему составу. Побудьте, пожалуйста, рядом со мной, я никуда Вас не отпущу одну.", effects: { loyalty: 8, safety: 5 }, escalation: true, roleModelSteps: ["acknowledge", "rule", "solution", "reassure"], reactionText: "Пассажирка немного успокаивается и остаётся рядом.", next: "n2" },
          { id: "n1-b", text: "Подождите здесь, я сама сейчас пробегусь по вагонам и поищу.", effects: { loyalty: -4, safety: -10 }, escalation: false, roleModelSteps: ["solution"], reactionText: "Пассажирка остаётся одна и начинает паниковать ещё сильнее — вы нарушили порядок действий: сначала нужно было сообщить начальнику поезда.", next: "n2" },
          { id: "n1-c", text: "Успокойтесь, дети часто теряются в поезде, скорее всего он просто в туалете.", effects: { loyalty: -10, safety: -3 }, escalation: false, roleModelSteps: [], reactionText: "Пассажирка чувствует, что её тревогу не восприняли всерьёз.", next: "n2" }
        ]
      },
      n2: {
        id: "n2", avatarInitials: "НП", contextNote: "Начальник поезда на связи, поиск уже начат по соседним вагонам",
        situationText: "Начальник поезда просит вас объявить о поиске мальчика по громкой связи в вашем вагоне, пока пассажирка ждёт рядом. Решение нужно принять быстро — пассажиры уже начинают спрашивать, что случилось.",
        timer: { seconds: 20 },
        choices: [
          { id: "n2-a", text: "Уважаемые пассажиры, просим обратить внимание: если вы видели мальчика 7 лет без сопровождения взрослых, сообщите проводнику вагона.", effects: { loyalty: 6, safety: 8 }, escalation: true, roleModelSteps: ["rule", "solution"], reactionText: "Корректная формулировка без лишних личных данных — начальник поезда подтверждает: поиск идёт по регламенту.", next: "n3" },
          { id: "n2-b", text: "По громкой связи, называя полное имя и приметы ребёнка, начать подробно описывать ситуацию всем вагоном, пока не найдётся.", effects: { loyalty: -2, safety: -12 }, escalation: true, roleModelSteps: ["solution"], hiddenPenalty: "Скрытая механика: разглашение персональных данных ребёнка по громкой связи — нарушение регламента связи.", reactionText: "Объявление вызывает лишнюю панику в вагоне.", next: "n3" },
          { id: "n2-timeout", text: "Время вышло", isTimeout: true, effects: { loyalty: -6, safety: -6 }, escalation: true, roleModelSteps: [], reactionText: "Пока вы решали, начальник поезда объявил поиск сам — без вашего участия.", next: "n3" }
        ]
      },
      n3: {
        id: "n3", avatarInitials: "ПС", contextNote: "Соседний вагон, столик с раскраской",
        situationText: "Ребёнка нашли — он спокойно сидел у столика в соседнем вагоне и раскрашивал картинку, ждал маму. Пассажирка на грани слёз от облегчения обнимает сына.",
        timer: null,
        choices: [
          { id: "n3-a", text: "Рада, что всё закончилось благополучно! Если понадобится любая помощь до конца поездки — обращайтесь, я рядом.", effects: { loyalty: 5, safety: 0 }, escalation: false, roleModelSteps: ["reassure"], reactionText: "Пассажирка искренне благодарит проводника.", next: null },
          { id: "n3-b", text: "Хорошо, что нашёлся. Мне нужно возвращаться к остальным пассажирам.", effects: { loyalty: -2, safety: 0 }, escalation: false, roleModelSteps: [], reactionText: "Пассажирка чувствует некоторую сухость в завершении разговора.", next: null }
        ]
      }
    }
  }
};

function buildGenericGraph(situation) {
  const esc = situation.escalation;
  const failure = situation.failure_pattern;
  return {
    startNode: "g1",
    nodes: {
      g1: {
        id: "g1", avatarInitials: "ПС", contextNote: null,
        situationText: `Ситуация: «${situation.title}». Пассажир обращается к Вам с этим вопросом и ждёт реакции.`,
        timer: null,
        choices: [
          { id: "g1-good", text: "Понимаю Вас. По правилам в этой ситуации предусмотрено следующее решение — сейчас всё уточню и вернусь к Вам. Благодарю за обращение.", effects: { loyalty: 8, safety: 6 }, escalation: !!esc, roleModelSteps: ["acknowledge", "rule", "solution", "reassure"], reactionText: "Пассажир удовлетворён полным и вежливым ответом.", next: "g2-good" },
          { id: "g1-partial", text: "Хорошо, сейчас разберёмся, подождите.", effects: { loyalty: 1, safety: 2 }, escalation: false, roleModelSteps: ["solution"], reactionText: "Пассажир доволен решением, но реакция была суховатой.", next: "g2-partial" },
          { id: "g1-bad", text: failure ? `Действие, характерное для провала: ${failure}.` : "Проигнорировать просьбу и уйти.", effects: { loyalty: -9, safety: -9 }, escalation: false, roleModelSteps: [], reactionText: "Пассажир недоволен, ситуация могла перерасти в конфликт.", next: "g2-bad" }
        ]
      },
      "g2-good": { id: "g2-good", avatarInitials: "ПС", contextNote: null, situationText: "Пассажир благодарит за оперативную и вежливую помощь.", timer: null, choices: [{ id: "g2-good-end", text: "Хорошего пути! Обращайтесь, если понадобится что-то ещё.", effects: { loyalty: 2, safety: 0 }, escalation: false, roleModelSteps: ["reassure"], reactionText: "Ситуация исчерпана.", next: null }] },
      "g2-partial": { id: "g2-partial", avatarInitials: "ПС", contextNote: null, situationText: "Пассажир принимает решение, но остаётся сдержанным.", timer: null, choices: [{ id: "g2-partial-end", text: "Всего доброго.", effects: { loyalty: 0, safety: 0 }, escalation: false, roleModelSteps: [], reactionText: "Ситуация исчерпана без особого впечатления у пассажира.", next: null }] },
      "g2-bad": { id: "g2-bad", avatarInitials: "ПС", contextNote: null, situationText: "Пассажир жалуется на обслуживание, инцидент зафиксирован.", timer: null, choices: [{ id: "g2-bad-end", text: "Приношу извинения за неудобства.", effects: { loyalty: 1, safety: 0 }, escalation: false, roleModelSteps: ["acknowledge"], reactionText: "Пассажир немного смягчается, но осадок остался.", next: null }] }
    }
  };
}

// Мок-хранилище уведомлений (localStorage) — та же роль аварийного переключателя, что и у
// PROGRESS_KEY выше: наполняется из mockGetDebrief (новая ачивка), читается mockGetNotifications.
const NOTIFICATIONS_KEY = "vsm.notifications.v1";
let mockNotificationCounter = 0;

function readNotifications() {
  try { return JSON.parse(localStorage.getItem(NOTIFICATIONS_KEY)) || []; } catch (e) { return []; }
}

function writeNotifications(list) {
  try { localStorage.setItem(NOTIFICATIONS_KEY, JSON.stringify(list.slice(0, 50))); } catch (e) { /* ignore */ }
}

function pushMockNotification(type, title, body) {
  mockNotificationCounter += 1;
  const list = readNotifications();
  list.unshift({
    id: `mock-notif-${Date.now()}-${mockNotificationCounter}`,
    type,
    title,
    body,
    createdAt: new Date().toISOString(),
    readAt: null
  });
  writeNotifications(list);
}

const mockSessions = {};
let mockSessionCounter = 0;

function delay(value) {
  return new Promise((resolve) => { setTimeout(() => resolve(value), NETWORK_DELAY_MS); });
}

function clampScale(v) { return Math.max(0, Math.min(100, v)); }

function mockNodeView(node) {
  return {
    id: node.id,
    contextNote: node.contextNote,
    avatarInitials: node.avatarInitials,
    situationText: node.situationText,
    timerSeconds: node.timer ? node.timer.seconds : null,
    deadlineAt: node.timer ? new Date(Date.now() + node.timer.seconds * 1000).toISOString() : null,
    terminal: false,
    terminalOutcome: null,
    outcomeSummary: null,
    choices: node.choices.filter((c) => !c.isTimeout).map((c) => ({ id: c.id, text: c.text }))
  };
}

function mockListScenarios() {
  return loadCatalog().then((catalog) => {
    const progress = readProgress();
    const blocks = {};
    Object.keys(catalog.blocks).forEach((key) => {
      blocks[key] = { key, label: catalog.blocks[key], total: 0, completed: 0 };
    });
    const situations = catalog.situations.map((s) => {
      const p = progress[String(s.id)];
      if (blocks[s.block]) {
        blocks[s.block].total += 1;
        if (p) blocks[s.block].completed += 1;
      }
      return {
        id: s.id, block: s.block, blockLabel: blockLabelFor(catalog, s.block), title: s.title,
        escalation: s.escalation, failurePattern: s.failure_pattern,
        flagship: FLAGSHIP_IDS_MOCK.indexOf(s.id) !== -1,
        status: p ? "completed" : "not_started",
        lastResult: p ? { loyalty: p.loyalty, safety: p.safety, verdict: p.verdict, completedAt: p.completedAt } : null
      };
    });
    return delay({
      totalCount: situations.length,
      completedCount: Object.keys(progress).length,
      blocks: Object.keys(blocks).map((k) => blocks[k]),
      situations
    });
  });
}

function mockStartScenario(scenarioId, carClass) {
  return loadCatalog().then((catalog) => {
    const situation = catalog.situations.filter((s) => String(s.id) === String(scenarioId))[0];
    if (!situation) return delay({ error: "not_found" });
    const graph = FULL_MOCK_GRAPHS[situation.id] || buildGenericGraph(situation);
    mockSessionCounter += 1;
    const sessionId = `sess-${mockSessionCounter}-${Date.now()}`;
    mockSessions[sessionId] = {
      scenarioId: situation.id, scenarioTitle: situation.title, block: situation.block,
      blockLabel: blockLabelFor(catalog, situation.block), graph, history: [],
      currentNodeId: graph.startNode, scales: { loyalty: 60, safety: 60 }
    };
    const node = graph.nodes[graph.startNode];
    return delay({
      sessionId,
      carClass: carClass || "STANDARD",
      scenario: { id: situation.id, title: situation.title, blockLabel: blockLabelFor(catalog, situation.block) },
      scales: mockSessions[sessionId].scales,
      node: mockNodeView(node)
    });
  });
}

function mockApplyChoice(sessionId, choiceId) {
  const session = mockSessions[sessionId];
  if (!session) return delay({ error: "session_not_found" });
  const node = session.graph.nodes[session.currentNodeId];
  let choice;
  if (choiceId === "__timeout__") {
    choice = node.choices.filter((c) => c.isTimeout)[0];
    if (!choice) {
      choice = node.choices.slice().sort((a, b) => (a.effects.loyalty + a.effects.safety) - (b.effects.loyalty + b.effects.safety))[0];
    }
  } else {
    choice = node.choices.filter((c) => c.id === choiceId)[0];
  }
  if (!choice) return delay({ error: "choice_not_found" });

  session.scales.loyalty = clampScale(session.scales.loyalty + choice.effects.loyalty);
  session.scales.safety = clampScale(session.scales.safety + choice.effects.safety);
  session.history.push({
    nodeId: node.id, situationText: node.situationText, choiceId: choice.id,
    choiceText: choice.isTimeout ? "Время вышло" : choice.text, isTimeout: !!choice.isTimeout,
    effects: choice.effects, escalation: !!choice.escalation, roleModelSteps: choice.roleModelSteps || [],
    hiddenPenalty: choice.hiddenPenalty || null, reactionText: choice.reactionText
  });

  const isFinal = !choice.next;
  let nextNode = null;
  if (!isFinal) {
    session.currentNodeId = choice.next;
    nextNode = mockNodeView(session.graph.nodes[choice.next]);
  }

  return delay({
    reaction: {
      text: choice.reactionText, deltas: choice.effects, escalation: !!choice.escalation,
      hiddenPenalty: choice.hiddenPenalty || null, wasTimeout: !!choice.isTimeout
    },
    scales: session.scales, isFinal, node: nextNode
  });
}

function mockChoose(sessionId, choiceId) { return mockApplyChoice(sessionId, choiceId); }
function mockTimeout(sessionId) { return mockApplyChoice(sessionId, "__timeout__"); }

function mockGetDebrief(sessionId) {
  const session = mockSessions[sessionId];
  if (!session) return delay({ error: "session_not_found" });
  const finalScales = session.scales;
  let verdict = "Хорошо справились";
  if (finalScales.safety < 40) verdict = "Критическая ошибка безопасности";
  else if (finalScales.loyalty < 40 || finalScales.safety < 55) verdict = "Есть над чем поработать";

  const hadFullRoleModel = session.history.some((h) => h.roleModelSteps.length === 4);
  const summary = hadFullRoleModel
    ? "Все 4 шага ролевой модели соблюдены хотя бы в одном ответе, конфликт шкал решён осознанно. Так и продолжайте: признание → правило → решение → заверение."
    : "В ключевой развилке стоило пройти все 4 шага модели (признать → обозначить правило → предложить решение → заверить), а не ограничиваться одним из них.";

  const timeline = session.history.map((h, i) => {
    const missing = h.roleModelSteps.length > 0
      ? ALL_ROLE_STEP_KEYS.filter((k) => h.roleModelSteps.indexOf(k) === -1)
      : [];
    return {
      sequenceIndex: i, nodeText: h.situationText, choiceText: h.choiceText, wasTimeout: h.isTimeout,
      deltas: h.effects,
      roleStepsCompleted: h.roleModelSteps.map((k) => ROLE_STEP_LABELS[k]),
      roleStepsSkipped: missing.map((k) => ROLE_STEP_LABELS[k]),
      scaleConflict: (h.effects.loyalty < 0) !== (h.effects.safety < 0) && (h.effects.loyalty !== 0 || h.effects.safety !== 0),
      escalation: h.escalation,
      explanation: h.hiddenPenalty || h.reactionText,
      // Тот же признак, что и в реальном режиме (DebriefStepDto.hiddenCommunicationEffect):
      // в моке единственный узел с такой механикой — n2 флагманского графа 30, отмечен
      // через hiddenPenalty на "плохой" альтернативе (переговоры по рации, пассажир не слышит).
      hiddenCommunicationEffect: !!h.hiddenPenalty
    };
  });

  // Ключевая развилка — та же логика, что backend DebriefService.buildDebrief: по каждому
  // пройденному узлу сравниваем фактический выбор со всеми его alternatives по сумме дельт,
  // берём узел с максимальным разрывом.
  let keyMoment = null;
  let bestGap = 0;
  session.history.forEach((h) => {
    const node = session.graph.nodes[h.nodeId];
    const chosen = node && node.choices.filter((c) => c.id === h.choiceId)[0];
    if (!chosen) return;
    const chosenScore = chosen.effects.loyalty + chosen.effects.safety;
    let best = chosen;
    let bestScore = chosenScore;
    node.choices.filter((c) => !c.isTimeout).forEach((c) => {
      const score = c.effects.loyalty + c.effects.safety;
      if (score > bestScore) { bestScore = score; best = c; }
    });
    const gap = bestScore - chosenScore;
    if (gap > bestGap) {
      bestGap = gap;
      keyMoment = {
        nodeText: h.situationText,
        chosenChoiceText: chosen.text,
        chosenDeltas: chosen.effects,
        betterChoiceText: best.text,
        betterDeltas: best.effects,
        adviceText: `Более сильный вариант в этом узле — «${best.text}».`,
        betterExplanation: best.hiddenPenalty || best.reactionText || null
      };
    }
  });

  const awardedPoints = Math.round((finalScales.loyalty + finalScales.safety) / 2);
  const achievements = [];
  if (finalScales.safety >= 90) achievements.push({ code: "safety-master", title: "Страж безопасности", earned: true });
  if (finalScales.loyalty >= 90) achievements.push({ code: "loyalty-master", title: "Любимец пассажиров", earned: true });
  achievements.forEach((a) => pushMockNotification("ACHIEVEMENT_UNLOCKED", "Новая ачивка", `«${a.title}» — поздравляем!`));

  const debrief = {
    progressId: sessionId,
    scenario: { id: session.scenarioId, title: session.scenarioTitle, block: session.block, blockLabel: session.blockLabel },
    verdict, interrupted: false, finalScales, timeline,
    keyMoment, summary, normReferences: [],
    accrual: { totalScore: awardedPoints, scenariosCompleted: Object.keys(readProgress()).length + 1, recentAchievements: achievements }
  };
  return delay(debrief).then((d) => {
    recordCompletion(session.scenarioId, finalScales.loyalty, finalScales.safety, verdict);
    logActivity(d);
    return d;
  });
}

function mockGetProfile() {
  const progress = readProgress();
  const entries = Object.keys(progress).map((k) => progress[k]);
  const totalScore = entries.reduce((sum, p) => sum + Math.round((p.loyalty + p.safety) / 2), 0);
  return delay({
    playerId: getPlayerId(),
    displayName: `Проводник-${getPlayerId().slice(-4)}`,
    totalScore,
    scenariosCompleted: entries.length,
    totalScenariosAvailable: 51,
    blockProgress: [],
    recentAchievements: [],
    leaderboardRank: entries.length > 0 ? 1 : null
  });
}

function mockGetLeaderboard() {
  return mockGetProfile().then((profile) => {
    // Синтетические коллеги только для мок-режима (152-ФЗ: вымышленные имена).
    const colleagues = [
      ["Ковалёва Дарья", 1840, 34], ["Гусейнов Тимур", 1512, 29], ["Лаптева Ника", 1296, 27],
      ["Орехов Семён", 1103, 22], ["Буранова Эльза", 922, 19], ["Щукин Вадим", 640, 14],
      ["Мирзоева Алина", 410, 9], ["Фомичёв Глеб", 188, 5]
    ].map(([displayName, totalScore, scenariosCompleted]) => ({ displayName, totalScore, scenariosCompleted, me: false }));
    const mine = profile.scenariosCompleted > 0
      ? { displayName: profile.displayName, totalScore: profile.totalScore, scenariosCompleted: profile.scenariosCompleted, me: true }
      : null;
    const all = colleagues.concat(mine ? [mine] : []).sort((a, b) => b.totalScore - a.totalScore)
      .map((e, i) => ({ ...e, rank: i + 1, key: `mock-${i}` }));
    return { top: all, me: all.find((e) => e.me) || null, total: all.length };
  });
}

const MOCK_ACHIEVEMENT_CATALOG = [
  { code: "FIRST_SCENARIO", title: "Первый рейс", description: "Завершите первое прохождение сценария.", category: "objem" },
  { code: "FLAWLESS_SAFETY", title: "Страж безопасности", description: "Успешное прохождение с высокой безопасностью.", category: "style" },
  { code: "PASSENGER_FAVORITE", title: "Любимец пассажиров", description: "Успешное прохождение с высокой лояльностью.", category: "style" },
  { code: "VERSATILE", title: "Универсал", description: "Пройдите сценарии из 3 разных блоков.", category: "objem" },
  { code: "VETERAN", title: "Ветеран", description: "10 завершённых прохождений.", category: "objem" }
];

function mockGetAchievements() {
  const completed = Object.keys(readProgress()).length;
  return delay(MOCK_ACHIEVEMENT_CATALOG.map((a) => ({ ...a, earned: a.code === "FIRST_SCENARIO" && completed > 0, earnedAt: null })));
}

function mockGetNotifications(unreadOnly) {
  const list = readNotifications();
  return delay(unreadOnly ? list.filter((n) => !n.readAt) : list);
}

function mockMarkNotificationRead(id) {
  const list = readNotifications();
  const found = list.filter((n) => n.id === id)[0];
  if (found) found.readAt = new Date().toISOString();
  writeNotifications(list);
  return delay(found || null);
}

function mockMarkAllNotificationsRead() {
  const list = readNotifications();
  const now = new Date().toISOString();
  let markedCount = 0;
  list.forEach((n) => { if (!n.readAt) { n.readAt = now; markedCount += 1; } });
  writeNotifications(list);
  return delay({ markedCount });
}

/**
 * Мок-аналитика компетенций — приблизительный аналог CompetencyAnalyticsService на клиентских
 * данных (readProgress хранит только итоговые шкалы и вердикт, без деталей по шагам ролевой
 * модели/нормам, поэтому roleStepCompliance/frequentNormViolations/recommendations в моке пустые
 * или упрощены). Аварийный переключатель ради демо, не претендует на точность формулы backend.
 */
function mockGetCompetencyAnalytics() {
  const progress = readProgress();
  const entries = Object.keys(progress).map((id) => ({ id, ...progress[id] }));
  return loadCatalog().then((catalog) => {
    const byBlock = {};
    entries.forEach((e) => {
      const situation = (catalog.situations || []).filter((s) => String(s.id) === e.id)[0];
      const block = situation ? situation.block : "unknown";
      if (!byBlock[block]) byBlock[block] = { block, playthroughs: 0, loyaltySum: 0, safetySum: 0, success: 0, failure: 0 };
      const agg = byBlock[block];
      agg.playthroughs += 1;
      agg.loyaltySum += e.loyalty;
      agg.safetySum += e.safety;
      if (e.safety < 40) agg.failure += 1;
      else if (e.loyalty >= 60 && e.safety >= 60) agg.success += 1;
    });
    const blockStats = Object.keys(byBlock).map((key) => {
      const agg = byBlock[key];
      const successRate = agg.success / agg.playthroughs;
      const failureRate = agg.failure / agg.playthroughs;
      return {
        block: key,
        playthroughs: agg.playthroughs,
        avgLoyaltyScore: agg.loyaltySum / agg.playthroughs,
        avgSafetyScore: agg.safetySum / agg.playthroughs,
        successRate,
        failureRate,
        weak: successRate - failureRate < 0.5
      };
    });
    const weakCompetencies = blockStats.filter((b) => b.weak).map((b) => b.block).slice(0, 3);
    return delay({
      playerId: getPlayerId(),
      totalPlaythroughs: entries.length,
      blockStats,
      roleStepCompliance: ALL_ROLE_STEP_KEYS.map((k) => ({
        step: k.toUpperCase(),
        stepLabel: ROLE_STEP_LABELS[k],
        timesFollowed: 0,
        timesSkipped: 0,
        complianceRate: 0
      })),
      frequentNormViolations: [],
      weakCompetencies,
      recommendations: []
    });
  });
}

// =======================================================================================
// ================================  ПУБЛИЧНОЕ API  ========================================
// =======================================================================================

export function listScenarios() { return USE_MOCKS ? mockListScenarios() : realListScenarios(); }
export function startScenario(scenarioId, carClass) { return USE_MOCKS ? mockStartScenario(scenarioId, carClass) : realStartScenario(scenarioId, carClass); }
export function choose(sessionId, choiceId) { return USE_MOCKS ? mockChoose(sessionId, choiceId) : realChoose(sessionId, choiceId); }
export function timeout(sessionId) { return USE_MOCKS ? mockTimeout(sessionId) : realTimeout(sessionId); }
export function getDebrief(sessionId) { return USE_MOCKS ? mockGetDebrief(sessionId) : realGetDebrief(sessionId); }
export function getProfile(playerId) { return USE_MOCKS ? mockGetProfile() : realGetProfile(playerId || getPlayerId()); }
export function getLeaderboard(opts) { return USE_MOCKS ? mockGetLeaderboard() : realGetLeaderboard(opts); }
export function getAchievements(playerId) { return USE_MOCKS ? mockGetAchievements() : realGetAchievements(playerId || getPlayerId()); }
export function getNotifications(playerId, unreadOnly) {
  return USE_MOCKS ? mockGetNotifications(unreadOnly) : realGetNotifications(playerId || getPlayerId(), unreadOnly);
}
export function markNotificationRead(id) {
  return USE_MOCKS ? mockMarkNotificationRead(id) : realMarkNotificationRead(id);
}
export function markAllNotificationsRead(playerId) {
  return USE_MOCKS ? mockMarkAllNotificationsRead() : realMarkAllNotificationsRead(playerId || getPlayerId());
}
export function getCompetencyAnalytics(playerId) {
  return USE_MOCKS ? mockGetCompetencyAnalytics() : realGetCompetencyAnalytics(playerId || getPlayerId());
}

// =======================================================================
// Витрина наград. Backend-контракт: PUT /api/gamification/showcase (тело — массив снимков,
// владелец по X-Player-Id), GET /api/gamification/showcase/{publicId}. Если endpoint не
// поднят, витрина остаётся локальной и в рейтинге показывается только в демо-режиме.
// =======================================================================

const MOCK_SHOWCASES = [
  [{ id: "shift:clean", title: "Смена без замечаний", shape: "circle", glyph: "train" }, { id: "module:medical", title: "Медицинские ситуации", shape: "circle", glyph: "cross" }, { id: "dist:FLAWLESS_SAFETY", title: "Отличие за безопасность", shape: "hexagon", glyph: "shield" }, { id: "streak:14", title: "Серия 14 дней", shape: "circle", text: "14" }],
  [{ id: "module:conflict", title: "Урегулирование конфликтов", shape: "circle", glyph: "users" }, { id: "dist:PASSENGER_FAVORITE", title: "Отличие за сервис", shape: "hexagon", glyph: "smile" }, { id: "streak:7", title: "Серия 7 дней", shape: "circle", text: "7" }],
  [{ id: "dist:VETERAN", title: "Десять учебных рейсов", shape: "hexagon", text: "10" }, { id: "module:boarding", title: "Посадка и документы", shape: "circle", glyph: "ticket" }],
  []
];

export function saveShowcase(items, finish) {
  if (USE_MOCKS) return delay({ ok: true, shared: true });
  const body = {
    finish: finish || "enamel",
    items: items.map((it) => ({ id: it.id, title: it.title, shape: it.shape, glyph: it.glyph || null, text: it.text || null }))
  };
  return apiFetch("/api/gamification/showcase", { method: "PUT", requiresPlayer: true, body: JSON.stringify(body) })
    .then(() => ({ ok: true, shared: true }), () => ({ ok: true, shared: false }));
}

export function getShowcase(entry) {
  if (USE_MOCKS || !entry.publicId) {
    const n = String(entry.displayName || entry.key || "").length;
    return delay({ items: entry.me ? null : MOCK_SHOWCASES[n % MOCK_SHOWCASES.length], finish: ["metal", "enamel", "glass"][n % 3] });
  }
  return apiFetch(`/api/gamification/showcase/${encodeURIComponent(entry.publicId)}`, { method: "GET" })
    .then((d) => ({ items: (d && d.items) || [], finish: (d && d.finish) || "enamel" }), () => ({ items: null, finish: "enamel" }));
}

// =======================================================================
// Учётная запись: регистрация, вход, Госуслуги (демо-ЕСИА на backend), выход.
// Токен — Authorization: Bearer; id учётной записи = playerId (анонимный прогресс сохраняется:
// регистрация передаёт текущий X-Player-Id, и он становится id учётной записи).
// =======================================================================

const TOKEN_KEY = "reactlab.token.v1";
const ACCOUNT_KEY = "reactlab.account.v1";
const authListeners = new Set();

export function getToken() {
  try { return localStorage.getItem(TOKEN_KEY); } catch (e) { return null; }
}

export function getAccount() {
  try { return JSON.parse(localStorage.getItem(ACCOUNT_KEY) || "null"); } catch (e) { return null; }
}

export function subscribeAuth(fn) {
  authListeners.add(fn);
  return () => authListeners.delete(fn);
}

function setSession(token, profile) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(ACCOUNT_KEY, JSON.stringify(profile));
    localStorage.setItem(PLAYER_ID_STORAGE_KEY, profile.id);
  } catch (e) { /* приватный режим */ }
  authListeners.forEach((fn) => fn(profile));
  return profile;
}

export function logout() {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ACCOUNT_KEY);
    localStorage.setItem(PLAYER_ID_STORAGE_KEY, generateUuidV4());
  } catch (e) { /* приватный режим */ }
  authListeners.forEach((fn) => fn(null));
}

function authError(err) {
  const map = {
    invalid_credentials: "Неверный логин или пароль.",
    login_already_taken: "Этот логин уже занят.",
    email_already_taken: "Эта почта уже зарегистрирована.",
    player_already_registered: "Этот прогресс уже привязан к другой учётной записи.",
    invalid_or_expired_esia_code: "Код входа через Госуслуги устарел. Попробуйте ещё раз."
  };
  const e = new Error(map[err.code] || err.message || "Не удалось выполнить запрос.");
  e.code = err.code;
  throw e;
}

export function login(loginName, password) {
  if (USE_MOCKS) {
    if (!loginName || !password) return Promise.reject(new Error("Введите логин и пароль."));
    const role = loginName === "admin" ? "ADMIN" : "USER";
    return delay(setSession("mock-token", { id: getPlayerId(), login: loginName, email: `${loginName}@demo.local`, displayName: loginName === "admin" ? "Администратор" : loginName, role, verified: false, createdAt: new Date().toISOString() }));
  }
  return apiFetch("/api/auth/login", { method: "POST", publicCall: true, noAuth: true, body: JSON.stringify({ login: loginName, password }) })
    .then((r) => setSession(r.token, r.profile), authError);
}

export function register({ login: loginName, email, password, displayName }) {
  if (USE_MOCKS) {
    return delay(setSession("mock-token", { id: getPlayerId(), login: loginName, email, displayName: displayName || loginName, role: "USER", verified: false, createdAt: new Date().toISOString() }));
  }
  return apiFetch("/api/auth/register", { method: "POST", noAuth: true, body: JSON.stringify({ login: loginName, email, password, displayName }) })
    .then(() => login(loginName, password), authError);
}

export function refreshAccount() {
  const token = getToken();
  if (!token || USE_MOCKS) return Promise.resolve(getAccount());
  return apiFetch("/api/auth/me", { method: "GET", publicCall: true })
    .then((profile) => setSession(token, profile), (err) => {
      if (err.status === 401) logout();
      return getAccount();
    });
}

/** Адрес страницы входа через Госуслуги (демо-провайдер на backend). Возврат — на #/auth/esia?code=… */
export function esiaAuthorizeUrl() {
  const back = `${window.location.origin}${window.location.pathname}#/auth/esia`;
  return `${API_BASE}/api/auth/esia/authorize?redirect_uri=${encodeURIComponent(back)}`;
}

/** Тестовые граждане демо-ЕСИА (совпадают с backend EsiaMockService) — для режима без backend. */
export const ESIA_DEMO_CITIZENS = [
  { code: "ivanova", fullName: "Иванова Мария Сергеевна", snils: "112-233-445 95" },
  { code: "petrov", fullName: "Петров Алексей Викторович", snils: "223-344-556 06" },
  { code: "sidorova", fullName: "Сидорова Ольга Дмитриевна", snils: "334-455-667 17" },
  { code: "kuznetsov", fullName: "Кузнецов Артём Игоревич", snils: "445-566-778 28" }
];

export function esiaCallback(code) {
  if (USE_MOCKS) {
    const c = ESIA_DEMO_CITIZENS.find((x) => x.code === code) || ESIA_DEMO_CITIZENS[0];
    const prev = getAccount();
    return delay(setSession("mock-token", { id: getPlayerId(), login: `esia_${c.code}`, email: `${c.code}@esia.mock.local`, displayName: c.fullName, role: prev && prev.role === "ADMIN" ? "ADMIN" : "USER", verified: true, createdAt: new Date().toISOString() }));
  }
  return apiFetch("/api/auth/esia/callback", { method: "POST", publicCall: true, noAuth: true, body: JSON.stringify({ code }) })
    .then((r) => setSession(r.token, r.profile), authError);
}

// =======================================================================
// Админ-панель: статистика, редактор сценариев, события. Требуют роль ADMIN (Bearer-токен).
// =======================================================================

const MOCK_OVERVIEW = { totalPlayers: 128, totalPlaythroughs: 1542, completedPlaythroughs: 1391, inProgressPlaythroughs: 151, avgLoyaltyScore: 63.4, avgSafetyScore: 71.2, successRate: 0.62, partialRate: 0.27, failureRate: 0.11, activePlayers24h: 37, activePlayers7d: 96 };

export function adminOverview() {
  if (USE_MOCKS) return delay(MOCK_OVERVIEW);
  return apiFetch("/api/admin/stats/overview");
}

export function adminBlocks() {
  if (USE_MOCKS) {
    return delay(Object.keys(MOCK_BLOCK_KEYS).map((b, i) => ({ block: b, totalPlaythroughs: 90 + i * 17, completedPlaythroughs: 80 + i * 15, successRate: 0.45 + (i % 5) * 0.09, avgLoyaltyScore: 55 + i * 2, avgSafetyScore: 60 + (i % 4) * 6 })));
  }
  return apiFetch("/api/admin/stats/blocks");
}

const MOCK_BLOCK_KEYS = { boarding: 1, baggage: 1, safety: 1, seating: 1, catering: 1, medical: 1, lost_found: 1, conflict: 1, comfort: 1, misc: 1 };

export function adminScenarioStats() {
  if (USE_MOCKS) {
    return listScenarios().then((d) => d.situations.slice(0, 12).map((s, i) => ({ scenarioId: s.id, code: s.code || `s-${s.id}`, title: s.title, block: s.block, totalPlaythroughs: 20 + i * 3, completedPlaythroughs: 18 + i * 3, successRate: 0.3 + i * 0.05, avgLoyaltyScore: 50 + i, avgSafetyScore: 55 + i, timeoutRate: 0.2 - i * 0.012 })));
  }
  return apiFetch("/api/admin/stats/scenarios");
}

/** Скачать CSV-выгрузку (players, scenarios, blocks, teams) — с токеном, поэтому через fetch и blob. */
export function adminDownloadCsv(name) {
  if (USE_MOCKS) {
    const blob = new Blob([`block;total\nsafety;42\n`], { type: "text/csv" });
    return Promise.resolve(saveBlob(blob, `${name}.csv`));
  }
  const headers = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  return fetch(`${API_BASE}/api/admin/stats/${name}.csv`, { headers }).then((r) => {
    if (!r.ok) throw new Error(`${r.status}`);
    return r.blob();
  }).then((b) => saveBlob(b, `${name}.csv`));
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  return true;
}

export function editorImportMarkdown(markdown, save) {
  if (USE_MOCKS) return delay(save ? { id: "mock", code: "new-scenario", title: "Новая ситуация" } : { scenario: { code: "preview", title: (markdown.match(/^# (.+)$/m) || [0, "Без названия"])[1], nodes: [] }, graphErrors: [] });
  return apiFetch(`/api/editor/scenarios/import-markdown?save=${save ? "true" : "false"}`, { method: "POST", body: JSON.stringify({ markdown }) });
}

export function editorExport(code) {
  if (USE_MOCKS) return delay({ code, title: "Демо", nodes: [] });
  return apiFetch(`/api/editor/scenarios/${encodeURIComponent(code)}`);
}

const MOCK_EVENTS = [];

export function adminEvents() {
  if (USE_MOCKS) return delay(MOCK_EVENTS.slice());
  return apiFetch("/api/admin/challenges");
}

export function adminCreateEvent(body) {
  if (USE_MOCKS) {
    const e = { id: `ev-${Date.now()}`, code: `event-${Date.now() % 100000}`, ...body, active: new Date(body.startsAt) <= new Date() && new Date(body.endsAt) >= new Date() };
    MOCK_EVENTS.unshift(e);
    return delay(e);
  }
  return apiFetch("/api/admin/challenges", { method: "POST", body: JSON.stringify(body) });
}

export function adminFinishEvent(id) {
  if (USE_MOCKS) {
    const e = MOCK_EVENTS.find((x) => x.id === id);
    if (e) { e.active = false; e.endsAt = new Date().toISOString(); }
    return delay(e);
  }
  return apiFetch(`/api/admin/challenges/${id}/finish`, { method: "POST" });
}

export function getChallenges() {
  if (USE_MOCKS) {
    const now = Date.now();
    return delay(MOCK_EVENTS.filter((e) => e.active).map((e) => ({ ...e, current: 0, completed: false })).concat([
      { code: "month-safety", title: "Месяц без критических ошибок", description: "Пройдите 5 ситуаций блока «Порядок и безопасность» без критических ошибок", goalType: "BLOCK_SCENARIOS_NO_FAILURE", targetBlock: "safety", targetCount: 5, rewardPoints: 300, startsAt: new Date(now - 864e5 * 10).toISOString(), endsAt: new Date(now + 864e5 * 20).toISOString(), current: 2, completed: false }
    ]));
  }
  return apiFetch(`/api/gamification/challenges?playerId=${encodeURIComponent(getPlayerId())}`);
}

const MOCK_AWARDS = [
  { id: "aw-1", code: "award-demo1", title: "Наставник смены", description: "Помог коллеге-стажёру на рейсе", shape: "shield", glyph: "users", verifiedOnly: false, earned: false, grantedCount: 3 },
  { id: "aw-2", code: "award-demo2", title: "Благодарность пассажира", description: "Отмечен в отзыве пассажира", shape: "octagon", glyph: "smile", verifiedOnly: true, earned: false, grantedCount: 1 }
];

export function adminAwards() {
  if (USE_MOCKS) return delay(MOCK_AWARDS.slice());
  return apiFetch("/api/admin/awards");
}

export function adminCreateAward(body) {
  if (USE_MOCKS) {
    const a = { id: `aw-${Date.now()}`, code: `award-${Date.now() % 1e5}`, ...body, earned: false, grantedCount: 0 };
    MOCK_AWARDS.unshift(a);
    return delay(a);
  }
  return apiFetch("/api/admin/awards", { method: "POST", body: JSON.stringify(body) });
}

export function adminGrantAward(id, player) {
  if (USE_MOCKS) {
    const a = MOCK_AWARDS.find((x) => x.id === id);
    if (a) {
      a.grantedCount += 1;
      const acc = getAccount();
      if (acc && (player === acc.login || player === acc.id)) { a.earned = true; a.earnedAt = new Date().toISOString(); }
    }
    return delay(a);
  }
  return apiFetch(`/api/admin/awards/${id}/grant`, { method: "POST", body: JSON.stringify({ player }) });
}

/** Награды администратора с отметкой полученных текущим игроком (пустой список, если backend их не знает). */
export function getCustomAwards() {
  if (USE_MOCKS) return delay(MOCK_AWARDS.slice());
  return apiFetch(`/api/gamification/custom-awards?playerId=${encodeURIComponent(getPlayerId())}`).catch(() => []);
}


// =======================================================================
// Администрирование: учётные записи, игроки, включение/выключение сценариев.
// =======================================================================

const MOCK_USERS = [
  { id: "00000000-0000-0000-0000-000000000001", login: "admin", email: "admin@demo.local", displayName: "Администратор", role: "ADMIN", verified: false, createdAt: "2026-09-01T09:00:00Z" },
  { id: "00000000-0000-0000-0000-000000000002", login: "esia_ivanova", email: "ivanova@esia.mock.local", displayName: "Иванова Мария Сергеевна", role: "USER", verified: true, createdAt: "2026-09-12T10:20:00Z" },
  { id: "00000000-0000-0000-0000-000000000003", login: "orlov", email: "orlov@demo.local", displayName: "Орлов Денис", role: "USER", verified: false, createdAt: "2026-09-20T15:05:00Z" },
  { id: "00000000-0000-0000-0000-000000000004", login: "belova", email: "belova@demo.local", displayName: "Белова Анна", role: "USER", verified: false, createdAt: "2026-09-24T08:40:00Z" }
];

export function adminUsers(q) {
  if (USE_MOCKS) {
    const n = String(q || "").toLowerCase();
    return delay(MOCK_USERS.filter((u) => !n || [u.login, u.email, u.displayName].some((x) => String(x || "").toLowerCase().includes(n))));
  }
  return apiFetch(`/api/admin/users${q ? `?q=${encodeURIComponent(q)}` : ""}`);
}

export function adminUpdateUser(id, patch) {
  if (USE_MOCKS) {
    const u = MOCK_USERS.find((x) => x.id === id);
    if (u) Object.assign(u, patch);
    return delay(u);
  }
  return apiFetch(`/api/admin/users/${id}`, { method: "PATCH", body: JSON.stringify(patch) });
}

export function adminPlayers() {
  if (USE_MOCKS) {
    const names = ["Иванова Мария", "Орлов Денис", "Белова Анна", "Кузнецов Артём", "Смирнова Ольга", "Громов Илья", "Никитина Вера", "Фёдоров Павел"];
    return delay(names.map((n, i) => ({ playerId: `p-${i}`, displayName: n, teamName: i % 2 ? "Депо Москва-Ленинградская" : "Депо Санкт-Петербург", totalScore: 2400 - i * 230, totalPlaythroughs: 40 - i * 4, successRate: 0.78 - i * 0.05, avgLoyaltyScore: 74 - i * 2, avgSafetyScore: 81 - i * 3, lastActivity: new Date(Date.now() - i * 36e5 * 7).toISOString() })));
  }
  return apiFetch("/api/admin/stats/players");
}

let MOCK_ADMIN_SCENARIOS = null;

export function adminScenarios() {
  if (USE_MOCKS) {
    if (MOCK_ADMIN_SCENARIOS) return delay(MOCK_ADMIN_SCENARIOS.slice());
    return listScenarios().then((d) => {
      MOCK_ADMIN_SCENARIOS = d.situations.map((s) => ({ code: s.code || `s-${s.id}`, title: s.title, block: s.block, flagship: s.flagship, active: true, version: 1 }));
      return MOCK_ADMIN_SCENARIOS.slice();
    });
  }
  return apiFetch("/api/admin/scenarios");
}

export function adminSetScenarioActive(code, active) {
  if (USE_MOCKS) {
    const s = (MOCK_ADMIN_SCENARIOS || []).find((x) => x.code === code);
    if (s) s.active = active;
    return delay(s);
  }
  return apiFetch(`/api/admin/scenarios/${encodeURIComponent(code)}`, { method: "PATCH", body: JSON.stringify({ active }) });
}

// =======================================================================
// Экзамен: набор сценариев подряд без подсказок, единая оценка в конце (см. README backend).
// Во время экзамена шкалы и дельты не показываются, разбор пунктов открывается после завершения.
// =======================================================================

const MOCK_EXAMS = {};

export function createExam({ carClass = "STANDARD", size = 5 } = {}) {
  if (USE_MOCKS) {
    return listScenarios().then((d) => {
      const byBlock = {};
      d.situations.forEach((s) => { (byBlock[s.block] = byBlock[s.block] || []).push(s); });
      const pool = Object.keys(byBlock).map((b) => byBlock[b][Math.floor(Math.random() * byBlock[b].length)]);
      const picked = pool.sort(() => Math.random() - 0.5).slice(0, size);
      const exam = {
        examId: `exam-${Date.now()}`, carClass, status: "IN_PROGRESS", size: picked.length, currentIndex: 0, startedAt: new Date().toISOString(), finishedAt: null, result: null,
        scenarios: picked.map((s, i) => ({ sortOrder: i, scenarioId: s.id, scenarioCode: s.code, block: s.block, title: s.title, flagship: s.flagship, userProgressId: null, completed: false, outcome: null, loyaltyScore: null, safetyScore: null }))
      };
      MOCK_EXAMS[exam.examId] = exam;
      return delay(examView(exam));
    });
  }
  return apiFetch(`/api/exams?carClass=${encodeURIComponent(carClass)}&size=${size}`, { method: "POST" }).then(examView);
}

export function getExam(examId) {
  if (USE_MOCKS) {
    const exam = MOCK_EXAMS[examId];
    if (!exam) return Promise.reject(new Error("not_found"));
    // В моке пункт считается пройденным, когда его прохождение завершено.
    exam.scenarios.forEach((it) => {
      const sess = it.userProgressId && MOCK_SESSIONS_FOR_EXAM[it.userProgressId];
      if (sess && sess.done && !it.completed) {
        it.completed = true;
        it.loyaltyScore = sess.scales.loyalty;
        it.safetyScore = sess.scales.safety;
        it.outcome = sess.scales.safety >= 60 && sess.scales.loyalty >= 50 ? "SUCCESS" : sess.scales.safety >= 45 ? "PARTIAL" : "FAILURE";
      }
    });
    const next = exam.scenarios.findIndex((it) => !it.completed);
    exam.currentIndex = next === -1 ? exam.scenarios.length : next;
    if (next === -1 && !exam.result) {
      const n = exam.scenarios.length || 1;
      const avgL = exam.scenarios.reduce((a, it) => a + it.loyaltyScore, 0) / n;
      const avgS = exam.scenarios.reduce((a, it) => a + it.safetyScore, 0) / n;
      const sr = exam.scenarios.filter((it) => it.outcome === "SUCCESS").length / n;
      const grade = avgS < 60 ? (avgS < 45 ? "UNSATISFACTORY" : "SATISFACTORY") : sr >= 0.8 && avgL >= 70 ? "EXCELLENT" : sr >= 0.5 ? "GOOD" : "SATISFACTORY";
      exam.status = "COMPLETED";
      exam.finishedAt = new Date().toISOString();
      exam.result = { avgLoyaltyScore: avgL, avgSafetyScore: avgS, successRate: sr, grade, weakBlocks: exam.scenarios.filter((it) => it.outcome !== "SUCCESS").map((it) => it.block) };
    }
    return delay(examView(exam));
  }
  return apiFetch(`/api/exams/${examId}`).then(examView);
}

const MOCK_SESSIONS_FOR_EXAM = {};

/** Начать текущий пункт экзамена — ответ той же формы, что у startScenario. */
export function startExamItem(examId) {
  if (USE_MOCKS) {
    const exam = MOCK_EXAMS[examId];
    const it = exam && exam.scenarios.find((x) => !x.completed);
    if (!it) return Promise.resolve({ error: "not_found" });
    return mockStartScenario(it.scenarioId, exam.carClass).then((res) => {
      if (res && !res.error) {
        it.userProgressId = res.sessionId;
        MOCK_SESSIONS_FOR_EXAM[res.sessionId] = { done: false, scales: res.scales };
      }
      return res;
    });
  }
  return Promise.all([
    apiFetch(`/api/exams/${examId}/current`, { method: "POST" }),
    loadCatalog().catch(() => null)
  ]).then(([progressState, catalog]) => {
    if (!progressState || !progressState.currentNode) return { error: "not_found" };
    return {
      sessionId: progressState.progressId,
      scenario: { id: progressState.scenarioId, title: progressState.scenarioCode, blockLabel: "" },
      carClass: progressState.carClass,
      scales: null,
      node: realNodeView(progressState.currentNode),
      catalog
    };
  }).catch(() => ({ error: "not_found" }));
}

/** В моке — отметить завершение пункта экзамена (реальный backend делает это сам). */
export function markExamItemDone(sessionId, scales) {
  if (MOCK_SESSIONS_FOR_EXAM[sessionId]) MOCK_SESSIONS_FOR_EXAM[sessionId] = { done: true, scales: scales || MOCK_SESSIONS_FOR_EXAM[sessionId].scales };
}

function examView(e) {
  return {
    ...e,
    scenarios: (e.scenarios || []).map((it) => ({ ...it, block: String(it.block || "misc").toLowerCase(), blockLabel: blockTitle(String(it.block || "misc").toLowerCase()) })),
    result: e.result ? { ...e.result, weakBlocks: (e.result.weakBlocks || []).map((b) => String(b).toLowerCase()) } : null
  };
}

export const api = {
  USE_MOCKS,
  getPlayerId,
  listScenarios,
  startScenario,
  choose,
  timeout,
  getDebrief,
  getProfile,
  getLeaderboard,
  getAchievements,
  getNotifications,
  markNotificationRead,
  markAllNotificationsRead,
  getCompetencyAnalytics,
  mapWsAppliedChoice
};

export default api;
