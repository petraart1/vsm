import { useState, useEffect, useRef, useCallback } from "react";
import * as api from "../../api.js";
import { subscribeNotificationsChanged } from "../../notificationsBus.js";
import Icon from "./Icon.jsx";
import styles from "./NotificationBell.module.css";

const POLL_MS = 30000;

function formatTime(iso) {
  try {
    return new Date(iso).toLocaleString("ru-RU", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });
  } catch (e) {
    return "";
  }
}

/**
 * Колокольчик уведомлений в шапке сайта: счётчик непрочитанных, выпадающий список, отметка
 * одного/всех прочитанными. Обновляется по трём поводам: монтирование, событие шины
 * (см. notificationsBus.js — например, после завершения сценария) и лёгкий опрос раз в 30с,
 * который приостанавливается, пока вкладка не видна (page visibility).
 */
export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const rootRef = useRef(null);

  const load = useCallback(() => {
    api.getNotifications(undefined, false).then(
      (list) => { setItems(list || []); setLoading(false); },
      () => setLoading(false)
    );
  }, []);

  useEffect(load, [load]);
  useEffect(() => subscribeNotificationsChanged(load), [load]);

  useEffect(() => {
    let timer = null;
    function stopPolling() {
      if (timer) { window.clearInterval(timer); timer = null; }
    }
    function startPolling() {
      stopPolling();
      timer = window.setInterval(() => {
        if (document.visibilityState === "visible") load();
      }, POLL_MS);
    }
    function onVisibilityChange() {
      if (document.visibilityState === "visible") {
        load();
        startPolling();
      } else {
        stopPolling();
      }
    }
    if (document.visibilityState === "visible") startPolling();
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      stopPolling();
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [load]);

  useEffect(() => {
    if (!open) return undefined;
    function onDocClick(ev) {
      if (rootRef.current && !rootRef.current.contains(ev.target)) setOpen(false);
    }
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, [open]);

  const unreadCount = items.filter((n) => !n.readAt).length;

  function handleItemClick(n) {
    if (n.readAt) return;
    setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, readAt: new Date().toISOString() } : x)));
    api.markNotificationRead(n.id).catch(load);
  }

  function handleMarkAll() {
    const now = new Date().toISOString();
    setItems((prev) => prev.map((n) => ({ ...n, readAt: n.readAt || now })));
    api.markAllNotificationsRead().catch(load);
  }

  return (
    <div className={styles.root} ref={rootRef}>
      <button
        type="button"
        className={styles.trigger}
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="true"
        aria-expanded={open}
        aria-label={unreadCount > 0 ? `Уведомления, непрочитанных: ${unreadCount}` : "Уведомления"}
      >
        <Icon name="bell" size={16} />
        {unreadCount > 0 && <span className={styles.badge} aria-hidden="true" />}
      </button>
      {open && (
        <div className={styles.dropdown} role="menu">
          <div className={styles.dropdownHead}>
            <span className={styles.dropdownTitle}>Уведомления</span>
            {unreadCount > 0 && (
              <button type="button" className={styles.markAll} onClick={handleMarkAll}>
                Прочитать все
              </button>
            )}
          </div>
          {loading ? (
            <p className={styles.empty}>Загрузка…</p>
          ) : items.length === 0 ? (
            <p className={styles.empty}>Пока нет уведомлений.</p>
          ) : (
            <ul className={styles.list}>
              {items.slice(0, 20).map((n) => (
                <li
                  key={n.id}
                  className={`${styles.item}${n.readAt ? "" : ` ${styles.itemUnread}`}`}
                  onClick={() => handleItemClick(n)}
                  role={n.readAt ? undefined : "button"}
                  tabIndex={n.readAt ? undefined : 0}
                >
                  <p className={styles.itemTitle}>{n.title}</p>
                  <p className={styles.itemBody}>{n.body}</p>
                  <p className={styles.itemTime}>{formatTime(n.createdAt)}</p>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
