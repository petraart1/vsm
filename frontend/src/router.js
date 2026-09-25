import { useState, useEffect } from "react";

/**
 * Минимальный hash-роутер (без библиотек, по требованию «навигация как у сайта»).
 * Разбирает window.location.hash вида "#/scenarios/30/play" в { path, segments, screen, query }.
 */

function parseHash() {
  const raw = window.location.hash || "#/";
  let full = raw.replace(/^#/, "");
  if (!full.startsWith("/")) full = "/" + full;
  const queryIndex = full.indexOf("?");
  const path = queryIndex === -1 ? full : full.slice(0, queryIndex);
  const queryString = queryIndex === -1 ? "" : full.slice(queryIndex + 1);
  const query = {};
  queryString.split("&").filter(Boolean).forEach((pair) => {
    const kv = pair.split("=");
    query[decodeURIComponent(kv[0])] = decodeURIComponent(kv[1] || "");
  });
  const segments = path.split("/").filter(Boolean);
  const screen = segments[0] || "scenarios";
  return { path, segments, screen, query };
}

export function navigate(path) {
  window.location.hash = path;
}

export function useHashRoute() {
  const [route, setRoute] = useState(parseHash);
  useEffect(() => {
    function onHashChange() {
      setRoute(parseHash());
    }
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);
  return route;
}

export { parseHash };
