/* global React, ReactDOM, VSM */
(function (global) {
  "use strict";
  var e = React.createElement;
  var Nav = VSM.components.Nav;
  var useHashRoute = VSM.router.useHashRoute;

  function App() {
    var route = useHashRoute();
    var screen = route.screen;

    // scenario-play не имеет общего хедера сайта (см. design/screens/scenario-play.md —
    // «модальный/полноэкранный режим в потоке»).
    var isFullscreenPlay = screen === "scenarios" && route.segments[2] === "play";

    var body;
    if (screen === "scenarios" && route.segments[2] === "play") {
      body = e(VSM.screens.ScenarioPlay, { route: route });
    } else if (screen === "scenarios") {
      body = e(VSM.screens.ScenarioList, { route: route });
    } else if (screen === "debrief") {
      body = e(VSM.screens.Debrief, { route: route });
    } else if (screen === "profile") {
      body = e(VSM.screens.Profile, { route: route });
    } else if (screen === "leaderboard") {
      body = e(VSM.screens.Leaderboard, { route: route });
    } else if (screen === "achievements") {
      body = e(VSM.screens.Achievements, { route: route });
    } else {
      body = e(
        "div",
        { className: "empty-state" },
        e("p", null, "Страница не найдена."),
        e("a", { className: "btn btn--primary", href: "#/scenarios" }, "К списку сценариев")
      );
    }

    return e(
      React.Fragment,
      null,
      !isFullscreenPlay && e(Nav, { activeScreen: screen }),
      e("main", { className: "app-main" }, body)
    );
  }

  var root = ReactDOM.createRoot(document.getElementById("root"));
  root.render(e(App));
})(window);
