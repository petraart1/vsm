import styles from "./Skeleton.module.css";

/** props: height (CSS-значение, реально разное на каждом месте использования — единственная
 * причина инлайн-стиля здесь), className. */
export default function Skeleton({ height = "20px", className }) {
  const cls = [styles.skeleton, className].filter(Boolean).join(" ");
  return <div className={cls} style={{ height }} />;
}
