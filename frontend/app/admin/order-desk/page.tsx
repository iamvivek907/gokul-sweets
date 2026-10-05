"use client";
import { useEffect, useState } from "react";
import { useAdminAuth } from "@/contexts/AdminAuthContext";
import { useStorefrontFeatures } from "@/hooks/useStorefrontFeatures";
import { getActiveBranches } from "@/services/branchApi";
import { preferredAdminBranchId, rememberAdminBranchId } from "@/lib/adminBranchSelection";
import { T } from "@/lib/language";
import StaffOrderDesk from "@/components/admin/StaffOrderDesk";
import type { Branch } from "@/types/branch";
export default function OrderDeskPage() {
    const { profile, authorization, ready, hasPermission } = useAdminAuth();
    const features = useStorefrontFeatures();
    const [branches, setBranches] = useState<Branch[]>([]), [branchId, setBranchId] = useState<number | null>(null), [error, setError] = useState(""), [retry, setRetry] = useState(0), [loaded, setLoaded] = useState(false);
    useEffect(() => {
        if (!profile || !hasPermission("ORDER_VIEW"))
            return;
        let mounted = true;
        const controller = new AbortController(), timeout = window.setTimeout(() => controller.abort(), 15000);
        void getActiveBranches(controller.signal).then(all => {
            const allowed = profile.roleName === "OWNER_ADMIN" ? all : all.filter(b => profile.branchIds.includes(b.id));
            if (controller.signal.aborted)
                return;
            setBranches(allowed);
            setBranchId(preferredAdminBranchId(profile.staffId, allowed));
            setError("");
            setLoaded(true);
        }).catch(() => { if (!mounted)
            return; setError("Could not load branches. Check your connection and retry."); setLoaded(true); }).finally(() => clearTimeout(timeout));
        return () => { mounted = false; controller.abort(); clearTimeout(timeout); };
    }, [profile, hasPermission, retry]);
    if (!ready)
        return <p role="status"><T text="Loading staff session…"/></p>;
    if (!authorization || !profile || !hasPermission("ORDER_VIEW"))
        return <p><T text="Order desk access is unavailable."/></p>;
    if (features?.adminPreparationBoard === false)
        return <p><T text="Order desk is not enabled for this environment."/></p>;
    return <main className="mx-auto max-w-7xl p-3 sm:p-5"><div className="mb-4 flex flex-wrap items-center justify-between gap-3"><h1 className="text-2xl font-bold text-[#7a1625]"><T text="Order desk"/></h1><label className="flex items-center gap-2"><T text="Branch"/><select className="min-h-11 max-w-52 rounded-xl border border-[#dacfc6] bg-white px-3" value={branchId ?? ""} onChange={e => { const id = Number(e.target.value); setBranchId(id); rememberAdminBranchId(profile.staffId, id); }}>{branches.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}</select></label></div>
        {error ? <p role="alert">{error} <button className="min-h-11 rounded border px-3" onClick={() => setRetry(n => n + 1)}><T text="Retry"/></button></p> : !loaded ? <p role="status"><T text="Loading branches…"/></p> : !branchId ? <p><T text="No branch is assigned to this staff account."/></p> : <StaffOrderDesk key={`${profile.staffId}:${branchId}`} branchId={branchId} authorization={authorization} canStart={hasPermission("ORDER_START_PREPARATION")} canReady={hasPermission("ORDER_MARK_READY")} canPickup={hasPermission("ORDER_MARK_PICKED_UP")} canPlan={["OWNER_ADMIN", "MANAGER"].includes(profile.roleName) && hasPermission("REPORT_VIEW")} canConfigure={["OWNER_ADMIN", "MANAGER"].includes(profile.roleName) && hasPermission("MENU_MANAGE")} canReschedule={["OWNER_ADMIN", "MANAGER"].includes(profile.roleName) && hasPermission("ORDER_CANCEL")}/>}
    </main>;
}
