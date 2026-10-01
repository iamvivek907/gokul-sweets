"use client";
import styles from "./CheckoutMobileAction.module.css";
export default function CheckoutMobileAction({label,amount,disabled,onContinue}:{label:string;amount?:number;disabled:boolean;onContinue:()=>void}) {
 return <><div className={styles.space} aria-hidden="true"/><aside className={styles.bar} aria-label="Checkout action"><div><small>{amount==null?"Review before payment":"Payable total"}</small><strong>{amount==null?"Check your total":new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(amount)}</strong></div><button type="button" disabled={disabled} onClick={onContinue}>{label}</button></aside></>;
}
