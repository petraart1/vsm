/* global React, VSM */
(function (global) {
  "use strict";
  var e = React.createElement;
  var Avatar = VSM.components.Avatar;

  function Row(props) {
    var entry = props.entry;
    var isMe = props.isMe;
    return e(
      "div",
      {
        className: "card",
        style: {
          display: "flex",
          alignItems: "center",
          gap: "var(--spacing-3)",
          marginTop: "var(--spacing-2)",
          border: isMe ? "2px solid var(--color-border-focus)" : undefined,
          background: isMe ? "var(--color-background-surface-raised)" : undefined
        }
      },
      e("strong", { style: { minWidth: "2ch" } }, "#" + entry.rank),
      e(Avatar, { initials: (entry.displayName || "??").slice(0, 2).toUpperCase(), size: 40 }),
      e(
        "div",
        { style: { flex: 1 } },
        e("p", null, entry.displayName + (isMe ? " (Вы)" : "")),
        e("p", { style: { color: "var(--color-text-muted)", fontSize: "var(--font-size-caption)" } }, entry.scenariosCompleted + " сценариев пройдено")
      ),
      e("strong", null, entry.totalScore)
    );
  }

  /** Лидерборд — топ игроков + закреплённая карточка "Ваше место". См. design/screens/leaderboard.md.
   * Данные — ru.vsm.backend.gamification (LeaderboardResponse). */
  function Leaderboard() {
    var state = React.useState({ phase: "loading" });
    var s = state[0];
    var setS = state[1];

    function load() {
      setS({ phase: "loading" });
      VSM.api.getLeaderboard({ limit: 20 }).then(
        function (data) { setS({ phase: "ready", data: data }); },
        function () { setS({ phase: "error" }); }
      );
    }

    React.useEffect(load, []);

    if (s.phase === "loading") {
      return e(
        "div",
        null,
        e("h1", null, "Лидерборд"),
        e("div", { className: "skeleton", style: { height: "60px", marginTop: "var(--spacing-4)" } }),
        e("div", { className: "skeleton", style: { height: "60px", marginTop: "var(--spacing-2)" } }),
        e("div", { className: "skeleton", style: { height: "60px", marginTop: "var(--spacing-2)" } })
      );
    }

    if (s.phase === "error") {
      return e(
        "div",
        { className: "empty-state" },
        e("p", null, "Не удалось загрузить лидерборд."),
        e("button", { className: "btn btn--primary", onClick: load }, "Повторить")
      );
    }

    var d = s.data;
    var meInTop = d.me && d.top.some(function (t) { return t.playerId === d.me.playerId; });

    return e(
      "div",
      null,
      e("h1", null, "Лидерборд"),
      d.top.length === 0 &&
        e(
          "div",
          { className: "empty-state", style: { marginTop: "var(--spacing-4)" } },
          e("p", null, "Станьте первым в рейтинге."),
          e("a", { className: "btn btn--primary", href: "#/scenarios" }, "К списку сценариев")
        ),
      d.top.length > 0 &&
        e(
          "div",
          { style: { marginTop: "var(--spacing-5)" } },
          d.top.map(function (entry) {
            return e(Row, { key: entry.playerId, entry: entry, isMe: d.me && entry.playerId === d.me.playerId });
          })
        ),
      d.me && !meInTop &&
        e(
          "div",
          { style: { marginTop: "var(--spacing-5)" } },
          e("p", { style: { color: "var(--color-text-secondary)", fontSize: "var(--font-size-small)", marginBottom: "var(--spacing-2)" } }, "Ваше место:"),
          e(Row, { entry: d.me, isMe: true })
        ),
      !d.me &&
        e(
          "div",
          { className: "card", style: { marginTop: "var(--spacing-5)" } },
          e("p", null, "Пройдите первый сценарий, чтобы попасть в рейтинг."),
          e("a", { className: "btn btn--primary", href: "#/scenarios", style: { marginTop: "var(--spacing-3)", display: "inline-block" } }, "К списку сценариев")
        )
    );
  }

  global.VSM = global.VSM || {};
  global.VSM.screens = global.VSM.screens || {};
  global.VSM.screens.Leaderboard = Leaderboard;
})(window);
