/* global React, VSM */
(function (global) {
  "use strict";
  var e = React.createElement;
  var ScaleBar = VSM.components.ScaleBar;

  function DeltaBadges(props) {
    var d = props.deltas;
    return e(
      "div",
      { className: "delta-row", style: { marginTop: "var(--spacing-2)" } },
      e("span", { className: "delta " + (d.loyalty >= 0 ? "delta--positive" : "delta--negative") }, (d.loyalty >= 0 ? "+" : "") + d.loyalty + " лояльность"),
      e("span", { className: "delta " + (d.safety >= 0 ? "delta--positive" : "delta--negative") }, (d.safety >= 0 ? "+" : "") + d.safety + " безопасность")
    );
  }

  function TimelineStep(props) {
    var step = props.step;
    var index = props.index;
    var expanded = props.expanded;
    var text = step.choiceText;
    var isLong = text.length > 90;
    var shown = expanded || !isLong ? text : text.slice(0, 90) + "…";

    return e(
      "div",
      { className: "timeline-step" },
      e("div", { className: "timeline-step__marker" }, index + 1),
      e(
        "div",
        { style: { flex: 1 }, onClick: isLong ? props.onToggle : undefined, role: isLong ? "button" : undefined, tabIndex: isLong ? 0 : undefined },
        e("p", { style: { color: "var(--color-text-secondary)", fontSize: "var(--font-size-small)" } }, step.nodeText),
        e("p", { className: "replica" }, shown),
        isLong && e("span", { style: { fontSize: "var(--font-size-caption)", color: "var(--color-text-link)", cursor: "pointer" } }, expanded ? "Свернуть" : "Показать полностью"),
        e(DeltaBadges, { deltas: step.deltas }),
        step.escalation && e("span", { className: "badge badge--escalation", style: { marginTop: "var(--spacing-2)", display: "inline-block" } }, "☎ Эскалация"),
        step.scaleConflict && e("span", { className: "badge badge--escalation", style: { marginTop: "var(--spacing-2)", marginLeft: "var(--spacing-2)", display: "inline-block" } }, "Конфликт шкал — осознанный компромисс"),
        (step.roleStepsCompleted.length > 0 || step.roleStepsSkipped.length > 0) &&
          e(
            "div",
            { className: "role-step-tags" },
            step.roleStepsCompleted.map(function (label) {
              return e("span", { key: "c-" + label, className: "role-step-tag" }, label);
            }),
            step.roleStepsSkipped.map(function (label) {
              return e("span", { key: "s-" + label, className: "role-step-tag role-step-tag--skipped" }, "пропущено: " + label);
            })
          ),
        step.explanation && e("p", { style: { color: "var(--color-text-muted)", fontSize: "var(--font-size-small)", marginTop: "var(--spacing-2)" } }, step.explanation)
      )
    );
  }

  /** props: route (сегменты ["debrief", progressId]) */
  function Debrief(props) {
    var progressId = props.route.segments[1];
    var state = React.useState({ phase: "loading" });
    var s = state[0];
    var setS = state[1];
    var expandedState = React.useState({});
    var expanded = expandedState[0];
    var setExpanded = expandedState[1];

    React.useEffect(function () {
      setS({ phase: "loading" });
      VSM.api.getDebrief(progressId).then(function (res) {
        if (res.error) {
          setS({ phase: "unavailable" });
          return;
        }
        setS({ phase: "ready", debrief: res });
      });
    }, [progressId]);

    if (s.phase === "loading") {
      return e(
        "div",
        { className: "play-shell" },
        e("div", { className: "skeleton", style: { height: "120px" } }),
        e("div", { className: "skeleton", style: { height: "260px" } })
      );
    }

    if (s.phase === "unavailable") {
      return e(
        "div",
        { className: "empty-state" },
        e("p", null, "Разбор недоступен."),
        e("a", { className: "btn btn--primary", href: "#/profile" }, "В профиль")
      );
    }

    var d = s.debrief;
    var a = d.accrual;

    return e(
      "div",
      { className: "play-shell" },
      e(
        "div",
        { className: "verdict-banner" },
        e("h1", { style: { fontSize: "var(--font-size-h1)" } }, d.verdict),
        e("p", { style: { color: "var(--color-text-secondary)" } }, d.scenario.title + " · " + d.scenario.blockLabel),
        d.interrupted && e("p", { className: "badge badge--escalation" }, "Прохождение не было завершено обычным образом")
      ),
      e(
        "div",
        { className: "scales-panel" },
        e(ScaleBar, { type: "loyalty", value: d.finalScales.loyalty }),
        e(ScaleBar, { type: "safety", value: d.finalScales.safety })
      ),
      e(
        "section",
        { className: "card" },
        e("h2", { style: { fontSize: "var(--font-size-h3)", marginBottom: "var(--spacing-3)" } }, "Пройденный путь"),
        e(
          "div",
          { className: "timeline" },
          d.timeline.map(function (step, i) {
            return e(TimelineStep, {
              key: i,
              index: i,
              step: step,
              expanded: !!expanded[i],
              onToggle: function () {
                setExpanded(function (prev) {
                  var next = Object.assign({}, prev);
                  next[i] = !prev[i];
                  return next;
                });
              }
            });
          })
        )
      ),
      e(
        "section",
        { className: "card" },
        e("h2", { style: { fontSize: "var(--font-size-h3)", marginBottom: "var(--spacing-2)" } }, "Что можно было сделать иначе"),
        e("p", null, d.summary),
        d.keyMoment &&
          e(
            "div",
            { style: { marginTop: "var(--spacing-3)", display: "flex", flexDirection: "column", gap: "var(--spacing-2)" } },
            e("p", { style: { color: "var(--color-text-secondary)", fontSize: "var(--font-size-small)" } }, d.keyMoment.nodeText),
            e("p", null, e("strong", null, "Вы выбрали: "), d.keyMoment.chosenChoiceText),
            e(DeltaBadges, { deltas: d.keyMoment.chosenDeltas }),
            e("p", { style: { marginTop: "var(--spacing-2)" } }, e("strong", null, "Сильнее было бы: "), d.keyMoment.betterChoiceText),
            e(DeltaBadges, { deltas: d.keyMoment.betterDeltas })
          ),
        d.normReferences && d.normReferences.length > 0 &&
          e(
            "p",
            { style: { color: "var(--color-text-muted)", fontSize: "var(--font-size-caption)", marginTop: "var(--spacing-3)" } },
            "Нормативная база: " + d.normReferences.join(", ")
          )
      ),
      e(
        "section",
        { className: "card" },
        e("h2", { style: { fontSize: "var(--font-size-h3)", marginBottom: "var(--spacing-2)" } }, "Начисления"),
        a.totalScore === null
          ? e("p", { style: { color: "var(--color-text-muted)", fontSize: "var(--font-size-small)" } }, "Начисления временно недоступны.")
          : e("p", null, "Общий счёт: " + a.totalScore + " очков · пройдено сценариев: " + a.scenariosCompleted),
        a.recentAchievements.length > 0
          ? e(
              "div",
              { style: { display: "flex", gap: "var(--spacing-2)", flexWrap: "wrap", marginTop: "var(--spacing-2)" } },
              a.recentAchievements.map(function (ach) {
                return e("a", { key: ach.code, href: "#/achievements?highlight=" + ach.code, className: "badge badge--done" }, "★ " + ach.title);
              })
            )
          : e("p", { style: { color: "var(--color-text-muted)", fontSize: "var(--font-size-small)" } }, "Новых ачивок пока нет.")
      ),
      e(
        "div",
        { style: { display: "flex", gap: "var(--spacing-3)", flexWrap: "wrap" } },
        e("a", { className: "btn btn--primary", href: "#/scenarios/" + d.scenario.id + "/play" }, "Пройти ещё раз"),
        e("a", { className: "btn btn--secondary", href: "#/scenarios" }, "К списку сценариев"),
        e("a", { className: "btn btn--secondary", href: "#/profile" }, "В профиль")
      )
    );
  }

  global.VSM = global.VSM || {};
  global.VSM.screens = global.VSM.screens || {};
  global.VSM.screens.Debrief = Debrief;
})(window);
