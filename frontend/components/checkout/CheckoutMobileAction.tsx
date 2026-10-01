"use client";
import {useTranslation} from "@/lib/language";
import styles from "./CheckoutMobileAction.module.css";
export default function CheckoutMobileAction({label,amount,disabled,onContinue}:{label:string;amount?:number;disabled:boolean;onContinue:()=>void}) {
    const translate = useTranslation();
 return <><div className={styles.space} aria-hidden="true"/><aside className={styles.bar} aria-label={translate("Checkout action")}><div><small>{amount==null?translate("Review before payment"):translate("Payable total")}</small><strong>{amount==null?translate("Check your total"):new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(amount)}</strong></div><button type="button" disabled={disabled} onClick={onContinue}>{label}</button></aside></>;
}
