"use client";
import {useTranslation} from "@/lib/language";
import {canCallPickupCustomer, pickupCustomerPhone} from "@/lib/pickupCustomerContact";
import styles from "./PickupCustomerContact.module.css";
export default function PickupCustomerContact({customerName,customerPhone,status,fulfillment}:{customerName?:string|null;customerPhone?:string|null;status:string;fulfillment:string}) {
 const t=useTranslation(),name=customerName?.trim()||t("Customer"),phone=pickupCustomerPhone(customerPhone);
 const display=phone?.startsWith("+91")&&phone.length===13?`${phone.slice(0,3)} ${phone.slice(3,8)} ${phone.slice(8)}`:phone;
 return <section className={styles.card} aria-label={t("Customer contact")}><span className={styles.avatar} aria-hidden="true">{Array.from(name)[0]}</span><div className={styles.identity}><small>{t("Customer contact")}</small><strong>{name}</strong><span dir="ltr">{display||t("Phone unavailable")}</span></div>{phone&&canCallPickupCustomer(status,fulfillment)&&<a className={styles.call} href={`tel:${phone}`} aria-label={`${t("Call customer")} · ${name} · ${display}`}><span aria-hidden="true">☎</span> {t("Call customer")}</a>}</section>;
}
