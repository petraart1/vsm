import styles from "./ScalesPanel.module.css";

/** Обёртка-грид для пары ScaleBar (лояльность + безопасность) — используется в прохождении
 * сценария и в разборе. props: children (обычно два <ScaleBar>). */
export default function ScalesPanel({ children }) {
  return <div className={styles.panel}>{children}</div>;
}
