/* global React, VSM */
(function (global) {
  "use strict";
  var e = React.createElement;
  var ScaleBar = VSM.components.ScaleBar;
  var Timer = VSM.components.Timer;
  var Avatar = VSM.components.Avatar;
  var navigate = VSM.router.navigate;

  var AUTO_ADVANCE_MS = 1800;

  /** props: route (сегменты ["scenarios", id, "play"]) */
  function ScenarioPlay(props) {
    var scenarioId = props.route.segments[1];

    var state = React.useState({ phase: "loading" });
    var s = state[0];
    var setS = state[1];
    var choosingRef = React.useRef(false);
    var autoAdvanceTimeoutRef = React.useRef(null);

    function load() {
      choosingRef.current = false;
      setS({ phase: "loading" });
      VSM.api.startScenario(scenarioId).then(function (res) {
        if (res.error) {
          setS({ phase: "not_found" });
          return;
        }
        setS({
          phase: "playing",
          sessionId: res.sessionId,
          scenario: res.scenario,
          scales: res.scales,
          node: res.node,
          stepIndex: 1
        });
      });
    }

    React.useEffect(load, [scenarioId]);
    React.useEffect(function () {
      return function () {
        if (autoAdvanceTimeoutRef.current) clearTimeout(autoAdvanceTimeoutRef.current);
      };
    }, []);

    function handleExit() {
      var ok = global.confirm("Прогресс текущего прохождения будет потерян. Выйти из сценария?");
      if (ok) navigate("/scenarios");
    }

    function applyResult(promise) {
      promise.then(function (res) {
        if (res.error) {
          setS({ phase: "not_found" });
          return;
        }
        setS(function (prev) {
          return Object.assign({}, prev, {
            phase: "reacting",
            scales: res.scales,
            reaction: res.reaction,
            pendingNode: res.node,
            isFinal: res.isFinal
          });
        });
        autoAdvanceTimeoutRef.current = setTimeout(advance, AUTO_ADVANCE_MS);
      });
    }

    function handleChoice(choiceId) {
      if (choosingRef.current || s.phase !== "playing") return;
      choosingRef.current = true;
      applyResult(VSM.api.choose(s.sessionId, choiceId));
    }

    function advance() {
      if (autoAdvanceTimeoutRef.current) {
        clearTimeout(autoAdvanceTimeoutRef.current);
        autoAdvanceTimeoutRef.current = null;
      }
      setS(function (prev) {
        if (prev.phase !== "reacting") return prev;
        if (prev.isFinal) {
          navigate("/debrief/" + prev.sessionId);
          return prev;
        }
        choosingRef.current = false;
        return Object.assign({}, prev, {
          phase: "playing",
          node: prev.pendingNode,
          reaction: null,
          pendingNode: null,
          stepIndex: (prev.stepIndex || 1) + 1
        });
      });
    }

    function handleTimeout() {
      if (choosingRef.current || s.phase !== "playing") return;
      choosingRef.current = true;
      applyResult(VSM.api.timeout(s.sessionId));
    }

    if (s.phase === "loading") {
      return e(
        "div",
        { className: "play-shell" },
        e("div", { className: "skeleton", style: { height: "60px" } }),
        e("div", { className: "skeleton", style: { height: "180px" } }),
        e("div", { className: "skeleton", style: { height: "200px" } })
      );
    }

    if (s.phase === "not_found") {
      return e(
        "div",
        { className: "empty-state" },
        e("p", null, "Сценарий не найден или больше не доступен."),
        e("a", { className: "btn btn--primary", href: "#/scenarios" }, "К списку сценариев")
      );
    }

    var node = s.node;

    return e(
      "div",
      { className: "play-shell" },
      e(
        "div",
        { className: "play-header" },
        e(
          "div",
          null,
          e("h1", { style: { fontSize: "var(--font-size-h2)" } }, s.scenario.title),
          e("p", { style: { color: "var(--color-text-secondary)", fontSize: "var(--font-size-small)" } }, s.scenario.blockLabel + " · шаг " + s.stepIndex)
        ),
        e("button", { className: "btn btn--secondary", onClick: handleExit }, "Выйти")
      ),
      e(
        "div",
        { className: "scales-panel" },
        e(ScaleBar, { type: "loyalty", value: s.scales.loyalty }),
        e(ScaleBar, { type: "safety", value: s.scales.safety })
      ),
      e(
        "div",
        { className: "card situation-card" },
        e(Avatar, { initials: node.avatarInitials }),
        e(
          "div",
          null,
          node.contextNote && e("p", { className: "node-context" }, node.contextNote),
          e("p", { className: "replica" }, node.situationText),
          node.deadlineAt && s.phase === "playing" && e(Timer, { key: node.id, timerSeconds: node.timerSeconds, deadlineAt: node.deadlineAt, onExpire: handleTimeout })
        )
      ),
      s.phase === "playing" &&
        e(
          "div",
          { className: "choice-list" },
          node.choices.map(function (choice) {
            return e(
              "button",
              {
                key: choice.id,
                className: "choice-btn",
                onClick: function () { handleChoice(choice.id); }
              },
              choice.text
            );
          })
        ),
      s.phase === "reacting" &&
        e(
          "div",
          { className: "card reaction-panel" },
          s.reaction.wasTimeout && e("span", { className: "badge badge--escalation" }, "Время вышло"),
          e("p", null, s.reaction.text),
          e(
            "div",
            { className: "delta-row" },
            e(
              "span",
              { className: "delta " + (s.reaction.deltas.loyalty >= 0 ? "delta--positive" : "delta--negative") },
              (s.reaction.deltas.loyalty >= 0 ? "+" : "") + s.reaction.deltas.loyalty + " лояльность"
            ),
            e(
              "span",
              { className: "delta " + (s.reaction.deltas.safety >= 0 ? "delta--positive" : "delta--negative") },
              (s.reaction.deltas.safety >= 0 ? "+" : "") + s.reaction.deltas.safety + " безопасность"
            )
          ),
          s.reaction.escalation && e("p", { className: "badge badge--escalation" }, "☎ Эскалация: вызван начальник поезда"),
          s.reaction.hiddenPenalty && e("p", { style: { color: "var(--color-feedback-danger)", fontSize: "var(--font-size-small)" } }, s.reaction.hiddenPenalty),
          e("button", { className: "btn btn--primary", onClick: advance }, s.isFinal ? "К разбору" : "Далее")
        )
    );
  }

  global.VSM = global.VSM || {};
  global.VSM.screens = global.VSM.screens || {};
  global.VSM.screens.ScenarioPlay = ScenarioPlay;
})(window);
