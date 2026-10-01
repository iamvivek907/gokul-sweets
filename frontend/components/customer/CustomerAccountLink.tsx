"use client";
import {useTranslation} from "@/lib/language";

import Link from "next/link";
import {useEffect, useState} from "react";
import {apiClient} from "@/services/apiClient";

type CustomerSession = {authenticated: boolean; phone?: string; name?: string};

/** Shared header entry point; session changes are announced by the profile panel. */
export default function CustomerAccountLink() {
    const translate = useTranslation();
    const [session, setSession] = useState<CustomerSession>({authenticated: false});

    useEffect(() => {
        let active = true;
        const refresh = () => {
            void apiClient<{enabled: boolean}>("/api/storefront/customer-identity")
                .then(config => config.enabled
                    ? apiClient<CustomerSession>("/api/customer/identity/me", {credentials: "include"})
                    : {authenticated: false})
                .then(current => {if (active) setSession(current);})
                .catch(() => {if (active) setSession({authenticated: false});});
        };
        refresh();
        window.addEventListener("gokul-customer-identity-changed", refresh);
        return () => {
            active = false;
            window.removeEventListener("gokul-customer-identity-changed", refresh);
        };
    }, []);

    const phone = session.authenticated ? session.phone : undefined;
    const label = session.authenticated ? session.name?.trim() || (phone ? `•••• ${phone.slice(-4)}` : "Account") : "Log in";
    return <Link href="/profile" className="customer-account-link max-w-[10rem] shrink-0 truncate rounded-full border border-[#d8c6ba] px-3 py-2 text-sm font-semibold text-[#173c39]"
        aria-label={session.authenticated ? `Open profile for ${session.authenticated ? label : translate(label)}` : translate("Log in to your account")}>
        {session.authenticated ? label : translate(label)}
    </Link>;
}
