"use client";
import CustomerRecovery from "@/components/common/CustomerRecovery";
export default function CheckoutError({retry}:{error:Error & {digest?:string};retry:()=>void}) {
    return <main><CustomerRecovery title="Let’s recover your checkout" message="We couldn’t open checkout. Your saved cart and existing order are kept. Check My orders if payment has already started." onRetry={retry}/></main>;
}
