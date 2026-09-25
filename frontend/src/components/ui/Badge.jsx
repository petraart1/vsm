import styles from "./Badge.module.css";

const VARIANT_CLASS = {
  neutral: styles.neutral,
  flagship: styles.flagship,
  escalation: styles.escalation,
  done: styles.done
};

/** props: variant ('neutral'|'flagship'|'escalation'|'done'), children, className, as (тег,
 * по умолчанию 'span' — иногда бейдж нужен как <a>, например ссылка на ачивку). */
export default function Badge({ variant = "neutral", as: As = "span", className, children, ...rest }) {
  const cls = [styles.badge, VARIANT_CLASS[variant] || VARIANT_CLASS.neutral, className].filter(Boolean).join(" ");
  return (
    <As className={cls} {...rest}>
      {children}
    </As>
  );
}
