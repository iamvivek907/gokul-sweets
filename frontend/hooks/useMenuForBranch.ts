"use client";

import {
    useCallback,
    useEffect,
    useState,
} from "react";

import { apiClient } from "@/services/apiClient";
import type { MenuCategory } from "@/types/menu";

interface UseMenuForBranchResult {
    categories: MenuCategory[];
    loading: boolean;
    error: string | null;
    refresh: () => Promise<void>;
}

export function useMenuForBranch(
    branchId: number | null
): UseMenuForBranchResult {
    const [categories, setCategories] =
        useState<MenuCategory[]>([]);

    const [loading, setLoading] =
        useState(false);

    const [error, setError] =
        useState<string | null>(null);


    /*
     * Manually reload the menu.
     *
     * Used by the page Refresh button and after
     * an image is removed.
     */
    const refresh = useCallback(async () => {
        if (!branchId) {
            return;
        }

        try {
            setLoading(true);
            setError(null);

            const result =
                await apiClient<MenuCategory[]>(
                    `/api/menu?branchId=${branchId}`
                );

            setCategories(result);
        } catch (err) {
            console.error(err);

            setError(
                err instanceof Error
                    ? err.message
                    : "Unable to load menu."
            );
        } finally {
            setLoading(false);
        }
    }, [branchId]);


    /*
     * Load menu when the selected branch changes.
     *
     * The asynchronous request is started from the effect.
     * State updates happen after the request resolves,
     * rather than synchronously at the beginning of the effect.
     */
    useEffect(() => {
        if (!branchId) {
            return;
        }

        let cancelled = false;

        async function fetchMenu() {
            try {
                setLoading(true);
                setError(null);

                const result =
                    await apiClient<MenuCategory[]>(
                        `/api/menu?branchId=${branchId}`
                    );

                if (!cancelled) {
                    setCategories(result);
                }
            } catch (err) {
                if (!cancelled) {
                    console.error(err);

                    setError(
                        err instanceof Error
                            ? err.message
                            : "Unable to load menu."
                    );
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        }

        void fetchMenu();

        return () => {
            cancelled = true;
        };
    }, [branchId]);


    /*
     * When there is no branch selected, the page should
     * behave as if there is no menu loaded.
     *
     * We derive this during render instead of calling
     * setState from the effect.
     */
    return {
        categories: branchId
            ? categories
            : [],
        loading: branchId
            ? loading
            : false,
        error: branchId
            ? error
            : null,
        refresh,
    };
}