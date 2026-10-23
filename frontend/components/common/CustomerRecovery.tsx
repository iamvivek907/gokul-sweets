"use client";
import Link from "next/link";
import {T} from "@/lib/language";

/** Recovery never clears saved state or starts a payment request. */
export default function CustomerRecovery({title="Let’s get you back to your order",message="We couldn’t open this page. Your saved cart is kept.",onRetry,disabled=false}:{title?:string;message?:string;onRetry?:()=>void;disabled?:boolean}) {
    return <section className="customer-recovery" aria-labelledby="customer-recovery-title">
        <span className="customer-recovery-brand">GOKUL <small>SWEETS</small></span>
        <span className="customer-recovery-symbol" aria-hidden="true">↻</span>
        <h1 id="customer-recovery-title"><T text={title}/></h1><p role="status"><T text={message}/></p>
        <div>{onRetry&&<button type="button" disabled={disabled} onClick={onRetry}><T text="Try again"/></button>}<Link href="/menu"><T text="Back to menu"/></Link><Link href="/orders"><T text="My orders"/></Link></div>
    </section>;
}
