import Seal from "./Seal.jsx";
import { formatDate, pluralRu } from "../../progress.js";
import styles from "./CertificateCard.module.css";

function statusLine(q) {
  if (q.status === "certified") return `Квалификация присвоена ${formatDate(q.certifiedAt) || ""}`.trim();
  if (q.status === "in_training") return `В обучении: ${q.completed} из ${q.total} ${pluralRu(q.total, "ситуации", "ситуаций", "ситуаций")}`;
  return `${q.total} ${pluralRu(q.total, "ситуация", "ситуации", "ситуаций")}, ${q.hours}\u00A0ак.\u00A0${pluralRu(q.hours, "час", "часа", "часов")}`;
}

/**
 * Строка квалификации по учебному модулю. Вся карточка — кнопка, открывает свидетельство.
 * props: qualification (progress.buildQualifications), onOpen, compact (для профиля), highlighted.
 */
export default function CertificateCard({ qualification: q, onOpen, compact = false, highlighted = false }) {
  return (
    <button
      type="button"
      className={styles.card}
      data-state={q.status}
      data-compact={compact || undefined}
      data-highlighted={highlighted || undefined}
      onClick={() => onOpen(q)}
    >
      <Seal
        mark={q.code}
        state={q.status}
        progress={q.total ? q.completed / q.total : 0}
        size={compact ? 64 : 76}
      />
      <span className={styles.body}>
        <span className={styles.title}>{q.title}</span>
        <span className={styles.status}>{statusLine(q)}</span>
        {q.status === "in_training" && (
          <span className={styles.track} aria-hidden="true">
            <span className={styles.fill} style={{ width: `${(q.completed / q.total) * 100}%` }} />
          </span>
        )}
      </span>
    </button>
  );
}
