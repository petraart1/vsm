import { useEffect, useRef, useState } from "react";
import Icon from "../ui/Icon.jsx";
import Button from "../ui/Button.jsx";
import Medal from "../awards/Medal.jsx";
import { SHOWCASE_MAX } from "../../engagement.js";
import styles from "./Engagement.module.css";

/**
 * Настройка витрины: выбор до шести полученных наград. Порядок — порядок выбора.
 * props: open, awards (все полученные снимки), selected (текущая витрина), finish, onSave(items), onClose.
 */
export default function ShowcaseEditor({ open, awards, selected, finish, onSave, onClose }) {
  const ref = useRef(null);
  const [picked, setPicked] = useState(selected.map((x) => x.id));
  useEffect(() => { if (open) setPicked(selected.map((x) => x.id)); }, [open, selected]);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);

  function toggle(id) {
    setPicked((p) => (p.includes(id) ? p.filter((x) => x !== id) : p.length >= SHOWCASE_MAX ? p : [...p, id]));
  }

  function save() {
    onSave(picked.map((id) => awards.find((a) => a.id === id)).filter(Boolean));
  }

  return (
    <dialog ref={ref} className={styles.editor} onClose={onClose} onClick={(e) => { if (e.target === ref.current) onClose(); }} aria-label="Настройка витрины">
      <div className={styles.editorInner}>
        <header className={styles.editorHead}>
          <button type="button" className={styles.linkBtn} onClick={onClose}>Отмена</button>
          <div className={styles.editorTitle}>
            <b>Витрина</b>
            <span>{picked.length} из {SHOWCASE_MAX}</span>
          </div>
          <button type="button" className={`${styles.linkBtn} ${styles.linkStrong}`} onClick={save}>Готово</button>
        </header>
        {awards.length === 0 ? (
          <p className={styles.empty}>Пока нечего выставить: пройдите смену без замечаний, получите квалификацию по модулю или держите серию входов 3 дня.</p>
        ) : (
          <ul className={styles.pickList}>
            {awards.map((a) => {
              const on = picked.includes(a.id);
              const full = !on && picked.length >= SHOWCASE_MAX;
              return (
                <li key={a.id}>
                  <button type="button" className={styles.pick} data-on={on || undefined} disabled={full} onClick={() => toggle(a.id)} aria-pressed={on}>
                    <Medal shape={a.shape} finish={finish} glyph={a.glyph || undefined} text={a.text || undefined} size={48} />
                    <span className={styles.pickTitle}>{a.title}</span>
                    <span className={styles.pickCheck}>{on ? <span className={styles.pickNum}>{picked.indexOf(a.id) + 1}</span> : <Icon name="plus" size={14} />}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
        <p className={styles.editorNote}><Icon name="eye" size={14} /> Витрину видят коллеги, открывшие ваш профиль в рейтинге.</p>
        <Button size="lg" className={styles.rewardBtn} onClick={save}>Сохранить витрину</Button>
      </div>
    </dialog>
  );
}
