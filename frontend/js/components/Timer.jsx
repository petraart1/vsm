/* global React, VSM */
(function (global) {
  "use strict";
  var e = React.createElement;

  /**
   * Таймер решения. Источник истины — серверный `deadlineAt` (абсолютный ISO-момент,
   * см. ru.vsm.backend.scenario.web.dto.NodeStateResponse#deadlineAt): каждую секунду remaining
   * пересчитывается от Date.now(), а не декрементируется локально — так таймер не расходится с
   * сервером при подтормаживании вкладки. `timerSeconds` используется только как приблизительная
   * длина отсчёта для порогов цвета (critical/urgent) и как fallback, если deadlineAt почему-то
   * не пришёл (тогда тикаем локально от timerSeconds, как раньше).
   *
   * props: timerSeconds (число, опц.), deadlineAt (ISO-строка, опц.), onExpire (колбэк без
   * аргументов, вызывается один раз). Родитель должен передавать key={node.id}, чтобы таймер
   * пересоздавался на каждом узле — реальный истёкший таймер обрабатывается вызовом
   * VSM.api.timeout(progressId) на уровне экрана, этот компонент только визуализирует отсчёт.
   */
  function Timer(props) {
    var onExpire = props.onExpire;
    var deadlineMs = props.deadlineAt ? new Date(props.deadlineAt).getTime() : null;

    function computeRemaining() {
      if (deadlineMs) return Math.max(0, Math.round((deadlineMs - Date.now()) / 1000));
      return null;
    }

    var totalSecondsRef = React.useRef(
      props.timerSeconds || (deadlineMs ? Math.max(1, computeRemaining()) : 1)
    );
    var state = React.useState(
      deadlineMs ? computeRemaining() : (props.timerSeconds || 0)
    );
    var remaining = state[0];
    var setRemaining = state[1];
    var expiredFired = React.useRef(false);

    React.useEffect(function () {
      if (remaining <= 0) {
        if (!expiredFired.current) {
          expiredFired.current = true;
          onExpire && onExpire();
        }
        return undefined;
      }
      var id = setTimeout(function () {
        setRemaining(deadlineMs ? computeRemaining() : function (r) { return r - 1; });
      }, 1000);
      return function () { clearTimeout(id); };
    }, [remaining]);

    var totalSeconds = totalSecondsRef.current || 1;
    var ratio = remaining / totalSeconds;
    var status = "normal";
    if (remaining <= 0) status = "expired";
    else if (ratio <= 0.1) status = "critical";
    else if (ratio <= 0.3) status = "urgent";

    var label = remaining > 0 ? remaining + " с" : "Время вышло";

    return e(
      "div",
      { className: "timer timer--" + status, role: "timer", "aria-live": "polite" },
      e("span", { "aria-hidden": "true" }, "⏱"),
      e("span", null, label)
    );
  }

  global.VSM = global.VSM || {};
  global.VSM.components = global.VSM.components || {};
  global.VSM.components.Timer = Timer;
})(window);
