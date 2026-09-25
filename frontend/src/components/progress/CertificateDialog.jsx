import { useEffect, useRef } from "react";
import Seal from "./Seal.jsx";
import Button from "../ui/Button.jsx";
import Icon from "../ui/Icon.jsx";
import { certificateNumber, formatDate, pluralRu } from "../../progress.js";
import styles from "./CertificateDialog.module.css";

function CheckIcon({ met }) {
  return (
    <svg className={styles.checkIcon} data-met={met || undefined} viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
      <circle cx="8" cy="8" r="7" />
      {met ? <path d="M4.8 8.3l2.1 2.1 4.3-4.6" /> : <path d="M5.5 8h5" />}
    </svg>
  );
}

function ModuleContent({ q, playerId, displayName }) {
  const certified = q.status === "certified";
  const nextScenario = q.scenarios.find((s) => s.status !== "completed");

  return (
    <>
      <header className={styles.head}>
        <Seal mark={q.code} state={q.status} progress={q.total ? q.completed / q.total : 0} size={104} />
        <div className={styles.headText}>
          <h2 className={styles.docTitle} id="certificate-title">
            {certified ? "Свидетельство о повышении квалификации" : "Программа учебного модуля"}
          </h2>
          {certified && <p className={styles.number}>№ {certificateNumber(q.code, playerId)}</p>}
        </div>
      </header>

      <p className={styles.statement}>
        {certified ? (
          <>Выдано <strong>{displayName}</strong> в подтверждение освоения модуля {q.code} «{q.title}» в объёме {q.hours}{"\u00A0"}ак.{"\u00A0"}{pluralRu(q.hours, "часа", "часов", "часов")}.</>
        ) : (
          <>Модуль {q.code} «{q.title}», {q.hours}{"\u00A0"}ак.{"\u00A0"}{pluralRu(q.hours, "час", "часа", "часов")}. Квалификация присваивается автоматически, когда выполнены все требования ниже.</>
        )}
      </p>

      <section aria-labelledby="req-title">
        <h3 className={styles.sectionTitle} id="req-title">Требования</h3>
        <ul className={styles.requirements}>
          {q.requirements.map((r) => (
            <li key={r.key} className={styles.requirement}>
              <CheckIcon met={r.met} />
              <span className={styles.reqLabel}>{r.label}</span>
              <span className={styles.reqDetail}>{r.detail}</span>
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="sit-title">
        <h3 className={styles.sectionTitle} id="sit-title">Ситуации модуля</h3>
        <ol className={styles.scenarios}>
          {q.scenarios.map((s) => (
            <li key={s.id} className={styles.scenario}>
              <a href={`#/scenarios/${s.id}/play`} className={styles.scenarioLink}>{s.title}</a>
              <span className={styles.scenarioResult} data-done={s.status === "completed" || undefined}>
                {s.lastResult ? (
                  <>
                    <span title="Рейтинг безопасности"><Icon name="shield" size={12} />{s.lastResult.safety}</span>
                    <span title="Лояльность пассажира"><Icon name="smile" size={12} />{s.lastResult.loyalty}</span>
                  </>
                ) : "не пройдена"}
              </span>
            </li>
          ))}
        </ol>
      </section>

      <footer className={styles.foot}>
        {certified ? (
          <p className={styles.signature}>Присвоено {formatDate(q.certifiedAt)}, учебный центр ReactLab</p>
        ) : (
          <p className={styles.signature}>Пройдено {q.completed} из {q.total}</p>
        )}
        {nextScenario && (
          <Button as="a" variant="primary" href={`#/scenarios/${nextScenario.id}/play`}>
            {q.completed === 0 ? "Начать модуль" : "Продолжить обучение"}
          </Button>
        )}
      </footer>
    </>
  );
}

function DistinctionContent({ d }) {
  return (
    <>
      <header className={styles.head}>
        <Seal mark={d.mark} caption="отличие" ring="ReactLab  •  служебное отличие  •  " state={d.earned ? "certified" : "not_started"} size={104} />
        <div className={styles.headText}>
          <h2 className={styles.docTitle} id="certificate-title">{d.title}</h2>
          <p className={styles.number}>{d.earned ? `Отмечено ${formatDate(d.earnedAt)}` : "Ещё не получено"}</p>
        </div>
      </header>
      <section>
        <h3 className={styles.sectionTitle}>Условие</h3>
        <p className={styles.statement}>{d.description}</p>
      </section>
    </>
  );
}

/**
 * Модальное свидетельство. item: { kind: "module", q } | { kind: "distinction", d } | null.
 * Используется нативный <dialog>: фокус-ловушка, Esc и затемнение фона — средствами браузера.
 */
export default function CertificateDialog({ item, onClose, playerId, displayName }) {
  const ref = useRef(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (item && !dialog.open) dialog.showModal();
    if (!item && dialog.open) dialog.close();
  }, [item]);

  function onBackdropClick(e) {
    if (e.target === ref.current) onClose();
  }

  return (
    <dialog ref={ref} className={styles.dialog} aria-labelledby="certificate-title" onClose={onClose} onClick={onBackdropClick}>
      {item && (
        <div className={styles.paper}>
          <button type="button" className={styles.close} onClick={onClose} aria-label="Закрыть">
            <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"><path d="M3.5 3.5l9 9M12.5 3.5l-9 9" /></svg>
          </button>
          {item.kind === "module"
            ? <ModuleContent q={item.q} playerId={playerId} displayName={displayName} />
            : <DistinctionContent d={item.d} />}
        </div>
      )}
    </dialog>
  );
}
