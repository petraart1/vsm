/**
 * Клиент живого WebSocket-канала прохождения (см. README, раздел «WebSocket: живой таймер и
 * шкалы» — контракт сообщений в ru.vsm.backend.ws.dto.ProgressWsMessage, только читать).
 *
 * REST остаётся источником истины и полностью работает без WebSocket — этот клиент только
 * подписывается на пуш поверх уже идущего REST-прохождения. Тонкая обёртка без какой-либо
 * бизнес-логики: строит URL от текущего origin (nginx/vite уже проксируют /ws на backend, см.
 * nginx.conf/vite.config.js), парсит JSON и передаёт сообщение вызывающей стороне, переподключается
 * ограниченное число раз при обрыве. Весь стейт прохождения остаётся на экране.
 */

const MAX_RECONNECT_ATTEMPTS = 3;
const RECONNECT_DELAY_MS = 1500;

/**
 * Открывает канал для progressId. handlers.onMessage(msg) вызывается на каждое сообщение
 * (type: tick/timeout/state/completed), handlers.onOpen() — при каждом успешном подключении
 * (в т.ч. после переподключения), handlers.onUnavailable() — один раз, если соединение так и не
 * удалось открыть/удержать (попытки переподключения исчерпаны) — сигнал экрану окончательно
 * перейти на чистый REST-путь.
 *
 * Возвращает функцию закрытия канала — вызывать при уходе с экрана или смене прохождения,
 * дальнейшие переподключения после неё не выполняются.
 */
export function connectProgressChannel(progressId, playerId, handlers) {
  let attempts = 0;
  let closedByCaller = false;
  let socket = null;
  let reconnectTimeoutId = null;

  function wsUrl() {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${window.location.host}/ws/progress/${progressId}?playerId=${encodeURIComponent(playerId)}`;
  }

  function open() {
    let ws;
    try {
      ws = new WebSocket(wsUrl());
    } catch (e) {
      scheduleReconnectOrGiveUp();
      return;
    }
    socket = ws;
    ws.onopen = () => {
      attempts = 0;
      handlers.onOpen && handlers.onOpen();
    };
    ws.onmessage = (ev) => {
      let msg = null;
      try { msg = JSON.parse(ev.data); } catch (e) { return; }
      handlers.onMessage && handlers.onMessage(msg);
    };
    ws.onclose = () => {
      socket = null;
      if (closedByCaller) return;
      handlers.onDrop && handlers.onDrop();
      scheduleReconnectOrGiveUp();
    };
    // onerror у WebSocket всегда сопровождается последующим onclose — переподключение
    // планируется там же, здесь достаточно не дать необработанному событию всплыть.
    ws.onerror = () => {};
  }

  function scheduleReconnectOrGiveUp() {
    if (closedByCaller) return;
    attempts += 1;
    if (attempts > MAX_RECONNECT_ATTEMPTS) {
      handlers.onUnavailable && handlers.onUnavailable();
      return;
    }
    reconnectTimeoutId = setTimeout(open, RECONNECT_DELAY_MS);
  }

  open();

  return function close() {
    closedByCaller = true;
    if (reconnectTimeoutId) clearTimeout(reconnectTimeoutId);
    if (socket) {
      socket.onopen = null;
      socket.onmessage = null;
      socket.onclose = null;
      socket.onerror = null;
      socket.close();
      socket = null;
    }
  };
}
