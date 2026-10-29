"use client";
import Image from 'next/image';
import {useEffect,useRef,useState} from 'react';
import {apiClient} from '@/services/apiClient';
import {availabilityItems,checkCartAvailability} from '@/services/availabilityApi';
import {getCartSnapshot,parseCart,saveCart} from '@/lib/cartStorage';
import {getPickupSlotSnapshot} from '@/lib/checkoutStorage';
import {getStoredBranchSnapshot} from '@/lib/branchStorage';
import {getPendingOrderSnapshot} from '@/lib/pendingOrderStorage';
import {pickupIsFresh} from '@/lib/pickupFreshness';
import {requestMenuPickup} from '@/lib/menuPickupRecovery';
import {addMeal,mealEstimate} from '@/lib/cartMeals';
import {menuTapFeedback} from '@/lib/menuHaptics';
import {menuMoney} from '@/lib/menuPresentationDetails';
import MenuDiscoverySheet from '@/components/menu/MenuDiscoverySheet';
import {T,useLanguage} from '@/lib/language';
import type {MenuProduct} from '@/types/menu';
import type {MealDefinition,MealQuote,CartMeal} from '@/types/meal';
import type {PickupSelection as StoredPickupSlot} from '@/types/pickup';
import styles from './Meals.module.css';
export default function MealCollection({branchId,products,smartAvailability}:{branchId:number;products:MenuProduct[];smartAvailability:boolean}){
 const locale=useLanguage(),map=new Map(products.map(p=>[p.id,{...p,available:p.catalogAvailable??p.available}]));
 const [meals,setMeals]=useState<MealDefinition[]>([]),[active,setActive]=useState<MealDefinition|null>(null),[selected,setSelected]=useState<string[]>([]),[quantity,setQuantity]=useState(1),[replaceId,setReplaceId]=useState<string|undefined>(),[error,setError]=useState(''),[notice,setNotice]=useState(''),[busy,setBusy]=useState(false);
 const controller=useRef<AbortController|null>(null),drafts=useRef(new Map<number,{version:number;selected:string[];quantity:number}>()),editOpened=useRef(false);
 useEffect(()=>{const c=new AbortController();apiClient<MealDefinition[]>(`/api/branches/${branchId}/meals`,{signal:AbortSignal.any([c.signal,AbortSignal.timeout(10000)])}).then(data=>{if(!c.signal.aborted)setMeals(Array.isArray(data)?data.filter(m=>m.active&&Array.isArray(m.groups)):[]);}).catch(()=>{});return()=>{c.abort();controller.current?.abort();};},[branchId]);
 useEffect(()=>{if(editOpened.current||!meals.length)return;const id=new URLSearchParams(location.search).get('mealEdit'),current=parseCart(getCartSnapshot()).meals?.find(m=>m.instanceId===id),meal=current&&meals.find(m=>m.id===current.mealId);if(!meal)return;editOpened.current=true;queueMicrotask(()=>{setActive(meal);setSelected(current.optionIds.filter(id=>meal.groups.some(g=>g.options.some(o=>o.key===id))));setQuantity(current.quantity);setReplaceId(current.instanceId);});},[meals]);
 useEffect(()=>{if(active)drafts.current.set(active.id,{version:active.version,selected,quantity});},[active,selected,quantity]);
 function open(meal:MealDefinition){const draft=drafts.current.get(meal.id);setSelected(draft?.version===meal.version?draft.selected:[]);setQuantity(draft?.version===meal.version?draft.quantity:1);setReplaceId(undefined);setError('');setActive(meal);}
 function choose(group:MealDefinition['groups'][number],key:string){setError('');setSelected(previous=>{let next=previous.filter(x=>x!==key);if(!previous.includes(key)){if(group.maximum===1)next=next.filter(x=>!group.options.some(o=>o.key===x));if(group.options.filter(o=>next.includes(o.key)).length>=group.maximum)return previous;next.push(key);}else if(group.minimum===1&&group.maximum===1)return previous;drafts.current.set(active!.id,{version:active!.version,selected:next,quantity});return next;});}
 async function submit(close:()=>void){
  if(!active||busy)return;menuTapFeedback();setBusy(true);setError('');const c=new AbortController();controller.current=c;let committed=false;
  try{if(getPendingOrderSnapshot()||sessionStorage.getItem('gokul-mobile-order-attempt'))throw Error('Finish or recover your current checkout before adding a meal.');
   const before=getCartSnapshot(),branchBefore=getStoredBranchSnapshot(),pickupBefore=getPickupSlotSnapshot();const current=parseCart(before);
   if(JSON.parse(branchBefore||'null')?.id!==branchId)throw Error('Your branch changed. Reopen the meal.');
   const claim={instanceId:replaceId??crypto.randomUUID(),mealId:active.id,version:active.version,quantity,optionIds:[...selected].sort()};
   const quote=await apiClient<MealQuote>(`/api/branches/${branchId}/meals/quote`,{method:'POST',body:JSON.stringify(claim),signal:AbortSignal.any([c.signal,AbortSignal.timeout(15000)])});
   const meal:CartMeal={...claim,title:quote.title,total:Number(quote.total),regularTotal:Number(quote.regularTotal),components:quote.components};
   const next=addMeal(current,meal,map,branchId,replaceId);
   if(smartAvailability){let pickup:StoredPickupSlot|null=null;try{pickup=JSON.parse(pickupBefore);}catch{}
    if(!pickup||pickup.slot.branchId!==branchId||!pickupIsFresh(pickup,new Date())){
     drafts.current.set(active.id,{version:active.version,selected,quantity});setNotice('Choose pickup, then reopen your meal. Your choices are saved.');setActive(null);requestMenuPickup(quote.components[0].productId);return;
    }
    const checked=await checkCartAvailability(branchId,pickup.date,1,availabilityItems(next.items),AbortSignal.any([c.signal,AbortSignal.timeout(15000)]));const option=checked.dates.find(d=>d.date===pickup!.date)?.slots.find(s=>s.slot.id===pickup!.slot.id);
    if(!option||!(pickup.pickupType==='PRIORITY'?option.priorityAvailable:option.normalAvailable))throw Error('This meal and cart cannot be prepared for that pickup. Choose another time.');
   }
   if(c.signal.aborted)return;
   if(before!==getCartSnapshot()||branchBefore!==getStoredBranchSnapshot()||pickupBefore!==getPickupSlotSnapshot()||getPendingOrderSnapshot()||sessionStorage.getItem('gokul-mobile-order-attempt'))throw Error('Your cart or pickup changed. Review the meal and try again.');
   saveCart(next);committed=true;setNotice('Your meal was added. Final prices, tax and pickup are checked before payment.');drafts.current.delete(active.id);close();
  }catch(reason){if(!c.signal.aborted)setError(reason instanceof Error?reason.message:'The meal could not be added. Try again.');}finally{if(!c.signal.aborted&&!committed)setBusy(false);}
 }
 if(!meals.length)return null;
 const estimate=active?mealEstimate(active,selected,quantity,map):null;
 return <section className={styles.collection} aria-label="App-exclusive meals"><div className={styles.heading}><span><T text="ONLY ON THE APP"/></span><h3><T text="Build your meal"/></h3><p><T text="Your favourites. Your combination."/></p></div>{notice&&<p role="status">{notice}</p>}<div className={styles.rail}>{meals.map(meal=>{const minimumChoices=meal.groups.flatMap(g=>[...g.options].sort((a,b)=>Number(a.upgrade)-Number(b.upgrade)).slice(0,g.minimum).map(o=>o.key)),price=mealEstimate(meal,minimumChoices,1,map).total,image=meal.imageUrl||map.get(meal.groups[0]?.options[0]?.productId)?.imageUrl;return <button type="button" className={styles.mealCard} key={meal.id} onClick={()=>open(meal)} aria-label={`Build meal ${meal.title}`}>{image&&<Image unoptimized width={112} height={148} src={image} alt="" loading="lazy" onError={e=>{e.currentTarget.style.display='none';}}/>}<span className={styles.cardCopy}><small><T text="APP EXCLUSIVE"/></small><strong>{locale==='hi'&&meal.titleHi?meal.titleHi:meal.title}</strong>{meal.description&&<span>{meal.description}</span>}<b><T text="From"/> {menuMoney(price)}</b><em><T text="Build meal"/> →</em></span></button>;})}</div>
 {active&&estimate&&<MenuDiscoverySheet tactile title={locale==='hi'&&active.titleHi?active.titleHi:active.title} className="menu-meal-sheet" onClose={()=>{controller.current?.abort();setBusy(false);setActive(null);}} footer={close=><div className={styles.builderFooter}><div className={styles.quantity}><button type="button" disabled={busy||quantity<=1} aria-label="Decrease meal quantity" onClick={()=>setQuantity(q=>q-1)}>−</button><strong>{quantity}</strong><button type="button" disabled={busy||quantity>=10} aria-label="Increase meal quantity" onClick={()=>setQuantity(q=>q+1)}>+</button></div><button type="button" className={styles.addMeal} disabled={busy||!estimate.valid} onClick={()=>void submit(close)}><T text={busy?'Checking…':replaceId?'Update meal':'Add meal'}/> · {menuMoney(estimate.total)}</button></div>}>
  <div className={styles.intro}><span className={styles.tag}><T text="APP EXCLUSIVE"/></span><p><T text="Pick your favourites to complete your meal."/></p>{estimate.valid&&estimate.regularTotal>estimate.total&&<p className={styles.saving}><s>{menuMoney(estimate.regularTotal)}</s> <T text="Meal savings"/> {menuMoney(estimate.regularTotal-estimate.total)}</p>}<small><T text="Before item tax and checkout charges"/></small></div>
  {active.groups.map(group=><fieldset key={group.key} className={styles.group}><legend>{locale==='hi'&&group.titleHi?group.titleHi:group.title}</legend><p><T text={group.minimum?'Required':'Optional'}/> · <T text="Choose"/> {group.minimum===group.maximum?group.minimum:`${group.minimum}–${group.maximum}`}</p>{group.options.map(option=>{const product=map.get(option.productId),checked=selected.includes(option.key),available=!!product&&(product.catalogAvailable??product.available),limited=!checked&&group.maximum>1&&group.options.filter(o=>selected.includes(o.key)).length>=group.maximum;return <label className={styles.option} key={option.key}><span><strong>{product?.name??'Unavailable dish'}</strong><small>{option.weightGrams!==null?`${option.weightGrams} g`:`${option.quantity} ×`} {Number(option.upgrade)>0?`· +${menuMoney(Number(option.upgrade))}`:''}{!available&&<> · <T text="Currently unavailable"/></>}</small></span><input type={group.minimum===1&&group.maximum===1?'radio':'checkbox'} name={group.key} checked={checked} disabled={busy||!available||limited} onChange={()=>choose(group,option.key)}/></label>;})}</fieldset>)}
  {error&&<p role="alert" className={styles.error}><T text={error}/></p>}{!estimate.valid&&<p role="status"><T text="Complete the required choices to add your meal."/></p>}
 </MenuDiscoverySheet>}
 </section>;
}
