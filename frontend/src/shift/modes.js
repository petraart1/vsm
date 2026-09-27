/**
 * Режимы смены:
 *  - «Сюжет» — последовательные главы карьеры проводника: от первого рейса стажёром до вагона
 *    первого класса. Глава открывается после того, как предыдущая пройдена с допуском.
 *  - «Свободная смена» — вагон и самочувствие выбирает игрок, параметры — из настроек.
 *  - «Случайный рейс» — всё решает случай: вагон, самочувствие, число и тип ситуаций, темп;
 *    перед рейсом — короткая история и инструкция.
 */
import { CAR_CLASSES, CLASS_ORDER } from "./shiftModel.js";
import { STRESS_SCENARIOS } from "./stressScenarios.js";

/**
 * План смены — всё, что влияет на ход рейса.
 *  cls — ключ класса вагона; condition — "fit" | "fever" | "alcohol" | "substances" | "random";
 *  medRate — вероятность проблемы на медосмотре при condition "random";
 *  stressCount — число стрессовых ситуаций (0..3); backendCount — число ситуаций из каталога (0..3);
 *  tripSeconds — длительность рейса при скорости 1×; patience — множитель времени ожидания пассажира;
 *  forcedStress — id стрессовой ситуации, которая точно будет; notice — особое указание на инструктаже;
 *  brief — история и инструкция перед сменой (для сюжета и случайного рейса).
 */

export const CHAPTERS = [
  {
    id: "c1",
    title: "Первый рейс",
    subtitle: "Стажировка под присмотром наставника",
    plan: { cls: "STANDARD", condition: "fit", stressCount: 0, backendCount: 2, tripSeconds: 150, patience: 1.4, notice: false },
    brief: {
      story: "Сегодня ваш первый самостоятельный рейс после стажировки. Начальник поезда Олег Викторович обещал заглядывать в вагон, но отвечать пассажирам будете вы.",
      tasks: ["Пройдите медосмотр и инструктаж", "Примите вагон — найдите хотя бы половину неисправностей", "Подойдите ко всем пассажирам, которые позовут"],
      goal: "Получить допуск к самостоятельной работе"
    }
  },
  {
    id: "c2",
    title: "Час пик",
    subtitle: "Полный вагон и первый конфликт",
    plan: { cls: "STANDARD", condition: "random", medRate: 0.15, stressCount: 1, backendCount: 2, tripSeconds: 140, patience: 1.2, notice: true },
    brief: {
      story: "Пятница, вечерний рейс, вагон забит полностью. На перроне уже спорят из-за мест, а у вас в наряде — особое указание.",
      tasks: ["Выслушайте особое указание на инструктаже", "Держите темп: пассажиры ждут недолго", "В конфликте — только слова и доклад, никакой силы"],
      goal: "Смена без пропущенных вызовов"
    }
  },
  {
    id: "c3",
    title: "Вагон «Комфорт»",
    subtitle: "Выше ожидания — выше цена ошибки",
    plan: { cls: "COMFORT", condition: "random", medRate: 0.25, stressCount: 2, backendCount: 2, tripSeconds: 140, patience: 1, notice: true },
    brief: {
      story: "Вас перевели в «Комфорт». Пассажиры здесь спокойнее, но замечают каждую мелочь, а начальник поезда попросил проверить вагон особенно тщательно.",
      tasks: ["Честно ответьте на медосмотре", "Найдите все неисправности на приёмке", "Отработайте две сложные ситуации в пути"],
      goal: "Допуск и ни одной критической ошибки"
    }
  },
  {
    id: "c4",
    title: "Ночной экспресс",
    subtitle: "Срочные вызовы и работа с нарядом полиции",
    plan: { cls: "COMFORT", condition: "random", medRate: 0.3, stressCount: 2, backendCount: 2, tripSeconds: 130, patience: 0.9, forcedStress: "drunk-rowdy", notice: true },
    brief: {
      story: "Поздний рейс после футбольного матча. В составе едет наряд транспортной полиции, охрана поезда на связи. Ночью силы на исходе — и у вас, и у пассажиров.",
      tasks: ["Помните: удалить пассажира из поезда может только полиция", "Докладывайте начальнику поезда сразу", "Защитите соседей по вагону"],
      goal: "Все инциденты — по регламенту"
    }
  },
  {
    id: "c5",
    title: "Бизнес-класс",
    subtitle: "Сервис без права на ошибку",
    plan: { cls: "BUSINESS", condition: "random", medRate: 0.3, stressCount: 2, backendCount: 3, tripSeconds: 130, patience: 0.85, notice: true },
    brief: {
      story: "Бизнес-класс: деловые пассажиры, столики, звонки на ходу. Любая заминка превращается в жалобу, а срочные ситуации никто не отменял.",
      tasks: ["Реагируйте быстро — пассажиры ждут недолго", "Сочетайте вежливость и безопасность", "Не забывайте про приёмку: столики и розетки"],
      goal: "Допуск и рекомендация к переводу"
    }
  },
  {
    id: "c6",
    title: "Первый класс",
    subtitle: "Финальная глава карьеры",
    plan: { cls: "FIRST", condition: "random", medRate: 0.35, stressCount: 3, backendCount: 2, tripSeconds: 130, patience: 0.8, notice: true },
    brief: {
      story: "Вам доверили вагон первого класса. Шесть кресел, каждый пассажир на виду. Сегодня в пути случится больше обычного — покажите всё, чему научились.",
      tasks: ["Три сложные ситуации и обычные вызовы", "Ни одного пропущенного пассажира", "Безупречный заступ и приёмка"],
      goal: "Подтвердить квалификацию проводника первого класса"
    }
  }
];

