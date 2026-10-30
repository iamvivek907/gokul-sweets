"use client";
import Link from 'next/link';
import {useCart} from '@/hooks/useCart';
import {getCartSnapshot,parseCart,saveCart,clearStoredCart} from '@/lib/cartStorage';
import {removeMeal,mealSavings} from '@/lib/cartMeals';
import {menuMoney} from '@/lib/menuPresentationDetails';
import {getPendingOrderSnapshot} from '@/lib/pendingOrderStorage';
import {T} from '@/lib/language';
import styles from './Meals.module.css';
export default function MealCartSummary({disabled=false}:{disabled?:boolean}){
 const cart=useCart();
 if(!cart.meals?.length&&!cart.mealNotice)return null;
 function remove(id:string){if(disabled||getPendingOrderSnapshot()||sessionStorage.getItem('gokul-mobile-order-attempt'))return;const current=removeMeal(parseCart(getCartSnapshot()),id);if(current.items.length)saveCart(current);else clearStoredCart();}
 return <aside className={styles.cart} aria-label="Your meals">{cart.mealNotice&&<p role="status"><T text={cart.mealNotice}/></p>}{cart.meals?.map(m=><div className={styles.cartMeal} key={m.instanceId}><div><strong>{m.title} × {m.quantity}</strong><small> #{m.instanceId.slice(-6)}</small><p><T text="Meal price"/> {menuMoney(m.total)} · <T text="Before item tax and checkout charges"/></p></div><div>{!disabled&&<Link href={`/menu?mealEdit=${encodeURIComponent(m.instanceId)}`}><T text="Edit meal"/></Link>}<button type="button" disabled={disabled} onClick={()=>remove(m.instanceId)} aria-label={`Remove meal ${m.title}`}><T text="Remove meal"/></button></div></div>)}{mealSavings(cart.meals)>0&&<strong className={styles.saving}><T text="Meal savings"/> {menuMoney(mealSavings(cart.meals))}</strong>}</aside>;
}
