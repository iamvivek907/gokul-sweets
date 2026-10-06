"use client";
import {useEffect,useRef,useState} from "react";
import MenuDiscoverySheet from "./MenuDiscoverySheet";
import CustomerIcon from "@/components/customer/CustomerIcon";
import {T} from "@/lib/language";
import type {MenuCategory} from '@/types/menu';
export default function MobileMenuFilters({categories,selected,onCategories,maximum,onMaximum,portions,onPortions}:{categories:MenuCategory[];selected:number[];onCategories:(ids:number[])=>void;maximum:number|null;onMaximum:(n:number|null)=>void;portions:boolean;onPortions:(b:boolean)=>void}){
 const [itemsOpen,setItemsOpen]=useState(false);
 const count=selected.length+Number(maximum!==null)+Number(portions);
 const pendingSection=useRef<number|null>(null);
 const clearFilters=()=>{
  // A category jump is local navigation. Preserve the product array when already
  // unfiltered so repeated taps do not restart pickup checks for a large menu.
  if(selected.length)onCategories([]);
  if(maximum!==null)onMaximum(null);
  if(portions)onPortions(false);
 };
 useEffect(()=>{
  if(itemsOpen||pendingSection.current===null)return;
  const id=pendingSection.current;pendingSection.current=null;
  const frame=requestAnimationFrame(()=>{
   const section=id===0?document.querySelector('.mobile-menu-filters'):document.getElementById(`menu-category-${id}`);
   const disclosure=section?.querySelector('details');
   if(disclosure)disclosure.open=true;
   section?.scrollIntoView({block:'start',behavior:'instant'});
  });
  return()=>cancelAnimationFrame(frame);
 },[itemsOpen]);
 const browseCategory=(id:number)=>{
  clearFilters();pendingSection.current=id;setItemsOpen(false);
 };
 return <><div className="mobile-items-picker"><button type="button" aria-label="Browse all item categories" aria-haspopup="dialog" aria-expanded={itemsOpen} onClick={()=>setItemsOpen(true)}><CustomerIcon kind="menu"/><T text="Items"/></button></div>{itemsOpen&&<MenuDiscoverySheet className="mobile-items-dialog" title="Items" onClose={()=>setItemsOpen(false)}><nav className="mobile-items-categories" aria-label="Item categories"><button type="button" onClick={()=>browseCategory(0)}><T text="All items"/><span>{categories.reduce((n,c)=>n+c.products.length,0)}</span></button>{categories.map(c=><button type="button" key={c.id} onClick={()=>browseCategory(c.id)}>{c.name}<span>{c.products.length}</span></button>)}</nav></MenuDiscoverySheet>}<div className="mobile-menu-filters"><details><summary><svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M3 6h18M3 12h18M3 18h18"/><circle cx="8" cy="6" r="2" fill="white"/><circle cx="16" cy="12" r="2" fill="white"/><circle cx="10" cy="18" r="2" fill="white"/></svg><T text="Filters"/>{count?` (${count})`:''}</summary><div className="mobile-filter-options"><fieldset><legend><T text="Categories"/></legend>{categories.map(c=><label key={c.id}><input type="checkbox" checked={selected.includes(c.id)} onChange={()=>onCategories(selected.includes(c.id)?selected.filter(id=>id!==c.id):[...selected,c.id])}/>{c.name}</label>)}</fieldset><label><T text="Price up to"/><select value={maximum??''} onChange={e=>onMaximum(e.target.value?Number(e.target.value):null)}><option value=""><T text="Any price"/></option>{[100,200,300,500].map(p=><option key={p} value={p}>₹{p}</option>)}</select></label><label><input type="checkbox" checked={portions} onChange={e=>onPortions(e.target.checked)}/><T text="Portion choices"/></label><button type="button" onClick={()=>{onCategories([]);onMaximum(null);onPortions(false);}}><T text="Clear filters"/></button></div></details><div className="mobile-filter-chips">{categories.map(c=><button key={c.id} type="button" aria-pressed={selected.includes(c.id)} onClick={()=>onCategories(selected.includes(c.id)?selected.filter(id=>id!==c.id):[...selected,c.id])}>{c.name}</button>)}</div></div></>;
}
