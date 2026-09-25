import styles from "./DeltaBadges.module.css";

/** Пара бейджей "+N лояльность / +N безопасность" — используется и в проигрывании сценария
 * (реакция на выбор), и в разборе (таймлайн, ключевая развилка). props: deltas ({loyalty, safety}). */
export default function DeltaBadges({ deltas, className }) {
  const cls = [styles.row, className].filter(Boolean).join(" ");
  return (
    <div className={cls}>
      <span className={`${styles.delta} ${deltas.loyalty >= 0 ? styles.positive : styles.negative}`}>
        {(deltas.loyalty >= 0 ? "+" : "") + deltas.loyalty} лояльность
      </span>
      <span className={`${styles.delta} ${deltas.safety >= 0 ? styles.positive : styles.negative}`}>
        {(deltas.safety >= 0 ? "+" : "") + deltas.safety} безопасность
      </span>
    </div>
  );
}
