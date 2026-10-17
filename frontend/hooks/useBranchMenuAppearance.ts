"use client";
import {useEffect, useState} from "react";
import {apiClient} from "@/services/apiClient";
import type {AppearanceConfig} from "@/components/admin/WorkspaceAppearance";

/** One published appearance read shared by the desktop and phone menu placements. */
export function useBranchMenuAppearance(branchId: number | null) {
    const [result, setResult] = useState<{branchId: number; config: AppearanceConfig} | null>(null);
    useEffect(() => {
        if (branchId === null) return;
        const controller = new AbortController();
        apiClient<AppearanceConfig>(`/api/menu/appearance?branchId=${branchId}`, {
            signal: controller.signal, cacheMode: "no-store",
        }).then(config => {
            if (!controller.signal.aborted && config && Array.isArray(config.banners) && Array.isArray(config.categories)) {
                setResult({branchId, config});
            }
        }).catch(() => { /* Optional marketing never blocks menu ordering. */ });
        return () => controller.abort();
    }, [branchId]);
    return result?.branchId === branchId ? result.config : null;
}
