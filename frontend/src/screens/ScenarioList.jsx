import { useState, useEffect, useRef } from "react";
import * as api from "../api.js";
import Badge from "../components/ui/Badge.jsx";
import VerdictMark from "../components/ui/VerdictMark.jsx";
import { STRESS_SCENARIOS } from "../shift/stressScenarios.js";
import { SeatedPerson } from "../components/characters/People.jsx";
import Button from "../components/ui/Button.jsx";
import Icon from "../components/ui/Icon.jsx";
import Skeleton from "../components/ui/Skeleton.jsx";
import EmptyState from "../components/ui/EmptyState.jsx";
import ErrorState from "../components/ui/ErrorState.jsx";
import PageHeader from "../components/ui/PageHeader.jsx";
import { CountUp } from "../components/motion/Motion.jsx";
import { BLOCK_ICON } from "../progress.js";
import styles from "./ScenarioList.module.css";
import examStyles from "./Exam.module.css";

const FILTERS = [
  { key: "all", label: "Все" },
  { key: "todo", label: "Не пройдены" },
  { key: "done", label: "Пройдены" }
];

function ScenarioRow({ situation, highlighted }) {
  const ref = useRef(null);
  useEffect(() => {
    if (highlighted && ref.current) ref.current.scrollIntoView({ behavior: document.documentElement.getAttribute("data-motion") === "reduce" ? "auto" : "smooth", block: "center" });
  }, [highlighted]);

  const done = situation.status === "completed";
  const r = situation.lastResult;

  return (
    <li ref={ref} className={styles.row} data-highlighted={highlighted || undefined}>
      <a className={styles.rowLink} href={`#/scenarios/${situation.id}/play`}>
        <VerdictMark verdict={done && r ? r.verdict : null} size={20} className={styles.statusMark} />
        <span className={styles.rowMain}>
          <span className={styles.rowTitle}>{situation.title}</span>
          <span className={styles.rowTags}>
            {situation.flagship && <Badge tone="inverse">Расширенный</Badge>}
            {situation.escalation && (
              <Badge tone="neutral"><Icon name="phone" size={11} />Эскалация</Badge>
            )}
          </span>
        </span>
        <span className={styles.rowResult}>
          {r ? (
            <>
              <span className={styles.score} data-scale="safety" title="Рейтинг безопасности">
                <Icon name="shield" size={13} />{Math.round(r.safety)}
              </span>
              <span className={styles.score} title="Лояльность пассажира">
                <Icon name="smile" size={13} />{Math.round(r.loyalty)}
              </span>
            </>
          ) : (
            <span className={styles.notYet}>Не пройден</span>
          )}
        </span>
        <span className={styles.rowAction}>{done ? "Повторить" : "Начать"}</span>
      </a>
    </li>
  );
}

function BlockSection({ block, situations, collapsed, highlightId, onToggle, index }) {
  if (situations.length === 0) return null;
  const pct = block.total ? (block.completed / block.total) * 100 : 0;
  return (
    <section className={`${styles.block} rv`} style={{ "--i": Math.min(index, 6) + 2 }}>
      <button type="button" className={styles.blockHead} onClick={onToggle} aria-expanded={!collapsed}>
        <Icon name="chevronDown" size={16} className={styles.chevron} />
        <h2 className={styles.blockTitle}>
          <span className={styles.blockIcon}><Icon name={BLOCK_ICON[block.key] || "help"} size={15} /></span>
          {block.label}
        </h2>
        <span className={styles.blockMeter} aria-hidden="true">
          <span style={{ width: `${pct}%` }} />
        </span>
        <span className={styles.blockCount}>{block.completed} из {block.total}</span>
      </button>
      <div className={styles.collapse} data-collapsed={collapsed || undefined}>
        <ul className={styles.list}>
          {situations.map((s) => (
            <ScenarioRow key={s.id} situation={s} highlighted={highlightId === s.id} />
          ))}
        </ul>
      </div>
    </section>
  );
}

