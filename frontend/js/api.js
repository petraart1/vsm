/* global VSM */
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
(function (global) {
  "use strict";

  var USE_MOCKS = false;
  var NETWORK_DELAY_MS = 220;

  // =======================================================================
  // Конфигурация: база API + playerId (общие для мока и реального режима —
  // playerId нужен в обоих, база API только в реальном).
  // =======================================================================

  var API_BASE_STORAGE_KEY = "vsm.apiBase.v1";
  var PLAYER_ID_STORAGE_KEY = "vsm.playerId.v1";
  var DEV_BACKEND_ORIGIN = "http://localhost:8080"; // фронт на :3000 (dev) -> backend на :8080

  /**
   * База API: по умолчанию тот же origin, что и у фронта (для демо backend раздаёт статику
   * React сам, same-origin, fetch("/api/...") без префикса). Если фронт
   * запущен отдельно на :3000 (dev, `python3 -m http.server 3000`) — по умолчанию бьём в
   * localhost:8080. Можно переопределить один раз через ?apiBase=http://host:port в URL —
   * переопределение сохраняется в localStorage (удобно, когда порт backend занят и используется
   * SERVER_PORT=8081).
   */
  function resolveApiBase() {
    try {
      var fromQuery = new URLSearchParams(global.location.search || "").get("apiBase");
      if (fromQuery) {
        try { localStorage.setItem(API_BASE_STORAGE_KEY, fromQuery); } catch (e) { /* ignore */ }
        return fromQuery.replace(/\/$/, "");
      }
    } catch (e) { /* ignore */ }
    try {
      var stored = localStorage.getItem(API_BASE_STORAGE_KEY);
      if (stored) return stored.replace(/\/$/, "");
    } catch (e) { /* ignore */ }
    if (global.location && global.location.port === "3000") return DEV_BACKEND_ORIGIN;
    return "";
  }

  var API_BASE = resolveApiBase();

  function generateUuidV4() {
    if (global.crypto && typeof global.crypto.randomUUID === "function") {
      return global.crypto.randomUUID();
    }
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
      var r = (Math.random() * 16) | 0;
      var v = c === "x" ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }

  /** Один UUID на браузер, хранится в localStorage; используется как X-Player-Id и как
   * playerId в gamification-эндпоинтах — единое пространство идентификаторов на клиенте. */
  function getPlayerId() {
    var id = null;
    try { id = localStorage.getItem(PLAYER_ID_STORAGE_KEY); } catch (e) { /* приватный режим */ }
    if (!id) {
      id = generateUuidV4();
      try { localStorage.setItem(PLAYER_ID_STORAGE_KEY, id); } catch (e) { /* ignore */ }
    }
    return id;
  }

  /** Тонкая обёртка над fetch: базовый URL, X-Player-Id (когда нужен), парсинг JSON/ошибок
   * в едином формате {error, message} (см. scenario/ScenarioExceptionHandler). */
  function apiFetch(path, options) {
    options = options || {};
    var headers = { Accept: "application/json" };
    if (options.requiresPlayer) headers["X-Player-Id"] = getPlayerId();
    return fetch(API_BASE + path, { method: options.method || "GET", headers: headers }).then(function (res) {
      if (res.status === 204) return null;
      return res.text().then(function (text) {
        var body = null;
        if (text) {
          try { body = JSON.parse(text); } catch (e) { body = null; }
        }
        if (!res.ok) {
          var err = new Error((body && body.message) || (res.status + " " + res.statusText));
          err.code = body && body.error;
          err.status = res.status;
          throw err;
        }
        return body;
      });
    });
  }

  // =======================================================================
  // Локальный каталог ситуаций (frontend/data/situations-index.json) — используется
  // ТОЛЬКО как декоративное обогащение реального списка сценариев (подпись блока на
  // русском, признак эскалации из датасета), не как источник самого списка/графа.
  // =======================================================================

  var CATALOG = null;

  function loadCatalog() {
    if (CATALOG) return Promise.resolve(CATALOG);
    return fetch("data/situations-index.json")
      .then(function (r) { return r.json(); })
      .then(function (json) { CATALOG = json; return CATALOG; });
  }

  function blockLabelFor(catalog, blockKey) {
    return (catalog && catalog.blocks && catalog.blocks[blockKey]) || blockKey;
  }

  // =======================================================================
  // Локальный кэш завершённых прохождений (localStorage) — backend не даёт единого
  // "список прохождений игрока" эндпоинта для scenario-list.md (статус "пройден"/
  // последний результат), поэтому это чисто клиентская декорация, наполняется из
  // getDebrief(). Ключ — id сценария как строка (UUID в реальном режиме, число в моке).
  // =======================================================================

  var PROGRESS_KEY = "vsm.completedScenarios.v2";

  function readProgress() {
    try { return JSON.parse(localStorage.getItem(PROGRESS_KEY)) || {}; } catch (e) { return {}; }
  }

  function writeProgress(progress) {
    try { localStorage.setItem(PROGRESS_KEY, JSON.stringify(progress)); } catch (e) { /* ignore */ }
  }

  function recordCompletion(scenarioId, loyalty, safety, verdict) {
    var progress = readProgress();
    progress[String(scenarioId)] = {
      completedAt: new Date().toISOString(),
      loyalty: loyalty,
      safety: safety,
      verdict: verdict
    };
    writeProgress(progress);
  }

  // =======================================================================
  // Ролевая модель: подписи шагов — те же строки, что отдаёт backend
  // (ru.vsm.backend.feedback.dto.RoleStep#label), используются и в моке для единообразия.
  // =======================================================================

  var ROLE_STEP_LABELS = {
    acknowledge: "Признать ситуацию",
    rule: "Обозначить правило",
    solution: "Предложить решение",
    reassure: "Заверить"
  };
  var ALL_ROLE_STEP_KEYS = ["acknowledge", "rule", "solution", "reassure"];

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
      choices: (n.choices || []).map(function (c) { return { id: c.id, text: c.text }; })
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
    var isFinal = res.status === "COMPLETED";
    var rawNext = res.nextNode;
    var deltas = { loyalty: res.loyaltyDelta, safety: res.safetyDelta };
    var outcomeSummary = rawNext && rawNext.terminal ? rawNext.outcomeSummary : null;
    return {
      reaction: {
        text: realReactionText(deltas, res.wasTimeout, outcomeSummary),
        deltas: deltas,
        escalation: !!(rawNext && rawNext.type === "ESCALATION"),
        hiddenPenalty: null,
        wasTimeout: !!res.wasTimeout
      },
      scales: { loyalty: res.loyaltyScore, safety: res.safetyScore },
      isFinal: isFinal,
      node: rawNext && !isFinal ? realNodeView(rawNext) : null
    };
  }

  function realListScenarios() {
    return Promise.all([
      apiFetch("/api/scenarios", { method: "GET" }),
      loadCatalog().catch(function () { return null; })
    ]).then(function (results) {
      var summaries = results[0] || [];
      var catalog = results[1];
      var progress = readProgress();
      var blocks = {};
      var situations = summaries.map(function (s) {
        var label = blockLabelFor(catalog, s.block);
        var localMeta = catalog && catalog.situations
          ? catalog.situations.filter(function (x) { return x.id === s.situationRef; })[0]
          : null;
        if (!blocks[s.block]) blocks[s.block] = { key: s.block, label: label, total: 0, completed: 0 };
        blocks[s.block].total += 1;
        var p = progress[String(s.id)];
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
      var completedCount = situations.filter(function (s) { return s.status === "completed"; }).length;
      return {
        totalCount: situations.length,
        completedCount: completedCount,
        blocks: Object.keys(blocks).map(function (k) { return blocks[k]; }),
        situations: situations
      };
    });
  }

  function realStartScenario(scenarioId) {
    return apiFetch("/api/scenarios/" + scenarioId, { method: "GET" }).catch(function () { return null; })
      .then(function (summary) {
        return apiFetch("/api/scenarios/" + scenarioId + "/progress", { method: "POST", requiresPlayer: true })
          .then(function (progressState) {
            if (!progressState.currentNode) return { error: "not_found" };
            return loadCatalog().catch(function () { return null; }).then(function (catalog) {
              return {
                sessionId: progressState.progressId,
                scenario: {
                  id: progressState.scenarioId,
                  title: summary ? summary.title : progressState.scenarioCode,
                  blockLabel: summary ? blockLabelFor(catalog, summary.block) : ""
                },
                scales: { loyalty: progressState.loyaltyScore, safety: progressState.safetyScore },
                node: realNodeView(progressState.currentNode)
              };
            });
          });
      })
      .catch(function () { return { error: "not_found" }; });
  }

  function realApplyChoiceRequest(progressId, pathSuffix) {
    return apiFetch("/api/scenarios/progress/" + progressId + pathSuffix, { method: "POST", requiresPlayer: true })
      .then(realMapChoiceApplied, function (err) {
        return { error: err.code || "choice_failed" };
      });
  }

  function realChoose(progressId, choiceId) {
    return realApplyChoiceRequest(progressId, "/choices/" + choiceId);
  }

  function realTimeout(progressId) {
    return realApplyChoiceRequest(progressId, "/timeout");
  }

  function realGetProfile(playerId) {
    return Promise.all([
      apiFetch("/api/gamification/profile/" + playerId, { method: "GET" }),
      loadCatalog().catch(function () { return null; })
    ]).then(function (results) {
      var profile = results[0];
      var catalog = results[1];
      return Object.assign({}, profile, {
        blockProgress: (profile.blockProgress || []).map(function (bp) {
          return Object.assign({}, bp, { blockLabel: blockLabelFor(catalog, bp.block) });
        })
      });
    });
  }

  function realGetLeaderboard(opts) {
    opts = opts || {};
    var qs = "?limit=" + (opts.limit || 20) + "&playerId=" + encodeURIComponent(opts.playerId || getPlayerId());
    return apiFetch("/api/gamification/leaderboard" + qs, { method: "GET" });
  }

  function realGetAchievements(playerId) {
    var qs = playerId ? "?playerId=" + encodeURIComponent(playerId) : "";
    return apiFetch("/api/gamification/achievements" + qs, { method: "GET" });
  }

  function realGetDebrief(progressId) {
    var playerId = getPlayerId();
    return apiFetch("/api/feedback/debrief/" + progressId, { method: "GET" }).then(function (d) {
      return loadCatalog().catch(function () { return null; }).then(function (catalog) {
        return Promise.all([
          realGetProfile(playerId).catch(function () { return null; }),
          realGetAchievements(playerId).catch(function () { return null; })
        ]).then(function (results) {
          var profile = results[0];
          var achievements = results[1];
          var debrief = {
            progressId: d.userProgressId,
            scenario: { id: d.scenarioId, title: d.scenarioTitle, blockLabel: blockLabelFor(catalog, d.scenarioBlock) },
            verdict: d.verdict,
            interrupted: !!d.interrupted,
            finalScales: { loyalty: d.finalLoyaltyScore, safety: d.finalSafetyScore },
            timeline: (d.timeline || []).map(function (t) {
              return {
                sequenceIndex: t.sequenceIndex,
                nodeText: t.nodeText,
                choiceText: t.wasTimeout ? "Время вышло" : t.choiceText,
                wasTimeout: !!t.wasTimeout,
                deltas: { loyalty: t.loyaltyDelta, safety: t.safetyDelta },
                roleStepsCompleted: t.roleStepsCompleted || [],
                roleStepsSkipped: t.roleStepsSkipped || [],
                scaleConflict: !!t.scaleConflict,
                escalation: t.nodeType === "ESCALATION",
                explanation: t.explanation || null
              };
            }),
            keyMoment: d.keyMoment
              ? {
                  nodeText: d.keyMoment.nodeText,
                  chosenChoiceText: d.keyMoment.chosenChoiceText,
                  chosenDeltas: { loyalty: d.keyMoment.chosenLoyaltyDelta, safety: d.keyMoment.chosenSafetyDelta },
                  betterChoiceText: d.keyMoment.betterChoiceText,
                  betterDeltas: { loyalty: d.keyMoment.betterLoyaltyDelta, safety: d.keyMoment.betterSafetyDelta },
                  adviceText: d.keyMoment.adviceText
                }
              : null,
            summary: d.summary,
            normReferences: d.normReferences || [],
            accrual: profile
              ? {
                  totalScore: profile.totalScore,
                  scenariosCompleted: profile.scenariosCompleted,
                  recentAchievements: (achievements || profile.recentAchievements || []).filter(function (a) {
                    return a.earned;
                  })
                }
              : { totalScore: null, scenariosCompleted: null, recentAchievements: [] }
          };
          recordCompletion(debrief.scenario.id, debrief.finalScales.loyalty, debrief.finalScales.safety, debrief.verdict);
          return debrief;
        });
      });
    }, function () {
      return { error: "debrief_unavailable" };
    });
  }

  // =======================================================================================
  // ===================================  МОК-РЕЖИМ  ========================================
  // (аварийный переключатель USE_MOCKS=true — например, если backend недоступен на демо;
  // возвращает объекты той же формы, что и реальный режим выше.)
  // =======================================================================================

  var FLAGSHIP_IDS_MOCK = [1, 6, 9, 19, 24, 30, 33, 42];

  var FULL_MOCK_GRAPHS = {
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
    var esc = situation.escalation;
    var failure = situation.failure_pattern;
    return {
      startNode: "g1",
      nodes: {
        g1: {
          id: "g1", avatarInitials: "ПС", contextNote: null,
          situationText: "Ситуация: «" + situation.title + "». Пассажир обращается к Вам с этим вопросом и ждёт реакции.",
          timer: null,
          choices: [
            { id: "g1-good", text: "Понимаю Вас. По правилам в этой ситуации предусмотрено следующее решение — сейчас всё уточню и вернусь к Вам. Благодарю за обращение.", effects: { loyalty: 8, safety: 6 }, escalation: !!esc, roleModelSteps: ["acknowledge", "rule", "solution", "reassure"], reactionText: "Пассажир удовлетворён полным и вежливым ответом.", next: "g2-good" },
            { id: "g1-partial", text: "Хорошо, сейчас разберёмся, подождите.", effects: { loyalty: 1, safety: 2 }, escalation: false, roleModelSteps: ["solution"], reactionText: "Пассажир доволен решением, но реакция была суховатой.", next: "g2-partial" },
            { id: "g1-bad", text: failure ? "Действие, характерное для провала: " + failure + "." : "Проигнорировать просьбу и уйти.", effects: { loyalty: -9, safety: -9 }, escalation: false, roleModelSteps: [], reactionText: "Пассажир недоволен, ситуация могла перерасти в конфликт.", next: "g2-bad" }
          ]
        },
        "g2-good": { id: "g2-good", avatarInitials: "ПС", contextNote: null, situationText: "Пассажир благодарит за оперативную и вежливую помощь.", timer: null, choices: [{ id: "g2-good-end", text: "Хорошего пути! Обращайтесь, если понадобится что-то ещё.", effects: { loyalty: 2, safety: 0 }, escalation: false, roleModelSteps: ["reassure"], reactionText: "Ситуация исчерпана.", next: null }] },
        "g2-partial": { id: "g2-partial", avatarInitials: "ПС", contextNote: null, situationText: "Пассажир принимает решение, но остаётся сдержанным.", timer: null, choices: [{ id: "g2-partial-end", text: "Всего доброго.", effects: { loyalty: 0, safety: 0 }, escalation: false, roleModelSteps: [], reactionText: "Ситуация исчерпана без особого впечатления у пассажира.", next: null }] },
        "g2-bad": { id: "g2-bad", avatarInitials: "ПС", contextNote: null, situationText: "Пассажир жалуется на обслуживание, инцидент зафиксирован.", timer: null, choices: [{ id: "g2-bad-end", text: "Приношу извинения за неудобства.", effects: { loyalty: 1, safety: 0 }, escalation: false, roleModelSteps: ["acknowledge"], reactionText: "Пассажир немного смягчается, но осадок остался.", next: null }] }
      }
    };
  }

  var mockSessions = {};
  var mockSessionCounter = 0;

  function delay(value) {
    return new Promise(function (resolve) { setTimeout(function () { resolve(value); }, NETWORK_DELAY_MS); });
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
      choices: node.choices.filter(function (c) { return !c.isTimeout; }).map(function (c) { return { id: c.id, text: c.text }; })
    };
  }

  function mockListScenarios() {
    return loadCatalog().then(function (catalog) {
      var progress = readProgress();
      var blocks = {};
      Object.keys(catalog.blocks).forEach(function (key) {
        blocks[key] = { key: key, label: catalog.blocks[key], total: 0, completed: 0 };
      });
      var situations = catalog.situations.map(function (s) {
        var p = progress[String(s.id)];
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
        blocks: Object.keys(blocks).map(function (k) { return blocks[k]; }),
        situations: situations
      });
    });
  }

  function mockStartScenario(scenarioId) {
    return loadCatalog().then(function (catalog) {
      var situation = catalog.situations.filter(function (s) { return String(s.id) === String(scenarioId); })[0];
      if (!situation) return delay({ error: "not_found" });
      var graph = FULL_MOCK_GRAPHS[situation.id] || buildGenericGraph(situation);
      mockSessionCounter += 1;
      var sessionId = "sess-" + mockSessionCounter + "-" + Date.now();
      mockSessions[sessionId] = {
        scenarioId: situation.id, scenarioTitle: situation.title,
        blockLabel: blockLabelFor(catalog, situation.block), graph: graph, history: [],
        currentNodeId: graph.startNode, scales: { loyalty: 60, safety: 60 }
      };
      var node = graph.nodes[graph.startNode];
      return delay({
        sessionId: sessionId,
        scenario: { id: situation.id, title: situation.title, blockLabel: blockLabelFor(catalog, situation.block) },
        scales: mockSessions[sessionId].scales,
        node: mockNodeView(node)
      });
    });
  }

  function mockApplyChoice(sessionId, choiceId) {
    var session = mockSessions[sessionId];
    if (!session) return delay({ error: "session_not_found" });
    var node = session.graph.nodes[session.currentNodeId];
    var choice;
    if (choiceId === "__timeout__") {
      choice = node.choices.filter(function (c) { return c.isTimeout; })[0];
      if (!choice) {
        choice = node.choices.slice().sort(function (a, b) {
          return (a.effects.loyalty + a.effects.safety) - (b.effects.loyalty + b.effects.safety);
        })[0];
      }
    } else {
      choice = node.choices.filter(function (c) { return c.id === choiceId; })[0];
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

    var isFinal = !choice.next;
    var nextNode = null;
    if (!isFinal) {
      session.currentNodeId = choice.next;
      nextNode = mockNodeView(session.graph.nodes[choice.next]);
    }

    return delay({
      reaction: {
        text: choice.reactionText, deltas: choice.effects, escalation: !!choice.escalation,
        hiddenPenalty: choice.hiddenPenalty || null, wasTimeout: !!choice.isTimeout
      },
      scales: session.scales, isFinal: isFinal, node: nextNode
    });
  }

  function mockChoose(sessionId, choiceId) { return mockApplyChoice(sessionId, choiceId); }
  function mockTimeout(sessionId) { return mockApplyChoice(sessionId, "__timeout__"); }

  function mockGetDebrief(sessionId) {
    var session = mockSessions[sessionId];
    if (!session) return delay({ error: "session_not_found" });
    var finalScales = session.scales;
    var verdict = "Хорошо справились";
    if (finalScales.safety < 40) verdict = "Критическая ошибка безопасности";
    else if (finalScales.loyalty < 40 || finalScales.safety < 55) verdict = "Есть над чем поработать";

    var hadFullRoleModel = session.history.some(function (h) { return h.roleModelSteps.length === 4; });
    var summary = hadFullRoleModel
      ? "Все 4 шага ролевой модели соблюдены хотя бы в одном ответе, конфликт шкал решён осознанно. Так и продолжайте: признание → правило → решение → заверение."
      : "В ключевой развилке стоило пройти все 4 шага модели (признать → обозначить правило → предложить решение → заверить), а не ограничиваться одним из них.";

    var timeline = session.history.map(function (h, i) {
      var missing = h.roleModelSteps.length > 0
        ? ALL_ROLE_STEP_KEYS.filter(function (k) { return h.roleModelSteps.indexOf(k) === -1; })
        : [];
      return {
        sequenceIndex: i, nodeText: h.situationText, choiceText: h.choiceText, wasTimeout: h.isTimeout,
        deltas: h.effects,
        roleStepsCompleted: h.roleModelSteps.map(function (k) { return ROLE_STEP_LABELS[k]; }),
        roleStepsSkipped: missing.map(function (k) { return ROLE_STEP_LABELS[k]; }),
        scaleConflict: (h.effects.loyalty < 0) !== (h.effects.safety < 0) && (h.effects.loyalty !== 0 || h.effects.safety !== 0),
        escalation: h.escalation,
        explanation: h.hiddenPenalty || h.reactionText
      };
    });

    var awardedPoints = Math.round((finalScales.loyalty + finalScales.safety) / 2);
    var achievements = [];
    if (finalScales.safety >= 90) achievements.push({ code: "safety-master", title: "Страж безопасности", earned: true });
    if (finalScales.loyalty >= 90) achievements.push({ code: "loyalty-master", title: "Любимец пассажиров", earned: true });

    var debrief = {
      progressId: sessionId,
      scenario: { id: session.scenarioId, title: session.scenarioTitle, blockLabel: session.blockLabel },
      verdict: verdict, interrupted: false, finalScales: finalScales, timeline: timeline,
      keyMoment: null, summary: summary, normReferences: [],
      accrual: { totalScore: awardedPoints, scenariosCompleted: Object.keys(readProgress()).length + 1, recentAchievements: achievements }
    };
    return delay(debrief).then(function (d) {
      recordCompletion(session.scenarioId, finalScales.loyalty, finalScales.safety, verdict);
      return d;
    });
  }

  function mockGetProfile() {
    var progress = readProgress();
    var entries = Object.keys(progress).map(function (k) { return progress[k]; });
    var totalScore = entries.reduce(function (sum, p) { return sum + Math.round((p.loyalty + p.safety) / 2); }, 0);
    return delay({
      playerId: getPlayerId(),
      displayName: "Проводник-" + getPlayerId().slice(-4),
      totalScore: totalScore,
      scenariosCompleted: entries.length,
      totalScenariosAvailable: 51,
      blockProgress: [],
      recentAchievements: [],
      leaderboardRank: entries.length > 0 ? 1 : null
    });
  }

  function mockGetLeaderboard() {
    return mockGetProfile().then(function (profile) {
      var me = profile.scenariosCompleted > 0
        ? { rank: 1, playerId: profile.playerId, displayName: profile.displayName, totalScore: profile.totalScore, scenariosCompleted: profile.scenariosCompleted }
        : null;
      return { top: me ? [me] : [], me: me };
    });
  }

  var MOCK_ACHIEVEMENT_CATALOG = [
    { code: "FIRST_SCENARIO", title: "Первый рейс", description: "Завершите первое прохождение сценария.", category: "objem" },
    { code: "FLAWLESS_SAFETY", title: "Страж безопасности", description: "Успешное прохождение с высокой безопасностью.", category: "style" },
    { code: "PASSENGER_FAVORITE", title: "Любимец пассажиров", description: "Успешное прохождение с высокой лояльностью.", category: "style" },
    { code: "VERSATILE", title: "Универсал", description: "Пройдите сценарии из 3 разных блоков.", category: "objem" },
    { code: "VETERAN", title: "Ветеран", description: "10 завершённых прохождений.", category: "objem" }
  ];

  function mockGetAchievements() {
    var completed = Object.keys(readProgress()).length;
    return delay(MOCK_ACHIEVEMENT_CATALOG.map(function (a) {
      return Object.assign({}, a, { earned: a.code === "FIRST_SCENARIO" && completed > 0, earnedAt: null });
    }));
  }

  // =======================================================================================
  // ================================  ПУБЛИЧНОЕ API  ========================================
  // =======================================================================================

  function listScenarios() { return USE_MOCKS ? mockListScenarios() : realListScenarios(); }
  function startScenario(scenarioId) { return USE_MOCKS ? mockStartScenario(scenarioId) : realStartScenario(scenarioId); }
  function choose(sessionId, choiceId) { return USE_MOCKS ? mockChoose(sessionId, choiceId) : realChoose(sessionId, choiceId); }
  function timeout(sessionId) { return USE_MOCKS ? mockTimeout(sessionId) : realTimeout(sessionId); }
  function getDebrief(sessionId) { return USE_MOCKS ? mockGetDebrief(sessionId) : realGetDebrief(sessionId); }
  function getProfile(playerId) { return USE_MOCKS ? mockGetProfile() : realGetProfile(playerId || getPlayerId()); }
  function getLeaderboard(opts) { return USE_MOCKS ? mockGetLeaderboard() : realGetLeaderboard(opts); }
  function getAchievements(playerId) { return USE_MOCKS ? mockGetAchievements() : realGetAchievements(playerId || getPlayerId()); }

  global.VSM = global.VSM || {};
  global.VSM.api = {
    USE_MOCKS: USE_MOCKS,
    getPlayerId: getPlayerId,
    listScenarios: listScenarios,
    startScenario: startScenario,
    choose: choose,
    timeout: timeout,
    getDebrief: getDebrief,
    getProfile: getProfile,
    getLeaderboard: getLeaderboard,
    getAchievements: getAchievements
  };
})(window);
