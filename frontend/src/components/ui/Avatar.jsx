import styles from "./Avatar.module.css";

/** props: initials (string), size (опц. px, по умолчанию 48) — размер и производный от него
 * размер шрифта являются реальными переменными значениями конкретного использования, поэтому
 * остаются инлайн-стилем (правило по умолчанию — избегать style={{}}, кроме таких случаев). */
export default function Avatar({ initials, size = 48 }) {
  return (
    <div
      className={styles.avatar}
      style={{ width: size, height: size, fontSize: Math.round(size * 0.38) }}
      aria-hidden="true"
    >
      {initials || "?"}
    </div>
  );
}
