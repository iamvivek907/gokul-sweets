"use client";

import {createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode} from "react";
import {authenticateAdmin, fetchAdminProfile, invalidateAdminSessionCheck, logoutAdmin, staffCsrfRejectedEvent, staffSessionCheckEvent} from "@/services/adminApi";
import {readStaffAlertDevice} from "@/services/staffAlertsApi";
import {readOrderPushBinding, clearOrderPushBinding} from "@/lib/orderPushBinding";
import type {AdminProfile} from "@/types/admin";

interface AdminAuthContextValue {
    profile: AdminProfile | null;
    authorization: string | null;
    ready: boolean;
    verificationFailed: boolean;
    isAuthenticated: boolean;
    login: (username: string, password: string, code?: string) => Promise<void>;
    refresh: () => Promise<void>;
    logout: () => Promise<void>;
    hasPermission: (permission: string) => boolean;
    hasAnyPermission: (permissions: string[]) => boolean;
}

const AdminAuthContext = createContext<AdminAuthContextValue | undefined>(undefined);
const changeEvent = "gokul-admin-session-change";

function broadcast() {
    window.dispatchEvent(new Event(changeEvent));
    try {
        const channel = new BroadcastChannel(changeEvent);
        channel.postMessage("changed");
        channel.close();
    } catch { /* PWA private mode */ }
}

export function AdminAuthProvider({children}: {children: ReactNode}) {
    const [profile, setProfile] = useState<AdminProfile | null>(null);
    const [ready, setReady] = useState(false);
    const [verificationFailed, setVerificationFailed] = useState(false);
    const pending = useRef<{controller: AbortController; done: Promise<void>} | null>(null);
    const changingSession = useRef(false);
    const signOutRequest = useRef<AbortController | null>(null);
    const recheckSession = useRef(false);

    const cancelCheck = useCallback(() => {
        pending.current?.controller.abort();
        pending.current = null;
    }, []);

    const refresh = useCallback(async () => {
        if (changingSession.current) return;
        if (pending.current) return pending.current.done;
        const controller = new AbortController();
        const done = fetchAdminProfile(controller.signal).then(next => {
            if (controller.signal.aborted) return;
            setProfile(current => JSON.stringify(current) === JSON.stringify(next) ? current : next);
            setVerificationFailed(false);
            setReady(true);
        }).catch(() => {
            if (!controller.signal.aborted) setVerificationFailed(true);
        }).finally(() => {
            if (pending.current?.controller === controller) pending.current = null;
        });
        pending.current = {controller, done};
        return done;
    }, []);

    useEffect(() => {
        try {sessionStorage.removeItem("gokul-admin-session");} catch { /* Storage disabled */ }
        const check = () => {void refresh();};
        const checkChangedSession = () => {
            if (changingSession.current) {
                recheckSession.current = true;
                return;
            }
            invalidateAdminSessionCheck();
            cancelCheck();
            check();
        };
        check();
        let channel: BroadcastChannel | null = null;
        try {
            channel = new BroadcastChannel(changeEvent);
            channel.onmessage = checkChangedSession;
        } catch { /* PWA private mode */ }
        window.addEventListener(staffSessionCheckEvent, check);
        window.addEventListener(staffCsrfRejectedEvent, checkChangedSession);
        window.addEventListener("focus", check);
        window.addEventListener("online", check);
        const visible = () => {if (document.visibilityState === "visible") check();};
        document.addEventListener("visibilitychange", visible);
        const timer = window.setInterval(visible, 30_000);
        return () => {
            cancelCheck();
            signOutRequest.current?.abort();
            channel?.close();
            window.removeEventListener(staffSessionCheckEvent, check);
            window.removeEventListener(staffCsrfRejectedEvent, checkChangedSession);
            window.removeEventListener("focus", check);
            window.removeEventListener("online", check);
            document.removeEventListener("visibilitychange", visible);
            window.clearInterval(timer);
        };
    }, [cancelCheck, refresh]);

    const finishSessionChange = useCallback(() => {
        changingSession.current = false;
        if (recheckSession.current) {
            recheckSession.current = false;
            invalidateAdminSessionCheck();
            void refresh();
        }
    }, [refresh]);

    const login = useCallback(async (username: string, password: string, code?: string) => {
        if (changingSession.current) throw new Error("A sign-in change is already in progress.");
        changingSession.current = true;
        cancelCheck();
        try {
            setProfile(await authenticateAdmin(username, password, code));
            setVerificationFailed(false);
            setReady(true);
            broadcast();
        } finally {
            finishSessionChange();
        }
    }, [cancelCheck, finishSessionChange]);

    const logout = useCallback(async () => {
        if (changingSession.current) throw new Error("A sign-in change is already in progress.");
        changingSession.current = true;
        cancelCheck();
        const controller = new AbortController();
        signOutRequest.current = controller;
        try {
            const bound = await readOrderPushBinding("staff");
            const device = profile ? readStaffAlertDevice(profile.staffId) : null;
            const verifiedProfile = await logoutAdmin(controller.signal,
                device ?? (bound?.owner === String(profile?.staffId) ? bound?.registrationId : null));
            if (controller.signal.aborted) return;
            setProfile(verifiedProfile);
            setVerificationFailed(false);
            setReady(true);
            if (verifiedProfile) {
                throw new Error("Sign-out could not be confirmed. Check your connection and try again.");
            }
            await clearOrderPushBinding("staff", profile ? String(profile.staffId) : undefined);
            broadcast();
        } finally {
            signOutRequest.current = null;
            if (!controller.signal.aborted) finishSessionChange();
        }
    }, [cancelCheck, finishSessionChange, profile]);

    const hasPermission = useCallback((permission: string) => !!profile &&
        (profile.roleName === "OWNER_ADMIN" || profile.permissions.includes(permission)), [profile]);
    const hasAnyPermission = useCallback((permissions: string[]) => permissions.some(hasPermission), [hasPermission]);
    const value = useMemo(() => ({profile, authorization: profile ? `staff-session:${profile.staffId}` : null,
        ready, verificationFailed, isAuthenticated: !!profile, login, refresh, logout, hasPermission, hasAnyPermission}),
        [profile, ready, verificationFailed, login, refresh, logout, hasPermission, hasAnyPermission]);
    return <AdminAuthContext.Provider value={value}>{children}</AdminAuthContext.Provider>;
}

export function useAdminAuth() {
    const value = useContext(AdminAuthContext);
    if (!value) throw new Error("useAdminAuth must be used inside AdminAuthProvider.");
    return value;
}
