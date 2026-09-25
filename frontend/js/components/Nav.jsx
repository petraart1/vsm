/* global React, VSM */
(function (global) {
  "use strict";
  var e = React.createElement;

  var LINKS = [
    { screen: "scenarios", path: "/scenarios", label: "Сценарии" },
    { screen: "profile", path: "/profile", label: "Профиль" },
    { screen: "leaderboard", path: "/leaderboard", label: "Лидерборд" },
    { screen: "achievements", path: "/achievements", label: "Ачивки" }
  ];

  function Nav(props) {
    var activeScreen = props.activeScreen;
    return e(
      "header",
      { className: "site-header" },
      e(
        "a",
        { className: "site-header__brand", href: "#/scenarios" },
        e("span", { className: "site-header__brand-mark" }, "ВСМ"),
        e("span", null, "Тренажёр проводника")
      ),
      e(
        "nav",
        { className: "site-nav", "aria-label": "Основная навигация" },
        LINKS.map(function (link) {
          var isActive = link.screen === activeScreen;
          return e(
            "a",
            {
              key: link.screen,
              href: "#" + link.path,
              className: "site-nav__link" + (isActive ? " is-active" : "")
            },
            link.label
          );
        })
      )
    );
  }

  global.VSM = global.VSM || {};
  global.VSM.components = global.VSM.components || {};
  global.VSM.components.Nav = Nav;
})(window);
