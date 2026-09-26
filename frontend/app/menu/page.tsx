"use client";

import AppShell
    from "@/components/layout/AppShell";

import MenuScreen
    from "@/components/menu/MenuScreen";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";


export default function MenuPage() {
    const features = useStorefrontFeatures();

    return (
        <AppShell editorial={features?.contextualStorefrontV2 === true}>

            <MenuScreen />

        </AppShell>
    );
}
