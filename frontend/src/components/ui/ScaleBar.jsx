import Icon from "./Icon.jsx";
import { CountUp } from "../motion/Motion.jsx";
import styles from "./ScaleBar.module.css";

const META = {
  loyalty: { label: "Лояльность пассажира", short: "Лояльность", icon: "smile" },
  safety: { label: "Рейтинг безопасности", short: "Безопасность", icon: "shield" }
};

/** Шкала прохождения: подпись, крупное число (досчитывается при изменении) и тонкая полоса 0–100.
 * props: type ('loyalty'|'safety'), value, compact (для шапки прохождения). */
export default function ScaleBar({ type, value, compact = false }) {
  const meta = META[type];
  const clamped = Math.max(0, Math.min(100, value));
  return (
    <div className={styles.row} data-type={type} data-compact={compact || undefined}>
      <div className={styles.head}>
        <span className={styles.label}>
          <Icon name={meta.icon} size={compact ? 14 : 16} />
          {compact ? meta.short : meta.label}
        </span>
        <CountUp className={styles.value} value={Math.round(value)} duration={900} />
      </div>
      <div className={styles.track} role="progressbar" aria-valuenow={Math.round(value)} aria-valuemin={0} aria-valuemax={100} aria-label={meta.label}>
        <div className={styles.fill} style={{ width: `${clamped}%` }} />
      </div>
    </div>
  );
}
