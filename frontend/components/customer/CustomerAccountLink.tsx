"use client";
import CustomerIcon from "./CustomerIcon";
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
    const initials = session.name?.trim().split(/\s+/).slice(0, 2).map(part => part[0]?.toUpperCase()).join("");
    return <Link href="/profile" className="customer-account-link max-w-[10rem] shrink-0 truncate rounded-full border border-[#d8c6ba] px-3 py-2 text-sm font-semibold text-[#173c39]"
        aria-label={session.authenticated ? `Open profile for ${label}${initials ? ` (${initials})` : ""}` : translate("Log in to your account")}>
        <span className="customer-account-desktop-label">{session.authenticated ? label : translate(label)}</span>
        <span className="customer-account-mobile-label" aria-hidden="true">{session.authenticated ? (initials || <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="8" r="3.2" /><path d="M5.5 20c.7-4 3-6 6.5-6s5.8 2 6.5 6" strokeLinecap="round" /></svg>) : <CustomerIcon kind="profile"/>}</span>
    </Link>;
}
