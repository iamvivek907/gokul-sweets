"use client";
import Link from 'next/link';
import {useState} from 'react';
import {useCart} from '@/hooks/useCart';
import {getCartSnapshot,parseCart,saveCart,clearStoredCart} from '@/lib/cartStorage';
import {removeMeal,mealSavings} from '@/lib/cartMeals';
import {menuMoney} from '@/lib/menuPresentationDetails';
import {getPendingOrderSnapshot} from '@/lib/pendingOrderStorage';
import {T} from '@/lib/language';
import styles from './Meals.module.css';
export default function MealCartSummary({disabled=false}:{disabled?:boolean}){
 const cart=useCart();
 const [notice,setNotice]=useState(false);
 if(!cart.meals?.length&&!cart.mealNotice)return null;
 function remove(id:string){
  if(disabled||getPendingOrderSnapshot()||sessionStorage.getItem('gokul-mobile-order-attempt'))return;
  const snapshot=getCartSnapshot();
  if(snapshot!==cart.snapshot){setNotice(true);return;}
  const current=parseCart(snapshot),next=removeMeal(current,id);
  if(next===current)return;
  if(next.items.length)saveCart(next);else clearStoredCart();
  setNotice(false);
 }
 return <aside className={styles.cart} aria-label="Your meals">{notice&&<p role="alert"><T text="Your cart changed. Review it and try again."/></p>}{cart.mealNotice&&<p role="status"><T text={cart.mealNotice}/></p>}{cart.meals?.map(m=><div className={styles.cartMeal} key={m.instanceId}><div><strong>{m.title} × {m.quantity}</strong><p className="meal-cart-price">{menuMoney(m.total)}</p><div className="meal-cart-parts">{m.components.map(part=>{const item=cart.items.find(i=>i.product.id===part.productId);return <span key={part.productId}>{part.weightGrams==null?`${part.quantity} × `:`${part.weightGrams} g `}{item?.product.name}</span>;})}</div></div><div>{!disabled&&<Link href={`/menu?mealEdit=${encodeURIComponent(m.instanceId)}`}><T text="Edit meal"/></Link>}<button type="button" disabled={disabled} onClick={()=>remove(m.instanceId)} aria-label={`Remove meal ${m.title}`}><T text="Remove meal"/></button></div></div>)}{mealSavings(cart.meals)>0&&<strong className={styles.saving}><T text="Meal savings"/> {menuMoney(mealSavings(cart.meals))}</strong>}</aside>;
}
