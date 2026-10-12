import type {Metadata} from "next";
export const metadata: Metadata = {title: {absolute: "Admin Portal | Gokul Sweets"}};
import KitchenAlarm from "@/components/admin/KitchenAlarm";
import type {
    ReactNode
} from "react";

import {
    AdminAuthProvider
} from "@/contexts/AdminAuthContext";

import AdminShell
    from "@/components/admin/AdminShell";


export default function AdminLayout({
    children
}: {
    children: ReactNode;
}) {

    return (
        <AdminAuthProvider>

            <AdminShell>

                <KitchenAlarm />
                {children}

            </AdminShell>

        </AdminAuthProvider>
    );
}