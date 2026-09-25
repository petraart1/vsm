/* global React, VSM */
(function (global) {
  "use strict";
  var e = React.createElement;

  var LABELS = { loyalty: "Лояльность пассажира", safety: "Рейтинг безопасности" };

  function fillColorVar(type, value) {
    if (value < 35) return "var(--color-" + type + "-fill-low)";
    if (value < 70) return "var(--color-" + type + "-fill-mid)";
    return "var(--color-" + type + "-fill-high)";
  }

  /** props: type ('loyalty'|'safety'), value (0-100), size ('normal'|'large') */
  function ScaleBar(props) {
    var type = props.type;
    var value = Math.max(0, Math.min(100, props.value));
    return e(
      "div",
      { className: "scale-row scale-row--" + type },
      e(
        "div",
        { className: "scale-row__head" },
        e("span", { className: "scale-row__label" }, LABELS[type]),
        e("span", { className: "scale-row__value" }, Math.round(value))
      ),
      e(
        "div",
        { className: "scale-track", role: "progressbar", "aria-valuenow": Math.round(value), "aria-valuemin": 0, "aria-valuemax": 100, "aria-label": LABELS[type] },
        e("div", {
          className: "scale-fill",
          style: { width: value + "%", background: fillColorVar(type, value) }
        })
      )
    );
  }

  global.VSM = global.VSM || {};
  global.VSM.components = global.VSM.components || {};
  global.VSM.components.ScaleBar = ScaleBar;
})(window);
