"use client";
import Image from "next/image";
import Link from "next/link";
import {useEffect,useState} from "react";
import {apiClient} from "@/services/apiClient";
import type {BranchDiscovery as Discovery} from "@/types/branchDiscovery";
import type {Branch} from "@/types/branch";
import BranchSelector from "./BranchSelector";
export default function BranchDiscovery({branch,selected}:{branch:Branch;selected:boolean}) {
 const [result,setResult]=useState<{branchId:number;data:Discovery}|null>(null),[error,setError]=useState(false),[retry,setRetry]=useState(0);
 useEffect(()=>{const c=new AbortController();apiClient<Discovery>(`/api/branches/${branch.id}/discovery`,{signal:AbortSignal.any([c.signal,AbortSignal.timeout(10000)])}).then(data=>{if(!c.signal.aborted){setResult({branchId:branch.id,data});setError(false);}}).catch(()=>{if(!c.signal.aborted)setError(true);});return()=>c.abort();},[branch.id,retry]);
 const data=result?.branchId===branch.id?result.data:null;
 if(!data)return <section className="branch-discovery"><p role="status">{error?"Branch highlights are unavailable right now. You can still browse the menu.":"Loading branch highlights…"}</p>{error&&<button type="button" onClick={()=>setRetry(n=>n+1)} className="min-h-11 underline">Retry branch highlights</button>}</section>;
 return <div className="branch-discovery">
 <section className="branch-rating" aria-label="Overall branch experience"><div><p className="gokul-overline">From customer orders</p><h2>How was the Gokul experience?</h2></div>{data.overallExperience.count>0?<div className="branch-rating-score"><strong>★ {data.overallExperience.average.toFixed(1)} <small>/ 5</small></strong><p>{data.overallExperience.count} published {data.overallExperience.count===1?"review":"reviews"} · overall experience</p></div>:<p>No published experience reviews yet.</p>}</section>
 {data.offerings.length>0&&<section><p className="gokul-overline">At this branch</p><h2>What you’ll find here</h2><div className="branch-offering-grid">{data.offerings.map((item,index)=><article key={`${index}-${item.title}`}><span>{String(index+1).padStart(2,"0")}</span><h3>{item.title}</h3><p>{item.description}</p></article>)}</div></section>}
 <section><p className="gokul-overline">Rated by customers</p><h2>Top-rated at this branch</h2><p className="branch-discovery-help">Item scores come from published reviews of completed orders at {branch.name}.</p>{data.topRatedItems.length===0?<p className="branch-discovery-help">No rated menu items yet. Explore the menu to find your favourites.</p>:<div className="branch-rated-grid">{data.topRatedItems.map(item=><article key={item.productId}>{item.imageUrl?<Image unoptimized src={item.imageUrl} alt={item.name} width={480} height={300}/>:<div className="branch-rated-fallback" aria-hidden="true">G</div>}<div className="branch-rated-copy"><h3>{item.name}</h3><p className="branch-item-rating">★ {item.average.toFixed(1)} · {item.count} item {item.count===1?"rating":"ratings"}</p>{item.review&&<figure><blockquote>{item.review.comment}</blockquote><figcaption>Published customer review · {item.review.overallRating}/5 overall experience<br/>From an order that included this item</figcaption></figure>}{selected?<Link href={`/menu?category=${item.categoryId}#gokul-product-${item.productId}`}>Find on the menu →</Link>:<BranchSelector cardBranch={branch} destination="menu"/>}</div></article>)}</div>}</section>
 </div>;
}
