/* global React, VSM */
(function (global) {
  "use strict";
  var e = React.createElement;
  var Avatar = VSM.components.Avatar;

  function initialsFor(name) {
    if (!name) return "ПР";
    var parts = name.split(/[\s-]+/).filter(Boolean);
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }

  /** Профиль проводника — карточка личности, сводные метрики по блокам, витрина ачивок.
   * См. design/screens/profile.md. Данные — ru.vsm.backend.gamification (ProfileResponse). */
  function Profile() {
    var state = React.useState({ phase: "loading" });
    var s = state[0];
    var setS = state[1];

    function load() {
      setS({ phase: "loading" });
      VSM.api.getProfile().then(
        function (data) { setS({ phase: "ready", data: data }); },
        function () { setS({ phase: "error" }); }
      );
    }

    React.useEffect(load, []);

    if (s.phase === "loading") {
      return e(
        "div",
        null,
        e("h1", null, "Профиль"),
        e("div", { className: "skeleton", style: { height: "100px", marginTop: "var(--spacing-4)" } }),
        e("div", { className: "skeleton", style: { height: "160px", marginTop: "var(--spacing-4)" } })
      );
    }

    if (s.phase === "error") {
      return e(
        "div",
        { className: "empty-state" },
        e("p", null, "Не удалось загрузить профиль."),
        e("button", { className: "btn btn--primary", onClick: load }, "Повторить")
      );
    }

    var p = s.data;
    var hasProgress = p.scenariosCompleted > 0;

    return e(
      "div",
      null,
      e("h1", null, "Профиль"),
      e(
        "div",
        { className: "card", style: { display: "flex", alignItems: "center", gap: "var(--spacing-4)", marginTop: "var(--spacing-5)" } },
        e(Avatar, { initials: initialsFor(p.displayName), size: 64 }),
        e(
          "div",
          null,
          e("h2", { style: { fontSize: "var(--font-size-h3)" } }, p.displayName),
          e("p", { style: { color: "var(--color-text-secondary)" } }, "Счёт компетенций: " + p.totalScore),
          e(
            "p",
            { style: { color: "var(--color-text-muted)", fontSize: "var(--font-size-small)" } },
            p.scenariosCompleted + " из " + p.totalScenariosAvailable + " сценариев · место в рейтинге: " + (p.leaderboardRank || "—")
          )
        )
      ),
      !hasProgress &&
        e(
          "div",
          { className: "card", style: { marginTop: "var(--spacing-4)" } },
          e("p", null, "Пройдите первый сценарий, чтобы увидеть свой прогресс."),
          e("a", { className: "btn btn--primary", href: "#/scenarios", style: { marginTop: "var(--spacing-3)", display: "inline-block" } }, "К списку сценариев")
        ),
      hasProgress && p.blockProgress.length > 0 &&
        e(
          "section",
          { className: "card", style: { marginTop: "var(--spacing-4)" } },
          e("h2", { style: { fontSize: "var(--font-size-h3)", marginBottom: "var(--spacing-3)" } }, "Прогресс по блокам"),
          e(
            "div",
            { style: { display: "flex", flexDirection: "column", gap: "var(--spacing-3)" } },
            p.blockProgress.map(function (bp) {
              return e(
                "div",
                { key: bp.block },
                e("p", null, (bp.blockLabel || bp.block) + " — пройдено " + bp.scenariosCompleted),
                e("p", { style: { color: "var(--color-text-muted)", fontSize: "var(--font-size-caption)" } }, "Лояльность: " + bp.loyaltyPoints + " · Безопасность: " + bp.safetyPoints)
              );
            })
          )
        ),
      e(
        "section",
        { className: "card", style: { marginTop: "var(--spacing-4)" } },
        e(
          "div",
          { style: { display: "flex", justifyContent: "space-between", alignItems: "baseline" } },
          e("h2", { style: { fontSize: "var(--font-size-h3)" } }, "Ачивки"),
          e("a", { href: "#/achievements", style: { color: "var(--color-text-link)", fontSize: "var(--font-size-small)" } }, "Все ачивки")
        ),
        p.recentAchievements.length > 0
          ? e(
              "div",
              { style: { display: "flex", gap: "var(--spacing-2)", flexWrap: "wrap", marginTop: "var(--spacing-3)" } },
              p.recentAchievements.map(function (a) {
                return e("span", { key: a.code, className: "badge badge--done" }, "★ " + a.title);
              })
            )
          : e("p", { style: { color: "var(--color-text-muted)", fontSize: "var(--font-size-small)", marginTop: "var(--spacing-2)" } }, "Пока нет полученных ачивок.")
      ),
      e(
        "a",
        { className: "btn btn--secondary", href: "#/leaderboard", style: { marginTop: "var(--spacing-4)", display: "inline-block" } },
        "Открыть лидерборд"
      )
    );
  }

  global.VSM = global.VSM || {};
  global.VSM.screens = global.VSM.screens || {};
  global.VSM.screens.Profile = Profile;
})(window);
