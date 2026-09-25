import { useEffect, useRef, useState } from "react";
import Icon from "./Icon.jsx";
import { STYLES, getStyle, setStyle, subscribeAppearance } from "../../appearance.js";
import bell from "./NotificationBell.module.css";
import styles from "./StyleSwitcher.module.css";

const GROUPS = [
  { title: "Однотонный фон", match: (s) => s.background !== "photo" },
  { title: "Фон с поездом", match: (s) => s.background === "photo" }
];

/** Миниатюра стиля: для фото — кадр, для однотонных — схема «фон, панель, акцент». */
function Preview({ style }) {
  return <span className={styles.preview} data-preview={style.key} aria-hidden="true"><span /><span /></span>;
}

/** Выбор стиля оформления в шапке. Новый стиль применяется сразу, с плавным переходом. */
export default function StyleSwitcher() {
  const [open, setOpen] = useState(false);
  const [current, setCurrent] = useState(getStyle);
  const rootRef = useRef(null);

  useEffect(() => subscribeAppearance(() => setCurrent(getStyle())), []);

  useEffect(() => {
    if (!open) return undefined;
    function onDoc(ev) {
      if (rootRef.current && !rootRef.current.contains(ev.target)) setOpen(false);
    }
    function onKey(ev) { if (ev.key === "Escape") setOpen(false); }
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div className={bell.root} ref={rootRef}>
      <button
        type="button"
        className={bell.trigger}
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="true"
        aria-expanded={open}
        aria-label="Стиль оформления"
      >
        <Icon name="palette" size={15} />
      </button>
      {open && (
        <div className={`${bell.dropdown} ${styles.menu}`} role="menu">
          <div className={bell.dropdownHead}>
            <span className={bell.dropdownTitle}>Стиль оформления</span>
          </div>
          {GROUPS.map((g) => (
            <div key={g.title} className={styles.group}>
              <p className={styles.groupTitle}>{g.title}</p>
              {STYLES.filter(g.match).map((s) => (
                <button
                  key={s.key}
                  type="button"
                  role="menuitemradio"
                  aria-checked={current === s.key}
                  className={styles.item}
                  onClick={() => setStyle(s.key)}
                >
                  <Preview style={s} />
                  <span className={styles.itemText}>
                    <span className={styles.itemTitle}>{s.title}</span>
                    <span className={styles.itemNote}>{s.note}</span>
                  </span>
                  {current === s.key && <Icon name="check" size={16} strokeWidth={2.25} className={styles.check} />}
                </button>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
