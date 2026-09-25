import styles from "./Avatar.module.css";

/** Монограмма в круге. props: initials, size (px), tone ('solid'|'soft'). */
export default function Avatar({ initials, size = 40, tone = "soft" }) {
  return (
    <div
      className={`${styles.avatar} ${styles[tone]}`}
      style={{ width: size, height: size, fontSize: Math.round(size * 0.36) }}
      aria-hidden="true"
    >
      {initials || "?"}
    </div>
  );
}
