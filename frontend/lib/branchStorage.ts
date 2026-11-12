import type {
    Branch
} from "@/types/branch";


export const BRANCH_STORAGE_KEY =
    "gokul-selected-branch";


export const BRANCH_CHANGE_EVENT =
    "gokul-branch-change";


export function getStoredBranchSnapshot():
    string {

    return (
        localStorage.getItem(
            BRANCH_STORAGE_KEY
        )
        ?? ""
    );
}


/** Return the live selection only when it still matches the branch shown by the control. */
export function getMatchingStoredBranchSnapshot(branchId: number): string | null {
    const snapshot = getStoredBranchSnapshot();
    return parseStoredBranch(snapshot)?.id === branchId ? snapshot : null;
}


export function getServerBranchSnapshot():
    string {

    return "";
}


export function subscribeToBranch(
    callback: () => void
): () => void {

    window.addEventListener(
        "storage",
        callback
    );

    window.addEventListener(
        BRANCH_CHANGE_EVENT,
        callback
    );


    return () => {

        window.removeEventListener(
            "storage",
            callback
        );

        window.removeEventListener(
            BRANCH_CHANGE_EVENT,
            callback
        );
    };
}


export function saveBranch(
    branch: Branch
): void {

    localStorage.setItem(
        BRANCH_STORAGE_KEY,
        JSON.stringify(
            branch
        )
    );


    window.dispatchEvent(
        new Event(
            BRANCH_CHANGE_EVENT
        )
    );
}


export function clearStoredBranch():
    void {

    localStorage.removeItem(
        BRANCH_STORAGE_KEY
    );


    window.dispatchEvent(
        new Event(
            BRANCH_CHANGE_EVENT
        )
    );
}


export function parseStoredBranch(
    value: string
): Branch | null {

    if (!value) {
        return null;
    }


    try {

        return JSON.parse(
            value
        ) as Branch;

    } catch {

        return null;
    }
}