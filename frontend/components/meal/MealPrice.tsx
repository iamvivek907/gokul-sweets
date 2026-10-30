import {T} from '@/lib/language';
import {menuMoney} from '@/lib/menuPresentationDetails';
import styles from './Meals.module.css';
/** Comparison is the actual selected dish total, never an invented menu MRP. */
export default function MealPrice({regular,total}:{regular:number;total:number}){
 const saving=regular>total&&total>0;
 return <span className={styles.prices}>{saving&&<><span className="sr-only"><T text="Regular price"/> </span><s className="menu-comparison-price">{menuMoney(regular)}</s></>}<span className="sr-only"><T text={saving?'Meal price':'Price'}/> </span><strong className={saving?`${styles.salePrice} menu-sale-price`:undefined}>{menuMoney(total)}</strong></span>;
}
