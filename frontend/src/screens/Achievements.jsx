import { useState, useEffect } from "react";
import * as api from "../api.js";
import Skeleton from "../components/ui/Skeleton.jsx";
import ErrorState from "../components/ui/ErrorState.jsx";
import PageHeader from "../components/ui/PageHeader.jsx";
import { CountUp } from "../components/motion/Motion.jsx";
import Medal from "../components/awards/Medal.jsx";
import CertificateDialog from "../components/progress/CertificateDialog.jsx";
import { readFinish, moduleMedal, distinctionMedal } from "../components/awards/awards.js";
import { buildQualifications, toDistinction, formatDate } from "../progress.js";
import { readShifts } from "../shift/shiftModel.js";
import { STREAK_MILESTONES, claimedStreakRewards, streakMedal } from "../engagement.js";
import { officialAwards } from "../components/awards/awards.js";
import VerifiedBadge from "../components/ui/VerifiedBadge.jsx";
import { useAccount } from "../account.js";
import styles from "./Achievements.module.css";

/**
 * Награды в духе Apple Fitness: объёмные медали сеткой, полученные — цветные, будущие — серые.
 * Единый дизайн-код: модуль — круг, служебное отличие — шестигранник, смена — эмалевый круг с поездом.
 * Query: ?highlight=<код отличия> или ?module=<блок> — открыть соответствующее свидетельство.
 */
