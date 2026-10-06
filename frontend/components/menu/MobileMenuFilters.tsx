"use client";
import {useEffect,useRef,useState} from "react";
import MenuDiscoverySheet from "./MenuDiscoverySheet";
import CustomerIcon from "@/components/customer/CustomerIcon";
import {T} from "@/lib/language";
import type {MenuCategory} from '@/types/menu';
export default function MobileMenuFilters({categories,selected,onCategories,maximum,onMaximum,portions,onPortions,onResetSearch}:{categories:MenuCategory[];selected:number[];onCategories:(ids:number[])=>void;maximum:number|null;onMaximum:(n:number|null)=>void;portions:boolean;onPortions:(b:boolean)=>void;onResetSearch:()=>void}){
 const [itemsOpen,setItemsOpen]=useState(false);
 const total=categories.reduce((n,c)=>n+c.products.length,0);
 const recommended=Math.min(6,categories.reduce((n,c)=>n+c.products.filter(p=>p.available).length,0));
 const pendingSection=useRef<number|null>(null);
 const clearFilters=()=>{
  // A category jump is local navigation. Preserve the product array when already
  // unfiltered so repeated taps do not restart pickup checks for a large menu.
  if(selected.length)onCategories([]);
  if(maximum!==null)onMaximum(null);
  if(portions)onPortions(false);
  onResetSearch();
 };
 useEffect(()=>{
  if(itemsOpen||pendingSection.current===null)return;
  const id=pendingSection.current;pendingSection.current=null;
  const frame=requestAnimationFrame(()=>{
   const section=id===0?document.getElementById('gokul-menu-items'):id===-1?document.querySelector('.mobile-menu-highlights'):document.getElementById(`menu-category-${id}`);
   const disclosure=section?.querySelector('details');
   if(disclosure)disclosure.open=true;
   section?.scrollIntoView({block:'start',behavior:'instant'});
  });
  return()=>cancelAnimationFrame(frame);
 },[itemsOpen]);
 const browseCategory=(id:number)=>{
  clearFilters();pendingSection.current=id;setItemsOpen(false);
 };
 return <>
  <div className="mobile-items-picker"><button type="button" aria-label="Browse all item categories" aria-haspopup="dialog" aria-expanded={itemsOpen} onClick={()=>setItemsOpen(true)}><CustomerIcon kind="menu"/><T text="Items"/></button></div>
  {itemsOpen&&<MenuDiscoverySheet className="mobile-items-dialog" title="Items" onClose={()=>setItemsOpen(false)}>
   <nav className="mobile-items-categories" aria-label="Item categories">
    {recommended>0&&<button type="button" onClick={()=>browseCategory(-1)}><T text="Recommended"/><span>{recommended}</span></button>}
    <button type="button" onClick={()=>browseCategory(0)}><T text="All items"/><span>{total}</span></button>
    {categories.map(c=><button type="button" key={c.id} onClick={()=>browseCategory(c.id)}>{c.name}<span>{c.products.length}</span></button>)}
   </nav>
  </MenuDiscoverySheet>}
 </>;
}
