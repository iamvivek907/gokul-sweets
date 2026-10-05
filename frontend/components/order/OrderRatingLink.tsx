"use client";
import Link from "next/link";
import {useEffect,useRef,useState} from "react";
import {getReviewContext} from "@/services/reviewApi";
/** History remains usable while visible completed cards independently read their real rating. */
export default function OrderRatingLink({orderNumber}:{orderNumber:string}) {
 const element=useRef<HTMLDivElement>(null);
 const [review,setReview]=useState<{order:string;rating:number}|null>(null);
 useEffect(()=>{
  const controller=new AbortController();let started=false;
  const observer=new IntersectionObserver(entries=>{if(started||!entries.some(e=>e.isIntersecting))return;started=true;observer.disconnect();getReviewContext(orderNumber,controller.signal).then(context=>{if(!controller.signal.aborted&&context.review)setReview({order:orderNumber,rating:context.review.overallRating});}).catch(()=>{/* The review link remains available on a failed read. */});});
  if(element.current)observer.observe(element.current);
  return()=>{observer.disconnect();controller.abort();};
 },[orderNumber]);
 const rating=review?.order===orderNumber?review.rating:0;
 return <div ref={element}><Link className="mobile-order-rate" href={`/orders/${encodeURIComponent(orderNumber)}#order-review`}><span>{rating?"Your meal rating":"Rate your meal"}</span><span aria-label={rating?`${rating} out of 5 stars`:"Not rated"} style={rating?{color:"#d69216"}:undefined}>{Array.from({length:5},(_,n)=>n<rating?"★":"☆").join(" ")}</span></Link></div>;
}
