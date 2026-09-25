import styles from "./EmptyState.module.css";

/** props: message, action (опц. React-узел — кнопка/ссылка), className. */
export default function EmptyState({ message, action, className }) {
  const cls = [styles.emptyState, className].filter(Boolean).join(" ");
  return (
    <div className={cls}>
      <p>{message}</p>
      {action}
    </div>
  );
}
