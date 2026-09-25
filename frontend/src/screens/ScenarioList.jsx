import { useState, useEffect, useRef } from "react";
import * as api from "../api.js";
import Card from "../components/ui/Card.jsx";
import Badge from "../components/ui/Badge.jsx";
import Button from "../components/ui/Button.jsx";
import Skeleton from "../components/ui/Skeleton.jsx";
import EmptyState from "../components/ui/EmptyState.jsx";
import ErrorState from "../components/ui/ErrorState.jsx";
import PageHeader from "../components/ui/PageHeader.jsx";
import styles from "./ScenarioList.module.css";

function ScenarioCard({ situation, highlighted }) {
  const ref = useRef(null);
  useEffect(() => {
    if (highlighted && ref.current) {
      ref.current.scrollIntoView({ behavior: "smooth", block: "center" });
    }
  }, [highlighted]);

  return (
    <Card as="article" ref={ref} className={`${styles.card}${highlighted ? ` ${styles.highlighted}` : ""}`}>
      <div className={styles.cardMeta}>
        {situation.flagship && <Badge variant="flagship">Расширенный сценарий</Badge>}
        {situation.escalation && <Badge variant="escalation">☎ Эскалация</Badge>}
        {situation.status === "completed" && <Badge variant="done">Пройден</Badge>}
      </div>
      <h3 className={styles.cardTitle}>{situation.title}</h3>
      {situation.lastResult ? (
        <div className={styles.miniScales}>
          <span>Лояльность: {Math.round(situation.lastResult.loyalty)}</span>
          <span>Безопасность: {Math.round(situation.lastResult.safety)}</span>
        </div>
      ) : (
        <div className={styles.miniScales}>Ещё не пройден</div>
      )}
      <Button as="a" variant="primary" href={`#/scenarios/${situation.id}/play`}>
        {situation.status === "completed" ? "Пройти снова" : "Начать"}
      </Button>
    </Card>
  );
}

function BlockSection({ block, situations, collapsed, highlightId, onToggle }) {
  if (situations.length === 0) return null;
  return (
    <section className={styles.blockSection}>
      <button className={styles.blockHead} onClick={onToggle} aria-expanded={!collapsed}>
        <h2>{block.label} — пройдено {block.completed} из {block.total}</h2>
        <span aria-hidden="true">{collapsed ? "▸" : "▾"}</span>
      </button>
      {!collapsed && (
        <div className={styles.grid}>
          {situations.map((s) => (
            <ScenarioCard key={s.id} situation={s} highlighted={highlightId === s.id} />
          ))}
        </div>
      )}
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
        <h1>Сценарии</h1>
        <div className={styles.skeletonGrid}>
          {[1, 2, 3, 4, 5, 6].map((i) => <Skeleton key={i} height="140px" />)}
        </div>
      </div>
    );
  }

  if (loadState.error) {
    return <ErrorState message="Не удалось загрузить каталог сценариев." onRetry={load} />;
  }

  const data = loadState.data;
  const filtered = data.situations.filter((s) => {
    const matchesSearch = !search || s.title.toLowerCase().indexOf(search.toLowerCase()) !== -1;
    const matchesStatus =
      statusFilter === "all" ||
      (statusFilter === "done" && s.status === "completed") ||
      (statusFilter === "todo" && s.status === "not_started");
    return matchesSearch && matchesStatus;
  });

  const byBlock = {};
  filtered.forEach((s) => {
    byBlock[s.block] = byBlock[s.block] || [];
    byBlock[s.block].push(s);
  });

  const hasAnyResult = filtered.length > 0;

  return (
    <div>
      <PageHeader title="Сценарии" meta={`${data.completedCount} из ${data.totalCount} пройдено`} />
      <div className={styles.filters}>
        <input
          type="search"
          className={styles.search}
          placeholder="Поиск по названию…"
          value={search}
          onChange={(ev) => setSearch(ev.target.value)}
        />
        <select className={styles.statusSelect} value={statusFilter} onChange={(ev) => setStatusFilter(ev.target.value)}>
          <option value="all">Все</option>
          <option value="todo">Не пройдено</option>
          <option value="done">Пройдено</option>
        </select>
      </div>
      {!hasAnyResult && (
        <EmptyState
          message="Ничего не найдено."
          action={<Button variant="secondary" onClick={() => { setSearch(""); setStatusFilter("all"); }}>Сбросить фильтр</Button>}
        />
      )}
      {data.blocks.map((block) => (
        <BlockSection
          key={block.key}
          block={block}
          situations={byBlock[block.key] || []}
          collapsed={!!collapsed[block.key]}
          highlightId={highlightId}
          onToggle={() => setCollapsed((prev) => ({ ...prev, [block.key]: !prev[block.key] }))}
        />
      ))}
    </div>
  );
}
