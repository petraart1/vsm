/* global React, VSM */
(function (global) {
  "use strict";
  var e = React.createElement;

  /** props: initials (string), size (optional px, по умолчанию 48) */
  function Avatar(props) {
    var size = props.size || 48;
    return e(
      "div",
      {
        className: "avatar",
        style: { width: size + "px", height: size + "px", fontSize: Math.round(size * 0.38) + "px" },
        "aria-hidden": "true"
      },
      props.initials || "?"
    );
  }

  global.VSM = global.VSM || {};
  global.VSM.components = global.VSM.components || {};
  global.VSM.components.Avatar = Avatar;
})(window);
