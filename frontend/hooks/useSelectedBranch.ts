"use client";

import {
    useMemo,
    useSyncExternalStore
} from "react";

import type {
    Branch
} from "@/types/branch";

import {
    clearStoredBranch,
    getServerBranchSnapshot,
    getStoredBranchSnapshot,
    parseStoredBranch,
    saveBranch,
    subscribeToBranch
} from "@/lib/branchStorage";


interface UseSelectedBranchResult {

    branch:
        Branch | null;

    selectBranch:
        (branch: Branch) => void;

    clearBranch:
        () => void;
}


export function useSelectedBranch():
    UseSelectedBranchResult {

    const storedBranch =
        useSyncExternalStore(
            subscribeToBranch,
            getStoredBranchSnapshot,
            getServerBranchSnapshot
        );


    const branch =
        useMemo(
            () =>
                parseStoredBranch(
                    storedBranch
                ),
            [
                storedBranch
            ]
        );


    return {
        branch,

        selectBranch:
            selectedBranch =>
                saveBranch(
                    selectedBranch
                ),

        clearBranch:
            () =>
                clearStoredBranch()
    };
}