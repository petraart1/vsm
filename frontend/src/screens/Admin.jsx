import { useEffect, useMemo, useState } from "react";
import * as api from "../api.js";
import { navigate } from "../router.js";
import PageHeader from "../components/ui/PageHeader.jsx";
import Button from "../components/ui/Button.jsx";
import Icon from "../components/ui/Icon.jsx";
import Skeleton from "../components/ui/Skeleton.jsx";
import Medal from "../components/awards/Medal.jsx";
import { CountUp } from "../components/motion/Motion.jsx";
import { useAccount } from "../account.js";
import { BLOCK_TITLES, blockTitle } from "../progress.js";
import { getSettings, setSetting } from "../settings.js";
import styles from "./Admin.module.css";

/**
 * Админ-панель (роль ADMIN): статистика, сценарии (импорт из markdown), события, награды,
 * параметры демо-стенда. Разделы — #/admin/<раздел>.
 */
const TABS = [
  { key: "overview", label: "Статистика", icon: "podium" },
  { key: "scenarios", label: "Сценарии", icon: "list" },
  { key: "events", label: "События", icon: "flag" },
  { key: "awards", label: "Награды", icon: "medal" },
  { key: "stand", label: "Стенд", icon: "clipboard" }
];

export default function Admin({ route }) {
  const account = useAccount();
  const tab = TABS.some((t) => t.key === route.segments[1]) ? route.segments[1] : "overview";

  if (!account || account.role !== "ADMIN") {
    return (
      <div className={styles.page}>
        <PageHeader title="Администрирование" description="Раздел доступен учётной записи с ролью администратора." />
        <section className={styles.card}>
          <p className={styles.muted}>Войдите под учётной записью администратора стенда.</p>
          <div className={styles.actions}><Button onClick={() => navigate("/login?next=/admin")}>Войти</Button></div>
        </section>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <PageHeader title="Администрирование" description="Статистика обучения, сценарии, события и награды программы." />
      <nav className={styles.tabs} aria-label="Разделы администрирования">
        {TABS.map((t) => (
          <a key={t.key} href={`#/admin/${t.key}`} className={styles.tab} aria-current={t.key === tab ? "page" : undefined}>
            <Icon name={t.icon} size={15} />{t.label}
          </a>
        ))}
      </nav>
      {tab === "overview" && <Overview />}
      {tab === "scenarios" && <Scenarios />}
      {tab === "events" && <Events />}
      {tab === "awards" && <Awards />}
      {tab === "stand" && <Stand />}
    </div>
  );
}

// ---------------------------------------------------------------------------

function pct(v) { return `${Math.round((v || 0) * 100)}%`; }

function Overview() {
  const [d, setD] = useState(null);
  const [err, setErr] = useState(null);
  useEffect(() => {
    Promise.all([api.adminOverview(), api.adminBlocks(), api.adminScenarioStats()])
      .then(([overview, blocks, scenarios]) => setD({ overview, blocks, scenarios }), (e) => setErr(e.message));
  }, []);
  if (err) return <section className={styles.card}><p className={styles.muted}>Не удалось загрузить статистику: {err}</p></section>;
  if (!d) return <div className={styles.grid4}>{[1, 2, 3, 4].map((i) => <Skeleton key={i} height="96px" />)}</div>;
  const o = d.overview;
  const hardest = d.scenarios.slice().sort((a, b) => a.successRate - b.successRate).slice(0, 8);
  const blocks = d.blocks.slice().sort((a, b) => a.successRate - b.successRate);
  return (
    <>
      <div className={styles.grid4}>
        <Kpi label="Проводников" value={o.totalPlayers} note={`${o.activePlayers24h} активны за сутки · ${o.activePlayers7d} за неделю`} />
        <Kpi label="Прохождений" value={o.totalPlaythroughs} note={`${o.completedPlaythroughs} завершено · ${o.inProgressPlaythroughs} в процессе`} />
        <Kpi label="Средняя безопасность" value={Math.round(o.avgSafetyScore)} note="по завершённым прохождениям" />
        <Kpi label="Средняя лояльность" value={Math.round(o.avgLoyaltyScore)} note="по завершённым прохождениям" />
      </div>

      <section className={styles.card}>
        <h2 className={styles.h2}>Итоги прохождений</h2>
        <div className={styles.stack} role="img" aria-label={`Успешно ${pct(o.successRate)}, частично ${pct(o.partialRate)}, с ошибками ${pct(o.failureRate)}`}>
          <span style={{ flex: o.successRate }} data-tone="1" />
          <span style={{ flex: o.partialRate }} data-tone="2" />
          <span style={{ flex: o.failureRate }} data-tone="3" />
        </div>
        <ul className={styles.legend}>
          <li data-tone="1">Успешно <b>{pct(o.successRate)}</b></li>
          <li data-tone="2">Частично <b>{pct(o.partialRate)}</b></li>
          <li data-tone="3">С критическими ошибками <b>{pct(o.failureRate)}</b></li>
        </ul>
      </section>

      <div className={styles.split}>
        <section className={styles.card}>
          <h2 className={styles.h2}>Блоки ситуаций</h2>
          <p className={styles.muted}>Сверху — блоки, где проводникам сложнее всего.</p>
          <table className={styles.table}>
            <thead><tr><th>Блок</th><th>Прохождений</th><th>Успешно</th></tr></thead>
            <tbody>
              {blocks.map((b) => (
                <tr key={b.block}>
                  <td>{blockTitle(b.block)}</td>
                  <td className={styles.num}>{b.totalPlaythroughs}</td>
                  <td><span className={styles.bar}><span style={{ transform: `scaleX(${b.successRate})` }} /></span><span className={styles.num}>{pct(b.successRate)}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
        <section className={styles.card}>
          <h2 className={styles.h2}>Сценарии, требующие внимания</h2>
          <p className={styles.muted}>Наименьшая доля успешных прохождений и частые таймауты.</p>
          <table className={styles.table}>
            <thead><tr><th>Сценарий</th><th>Успешно</th><th>Таймауты</th></tr></thead>
            <tbody>
              {hardest.map((s) => (
                <tr key={s.scenarioId}>
                  <td><span className={styles.cellTitle}>{s.title}</span><span className={styles.cellSub}>{blockTitle(s.block)}</span></td>
                  <td className={styles.num}>{pct(s.successRate)}</td>
                  <td className={styles.num}>{pct(s.timeoutRate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </div>

      <section className={styles.card}>
        <h2 className={styles.h2}>Выгрузки для HR и аналитики</h2>
        <div className={styles.actions}>
          {[["players", "Проводники"], ["scenarios", "Сценарии"], ["blocks", "Блоки"], ["teams", "Команды"]].map(([k, label]) => (
            <Button key={k} variant="secondary" onClick={() => api.adminDownloadCsv(k)}><Icon name="clipboard" size={14} />{label}, CSV</Button>
          ))}
        </div>
      </section>
    </>
  );
}

function Kpi({ label, value, note }) {
  return (
    <section className={styles.kpi}>
      <p className={styles.kpiLabel}>{label}</p>
      <p className={styles.kpiValue}><CountUp value={value || 0} /></p>
      <p className={styles.kpiNote}>{note}</p>
    </section>
  );
}

// ---------------------------------------------------------------------------

const MD_TEMPLATE = `# Пассажир просит поменяться местами
Код: seat-swap-family
Блок: seating
Описание: Семья хочет сидеть вместе, соседний пассажир против.

## start
Пассажирка с ребёнком просит пересадить её к мужу — через проход, на соседнее место.

- [explain] Уточнить свободные места и предложить вариант обмена без ущерба для других -> agree (лояльность +8, безопасность +2)
> Пояснение: решение по правилам, никого не ущемляет.
> Шаги: признать, правило, решение, заверить
- [refuse] Отказать: «Места по билетам» -> end-bad (лояльность -8)
Таймер: 30 с, по умолчанию: refuse

## agree
Пассажиры договорились, семья сидит вместе.
Итог: SUCCESS

## end-bad
Семья недовольна, пишет жалобу.
Итог: PARTIAL
`;

function Scenarios() {
  const [list, setList] = useState(null);
  const [q, setQ] = useState("");
  const [md, setMd] = useState(MD_TEMPLATE);
  const [check, setCheck] = useState(null);
  const [busy, setBusy] = useState(false);
  const [exported, setExported] = useState(null);
  useEffect(() => { api.listScenarios().then((d) => setList(d.situations || []), () => setList([])); }, []);
  const filtered = useMemo(() => (list || []).filter((s) => !q || s.title.toLowerCase().includes(q.toLowerCase())), [list, q]);

  function run(save) {
    setBusy(true);
    setCheck(null);
    api.editorImportMarkdown(md, save).then((r) => {
      setBusy(false);
      if (save) setCheck({ ok: true, text: `Сохранено: «${r.title || r.code}». Сценарий доступен в каталоге.` });
      else setCheck({ ok: !(r.graphErrors || []).length, text: (r.graphErrors || []).length ? r.graphErrors.join("\n") : `Разметка корректна: «${r.scenario ? r.scenario.title : ""}», узлов: ${r.scenario && r.scenario.nodes ? r.scenario.nodes.length : 0}.` });
      if (save) api.listScenarios().then((d) => setList(d.situations || []));
    }, (e) => { setBusy(false); setCheck({ ok: false, text: e.message }); });
  }

  return (
    <div className={styles.split}>
      <section className={styles.card}>
        <h2 className={styles.h2}>Новая ситуация из markdown</h2>
        <p className={styles.muted}>Заголовок, код и блок, затем узлы: реплика, варианты с эффектами на шкалы, пояснения и итог. Перед сохранением граф проверяется.</p>
        <textarea className={styles.editor} value={md} onChange={(e) => setMd(e.target.value)} spellCheck={false} aria-label="Текст сценария в формате markdown" />
        {check && <pre className={styles.check} data-ok={check.ok || undefined}>{check.text}</pre>}
        <div className={styles.actions}>
          <Button variant="secondary" onClick={() => run(false)} disabled={busy}>Проверить</Button>
          <Button onClick={() => run(true)} disabled={busy}>Сохранить в каталог</Button>
          <Button variant="ghost" onClick={() => { setMd(MD_TEMPLATE); setCheck(null); }}>Шаблон</Button>
        </div>
      </section>
      <section className={styles.card}>
        <h2 className={styles.h2}>Каталог ({list ? list.length : "…"})</h2>
        <input className={styles.search} placeholder="Поиск по названию" value={q} onChange={(e) => setQ(e.target.value)} />
        <ul className={styles.list}>
          {filtered.slice(0, 60).map((s) => (
            <li key={s.id}>
              <span className={styles.cellTitle}>{s.title}</span>
              <span className={styles.cellSub}>{blockTitle(s.block)}{s.flagship ? " · расширенный" : ""}</span>
              {s.code && <button type="button" className={styles.link} onClick={() => api.editorExport(s.code).then((d) => setExported({ code: s.code, json: JSON.stringify(d, null, 2) }))}>JSON</button>}
            </li>
          ))}
        </ul>
        {exported && (
          <div className={styles.exported}>
            <div className={styles.exportedHead}><b>{exported.code}</b><button type="button" className={styles.link} onClick={() => setExported(null)}>Закрыть</button></div>
            <pre>{exported.json}</pre>
          </div>
        )}
      </section>
    </div>
  );
}

// ---------------------------------------------------------------------------

const GOALS = [
  { key: "BLOCK_SCENARIOS_NO_FAILURE", label: "Ситуации блока без критических ошибок" },
  { key: "SAFETY_STREAK", label: "Серия прохождений с высокой безопасностью" },
  { key: "ROLE_MODEL_ALL_STEPS", label: "Прохождения со всеми шагами ролевой модели" }
];
const ACHIEVEMENTS = [["", "Без ачивки"], ["CHALLENGE_CHAMPION", "Чемпион месяца"], ["FLAWLESS_SAFETY", "Безупречная безопасность"], ["PASSENGER_FAVORITE", "Любимец пассажиров"], ["VERSATILE", "Универсал"], ["CERTIFICATE", "Сертификат"]];

function toLocalInput(d) {
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

function Events() {
  const [list, setList] = useState(null);
  const [err, setErr] = useState(null);
  const now = new Date();
  const [f, setF] = useState({ title: "", description: "", goalType: GOALS[0].key, targetBlock: "safety", targetCount: 3, safetyThreshold: 70, startsAt: toLocalInput(now), endsAt: toLocalInput(new Date(now.getTime() + 7 * 864e5)), rewardPoints: 300, rewardAchievementCode: "" });
  const [msg, setMsg] = useState(null);
  const load = () => api.adminEvents().then(setList, (e) => setErr(e.message));
  useEffect(() => { load(); }, []);
  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.type === "number" ? Number(e.target.value) : e.target.value }));

  function create(e) {
    e.preventDefault();
    setMsg(null);
    const body = {
      ...f,
      targetBlock: f.goalType === "BLOCK_SCENARIOS_NO_FAILURE" ? f.targetBlock : null,
      safetyThreshold: f.goalType === "SAFETY_STREAK" ? f.safetyThreshold : null,
      startsAt: new Date(f.startsAt).toISOString(),
      endsAt: new Date(f.endsAt).toISOString(),
      rewardAchievementCode: f.rewardAchievementCode || null
    };
    api.adminCreateEvent(body).then(() => { setMsg({ ok: true, text: "Событие создано — оно появится у проводников в списке целей." }); setF((x) => ({ ...x, title: "", description: "" })); load(); }, (e2) => setMsg({ ok: false, text: e2.message }));
  }

  return (
    <div className={styles.split}>
      <section className={styles.card}>
        <h2 className={styles.h2}>Новое событие</h2>
        <form className={styles.form} onSubmit={create}>
          <label>Название<input value={f.title} onChange={set("title")} required maxLength={150} placeholder="Неделя безопасности" /></label>
          <label>Описание<textarea value={f.description} onChange={set("description")} required maxLength={500} rows={3} placeholder="Что нужно сделать, чтобы выполнить событие" /></label>
          <label>Цель<select value={f.goalType} onChange={set("goalType")}>{GOALS.map((g) => <option key={g.key} value={g.key}>{g.label}</option>)}</select></label>
          {f.goalType === "BLOCK_SCENARIOS_NO_FAILURE" && (
            <label>Блок ситуаций<select value={f.targetBlock} onChange={set("targetBlock")}>{Object.keys(BLOCK_TITLES).map((k) => <option key={k} value={k}>{BLOCK_TITLES[k]}</option>)}</select></label>
          )}
          <div className={styles.row2}>
            <label>Сколько раз<input type="number" min={1} max={100} value={f.targetCount} onChange={set("targetCount")} /></label>
            {f.goalType === "SAFETY_STREAK" && <label>Порог безопасности<input type="number" min={0} max={100} value={f.safetyThreshold} onChange={set("safetyThreshold")} /></label>}
          </div>
          <div className={styles.row2}>
            <label>Начало<input type="datetime-local" value={f.startsAt} onChange={set("startsAt")} required /></label>
            <label>Окончание<input type="datetime-local" value={f.endsAt} onChange={set("endsAt")} required /></label>
          </div>
          <div className={styles.row2}>
            <label>Очки за выполнение<input type="number" min={0} max={5000} value={f.rewardPoints} onChange={set("rewardPoints")} /></label>
            <label>Награда<select value={f.rewardAchievementCode} onChange={set("rewardAchievementCode")}>{ACHIEVEMENTS.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></label>
          </div>
          {msg && <p className={styles.check} data-ok={msg.ok || undefined}>{msg.text}</p>}
          <div className={styles.actions}><Button type="submit">Создать событие</Button></div>
        </form>
      </section>
      <section className={styles.card}>
        <h2 className={styles.h2}>События</h2>
        {err && <p className={styles.muted}>Не удалось загрузить: {err}</p>}
        {!list ? <Skeleton height="120px" /> : list.length === 0 ? <p className={styles.muted}>Событий пока нет. Ежемесячные цели создаются автоматически.</p> : (
          <ul className={styles.list}>
            {list.map((e) => (
              <li key={e.id}>
                <span className={styles.status} data-on={e.active || undefined}>{e.active ? "Идёт" : "Завершено"}</span>
                <span className={styles.cellTitle}>{e.title}</span>
                <span className={styles.cellSub}>{e.description}</span>
                <span className={styles.cellSub}>{new Date(e.startsAt).toLocaleDateString("ru-RU")} — {new Date(e.endsAt).toLocaleDateString("ru-RU")} · {e.rewardPoints} очков</span>
                {e.active && <button type="button" className={styles.link} onClick={() => api.adminFinishEvent(e.id).then(load)}>Завершить</button>}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

// ---------------------------------------------------------------------------

const GLYPHS = ["medal", "shield", "train", "users", "smile", "flag", "sparkle", "hand", "coffee", "cross", "ticket", "bolt", "flame"];

function Awards() {
  const [list, setList] = useState(null);
  const [f, setF] = useState({ title: "", description: "", shape: "shield", glyph: "sparkle", verifiedOnly: false });
  const [grant, setGrant] = useState({});
  const [msg, setMsg] = useState(null);
  const load = () => api.adminAwards().then(setList, () => setList([]));
  useEffect(() => { load(); }, []);
  function create(e) {
    e.preventDefault();
    api.adminCreateAward(f).then(() => { setMsg({ ok: true, text: "Награда создана." }); setF((x) => ({ ...x, title: "", description: "" })); load(); }, (e2) => setMsg({ ok: false, text: e2.message }));
  }
  function give(id) {
    const who = (grant[id] || "").trim();
    if (!who) return;
    api.adminGrantAward(id, who).then(() => { setMsg({ ok: true, text: `Награда выдана: ${who}` }); setGrant((g) => ({ ...g, [id]: "" })); load(); }, (e) => setMsg({ ok: false, text: e.message }));
  }
  return (
    <div className={styles.split}>
      <section className={styles.card}>
        <h2 className={styles.h2}>Новая награда</h2>
        <div className={styles.preview}>
          <Medal shape={f.shape} finish="enamel" glyph={f.glyph} size={104} spin backTitle={f.title || "Награда"} backNote="ReactLab · ВСМ" />
        </div>
        <form className={styles.form} onSubmit={create}>
          <label>Название<input value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} required maxLength={120} placeholder="Наставник смены" /></label>
          <label>За что<textarea rows={2} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} required maxLength={300} placeholder="Помог коллеге-стажёру на рейсе" /></label>
          <div className={styles.row2}>
            <label>Форма<select value={f.shape} onChange={(e) => setF({ ...f, shape: e.target.value })}>
              <option value="circle">Круг</option><option value="hexagon">Шестигранник</option><option value="octagon">Восьмигранник</option><option value="shield">Щит</option>
            </select></label>
            <label>Пиктограмма<select value={f.glyph} onChange={(e) => setF({ ...f, glyph: e.target.value })}>{GLYPHS.map((g) => <option key={g} value={g}>{g}</option>)}</select></label>
          </div>
          <label className={styles.checkbox}><input type="checkbox" checked={f.verifiedOnly} onChange={(e) => setF({ ...f, verifiedOnly: e.target.checked })} />Только для подтверждённых через Госуслуги</label>
          {msg && <p className={styles.check} data-ok={msg.ok || undefined}>{msg.text}</p>}
          <div className={styles.actions}><Button type="submit">Создать награду</Button></div>
        </form>
      </section>
      <section className={styles.card}>
        <h2 className={styles.h2}>Награды программы</h2>
        <p className={styles.muted}>Встроенные ачивки выдаются автоматически. Созданные здесь награды выдаются вручную — по логину или идентификатору игрока.</p>
        {!list ? <Skeleton height="120px" /> : (
          <ul className={styles.awardList}>
            {list.map((a) => (
              <li key={a.id}>
                <Medal shape={a.shape} finish="enamel" glyph={a.glyph || "medal"} size={56} />
                <div className={styles.awardBody}>
                  <span className={styles.cellTitle}>{a.title}{a.verifiedOnly && <span className={styles.tag}>подтверждённым</span>}</span>
                  <span className={styles.cellSub}>{a.description} · выдано {a.grantedCount}</span>
                  <span className={styles.grantRow}>
                    <input placeholder="Логин или ID игрока" value={grant[a.id] || ""} onChange={(e) => setGrant((g) => ({ ...g, [a.id]: e.target.value }))} />
                    <Button size="sm" variant="secondary" onClick={() => give(a.id)}>Выдать</Button>
                  </span>
                </div>
              </li>
            ))}
            {list.length === 0 && <li className={styles.muted}>Пока нет созданных наград.</li>}
          </ul>
        )}
      </section>
    </div>
  );
}

// ---------------------------------------------------------------------------

function Stand() {
  const [s, setS] = useState(getSettings);
  const set = (k) => (e) => setS(setSetting(k, Number(e.target.value)));
  return (
    <div className={styles.split}>
      <section className={styles.card}>
        <h2 className={styles.h2}>Параметры симулятора</h2>
        <p className={styles.muted}>Для показа и тестирования на этом устройстве.</p>
        <div className={styles.form}>
          <label>Доля смен с проблемой на медосмотре: <b>{Math.round(s.simMedProblemRate * 100)}%</b>
            <input type="range" min={0} max={1} step={0.05} value={s.simMedProblemRate} onChange={set("simMedProblemRate")} />
          </label>
          <label>Стрессовых ситуаций за рейс: <b>{s.simStressCount}</b>
            <input type="range" min={0} max={3} step={1} value={s.simStressCount} onChange={set("simStressCount")} />
          </label>
          <label>Длительность рейса при скорости 1×: <b>{s.simTripSeconds} с</b>
            <input type="range" min={60} max={300} step={10} value={s.simTripSeconds} onChange={set("simTripSeconds")} />
          </label>
        </div>
      </section>
      <section className={styles.card}>
        <h2 className={styles.h2}>Стенд</h2>
        <ul className={styles.list}>
          <li><span className={styles.cellTitle}>Режим данных</span><span className={styles.cellSub}>{api.USE_MOCKS ? "Демо-данные в браузере (без сервера)" : "Сервер тренажёра"}</span></li>
          <li><span className={styles.cellTitle}>Документация API</span><span className={styles.cellSub}><a className={styles.link} href="/swagger-ui.html" target="_blank" rel="noreferrer">Swagger UI</a></span></li>
          <li><span className={styles.cellTitle}>Начисление очков</span><span className={styles.cellSub}>Неподтверждённым учётным записям — с коэффициентом 0,5; суточный лимит на игрока. Настраивается на сервере.</span></li>
          <li><span className={styles.cellTitle}>Вход через Госуслуги</span><span className={styles.cellSub}>Демо-провайдер с тестовыми гражданами; для реальной интеграции нужна регистрация ИС в ЕСИА и ГОСТ-криптография.</span></li>
        </ul>
      </section>
    </div>
  );
}
