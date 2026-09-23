"use client";

import {useAdminAuth} from "@/contexts/AdminAuthContext";
import AdminNavigation from "./AdminNavigation";

export default function AdminSidebar() {
    const {profile} = useAdminAuth();
    return <aside className="hidden w-60 shrink-0 border-r border-[#eadfd6] bg-white lg:block">
        <div className="sticky top-0 flex h-screen flex-col px-3 py-5">
            <div className="mb-5 px-3"><p className="text-xs font-semibold uppercase tracking-widest text-[#c88a20]">Gokul Sweets</p>
                <h1 className="mt-1 text-xl font-bold text-[#7a1625]">Admin Portal</h1></div>
            <div className="min-h-0 flex-1 overflow-y-auto"><AdminNavigation /></div>
            <div className="mt-4 border-t border-[#eadfd6] px-3 pt-3">
                <p className="truncate text-sm font-bold">{profile?.fullName}</p><p className="text-xs text-[#756763]">{profile?.roleName}</p>
            </div>
        </div>
    </aside>;
}
