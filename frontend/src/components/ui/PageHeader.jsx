import styles from "./PageHeader.module.css";

/** Заголовок страницы + опциональная строка метаданных справа (счётчик "N из M пройдено" и т.п.).
 * props: title, meta (опц. React-узел/строка), className. */
export default function PageHeader({ title, meta, className }) {
  const cls = [styles.header, className].filter(Boolean).join(" ");
  return (
    <div className={cls}>
      <h1>{title}</h1>
      {meta && <div className={styles.meta}>{meta}</div>}
    </div>
  );
}
