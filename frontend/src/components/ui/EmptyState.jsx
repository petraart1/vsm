import styles from "./EmptyState.module.css";

/** Пустое/ошибочное состояние: заголовок, пояснение, действие. props: title, message, action, className. */
export default function EmptyState({ title, message, action, className }) {
  const cls = [styles.emptyState, "rv", className].filter(Boolean).join(" ");
  return (
    <div className={cls}>
      {title && <h2 className={styles.title}>{title}</h2>}
      {message && <p className={styles.message}>{message}</p>}
      {action && <div className={styles.action}>{action}</div>}
    </div>
  );
}
