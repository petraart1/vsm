import { useState, useEffect } from "react";
import * as api from "../api.js";
import Skeleton from "../components/ui/Skeleton.jsx";
import ErrorState from "../components/ui/ErrorState.jsx";
import PageHeader from "../components/ui/PageHeader.jsx";
import { CountUp } from "../components/motion/Motion.jsx";
import Seal from "../components/progress/Seal.jsx";
import CertificateCard from "../components/progress/CertificateCard.jsx";
import CertificateDialog from "../components/progress/CertificateDialog.jsx";
import { buildQualifications, toDistinction, formatDate } from "../progress.js";
import styles from "./Achievements.module.css";

const STATUS_LABEL = {
  certified: "присвоена",
  in_training: "в обучении",
  not_started: "не начата"
};

/** Сводная ведомость: одна ячейка на модуль, цвет — статус квалификации. */
function Transcript({ qualifications, onOpen }) {
  return (
    <ol className={styles.transcript} aria-label="Ведомость по учебным модулям">
      {qualifications.map((q) => (
        <li key={q.block}>
          <button
            type="button"
            className={styles.cell}
            data-state={q.status}
            onClick={() => onOpen(q)}
            aria-label={`Модуль ${q.code}, ${q.title}: квалификация ${STATUS_LABEL[q.status]}`}
          >
            <span className={styles.cellCode}>{q.code}</span>
            <span className={styles.cellBar}>
              <span style={{ transform: `scaleX(${q.total ? q.completed / q.total : 0})` }} />
            </span>
          </button>
        </li>
      ))}
    </ol>
  );
}

/**
 * Квалификации проводника: учебные модули (по блокам ситуаций) и служебные отличия
 * (каталог ачивок backend). См. design/screens/achievements.md.
 * Query: ?highlight=<код отличия> или ?module=<блок> — открыть соответствующее свидетельство.
 */
export default function Achievements({ route }) {
  const query = route.query || {};
  const [s, setS] = useState({ phase: "loading" });
  const [opened, setOpened] = useState(null);

  function load() {
    setS({ phase: "loading" });
    Promise.all([
      api.listScenarios(),
      api.getAchievements(),
      api.getProfile().catch(() => null)
    ]).then(
      ([scenarios, achievements, profile]) => {
        const qualifications = buildQualifications(scenarios);
        const distinctions = (achievements || []).map(toDistinction);
        setS({ phase: "ready", qualifications, distinctions, profile });
        if (query.highlight) {
          const d = distinctions.find((x) => x.code === query.highlight);
          if (d) setOpened({ kind: "distinction", d });
        } else if (query.module) {
          const q = qualifications.find((x) => x.block === query.module);
          if (q) setOpened({ kind: "module", q });
        }
      },
      () => setS({ phase: "error" })
    );
  }

  useEffect(load, []);

  if (s.phase === "loading") {
    return (
      <div className={styles.page}>
        <PageHeader title="Квалификации" />
        <Skeleton height="56px" />
        <div className={styles.grid}>
          {[1, 2, 3, 4, 5, 6].map((i) => <Skeleton key={i} height="108px" />)}
        </div>
      </div>
    );
  }

  if (s.phase === "error") {
    return <ErrorState message="Сервер тренажёра не вернул каталог модулей. Проверьте, что backend запущен, и повторите." onRetry={load} />;
  }

  const { qualifications, distinctions, profile } = s;
  const certified = qualifications.filter((q) => q.status === "certified").length;
  const inTraining = qualifications.filter((q) => q.status === "in_training").length;
  const earnedDistinctions = distinctions.filter((d) => d.earned).length;
  const totalHours = qualifications.filter((q) => q.status === "certified").reduce((a, q) => a + q.hours, 0);
  const openModule = (q) => setOpened({ kind: "module", q });

  return (
    <div className={styles.page}>
      <PageHeader
        title="Квалификации"
        description="Десять учебных модулей по типовым ситуациям на борту. Квалификация по модулю присваивается, когда все его ситуации пройдены без критических ошибок безопасности."
      />

      <dl className={`${styles.totals} rv`} style={{ "--i": 2 }}>
        <div><dt>Присвоено</dt><dd><CountUp value={certified} delay={300} /><span className={styles.of}>из {qualifications.length}</span></dd></div>
        <div><dt>В обучении</dt><dd><CountUp value={inTraining} delay={350} /></dd></div>
        <div><dt>Зачтено академических часов</dt><dd><CountUp value={totalHours} delay={400} /></dd></div>
        <div><dt>Служебные отличия</dt><dd><CountUp value={earnedDistinctions} delay={450} /><span className={styles.of}>из {distinctions.length}</span></dd></div>
      </dl>

      <div className="rv" style={{ "--i": 3 }}>
        <Transcript qualifications={qualifications} onOpen={openModule} />
      </div>

      <section className="rv" style={{ "--i": 4 }} aria-labelledby="modules-title">
        <h2 className={styles.sectionTitle} id="modules-title">Учебные модули</h2>
        <div className={styles.grid}>
          {qualifications.map((q) => (
            <CertificateCard
              key={q.block}
              qualification={q}
              onOpen={openModule}
              highlighted={query.module === q.block}
            />
          ))}
        </div>
      </section>

      <section className="rv" style={{ "--i": 5 }} aria-labelledby="dist-title">
        <div className={styles.sectionHead}>
          <h2 className={styles.sectionTitle} id="dist-title">Служебные отличия</h2>
        </div>
        <ul className={styles.distinctions}>
          {distinctions.map((d) => (
            <li key={d.code}>
              <button
                type="button"
                className={styles.distinction}
                data-earned={d.earned || undefined}
                data-highlighted={query.highlight === d.code || undefined}
                onClick={() => setOpened({ kind: "distinction", d })}
              >
                <Seal
                  mark={d.mark}
                  caption="отличие"
                  ring="ReactLab  •  служебное отличие  •  "
                  state={d.earned ? "certified" : "not_started"}
                  size={84}
                />
                <span className={styles.distTitle}>{d.title}</span>
                <span className={styles.distMeta}>{d.earned ? formatDate(d.earnedAt, { day: "numeric", month: "short", year: "numeric" }) : d.description}</span>
              </button>
            </li>
          ))}
        </ul>
      </section>

      <CertificateDialog
        item={opened}
        onClose={() => setOpened(null)}
        playerId={profile ? profile.playerId : api.getPlayerId()}
        displayName={profile ? profile.displayName : "проводнику"}
      />
    </div>
  );
}
