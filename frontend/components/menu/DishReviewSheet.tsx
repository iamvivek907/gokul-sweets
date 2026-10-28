"use client";
import {useEffect,useState} from "react";
import MenuDiscoverySheet from "./MenuDiscoverySheet";
import {apiClient} from "@/services/apiClient";
import {T,useTranslation} from "@/lib/language";
import {useMenuExperience} from "./MenuExperience";
type Review={comment:string;overallRating:number;displayName:string;itemRatings:{productId:number;productName:string;rating:number}[]};
type Page={reviews:Review[];nextBefore:number|null};
function validReview(value:Review){return value&&typeof value.comment==="string"&&typeof value.displayName==="string"&&Number.isInteger(value.overallRating)&&value.overallRating>=1&&value.overallRating<=5&&Array.isArray(value.itemRatings)&&value.itemRatings.length<=20&&value.itemRatings.every(i=>i&&typeof i.productName==="string"&&Number.isSafeInteger(i.productId)&&i.productId>0&&Number.isInteger(i.rating)&&i.rating>=1&&i.rating<=5);}
export default function DishReviewSheet({branchId,productIds,name,onClose}:{branchId:number;productIds:number[];name:string;onClose:()=>void}){
 const t=useTranslation(),{products}=useMenuExperience();
 const [size,setSize]=useState<number|null>(null),[stars,setStars]=useState<number|null>(null),[before,setBefore]=useState<number|null>(null),[retry,setRetry]=useState(0);
 const [snapshot,setSnapshot]=useState<{key:string;page?:Page;error?:boolean}|null>(null);
 const ids=JSON.stringify(size===null?productIds:[size]),key=JSON.stringify([branchId,ids,stars,before,retry]);
 useEffect(()=>{
  const controller=new AbortController(),query=new URLSearchParams({productIds:JSON.parse(ids).join(",")});if(stars!==null)query.set("stars",String(stars));if(before!==null)query.set("before",String(before));
  void apiClient<Page>(`/api/branches/${branchId}/discovery/dish-reviews?${query}`,{signal:AbortSignal.any([controller.signal,AbortSignal.timeout(10000)])}).then(page=>{
   if(!page||!Array.isArray(page.reviews)||page.reviews.length>20||!page.reviews.every(validReview))throw Error("Invalid review page");
   if(page.nextBefore!==null&&(!Number.isSafeInteger(page.nextBefore)||page.nextBefore<=0||before!==null&&page.nextBefore>=before))throw Error("Invalid review cursor");
   if(!controller.signal.aborted)setSnapshot({key,page});
  }).catch(()=>{if(!controller.signal.aborted)setSnapshot({key,error:true});});return()=>controller.abort();
 },[branchId,ids,key,stars,before]);
 const current=snapshot?.key===key?snapshot:null,page=current?.page;
 return <MenuDiscoverySheet title={`${t("Customer reviews")} · ${name}`} onClose={onClose} className="menu-dish-reviews">
  <p><T text="Published feedback from completed orders. Dish stars and overall experience are shown separately."/></p>
  <div className="menu-review-filters">{productIds.length>1&&<label><T text="Size"/><select aria-label={t("Size")} value={size??""} onChange={e=>{setSize(e.target.value?Number(e.target.value):null);setBefore(null);}}><option value="">{t("All sizes")}</option>{productIds.map(id=><option key={id} value={id}>{products.find(p=>p.id===id)?.name??`${name} · ${id}`}</option>)}</select></label>}
  <label><T text="Dish rating"/><select aria-label={t("Dish rating")} value={stars??""} onChange={e=>{setStars(e.target.value?Number(e.target.value):null);setBefore(null);}}><option value="">{t("All ratings")}</option>{[5,4,3,2,1].map(n=><option key={n} value={n}>{n} ★</option>)}</select></label></div>
  {!current?<p role="status"><T text="Loading customer reviews…"/></p>:current.error?<div role="alert"><p><T text="Reviews could not be loaded."/></p><button type="button" onClick={()=>setRetry(n=>n+1)}><T text="Try again"/></button></div>:<>
  {!page?.reviews.length&&<p><T text="No published comments match this dish and filter yet."/></p>}
  {page?.reviews.map((review,index)=><figure className="menu-dish-review" key={`${before}:${index}`}><figcaption><strong>{review.displayName}</strong> · <T text="Completed order"/></figcaption><p><T text="Overall experience"/>: {review.overallRating}/5</p><ul>{review.itemRatings.map(item=><li key={item.productId}>{item.productName} · {item.rating}/5 ★</li>)}</ul><blockquote>{review.comment}</blockquote></figure>)}
  </>}
  <nav aria-label={t("Customer review pages")}>{before!==null&&<button type="button" onClick={()=>setBefore(null)}><T text="Latest reviews"/></button>}{page?.nextBefore!=null&&<button type="button" onClick={()=>setBefore(page.nextBefore)}><T text="More reviews"/></button>}</nav>
 </MenuDiscoverySheet>;
}
