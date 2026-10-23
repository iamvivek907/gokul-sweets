"use client";
import CustomerRecovery from "@/components/common/CustomerRecovery";
export default function ErrorPage({retry}:{error:Error & {digest?:string};retry:()=>void}) {
    return <main><CustomerRecovery onRetry={retry}/></main>;
}
