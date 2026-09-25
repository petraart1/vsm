import { useEffect } from "react";
import styles from "./Toast.module.css";

const AUTO_DISMISS_MS = 6000;

function ToastItem({ toast, onDismiss }) {
  useEffect(() => {
    const timer = window.setTimeout(() => onDismiss(toast.id), AUTO_DISMISS_MS);
    return () => window.clearTimeout(timer);
  }, [toast.id, onDismiss]);

  return (
    <div className={styles.toast} role="status">
      <span className={styles.toastIcon} aria-hidden="true">★</span>
      <div>
        <p className={styles.toastTitle}>{toast.title}</p>
        {toast.body && <p className={styles.toastBody}>{toast.body}</p>}
      </div>
      <button type="button" className={styles.toastClose} onClick={() => onDismiss(toast.id)} aria-label="Закрыть">
        ×
      </button>
    </div>
  );
}

/** Стек тостов в правом верхнем углу — сейчас единственный сценарий использования: новая ачивка
 * на экране разбора. props: toasts ([{id, title, body}]), onDismiss(id). */
export default function Toast({ toasts, onDismiss }) {
  if (!toasts || toasts.length === 0) return null;
  return (
    <div className={styles.stack}>
      {toasts.map((t) => (
        <ToastItem key={t.id} toast={t} onDismiss={onDismiss} />
      ))}
    </div>
  );
}
