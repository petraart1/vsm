/* global React, VSM */
(function (global) {
  "use strict";
  var e = React.createElement;

  function AchievementCard(props) {
    var a = props.achievement;
    var highlighted = props.highlighted;
    return e(
      "div",
      {
        className: "card" + (highlighted ? " is-highlighted" : ""),
        style: { display: "flex", flexDirection: "column", gap: "var(--spacing-2)", opacity: a.earned ? 1 : 0.65 }
      },
      e(
        "div",
        { style: { display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "var(--spacing-2)" } },
        e("span", { className: "badge " + (a.earned ? "badge--done" : "badge--escalation") }, a.earned ? "★ Получено" : "Заблокировано"),
        a.earnedAt && e("span", { style: { fontSize: "var(--font-size-caption)", color: "var(--color-text-muted)" } }, new Date(a.earnedAt).toLocaleDateString("ru-RU"))
      ),
      e("h3", { style: { fontSize: "var(--font-size-h3)" } }, a.title),
      e("p", { style: { color: "var(--color-text-secondary)", fontSize: "var(--font-size-small)" } }, a.description)
    );
  }

  /** Полная витрина ачивок (полученных и заблокированных, с понятными условиями).
   * См. design/screens/achievements.md. Данные — ru.vsm.backend.gamification (AchievementDto[]). */
  function Achievements(props) {
    var highlight = props.route.query && props.route.query.highlight;
    var state = React.useState({ phase: "loading" });
    var s = state[0];
    var setS = state[1];

    function load() {
      setS({ phase: "loading" });
      VSM.api.getAchievements().then(
        function (data) { setS({ phase: "ready", data: data }); },
        function () { setS({ phase: "error" }); }
      );
    }

    React.useEffect(load, []);

    if (s.phase === "loading") {
      return e(
        "div",
        null,
        e("h1", null, "Ачивки"),
        e(
          "div",
          { className: "scenario-grid", style: { marginTop: "var(--spacing-5)" } },
          [1, 2, 3, 4, 5].map(function (i) { return e("div", { key: i, className: "skeleton", style: { height: "120px" } }); })
        )
      );
    }

    if (s.phase === "error") {
      return e(
        "div",
        { className: "empty-state" },
        e("p", null, "Не удалось загрузить каталог ачивок."),
        e("button", { className: "btn btn--primary", onClick: load }, "Повторить")
      );
    }

    var list = s.data;
    var earnedCount = list.filter(function (a) { return a.earned; }).length;

    return e(
      "div",
      null,
      e(
        "div",
        { style: { display: "flex", justifyContent: "space-between", alignItems: "baseline", flexWrap: "wrap", gap: "var(--spacing-3)" } },
        e("h1", null, "Ачивки"),
        e("p", { style: { color: "var(--color-text-secondary)" } }, "Получено " + earnedCount + " из " + list.length)
      ),
      e(
        "div",
        { className: "scenario-grid", style: { marginTop: "var(--spacing-5)" } },
        list.map(function (a) {
          return e(AchievementCard, { key: a.code, achievement: a, highlighted: highlight === a.code });
        })
      )
    );
  }

  global.VSM = global.VSM || {};
  global.VSM.screens = global.VSM.screens || {};
  global.VSM.screens.Achievements = Achievements;
})(window);