const STORY_KEY = "reactlab.story.v1";

export function readStory() {
  try { return JSON.parse(localStorage.getItem(STORY_KEY) || "{}") || {}; } catch (e) { return {}; }
}

/** Отметить итог главы: пройдена (с допуском) или нет. Возвращает обновлённое состояние. */
export function recordChapter(id, passed, score) {
  const s = readStory();
  const prev = s[id] || {};
  const next = { ...s, [id]: { passed: prev.passed || passed, best: Math.max(prev.best || 0, score || 0), attempts: (prev.attempts || 0) + 1 } };
  try { localStorage.setItem(STORY_KEY, JSON.stringify(next)); } catch (e) { /* приватный режим */ }
  return next;
}

/** Открыта ли глава: первая — всегда, остальные — после допуска в предыдущей. */
export function chapterUnlocked(story, index) {
  return index === 0 || !!(story[CHAPTERS[index - 1].id] && story[CHAPTERS[index - 1].id].passed);
}

export function findChapter(id) {
  return CHAPTERS.find((c) => c.id === id) || null;
}

// ---------------------------------------------------------------------------
// Случайный рейс: план и история генерируются из seed.
// ---------------------------------------------------------------------------

const NAMES = ["Олег Викторович", "Марина Андреевна", "Сергей Павлович", "Елена Игоревна", "Андрей Николаевич"];
const WEATHER = ["метель и задержки на подходе к Твери", "жара за тридцать, кондиционеры на пределе", "ясное утро и полный вагон отпускников", "дождь, мокрые зонты и скользкий тамбур", "праздничные выходные — много семей с детьми"];
const TIMES = ["ранний утренний рейс", "дневной рейс", "вечерний рейс в пятницу", "поздний рейс после концерта"];
const PACE = [
  { key: "calm", label: "Спокойный темп", tripSeconds: 160, patience: 1.3 },
  { key: "normal", label: "Обычный темп", tripSeconds: 140, patience: 1 },
  { key: "busy", label: "Напряжённый темп", tripSeconds: 120, patience: 0.8 }
];

export function randomPlan(rand) {
  const pick = (list) => list[Math.floor(rand() * list.length)];
  const cls = pick(CLASS_ORDER);
  const pace = pick(PACE);
  const stressCount = Math.floor(rand() * 4);
  const backendCount = Math.max(1, 3 - Math.floor(stressCount / 2) - Math.floor(rand() * 2));
  const chief = pick(NAMES);
  const weather = pick(WEATHER);
  const when = pick(TIMES);
  const c = CAR_CLASSES[cls];
  const tasks = [
    "Пройдите медосмотр — отвечайте честно, допуск решает медработник",
    `Примите вагон ${c.car} «${c.title}»: найдите неисправности до посадки`,
    stressCount === 0 ? "В пути — обычные вызовы пассажиров" : `В пути — ${stressCount} ${stressCount === 1 ? "сложная ситуация" : "сложные ситуации"} и обычные вызовы`,
    "О любом нарушении порядка — доклад начальнику поезда"
  ];
  return {
    cls,
    condition: "random",
    medRate: 0.1 + rand() * 0.3,
    stressCount,
    backendCount,
    tripSeconds: pace.tripSeconds,
    patience: pace.patience,
    notice: rand() < 0.8,
    pace: pace.label,
    brief: {
      story: `${capitalize(when)}, ${weather}. Начальник поезда сегодня — ${chief}. Вам достался вагон ${c.car}, класс «${c.title}». Что случится в пути, не знает никто.`,
      tasks,
      goal: "Отработать смену так, чтобы получить допуск"
    }
  };
}

export const STRESS_TITLES = Object.fromEntries(STRESS_SCENARIOS.map((s) => [s.id, s.title]));

function capitalize(s) { return s.charAt(0).toUpperCase() + s.slice(1); }
