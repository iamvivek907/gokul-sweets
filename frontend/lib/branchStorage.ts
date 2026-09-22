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