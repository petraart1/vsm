import EmptyState from "./EmptyState.jsx";
import Button from "./Button.jsx";

/** Ошибка загрузки: что случилось + кнопка повтора. props: message, onRetry, retryLabel. */
export default function ErrorState({ title = "Не удалось загрузить данные", message, onRetry, retryLabel = "Повторить" }) {
  return (
    <EmptyState
      title={title}
      message={message}
      action={onRetry ? <Button onClick={onRetry}>{retryLabel}</Button> : null}
    />
  );
}
