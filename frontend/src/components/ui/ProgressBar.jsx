import styles from "./ProgressBar.module.css";

/** Полоса 0-100 с подписью. При появлении заполняется слева направо.
 * props: label, value, valueLabel, tone ('default'|'warning'|'safety'). */
export default function ProgressBar({ label, value, valueLabel, tone = "default" }) {
  const clamped = Math.max(0, Math.min(100, value || 0));
  return (
    <div className={styles.row} data-tone={tone}>
      <div className={styles.head}>
        <span className={styles.label}>{label}</span>
        <span className={styles.value}>{valueLabel !== undefined ? valueLabel : Math.round(clamped)}</span>
      </div>
      <div className={styles.track} role="progressbar" aria-valuenow={Math.round(clamped)} aria-valuemin={0} aria-valuemax={100} aria-label={label}>
        <div className={styles.fill} style={{ width: `${clamped}%` }} />
      </div>
    </div>
  );
}
