import styles from "./DeltaBadges.module.css";

function Delta({ value, label }) {
  const tone = value > 0 ? "up" : value < 0 ? "down" : "flat";
  const sign = value > 0 ? "+" : value < 0 ? "−" : "±";
  return (
    <span className={styles.delta} data-tone={tone}>
      <span className={styles.num}>{sign}{Math.abs(value)}</span> {label}
    </span>
  );
}

/** Изменения шкал после решения. props: deltas ({loyalty, safety}), className. */
export default function DeltaBadges({ deltas, className }) {
  return (
    <div className={[styles.row, className].filter(Boolean).join(" ")}>
      <Delta value={deltas.safety} label="безопасность" />
      <Delta value={deltas.loyalty} label="лояльность" />
    </div>
  );
}
