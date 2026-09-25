import { forwardRef } from "react";
import styles from "./Card.module.css";

/** props: as (тег, по умолчанию 'div'), className (доп. классы экрана для композиции layout'а
 * внутри карточки), остальное прокидывается в тег как есть. Поддерживает ref (forwardRef) —
 * нужен, например, для scrollIntoView на подсвеченной карточке в списке сценариев. */
const Card = forwardRef(function Card({ as: As = "div", className, children, ...rest }, ref) {
  const cls = className ? `${styles.card} ${className}` : styles.card;
  return (
    <As ref={ref} className={cls} {...rest}>
      {children}
    </As>
  );
});

export default Card;
