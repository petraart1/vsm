import styles from "./VerdictMark.module.css";

/**
 * Итог прохождения без «светофора»: заполненность круга (как шары Харви в отчётах) в
 * фирменном синем. Полный — «Хорошо справились», половина — «Есть над чем поработать»,
 * пустой с чертой — критическая ошибка; пустой контур — ещё не пройдено.
 * props: verdict (строка backend) | null, size, withLabel.
 */
const LEVEL = {
  "Хорошо справились": 1,
  "Есть над чем поработать": 0.5,
  "Критическая ошибка безопасности": 0
};

export function verdictLevel(verdict) {
  return verdict in LEVEL ? LEVEL[verdict] : null;
}

export default function VerdictMark({ verdict, size = 16, withLabel = false, className }) {
  const lvl = verdictLevel(verdict);
  const r = 7;
  const c = 2 * Math.PI * r;
  return (
    <span className={[styles.mark, className].filter(Boolean).join(" ")} data-level={lvl === null ? "none" : String(lvl)} title={verdict || "Не пройдено"}>
      <svg viewBox="0 0 18 18" width={size} height={size} aria-hidden="true">
        <circle cx="9" cy="9" r={r} className={styles.ring} />
        {lvl === 1 && <circle cx="9" cy="9" r={r} className={styles.fill} />}
        {lvl === 0.5 && <path d={`M9 2 A7 7 0 0 1 9 16 Z`} className={styles.fill} />}
        {lvl === 0 && <path d="M4.6 13.4 L13.4 4.6" className={styles.slash} />}
        {lvl === null && null}
        <circle cx="9" cy="9" r={r} className={styles.edge} strokeDasharray={c} />
      </svg>
      {withLabel && <span className={styles.label}>{verdict || "Не пройдено"}</span>}
    </span>
  );
}
