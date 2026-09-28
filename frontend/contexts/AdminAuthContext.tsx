"use client";

import {createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode} from "react";
import {authenticateAdmin, fetchAdminProfile, logoutAdmin} from "@/services/adminApi";
import type {AdminProfile} from "@/types/admin";

interface AdminAuthContextValue {
    profile: AdminProfile | null;
    authorization: string | null;
    ready: boolean;
    isAuthenticated: boolean;
    login: (username: string, password: string, code?: string) => Promise<void>;
    refresh: () => Promise<void>;
    logout: () => Promise<void>;
    hasPermission: (permission: string) => boolean;
    hasAnyPermission: (permissions: string[]) => boolean;
}
const AdminAuthContext = createContext<AdminAuthContextValue | undefined>(undefined);
const changeEvent = "gokul-admin-session-change";
function broadcast() {window.dispatchEvent(new Event(changeEvent));
    try {const channel = new BroadcastChannel(changeEvent); channel.postMessage("changed"); channel.close();} catch { /* PWA private mode */ }}

export function AdminAuthProvider({children}: {children: ReactNode}) {
    const [profile, setProfile] = useState<AdminProfile | null>(null);
    useEffect(() => {try {sessionStorage.removeItem("gokul-admin-session");} catch { /* storage disabled */ }}, []);
    const [ready, setReady] = useState(false);
    const refresh = useCallback(async () => {
        try {setProfile(await fetchAdminProfile());}
        catch {setProfile(null);}
        finally {setReady(true);}
    }, []);
    useEffect(() => {
        const controller = new AbortController();
        fetchAdminProfile(controller.signal).then(setProfile).catch(() => setProfile(null)).finally(() => setReady(true));
        const onExpired = () => {setProfile(null); setReady(true);};
        const onFocus = () => {void refresh();};
        let channel: BroadcastChannel | null = null;
        try {channel = new BroadcastChannel(changeEvent); channel.onmessage = onFocus;} catch { /* PWA private mode */ }
        window.addEventListener("gokul-admin-expired", onExpired);
        window.addEventListener("focus", onFocus);
        return () => {controller.abort(); channel?.close(); window.removeEventListener("gokul-admin-expired", onExpired);
            window.removeEventListener("focus", onFocus);};
    }, [refresh]);
    const login = useCallback(async (username: string, password: string, code?: string) => {
        setProfile(await authenticateAdmin(username, password, code)); setReady(true); broadcast();
    }, []);
    const logout = useCallback(async () => {
        try {await logoutAdmin();} finally {setProfile(null); setReady(true); broadcast();}
    }, []);
    const hasPermission = useCallback((permission: string) => !!profile &&
        (profile.roleName === "OWNER_ADMIN" || profile.permissions.includes(permission)), [profile]);
    const hasAnyPermission = useCallback((permissions: string[]) => permissions.some(hasPermission), [hasPermission]);
    const value = useMemo(() => ({profile, authorization: profile ? "staff-session" : null,
        ready, isAuthenticated: !!profile, login, refresh, logout, hasPermission, hasAnyPermission}),
        [profile, ready, login, refresh, logout, hasPermission, hasAnyPermission]);
    return <AdminAuthContext.Provider value={value}>{children}</AdminAuthContext.Provider>;
}
export function useAdminAuth() {
    const value = useContext(AdminAuthContext);
    if (!value) throw new Error("useAdminAuth must be used inside AdminAuthProvider.");
    return value;
}
