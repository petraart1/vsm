import styles from "./VerifiedBadge.module.css";

/** Отметка подтверждённой учётной записи (вход через Госуслуги). props: size, withLabel. */
export default function VerifiedBadge({ size = 16, withLabel = false, className }) {
  return (
    <span className={[styles.badge, withLabel ? styles.withLabel : null, className].filter(Boolean).join(" ")} title="Личность подтверждена через Госуслуги">
      <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden="true">
        <path className={styles.seal} d="M12 1.8l2.4 1.9 3-.3 1.2 2.8 2.8 1.2-.3 3 1.9 2.4-1.9 2.4.3 3-2.8 1.2-1.2 2.8-3-.3L12 22.2l-2.4-1.9-3 .3-1.2-2.8-2.8-1.2.3-3L1 12l1.9-2.4-.3-3 2.8-1.2 1.2-2.8 3 .3z" />
        <path className={styles.tick} d="M7.6 12.3l3 3 5.8-6.2" />
      </svg>
      {withLabel && <span>Подтверждён</span>}
      {!withLabel && <span className="visually-hidden">Личность подтверждена</span>}
    </span>
  );
}
