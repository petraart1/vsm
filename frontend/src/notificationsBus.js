/**
 * Минимальная шина событий для обновления счётчика непрочитанных уведомлений (NotificationBell)
 * из мест, не связанных с ним напрямую компонентным деревом — сейчас только Debrief после
 * завершения сценария (новая ачивка/личный рекорд создают уведомление на backend синхронно с
 * начислением очков, см. ru.vsm.backend.gamification.service.NotificationService).
 */

const listeners = new Set();

export function notifyNotificationsChanged() {
  listeners.forEach((fn) => {
    try { fn(); } catch (e) { /* ignore listener error */ }
  });
}

export function subscribeNotificationsChanged(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}
