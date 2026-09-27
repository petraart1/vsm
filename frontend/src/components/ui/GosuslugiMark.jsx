import { useState } from "react";
import Icon from "./Icon.jsx";
import styles from "./GosuslugiMark.module.css";

/**
 * Знак портала Госуслуг для кнопок входа и подтверждения личности.
 * Файл знака — public/brand/gosuslugi.png (+ @2x); пока файла нет — показывается
 * нейтральный значок щита.
 */
export default function GosuslugiMark({ size = 24, className }) {
  const [failed, setFailed] = useState(false);
  const cls = [styles.mark, className].filter(Boolean).join(" ");
  if (failed) {
    return <span className={`${cls} ${styles.fallback}`} style={{ width: size, height: size }} aria-hidden="true"><Icon name="shield" size={Math.round(size * 0.62)} /></span>;
  }
  const base = `${import.meta.env.BASE_URL}brand/gosuslugi`;
  return (
    <img
      className={cls}
      src={`${base}.png`}
      srcSet={`${base}.png 1x, ${base}@2x.png 2x`}
      width={size}
      height={size}
      alt=""
      aria-hidden="true"
      onError={() => setFailed(true)}
    />
  );
}
