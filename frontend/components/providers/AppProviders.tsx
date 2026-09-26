"use client";

import type {
    ReactNode
} from "react";

import ServiceWorkerRegistration
    from "@/components/pwa/ServiceWorkerRegistration";

import OfflineBanner
    from "@/components/common/OfflineBanner";
import StorefrontVitals from "@/components/common/StorefrontVitals";
import AccessibleOrderingRuntime from "@/components/common/AccessibleOrderingRuntime";


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
            <StorefrontVitals />
            <AccessibleOrderingRuntime />

            {children}
        </>
    );
}
