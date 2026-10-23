"use client";
import Link from "next/link";
import {useSyncExternalStore} from "react";
import {useCart} from "@/hooks/useCart";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {usePickupIntent} from "@/hooks/usePickupIntent";
import {getPendingOrderSnapshot,getServerPendingOrderSnapshot,subscribeToPendingOrder} from "@/lib/pendingOrderStorage";
import {T,useLanguage} from "@/lib/language";
/** Browsing summary only; never substitute cart values for a reserved/payment order. */
export default function CheckoutPlanSummary(){
 const cart=useCart(),{branch}=useSelectedBranch(),{selection}=usePickupIntent(branch?.id),locale=useLanguage();
 const pending=useSyncExternalStore(subscribeToPendingOrder,getPendingOrderSnapshot,getServerPendingOrderSnapshot);
 if(pending||cart.isEmpty||cart.branchId!==branch?.id)return null;
 const date=selection?new Intl.DateTimeFormat(locale==="hi"?"hi-IN":"en-IN",{day:"numeric",month:"short",weekday:"short",timeZone:"Asia/Kolkata"}).format(new Date(`${selection.date}T12:00:00+05:30`)):null;
 return <aside className="checkout-plan-summary" aria-label="Your pickup plan"><h2><T text="Your pickup plan"/></h2><strong>{branch.name}</strong><p>{branch.address}</p><dl><div><dt><T text="Items"/></dt><dd>{cart.itemCount}</dd></div><div><dt><T text="Items subtotal"/></dt><dd>{new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(cart.subtotal)}</dd></div></dl><p>{date?`${date} · ${selection!.slot.startTime.slice(0,5)}–${selection!.slot.endTime.slice(0,5)} IST`:<T text="Choose pickup time"/>}</p><small><T text="Your price and pickup are verified before payment."/></small><Link href="/cart"><T text="Edit items"/></Link>{branch.phone&&<a href={`tel:${branch.phone.replace(/[^+\d]/g,"")}`}><T text="Call branch"/></a>}</aside>;
}
