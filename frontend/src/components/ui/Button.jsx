import styles from "./Button.module.css";

const VARIANT_CLASS = {
  primary: styles.primary,
  secondary: styles.secondary,
  choice: styles.choice
};

/** props: variant ('primary'|'secondary'|'choice'), as (тег, по умолчанию 'button' — 'a' для
 * ссылок, оформленных как кнопка), className, остальное прокидывается в тег как есть. */
export default function Button({ variant = "primary", as: As = "button", className, children, ...rest }) {
  const cls = [styles.btn, VARIANT_CLASS[variant] || VARIANT_CLASS.primary, className].filter(Boolean).join(" ");
  return (
    <As className={cls} {...rest}>
      {children}
    </As>
  );
}
