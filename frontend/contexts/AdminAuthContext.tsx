"use client";

import {
    createContext,
    useCallback,
    useContext,
    useMemo,
    useSyncExternalStore,
    type ReactNode
} from "react";

import {
    authenticateAdmin
} from "@/services/adminApi";

import type {
    AdminProfile,
    AdminSession
} from "@/types/admin";


const STORAGE_KEY =
    "gokul-admin-session";


const SESSION_CHANGE_EVENT =
    "gokul-admin-session-change";


interface AdminAuthContextValue {

    profile: AdminProfile | null;

    authorization: string | null;

    ready: boolean;

    isAuthenticated: boolean;

    login: (
        username: string,
        password: string
    ) => Promise<void>;

    logout: () => void;

    hasPermission: (
        permission: string
    ) => boolean;

    hasAnyPermission: (
        permissions: string[]
    ) => boolean;
}


const AdminAuthContext =
    createContext<
        AdminAuthContextValue | undefined
    >(undefined);


/*
 * =========================================================
 * SESSION STORAGE HELPERS
 * =========================================================
 */

function getSessionSnapshot():
    string | null {

    if (
        typeof window
        === "undefined"
    ) {

        return null;
    }


    return sessionStorage
        .getItem(
            STORAGE_KEY
        );
}


function getServerSessionSnapshot():
    string | null {

    return null;
}


function subscribeToSession(
    callback: () => void
) {

    function handleStorage(
        event: StorageEvent
    ) {

        if (
            event.storageArea
            === sessionStorage
            &&
            event.key
            === STORAGE_KEY
        ) {

            callback();
        }
    }


    function handleLocalSessionChange() {

        callback();
    }


    window.addEventListener(
        "storage",
        handleStorage
    );


    window.addEventListener(
        SESSION_CHANGE_EVENT,
        handleLocalSessionChange
    );


    return () => {

        window.removeEventListener(
            "storage",
            handleStorage
        );


        window.removeEventListener(
            SESSION_CHANGE_EVENT,
            handleLocalSessionChange
        );
    };
}


function notifySessionChanged() {

    window.dispatchEvent(
        new Event(
            SESSION_CHANGE_EVENT
        )
    );
}


/*
 * =========================================================
 * HYDRATION READINESS
 * =========================================================
 *
 * The server must render ready=false.
 *
 * During hydration React initially uses the same server
 * snapshot, preventing a server/client markup mismatch.
 *
 * Once the component is mounted in the browser,
 * getClientReadySnapshot() becomes authoritative and
 * ready becomes true.
 *
 * This avoids:
 *
 * server  -> Loading admin portal
 * client  -> Admin shell immediately
 *
 * which can otherwise produce hydration inconsistencies.
 */

function subscribeToReady() {

    return () => {

        /*
         * Hydration readiness does not require an external
         * event subscription.
         */
    };
}


function getClientReadySnapshot() {

    return true;
}


function getServerReadySnapshot() {

    return false;
}


/*
 * =========================================================
 * PROVIDER
 * =========================================================
 */

export function AdminAuthProvider({
    children
}: {
    children: ReactNode;
}) {

    /*
     * sessionStorage is an external browser store.
     *
     * useSyncExternalStore keeps React synchronized with it
     * without calling setState inside useEffect.
     */
    const rawSession =
        useSyncExternalStore(
            subscribeToSession,
            getSessionSnapshot,
            getServerSessionSnapshot
        );


    /*
     * Hydration-safe browser readiness.
     *
     * Server render:
     * ready = false
     *
     * Initial hydration:
     * ready = false
     *
     * Browser after hydration:
     * ready = true
     */
    const ready =
        useSyncExternalStore(
            subscribeToReady,
            getClientReadySnapshot,
            getServerReadySnapshot
        );


    /*
     * Convert the stored JSON into our typed session.
     */
    const session =
        useMemo<
            AdminSession | null
        >(
            () => {

                if (!rawSession) {

                    return null;
                }


                try {

                    const parsed =
                        JSON.parse(
                            rawSession
                        ) as AdminSession;


                    if (
                        !parsed.authorization
                        ||
                        !parsed.profile
                    ) {

                        return null;
                    }


                    return parsed;

                } catch {

                    return null;
                }
            },
            [
                rawSession
            ]
        );


    /*
     * =========================================================
     * LOGIN
     * =========================================================
     */

    const login =
        useCallback(
            async (
                username: string,
                password: string
            ) => {

                const authenticated =
                    await authenticateAdmin(
                        username,
                        password
                    );


                const nextSession:
                    AdminSession = {

                    authorization:
                        authenticated.authorization,

                    profile:
                        authenticated.profile
                };


                sessionStorage
                    .setItem(
                        STORAGE_KEY,
                        JSON.stringify(
                            nextSession
                        )
                    );


                notifySessionChanged();
            },
            []
        );


    /*
     * =========================================================
     * LOGOUT
     * =========================================================
     */

    const logout =
        useCallback(
            () => {

                sessionStorage
                    .removeItem(
                        STORAGE_KEY
                    );


                notifySessionChanged();
            },
            []
        );


    /*
     * =========================================================
     * PERMISSION CHECK
     * =========================================================
     */

    const hasPermission =
        useCallback(
            (
                permission: string
            ) => {

                if (!session) {

                    return false;
                }


                /*
                 * OWNER_ADMIN currently receives portal-wide
                 * frontend access.
                 */
                if (
                    session.profile.roleName
                    === "OWNER_ADMIN"
                ) {

                    return true;
                }


                return session
                    .profile
                    .permissions
                    .includes(
                        permission
                    );
            },
            [
                session
            ]
        );


    const hasAnyPermission =
        useCallback(
            (
                permissions: string[]
            ) => {

                return permissions
                    .some(
                        permission =>
                            hasPermission(
                                permission
                            )
                    );
            },
            [
                hasPermission
            ]
        );


    /*
     * =========================================================
     * CONTEXT VALUE
     * =========================================================
     */

    const value =
        useMemo<
            AdminAuthContextValue
        >(
            () => ({

                profile:
                    session?.profile
                    ?? null,

                authorization:
                    session?.authorization
                    ?? null,

                ready,

                isAuthenticated:
                    session !== null,

                login,

                logout,

                hasPermission,

                hasAnyPermission
            }),
            [
                session,
                ready,
                login,
                logout,
                hasPermission,
                hasAnyPermission
            ]
        );


    return (
        <AdminAuthContext.Provider
            value={value}
        >
            {children}
        </AdminAuthContext.Provider>
    );
}


/*
 * =========================================================
 * HOOK
 * =========================================================
 */

export function useAdminAuth() {

    const context =
        useContext(
            AdminAuthContext
        );


    if (!context) {

        throw new Error(
            "useAdminAuth must be used inside AdminAuthProvider."
        );
    }


    return context;
}