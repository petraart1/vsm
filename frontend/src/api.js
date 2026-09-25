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
  return "";
}

const API_BASE = resolveApiBase();

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
  if (options.requiresPlayer) headers["X-Player-Id"] = getPlayerId();
  return fetch(API_BASE + path, { method: options.method || "GET", headers }).then((res) => {
    if (res.status === 204) return null;
    return res.text().then((text) => {
      let body = null;
      if (text) {
        try { body = JSON.parse(text); } catch (e) { body = null; }
      }
      if (!res.ok) {
        const err = new Error((body && body.message) || `${res.status} ${res.statusText}`);
        err.code = body && body.error;
        err.status = res.status;
        throw err;
      }
      return body;
    });
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
  return fetch("/data/situations-index.json")
    .then((r) => r.json())
    .then((json) => { CATALOG = json; return CATALOG; });
}

function blockLabelFor(catalog, blockKey) {
  return (catalog && catalog.blocks && catalog.blocks[blockKey]) || blockKey;
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
    const situations = summaries.map((s) => {
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
        lastResult: p ? { loyalty: p.loyalty, safety: p.safety, verdict: p.verdict } : null
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

function realStartScenario(scenarioId) {
  return apiFetch(`/api/scenarios/${scenarioId}`, { method: "GET" }).catch(() => null)
    .then((summary) => apiFetch(`/api/scenarios/${scenarioId}/progress`, { method: "POST", requiresPlayer: true })
      .then((progressState) => {
        if (!progressState.currentNode) return { error: "not_found" };
        return loadCatalog().catch(() => null).then((catalog) => ({
          sessionId: progressState.progressId,
          scenario: {
            id: progressState.scenarioId,
            title: summary ? summary.title : progressState.scenarioCode,
            blockLabel: summary ? blockLabelFor(catalog, summary.block) : ""
          },
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

function realGetLeaderboard(opts = {}) {
  const qs = `?limit=${opts.limit || 20}&playerId=${encodeURIComponent(opts.playerId || getPlayerId())}`;
  return apiFetch(`/api/gamification/leaderboard${qs}`, { method: "GET" });
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
        scenario: { id: d.scenarioId, title: d.scenarioTitle, blockLabel: blockLabelFor(catalog, d.scenarioBlock) },
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
        lastResult: p ? { loyalty: p.loyalty, safety: p.safety, verdict: p.verdict } : null
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

function mockStartScenario(scenarioId) {
  return loadCatalog().then((catalog) => {
    const situation = catalog.situations.filter((s) => String(s.id) === String(scenarioId))[0];
    if (!situation) return delay({ error: "not_found" });
    const graph = FULL_MOCK_GRAPHS[situation.id] || buildGenericGraph(situation);
    mockSessionCounter += 1;
    const sessionId = `sess-${mockSessionCounter}-${Date.now()}`;
    mockSessions[sessionId] = {
      scenarioId: situation.id, scenarioTitle: situation.title,
      blockLabel: blockLabelFor(catalog, situation.block), graph, history: [],
      currentNodeId: graph.startNode, scales: { loyalty: 60, safety: 60 }
    };
    const node = graph.nodes[graph.startNode];
    return delay({
      sessionId,
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
    scenario: { id: session.scenarioId, title: session.scenarioTitle, blockLabel: session.blockLabel },
    verdict, interrupted: false, finalScales, timeline,
    keyMoment, summary, normReferences: [],
    accrual: { totalScore: awardedPoints, scenariosCompleted: Object.keys(readProgress()).length + 1, recentAchievements: achievements }
  };
  return delay(debrief).then((d) => {
    recordCompletion(session.scenarioId, finalScales.loyalty, finalScales.safety, verdict);
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
    const me = profile.scenariosCompleted > 0
      ? { rank: 1, playerId: profile.playerId, displayName: profile.displayName, totalScore: profile.totalScore, scenariosCompleted: profile.scenariosCompleted }
      : null;
    return { top: me ? [me] : [], me };
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
export function startScenario(scenarioId) { return USE_MOCKS ? mockStartScenario(scenarioId) : realStartScenario(scenarioId); }
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
