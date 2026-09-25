/* global React, VSM */
(function (global) {
  "use strict";
  var e = React.createElement;
  var ScaleBar = VSM.components.ScaleBar;

  function ScenarioCard(props) {
    var s = props.situation;
    var ref = React.useRef(null);
    React.useEffect(function () {
      if (props.highlighted && ref.current) {
        ref.current.scrollIntoView({ behavior: "smooth", block: "center" });
      }
    }, [props.highlighted]);

    return e(
      "article",
      {
        ref: ref,
        className: "card scenario-card" + (props.highlighted ? " is-highlighted" : "")
      },
      e(
        "div",
        { className: "scenario-card__meta" },
        s.flagship && e("span", { className: "badge badge--flagship" }, "Расширенный сценарий"),
        s.escalation && e("span", { className: "badge badge--escalation" }, "☎ Эскалация"),
        s.status === "completed" && e("span", { className: "badge badge--done" }, "Пройден")
      ),
      e("h3", { className: "scenario-card__title" }, s.title),
      s.lastResult
        ? e(
            "div",
            { className: "mini-scales" },
            e("span", null, "Лояльность: " + Math.round(s.lastResult.loyalty)),
            e("span", null, "Безопасность: " + Math.round(s.lastResult.safety))
          )
        : e("div", { className: "mini-scales" }, "Ещё не пройден"),
      e(
        "a",
        {
          className: "btn btn--primary",
          href: "#/scenarios/" + s.id + "/play"
        },
        s.status === "completed" ? "Пройти снова" : "Начать"
      )
    );
  }

  function BlockSection(props) {
    var block = props.block;
    var situations = props.situations;
    var collapsed = props.collapsed;
    if (situations.length === 0) return null;
    return e(
      "section",
      { className: "block-section" },
      e(
        "button",
        { className: "block-section__head", onClick: props.onToggle, "aria-expanded": !collapsed },
        e("h2", null, block.label + " — пройдено " + block.completed + " из " + block.total),
        e("span", { "aria-hidden": "true" }, collapsed ? "▸" : "▾")
      ),
      !collapsed &&
        e(
          "div",
          { className: "scenario-grid" },
          situations.map(function (s) {
            return e(ScenarioCard, { key: s.id, situation: s, highlighted: props.highlightId === s.id });
          })
        )
    );
  }

  function ScenarioList(props) {
    var highlightId = props.route.query && props.route.query.highlight ? Number(props.route.query.highlight) : null;
    var state = React.useState({ loading: true, error: false, data: null });
    var loadState = state[0];
    var setLoadState = state[1];
    var searchState = React.useState("");
    var search = searchState[0];
    var setSearch = searchState[1];
    var statusState = React.useState("all");
    var statusFilter = statusState[0];
    var setStatusFilter = statusState[1];
    var collapsedState = React.useState({});
    var collapsed = collapsedState[0];
    var setCollapsed = collapsedState[1];

    function load() {
      setLoadState({ loading: true, error: false, data: loadState.data });
      VSM.api.listScenarios().then(
        function (data) { setLoadState({ loading: false, error: false, data: data }); },
        function () { setLoadState({ loading: false, error: true, data: null }); }
      );
    }

    React.useEffect(load, []);

    if (loadState.loading && !loadState.data) {
      return e(
        "div",
        null,
        e("h1", null, "Сценарии"),
        e(
          "div",
          { className: "scenario-grid", style: { marginTop: "var(--spacing-5)" } },
          [1, 2, 3, 4, 5, 6].map(function (i) {
            return e("div", { key: i, className: "skeleton", style: { height: "140px" } });
          })
        )
      );
    }

    if (loadState.error) {
      return e(
        "div",
        { className: "empty-state" },
        e("p", null, "Не удалось загрузить каталог сценариев."),
        e("button", { className: "btn btn--primary", onClick: load }, "Повторить")
      );
    }

    var data = loadState.data;
    var filtered = data.situations.filter(function (s) {
      var matchesSearch = !search || s.title.toLowerCase().indexOf(search.toLowerCase()) !== -1;
      var matchesStatus =
        statusFilter === "all" ||
        (statusFilter === "done" && s.status === "completed") ||
        (statusFilter === "todo" && s.status === "not_started");
      return matchesSearch && matchesStatus;
    });

    var byBlock = {};
    filtered.forEach(function (s) {
      byBlock[s.block] = byBlock[s.block] || [];
      byBlock[s.block].push(s);
    });

    var hasAnyResult = filtered.length > 0;

    return e(
      "div",
      null,
      e(
        "div",
        { style: { display: "flex", justifyContent: "space-between", alignItems: "baseline", flexWrap: "wrap", gap: "var(--spacing-3)" } },
        e("h1", null, "Сценарии"),
        e("p", { style: { color: "var(--color-text-secondary)" } }, data.completedCount + " из " + data.totalCount + " пройдено")
      ),
      e(
        "div",
        { style: { display: "flex", gap: "var(--spacing-3)", margin: "var(--spacing-5) 0", flexWrap: "wrap" } },
        e("input", {
          type: "search",
          placeholder: "Поиск по названию…",
          value: search,
          onChange: function (ev) { setSearch(ev.target.value); },
          style: {
            flex: "1 1 240px",
            padding: "var(--spacing-3) var(--spacing-4)",
            borderRadius: "var(--radius-pill)",
            border: "1px solid var(--color-border-default)",
            fontSize: "var(--font-size-body)"
          }
        }),
        e(
          "select",
          {
            value: statusFilter,
            onChange: function (ev) { setStatusFilter(ev.target.value); },
            style: { padding: "var(--spacing-3) var(--spacing-4)", borderRadius: "var(--radius-pill)", border: "1px solid var(--color-border-default)" }
          },
          e("option", { value: "all" }, "Все"),
          e("option", { value: "todo" }, "Не пройдено"),
          e("option", { value: "done" }, "Пройдено")
        )
      ),
      !hasAnyResult &&
        e(
          "div",
          { className: "empty-state" },
          e("p", null, "Ничего не найдено."),
          e(
            "button",
            {
              className: "btn btn--secondary",
              onClick: function () { setSearch(""); setStatusFilter("all"); }
            },
            "Сбросить фильтр"
          )
        ),
      data.blocks.map(function (block) {
        return e(BlockSection, {
          key: block.key,
          block: block,
          situations: byBlock[block.key] || [],
          collapsed: !!collapsed[block.key],
          highlightId: highlightId,
          onToggle: function () {
            setCollapsed(function (prev) {
              var next = Object.assign({}, prev);
              next[block.key] = !prev[block.key];
              return next;
            });
          }
        });
      })
    );
  }

  global.VSM = global.VSM || {};
  global.VSM.screens = global.VSM.screens || {};
  global.VSM.screens.ScenarioList = ScenarioList;
})(window);
