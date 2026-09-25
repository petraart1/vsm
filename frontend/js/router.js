/* global React */
/**
 * Минимальный hash-роутер (без библиотек, по требованию «навигация как у сайта»).
 * Разбирает window.location.hash вида "#/scenarios/30/play" в { path, segments, screen, params }.
 */
(function (global) {
  "use strict";

  function parseHash() {
    var raw = global.location.hash || "#/";
    var full = raw.replace(/^#/, "");
    if (!full.startsWith("/")) full = "/" + full;
    var queryIndex = full.indexOf("?");
    var path = queryIndex === -1 ? full : full.slice(0, queryIndex);
    var queryString = queryIndex === -1 ? "" : full.slice(queryIndex + 1);
    var query = {};
    queryString.split("&").filter(Boolean).forEach(function (pair) {
      var kv = pair.split("=");
      query[decodeURIComponent(kv[0])] = decodeURIComponent(kv[1] || "");
    });
    var segments = path.split("/").filter(Boolean);
    var screen = segments[0] || "scenarios";
    return { path: path, segments: segments, screen: screen, query: query };
  }

  function navigate(path) {
    global.location.hash = path;
  }

  function useHashRoute() {
    var React = global.React;
    var state = React.useState(parseHash);
    var route = state[0];
    var setRoute = state[1];
    React.useEffect(function () {
      function onHashChange() {
        setRoute(parseHash());
      }
      global.addEventListener("hashchange", onHashChange);
      return function () { global.removeEventListener("hashchange", onHashChange); };
    }, []);
    return route;
  }

  global.VSM = global.VSM || {};
  global.VSM.router = { useHashRoute: useHashRoute, navigate: navigate, parseHash: parseHash };
})(window);
