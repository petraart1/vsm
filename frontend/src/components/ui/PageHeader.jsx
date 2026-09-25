import { SplitText } from "../motion/Motion.jsx";
import styles from "./PageHeader.module.css";

/** Заголовок экрана: крупный заголовок, проявляющийся по словам, подзаголовок и действия справа.
 * props: title, description, actions (узел), className. */
export default function PageHeader({ title, description, actions, className }) {
  return (
    <header className={[styles.header, className].filter(Boolean).join(" ")}>
      <div className={styles.text}>
        <SplitText as="h1" text={title} className={styles.title} />
        {description && <p className={`${styles.description} rv`} style={{ "--i": 1 }}>{description}</p>}
      </div>
      {actions && <div className={`${styles.actions} rv`} style={{ "--i": 2 }}>{actions}</div>}
    </header>
  );
}
