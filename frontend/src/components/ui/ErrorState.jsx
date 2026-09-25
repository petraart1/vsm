import EmptyState from "./EmptyState.jsx";
import Button from "./Button.jsx";

/** Частный случай EmptyState для ошибок загрузки: сообщение + кнопка "Повторить".
 * props: message, onRetry, retryLabel (опц.). */
export default function ErrorState({ message, onRetry, retryLabel = "Повторить" }) {
  return (
    <EmptyState
      message={message}
      action={onRetry ? <Button variant="primary" onClick={onRetry}>{retryLabel}</Button> : null}
    />
  );
}
