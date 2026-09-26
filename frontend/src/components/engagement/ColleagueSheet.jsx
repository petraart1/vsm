import { useEffect, useRef, useState } from "react";
import * as api from "../../api.js";
import Avatar from "../ui/Avatar.jsx";
import Icon from "../ui/Icon.jsx";
import ShowcaseGrid from "./ShowcaseGrid.jsx";
import { readShowcase } from "../../engagement.js";
import { readFinish } from "../awards/awards.js";
import { gradeFor } from "../../progress.js";
import styles from "./Engagement.module.css";

/** Профиль коллеги из рейтинга: разряд, очки и его витрина наград. entry — строка лидерборда. */
export default function ColleagueSheet({ entry, onClose }) {
  const ref = useRef(null);
  const [data, setData] = useState(null);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (entry && !d.open) d.showModal();
    if (!entry && d.open) d.close();
    setData(null);
    if (entry) {
      if (entry.me) setData({ items: readShowcase(), finish: readFinish() });
      else api.getShowcase(entry).then(setData, () => setData({ items: null }));
    }
  }, [entry]);
  const grade = entry ? gradeFor(entry.totalScore).current : null;
  const initials = entry ? String(entry.displayName || "??").split(/\s+/).map((w) => w[0]).join("").slice(0, 2).toUpperCase() : "";
  return (
    <dialog ref={ref} className={styles.editor} onClose={onClose} onClick={(e) => { if (e.target === ref.current) onClose(); }} aria-label="Профиль коллеги">
      {entry && (
        <div className={styles.editorInner}>
          <header className={styles.colleagueHead}>
            <Avatar initials={initials} size={64} tone={entry.me ? "solid" : "soft"} />
            <div>
              <p className={styles.colleagueName}>{entry.me ? "Вы" : entry.displayName}</p>
              <p className={styles.colleagueMeta}>{grade.title} · {entry.rank} место · {entry.totalScore} очков</p>
            </div>
            <button type="button" className={styles.closeRound} onClick={onClose} aria-label="Закрыть"><Icon name="x" size={16} /></button>
          </header>
          <p className={styles.kicker}>Витрина наград</p>
          {data === null ? (
            <p className={styles.empty}>Загружаем витрину…</p>
          ) : data.items === null ? (
            <p className={styles.empty}>Коллега ещё не открыл свою витрину.</p>
          ) : (
            <ShowcaseGrid items={data.items} finish={data.finish || "enamel"} size={80} emptyText={entry.me ? "Выставьте награды в профиле — их увидят коллеги." : "Витрина пока пуста."} />
          )}
          {entry.me && <a className={styles.linkBtn} href="#/profile" onClick={onClose}>Настроить витрину</a>}
        </div>
      )}
    </dialog>
  );
}
