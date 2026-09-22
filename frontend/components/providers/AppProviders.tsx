"use client";

import type {
    ReactNode
} from "react";

import ServiceWorkerRegistration
    from "@/components/pwa/ServiceWorkerRegistration";

import OfflineBanner
    from "@/components/common/OfflineBanner";


interface AppProvidersProps {
    children: ReactNode;
}


export default function AppProviders({
    children
}: AppProvidersProps) {

    return (
        <>
            <ServiceWorkerRegistration />

            <OfflineBanner />

            {children}
        </>
    );
}