import Icon from "./Icon.jsx";
import styles from "./Badge.module.css";

const LEGACY = { flagship: "inverse", escalation: "amber", done: "green", neutral: "neutral" };

/**
 * Плашка-тег: тонкая рамка и лёгкая заливка в цвет тона, без точки-индикатора.
 * props: tone ('neutral'|'blue'|'green'|'red'|'amber'|'inverse'), icon (имя иконки), as (тег),
 * className. variant — совместимость со старыми вызовами.
 */
export default function Badge({ tone = "neutral", variant, icon, dot, as: As = "span", className, children, ...rest }) {
  const t = variant ? LEGACY[variant] || "neutral" : tone;
  const cls = [styles.badge, styles[t], className].filter(Boolean).join(" ");
  return (
    <As className={cls} {...rest}>
      {icon && <Icon name={icon} size={12} strokeWidth={2} />}
      {children}
    </As>
  );
}
