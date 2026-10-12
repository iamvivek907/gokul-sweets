"use client";
import {useEffect,useRef} from "react";
import {T} from "@/lib/language";
import type {CheckoutQuote} from "@/types/order";
import type {AvailableRebateResponse} from "@/types/rebate";
import "./reward-controls.css";
const money=(n:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(n);
export default function MobileBillSummary({quote,best,rewardDiscount,paymentFee,paymentFeeTax,total,onClose}:{quote:CheckoutQuote;best:AvailableRebateResponse|null|undefined;rewardDiscount:number;paymentFee?:number;paymentFeeTax?:number;total:number;onClose:()=>void}){
 const dialog=useRef<HTMLDialogElement>(null);
 useEffect(()=>{const surface=dialog.current,previous=document.activeElement as HTMLElement|null;surface?.showModal();return()=>{surface?.close();previous?.focus();};},[]);
 const savings=rewardDiscount+(best?.rebateAmount??0);
 return <dialog ref={dialog} className="checkout-offer-dialog mobile-bill-dialog" aria-labelledby="mobile-bill-title" onCancel={e=>{e.preventDefault();onClose();}} onClick={e=>{const r=e.currentTarget.getBoundingClientRect();if(e.target===e.currentTarget&&(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom))onClose();}}><div><header><h2 id="mobile-bill-title"><T text="Bill summary"/></h2><button type="button" onClick={onClose}><T text="Close"/> ×</button></header><dl>
 <div><dt><T text="Items"/></dt><dd>{money(Number(quote.subtotal))}</dd></div>
 <div><dt><T text="Tax"/></dt><dd>{money(Number(quote.taxAmount))}</dd></div>
 <div><dt><T text="Priority pickup"/></dt><dd>{money(Number(quote.priorityCharge??0))}</dd></div>
 <div><dt><T text="Convenience fee"/></dt><dd>{money(Number(quote.convenienceFee??0))}</dd></div>
 <div><dt><T text="Online payment fee"/> ({quote.paymentFeeRate??0}%)<small><T text="Includes"/> {money(Number(paymentFeeTax??quote.paymentFeeTax??0))} <T text="fee tax"/></small></dt><dd>{money(Number(paymentFee??quote.paymentFee??0))}</dd></div>
 {rewardDiscount>0&&<div className="mobile-bill-saving"><dt><T text="Reward savings"/></dt><dd>−{money(rewardDiscount)}</dd></div>}
 {best&&<div className="mobile-bill-saving"><dt>{best.name}</dt><dd>−{money(best.rebateAmount)}</dd></div>}
 <div className="mobile-bill-total"><dt><T text="Total to pay"/></dt><dd>{money(total)}</dd></div>
 </dl>{savings>0&&<p className="mobile-checkout-savings"><T text="You’re saving"/> {money(savings)} <T text="on this order"/></p>}<p><T text="Offers and coins reduce eligible food amounts. Additional charges remain payable."/></p></div></dialog>;
}
