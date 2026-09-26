import { useEffect, useMemo, useState } from "react";
import * as api from "../api.js";
import StreakCard from "../components/engagement/StreakCard.jsx";
import ShowcaseGrid from "../components/engagement/ShowcaseGrid.jsx";
import { readShowcase } from "../engagement.js";
import { readFinish } from "../components/awards/awards.js";
import Icon from "../components/ui/Icon.jsx";
import Rings from "../components/ui/Rings.jsx";
import { Person } from "../components/characters/People.jsx";
import { CountUp, SplitText } from "../components/motion/Motion.jsx";
import { CAR_CLASSES, readShifts } from "../shift/shiftModel.js";
import styles from "./Today.module.css";

/**
 * «Сегодня» — рабочий главный экран (правило «чистого экрана»): одна главная кнопка — начать
 * смену, недельная активность кольцами и последние смены. Награды и рейтинг — в своих разделах.
 */
const WEEK_GOAL = 5;

export default function Today() {
  const shifts = useMemo(() => readShifts(), []);
  const week = shifts.filter((s) => Date.now() - new Date(s.at).getTime() < 7 * 864e5);
  const avg = (key) => (week.length ? week.reduce((a, s) => a + (s.rings?.[key] ?? 0), 0) / week.length : 0);
  const date = new Date().toLocaleDateString("ru-RU", { weekday: "long", day: "numeric", month: "long" });
  const admittedIn = shifts.find((s) => s.admitted);
  const showcase = readShowcase();
  const [board, setBoard] = useState(null);
  useEffect(() => { api.getLeaderboard({ limit: 5 }).then(setBoard, () => setBoard(null)); }, []);
  const pct = board && board.me && board.total > 1 ? Math.round(((board.total - board.me.rank) / (board.total - 1)) * 100) : null;

  return (
    <div className={styles.page}>
      <p className={`${styles.date} rv`}>{date}</p>
      <SplitText as="h1" text="Сегодня" className={styles.title} />
      <div className={styles.layout}>
      <div className={styles.main}>

      <a href="#/shift" className={`${styles.hero} rv`} style={{ "--i": 1 }}>
        <img className={styles.heroImg} src={`${import.meta.env.BASE_URL}backgrounds/express.jpg`} alt="" />
        <span className={styles.heroShade} />
        <span className={styles.heroText}>
          <span className={styles.heroKicker}>Тренажёр проводника ВСМ</span>
          <span className={styles.heroTitle}>Смена проводника</span>
          <span className={styles.heroNote}>Заступ, приёмка вагона и рейс Москва — Санкт-Петербург. Что случится в пути, заранее не известно.</span>
          <span className={styles.heroBtn}><Icon name="play" size={14} />Начать смену</span>
        </span>
        <span className={styles.heroPerson} aria-hidden="true"><Person outfit="conductor" size={170} /></span>
      </a>

      <section className={`${styles.card} ${styles.activity} rv`} style={{ "--i": 2 }}>
        <Rings size={112} rings={[
          { value: Math.min(1, week.length / WEEK_GOAL), tone: "navy", label: "Смены" },
          { value: avg("procedure"), tone: "blue", label: "Регламент" },
          { value: avg("quality"), tone: "sky", label: "Качество" }
        ]} />
        <dl className={styles.legend}>
          <div data-tone="navy"><dt>Смены за неделю</dt><dd><CountUp value={week.length} /><small>/{WEEK_GOAL}</small></dd></div>
          <div data-tone="blue"><dt>Регламент</dt><dd><CountUp value={Math.round(avg("procedure") * 100)} /><small>%</small></dd></div>
          <div data-tone="sky"><dt>Качество диалогов</dt><dd><CountUp value={Math.round(avg("quality") * 100)} /><small>%</small></dd></div>
        </dl>
      </section>

      <h2 className={styles.label}>Последние смены</h2>
      {shifts.length === 0 ? (
        <div className={`${styles.card} ${styles.empty}`}>Здесь появятся итоги ваших смен: допуск, баллы и рекомендации по классу вагона.</div>
      ) : (
        <ul className={styles.list}>
          {shifts.slice(0, 4).map((s, i) => {
            const c = CAR_CLASSES[s.cls] || CAR_CLASSES.STANDARD;
            return (
              <li key={s.at} className={`${styles.row} rv`} style={{ "--i": i + 3 }}>
                <span className={styles.rowIcon} data-ok={s.admitted || undefined}><Icon name={s.admitted ? "check" : "rotate"} size={16} /></span>
                <span className={styles.rowText}>
                  <b>Вагон {c.car} · {c.title}</b>
                  <span>{new Date(s.at).toLocaleString("ru-RU", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })} · {s.admitted ? "допуск подтверждён" : "нужна повторная смена"}</span>
                </span>
                <span className={styles.rowScore}>{s.score}</span>
              </li>
            );
          })}
        </ul>
      )}

      <h2 className={styles.label}>Подготовка</h2>
      <ul className={styles.list}>
        <li><a className={styles.row} href="#/scenarios">
          <span className={styles.rowIcon}><Icon name="list" size={16} /></span>
          <span className={styles.rowText}><b>Отработать ситуацию</b><span>Каталог диалогов по блокам — без смены и таймера рейса</span></span>
          <Icon name="chevronRight" size={16} className={styles.chev} />
        </a></li>
        <li><a className={styles.row} href="#/achievements">
          <span className={styles.rowIcon}><Icon name="medal" size={16} /></span>
          <span className={styles.rowText}><b>Награды и квалификации</b><span>{admittedIn ? "Первая смена с допуском уже есть" : "Первая медаль — за смену без замечаний"}</span></span>
          <Icon name="chevronRight" size={16} className={styles.chev} />
        </a></li>
      </ul>
      </div>

      <aside className={styles.aside}>
        <div className="rv" style={{ "--i": 2 }}><StreakCard compact /></div>

        <section className={`${styles.card} ${styles.sideCard} rv`} style={{ "--i": 3 }}>
          <header className={styles.sideHead}>
            <h2>Витрина</h2>
            <a href="#/profile">Настроить</a>
          </header>
          <ShowcaseGrid items={showcase} finish={readFinish()} size={64} emptyText="Выставьте до шести наград — их увидят коллеги в рейтинге." />
        </section>

        {board && board.top.length > 0 && (
          <section className={`${styles.card} ${styles.sideCard} rv`} style={{ "--i": 4 }}>
            <header className={styles.sideHead}>
              <h2>Рейтинг</h2>
              <a href="#/leaderboard">Все</a>
            </header>
            {pct !== null && <p className={styles.pct}><b className="num">{pct}%</b> коллег — ниже вас</p>}
            <ol className={styles.mini}>
              {board.top.slice(0, 3).concat(board.me && !board.top.slice(0, 3).some((e) => e.me) ? [board.me] : []).map((e) => (
                <li key={e.key} data-me={e.me || undefined}>
                  <span className={styles.miniRank}>{e.rank}</span>
                  <span className={styles.miniName}>{e.me ? "Вы" : e.displayName}</span>
                  <span className={styles.miniScore}>{e.totalScore}</span>
                </li>
              ))}
            </ol>
          </section>
        )}
      </aside>
      </div>
    </div>
  );
}
