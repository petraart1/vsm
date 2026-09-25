import styles from "./ProgressBar.module.css";

/** Универсальная полоса прогресса 0-100 (в отличие от ScaleBar — без жёстко заданных подписей
 * "лояльность"/"безопасность", годится для компетенций по блокам и шагам ролевой модели).
 * props: label, value (0-100), valueLabel (опц. строка справа вместо округлённого value),
 * tone ('default'|'warning' — подсветка просевшей компетенции). */
export default function ProgressBar({ label, value, valueLabel, tone = "default" }) {
  const clamped = Math.max(0, Math.min(100, value));
  return (
    <div className={styles.row}>
      <div className={styles.head}>
        <span className={styles.label}>{label}</span>
        <span className={styles.value}>{valueLabel !== undefined ? valueLabel : Math.round(clamped)}</span>
      </div>
      <div
        className={styles.track}
        role="progressbar"
        aria-valuenow={Math.round(clamped)}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={label}
      >
        <div
          className={`${styles.fill}${tone === "warning" ? ` ${styles.fillWarning}` : ""}`}
          style={{ width: `${clamped}%` }}
        />
      </div>
    </div>
  );
}