export default function ScenarioList({ route }) {
  const highlightId = route.query && route.query.highlight ? Number(route.query.highlight) : null;
  const [loadState, setLoadState] = useState({ loading: true, error: false, data: null });
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");
  const [collapsed, setCollapsed] = useState({});

  function load() {
    setLoadState((prev) => ({ loading: true, error: false, data: prev.data }));
    api.listScenarios().then(
      (data) => setLoadState({ loading: false, error: false, data }),
      () => setLoadState({ loading: false, error: true, data: null })
    );
  }

  useEffect(load, []);

  if (loadState.loading && !loadState.data) {
    return (
      <div>
        <PageHeader title="Сценарии" />
        <div className={styles.skeletons}>
          {[1, 2, 3, 4, 5, 6].map((i) => <Skeleton key={i} height="56px" />)}
        </div>
      </div>
    );
  }

  if (loadState.error) {
    return <ErrorState message="Сервер тренажёра не ответил. Проверьте, что backend запущен, и повторите." onRetry={load} />;
  }

  const data = loadState.data;
  const query = search.trim().toLowerCase();
  const filtered = data.situations.filter((s) => {
    const matchesSearch = !query || s.title.toLowerCase().includes(query);
    const matchesStatus =
      statusFilter === "all" ||
      (statusFilter === "done" && s.status === "completed") ||
      (statusFilter === "todo" && s.status !== "completed");
    return matchesSearch && matchesStatus;
  });

  const byBlock = {};
  filtered.forEach((s) => {
    byBlock[s.block] = byBlock[s.block] || [];
    byBlock[s.block].push(s);
  });

  const nextUp = data.situations.find((s) => s.status !== "completed");

  return (
    <div>
      <PageHeader
        title="Тренировки"
        description="Ситуации на борту ВСМ Москва — Санкт-Петербург вне смены: отработайте диалог, затем проверьте себя в рейсе."
        actions={nextUp && (
          <Button as="a" href={`#/scenarios/${nextUp.id}/play`}>
            <Icon name="play" size={14} />Следующий сценарий
          </Button>
        )}
      />

      <section className={`${examStyles.entry} rv`} style={{ "--i": 1 }} aria-labelledby="exam-entry-title">
        <span className={examStyles.entryIcon} aria-hidden="true"><Icon name="clipboard" size={22} /></span>
        <span className={examStyles.entryText}>
          <h2 id="exam-entry-title" className={examStyles.entryTitle}>Экзамен</h2>
          <p className={examStyles.entryNote}>5 или 10 ситуаций из разных блоков подряд, без подсказок. Итоговая оценка и бонусные очки — в конце.</p>
        </span>
        <Button as="a" href="#/exam" className={examStyles.entryBtn}>Перейти к экзамену<Icon name="chevronRight" size={14} /></Button>
      </section>

      <div className={`${styles.summary} rv`} style={{ "--i": 1 }}>
        <p className={styles.summaryNum}>
          <CountUp value={data.completedCount} /><span className={styles.summaryTotal}> / {data.totalCount}</span>
        </p>
        <p className={styles.summaryLabel}>ситуаций пройдено</p>
        <div className={styles.summaryBar} aria-hidden="true">
          <span style={{ width: `${data.totalCount ? (data.completedCount / data.totalCount) * 100 : 0}%` }} />
        </div>
      </div>

      <section className={`${styles.stress} rv`} style={{ "--i": 2 }} aria-labelledby="stress-title">
        <div className={styles.stressHead}>
          <h2 id="stress-title">Стрессовые ситуации</h2>
          <p>Нарушение порядка, опьянение, драка, приступ — что делает проводник и что делает полиция.</p>
        </div>
        <ul className={styles.stressList}>
          {STRESS_SCENARIOS.map((sc, i) => (
            <li key={sc.id}>
              <a className={styles.stressCard} href={`#/scenarios/local-${sc.id}/play`}>
                <span className={styles.stressArt} aria-hidden="true"><SeatedPerson variant={i * 3 + 1} size={64} mood={sc.mood} /></span>
                <span className={styles.stressText}>
                  <span className={styles.stressTitle}>{sc.title}</span>
                  <span className={styles.stressMeta}>{sc.urgent ? "Срочно" : "Без спешки"} · {sc.blockLabel}</span>
                </span>
              </a>
            </li>
          ))}
        </ul>
      </section>

      <div className={`${styles.toolbar} rv`} style={{ "--i": 3 }}>
        <label className={styles.search}>
          <Icon name="search" size={16} />
          <span className="visually-hidden">Поиск по названию</span>
          <input
            type="search"
            placeholder="Найти ситуацию"
            value={search}
            onChange={(ev) => setSearch(ev.target.value)}
          />
        </label>
        <div className={styles.segmented} role="radiogroup" aria-label="Статус">
          {FILTERS.map((f) => (
            <button
              key={f.key}
              type="button"
              role="radio"
              aria-checked={statusFilter === f.key}
              className={styles.segment}
              onClick={() => setStatusFilter(f.key)}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {filtered.length === 0 && (
        <EmptyState
          title="Ничего не найдено"
          message="Под этот запрос и фильтр не подходит ни одна ситуация."
          action={<Button variant="secondary" onClick={() => { setSearch(""); setStatusFilter("all"); }}>Сбросить фильтры</Button>}
        />
      )}

      <div className={styles.blocks}>
        {data.blocks.map((block, i) => (
          <BlockSection
            key={block.key}
            index={i}
            block={block}
            situations={byBlock[block.key] || []}
            collapsed={!!collapsed[block.key]}
            highlightId={highlightId}
            onToggle={() => setCollapsed((prev) => ({ ...prev, [block.key]: !prev[block.key] }))}
          />
        ))}
      </div>
    </div>
  );
}
