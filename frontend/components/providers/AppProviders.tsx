"use client";
import NavigationFeedback from "@/components/common/NavigationFeedback";
import {LanguageRuntime} from "@/lib/language";

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
            <LanguageRuntime />
            <NavigationFeedback />
            <ServiceWorkerRegistration />

            <OfflineBanner />
            <StorefrontVitals />
            <AccessibleOrderingRuntime />

            {children}
        </>
    );
}
