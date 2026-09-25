import styles from "./ScaleBar.module.css";

const LABELS = { loyalty: "Лояльность пассажира", safety: "Рейтинг безопасности" };

function fillLevelClass(value) {
  if (value < 35) return styles.fillLow;
  if (value < 70) return styles.fillMid;
  return styles.fillHigh;
}

/** props: type ('loyalty'|'safety'), value (0-100). Ширина заполнения — реально динамическое
 * значение конкретного прохождения, остаётся инлайн-стилем; цвет заполнения — модификатор
 * класса (зависит и от типа шкалы, и от порога значения, задан в ScaleBar.module.css). */
export default function ScaleBar({ type, value }) {
  const clamped = Math.max(0, Math.min(100, value));
  return (
    <div className={`${styles.row} ${styles[type]}`}>
      <div className={styles.head}>
        <span className={styles.label}>{LABELS[type]}</span>
        <span className={styles.value}>{Math.round(clamped)}</span>
      </div>
      <div
        className={styles.track}
        role="progressbar"
        aria-valuenow={Math.round(clamped)}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={LABELS[type]}
      >
        <div className={`${styles.fill} ${fillLevelClass(clamped)}`} style={{ width: `${clamped}%` }} />
      </div>
    </div>
  );
}
