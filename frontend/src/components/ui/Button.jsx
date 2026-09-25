import styles from "./Button.module.css";

const VARIANT_CLASS = {
  primary: styles.primary,
  secondary: styles.secondary,
  ghost: styles.ghost,
  choice: styles.choice
};

/** props: variant ('primary'|'secondary'|'ghost'|'choice'), size ('md'|'sm'|'lg'), as (тег —
 * 'a' для ссылок, оформленных как кнопка), className, остальное прокидывается в тег как есть. */
export default function Button({ variant = "primary", size = "md", as: As = "button", className, children, ...rest }) {
  const cls = [styles.btn, VARIANT_CLASS[variant] || VARIANT_CLASS.primary, styles[size], className].filter(Boolean).join(" ");
  const extra = As === "button" && !rest.type ? { type: "button" } : {};
  return (
    <As className={cls} {...extra} {...rest}>
      {children}
    </As>
  );
}