export default function Achievements({ route }) {
  const query = route.query || {};
  const [s, setS] = useState({ phase: "loading" });
  const [opened, setOpened] = useState(null);
  const [finish] = useState(readFinish);
  const account = useAccount();
  const verified = !!(account && account.verified);
  const [custom, setCustom] = useState([]);
  useEffect(() => { api.getCustomAwards().then((l) => setCustom(Array.isArray(l) ? l : []), () => setCustom([])); }, []);

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
        <PageHeader title="Награды" />
        <div className={styles.grid}>
          {[1, 2, 3, 4, 5, 6].map((i) => <Skeleton key={i} height="140px" />)}
        </div>
      </div>
    );
  }

  if (s.phase === "error") {
    return <ErrorState message="Не удалось загрузить модули. Проверьте подключение к интернету и попробуйте ещё раз." onRetry={load} />;
  }

  const { qualifications, distinctions, profile } = s;
  const certified = qualifications.filter((q) => q.status === "certified").length;
  const earnedDistinctions = distinctions.filter((d) => d.earned).length;
  const shifts = readShifts();
  const cleanShifts = shifts.filter((x) => x.admitted);
  const openModule = (q) => setOpened({ kind: "module", q });

  return (
    <div className={styles.page}>
      <PageHeader title="Награды" description="Квалификации по модулям, служебные отличия и смены без замечаний." />

      <div className={`${styles.toolbar} rv`} style={{ "--i": 1 }}>
        <dl className={styles.totals}>
          <div><dt>Модули</dt><dd><CountUp value={certified} /><small>/{qualifications.length}</small></dd></div>
          <div><dt>Отличия</dt><dd><CountUp value={earnedDistinctions} /><small>/{distinctions.length}</small></dd></div>
          <div><dt>Смены</dt><dd><CountUp value={cleanShifts.length} /></dd></div>
        </dl>
      </div>

      {cleanShifts.length > 0 && (
        <section className="rv" style={{ "--i": 2 }}>
          <h2 className={styles.sectionTitle}>Смены</h2>
          <div className={styles.featured}>
            <Medal shape="circle" finish={finish} glyph="train" size={108} spin backTitle="Смена без замечаний" backNote={`${cleanShifts.length} ${cleanShifts.length === 1 ? "раз" : "раза"}`} />
            <div>
              <p className={styles.featTitle}>Смена без замечаний</p>
              <p className={styles.featNote}>Допуск подтверждён {cleanShifts.length} {cleanShifts.length === 1 ? "раз" : cleanShifts.length < 5 ? "раза" : "раз"}. Последний — {formatDate(cleanShifts[0].at, { day: "numeric", month: "long" })}.</p>
              <p className={styles.featHint}>Покрутите медаль пальцем</p>
            </div>
          </div>
        </section>
      )}

      <section className="rv" style={{ "--i": 3 }}>
        <div className={styles.sectionRow}>
          <h2 className={styles.sectionTitle}>Официальные награды</h2>
          {verified ? <VerifiedBadge withLabel /> : <a className={styles.sectionLink} href={api.USE_MOCKS ? "#/auth/esia-demo" : (account ? api.esiaAuthorizeUrl() : "#/login?next=/achievements")}>Подтвердить через Госуслуги</a>}
        </div>
        {!verified && <p className={styles.note}>Официальные награды получают подтверждённые учётные записи: они учитываются в допуске и решении для HR. Выполненные условия сохраняются — награды появятся сразу после подтверждения.</p>}
        <ul className={styles.grid}>
          {officialAwards({ shifts, qualifications }).map((a, i) => {
            const got = verified && a.condition;
            return (
              <li key={a.id} style={{ "--i": i }}>
                <div className={styles.award} data-earned={got || undefined}>
                  <span className={styles.medalWrap}><Medal shape={a.shape} finish={finish} glyph={a.glyph} earned={got} size={92} spin={got} backTitle={a.title} backNote="Официальная награда" /></span>
                  <span className={styles.awardTitle}>{a.title}</span>
                  <span className={styles.awardMeta}>{got ? "Получено" : a.condition ? "Условие выполнено — нужна проверка личности" : a.note}</span>
                </div>
              </li>
            );
          })}
        </ul>
      </section>

      {custom.length > 0 && (
        <section className="rv" style={{ "--i": 3 }}>
          <h2 className={styles.sectionTitle}>Особые награды</h2>
          <ul className={styles.grid}>
            {custom.map((a, i) => (
              <li key={a.id} style={{ "--i": i }}>
                <div className={styles.award} data-earned={a.earned || undefined}>
                  <span className={styles.medalWrap}><Medal shape={a.shape} finish={finish} glyph={a.glyph || "medal"} earned={!!a.earned} size={92} spin={!!a.earned} backTitle={a.title} backNote="Особая награда" /></span>
                  <span className={styles.awardTitle}>{a.title}</span>
                  <span className={styles.awardMeta}>{a.earned ? `Получено ${formatDate(a.earnedAt, { day: "numeric", month: "short" }) || ""}` : a.description}{a.verifiedOnly && !a.earned ? " · для подтверждённых" : ""}</span>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="rv" style={{ "--i": 3 }}>
        <div className={styles.sectionRow}>
          <h2 className={styles.sectionTitle}>Серии входов</h2>
          <a className={styles.sectionLink} href="#/profile">Витрина профиля</a>
        </div>
        <ul className={styles.grid}>
          {STREAK_MILESTONES.map((m, i) => {
            const got = claimedStreakRewards().includes(m.days);
            return (
              <li key={m.days} style={{ "--i": i }}>
                <div className={styles.award} data-earned={got || undefined}>
                  <span className={styles.medalWrap}><Medal {...streakMedal(m.days, finish, got)} size={92} spin={got} /></span>
                  <span className={styles.awardTitle}>{m.title}</span>
                  <span className={styles.awardMeta}>{got ? "Получено" : m.note}</span>
                </div>
              </li>
            );
          })}
        </ul>
      </section>

      <section className="rv" style={{ "--i": 3 }}>
        <h2 className={styles.sectionTitle}>Учебные модули</h2>
        <ul className={styles.grid}>
          {qualifications.map((q, i) => (
            <li key={q.block} style={{ "--i": i }}>
              <button type="button" className={styles.award} data-earned={q.status === "certified" || undefined} data-highlighted={query.module === q.block || undefined} onClick={() => openModule(q)}>
                <span className={styles.medalWrap}>
                  <Medal {...moduleMedal(q, finish)} size={92} />
                  {q.status === "in_training" && <Progress value={q.completed / q.total} />}
                </span>
                <span className={styles.awardTitle}>{q.title}</span>
                <span className={styles.awardMeta}>{q.status === "certified" ? formatDate(q.certifiedAt, { day: "numeric", month: "short", year: "numeric" }) : q.status === "in_training" ? `${q.completed} из ${q.total}` : `Модуль ${q.code}`}</span>
              </button>
            </li>
          ))}
        </ul>
      </section>

      <section className="rv" style={{ "--i": 4 }}>
        <h2 className={styles.sectionTitle}>Служебные отличия</h2>
        <ul className={styles.grid}>
          {distinctions.map((d, i) => (
            <li key={d.code} style={{ "--i": i }}>
              <button type="button" className={styles.award} data-earned={d.earned || undefined} data-highlighted={query.highlight === d.code || undefined} onClick={() => setOpened({ kind: "distinction", d })}>
                <span className={styles.medalWrap}><Medal {...distinctionMedal(d, finish)} size={92} /></span>
                <span className={styles.awardTitle}>{d.title}</span>
                <span className={styles.awardMeta}>{d.earned ? formatDate(d.earnedAt, { day: "numeric", month: "short", year: "numeric" }) : d.description}</span>
              </button>
            </li>
          ))}
        </ul>
      </section>

      <CertificateDialog
        key={finish}
        item={opened}
        onClose={() => setOpened(null)}
        playerId={profile ? profile.playerId : api.getPlayerId()}
        displayName={profile ? profile.displayName : "проводнику"}
      />
    </div>
  );
}

function Progress({ value }) {
  const c = 2 * Math.PI * 52;
  return (
    <svg className={styles.progress} viewBox="0 0 112 112" aria-hidden="true">
      <circle cx="56" cy="56" r="52" className={styles.progressTrack} />
      <circle cx="56" cy="56" r="52" className={styles.progressFill} strokeDasharray={c} strokeDashoffset={c * (1 - value)} />
    </svg>
  );
}
