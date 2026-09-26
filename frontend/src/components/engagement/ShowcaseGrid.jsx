import Medal from "../awards/Medal.jsx";
import { SHOWCASE_MAX } from "../../engagement.js";
import styles from "./Engagement.module.css";

/**
 * Витрина наград: до 6 медалей, которые видят коллеги. items — снимки { id, title, shape, glyph, text }.
 * finish — отделка владельца витрины; editable — показывать пустые ячейки-приглашения.
 */
export default function ShowcaseGrid({ items, finish = "enamel", size = 72, editable = false, onOpen, emptyText }) {
  const cells = editable ? [...items, ...Array.from({ length: Math.max(0, SHOWCASE_MAX - items.length) }, () => null)] : items;
  if (!cells.length) return <p className={styles.empty}>{emptyText || "Витрина пока пуста."}</p>;
  return (
    <ul className={styles.showcase}>
      {cells.map((it, i) => (
        <li key={it ? it.id : `e${i}`} style={{ "--i": i }}>
          {it ? (
            <button type="button" className={styles.shelf} onClick={() => onOpen && onOpen(it)}>
              <Medal shape={it.shape} finish={finish} glyph={it.glyph || undefined} text={it.text || undefined} earned size={size} backTitle={it.title} backNote="ReactLab · ВСМ" />
              <span className={styles.shelfTitle}>{it.title}</span>
            </button>
          ) : (
            <span className={styles.slot} aria-hidden="true"><span>+</span></span>
          )}
        </li>
      ))}
    </ul>
  );
}
