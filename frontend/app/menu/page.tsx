"use client";

import AppShell
    from "@/components/layout/AppShell";

import MenuScreen
    from "@/components/menu/MenuScreen";
import {usePhoneViewport} from "@/hooks/usePhoneViewport";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";


export default function MenuPage() {
    const features = useStorefrontFeatures();
    const phone=usePhoneViewport();

    return (
        <AppShell showSocialPopup={!(phone===true&&features?.simplifiedCheckout&&features.checkoutExperienceV2)} editorial={features?.contextualStorefrontV2 === true}>

            <MenuScreen />

        </AppShell>
    );
}
