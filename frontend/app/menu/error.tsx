"use client";

import CustomerRecovery from "@/components/common/CustomerRecovery";

export default function MenuError({retry}:{retry:()=>void}) {
    return <main><CustomerRecovery title="Let’s get back to the menu" message="We couldn’t open the menu. Your saved cart is kept." onRetry={retry}/></main>;
}
