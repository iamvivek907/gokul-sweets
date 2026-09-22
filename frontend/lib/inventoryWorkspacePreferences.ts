export interface InventoryWorkspacePreferences {
    branchId: number | null;
    serviceDate: string;
    automationFromDate?: string;
    automationThroughDate?: string;
}

const STORAGE_KEY = "gokul-admin-inventory-workspace";

export function readInventoryWorkspacePreferences():
    Partial<InventoryWorkspacePreferences> {
    if (typeof window === "undefined") return {};

    try {
        const raw = window.localStorage.getItem(STORAGE_KEY);
        if (!raw) return {};

        const parsed = JSON.parse(raw) as Partial<InventoryWorkspacePreferences>;
        return {
            branchId: typeof parsed.branchId === "number" ? parsed.branchId : null,
            serviceDate: typeof parsed.serviceDate === "string" ? parsed.serviceDate : undefined,
            automationFromDate: typeof parsed.automationFromDate === "string"
                ? parsed.automationFromDate
                : undefined,
            automationThroughDate: typeof parsed.automationThroughDate === "string"
                ? parsed.automationThroughDate
                : undefined
        };
    } catch {
        return {};
    }
}

export function updateInventoryWorkspacePreferences(
    update: Partial<InventoryWorkspacePreferences>
): void {
    if (typeof window === "undefined") return;

    const current = readInventoryWorkspacePreferences();
    window.localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({...current, ...update})
    );
}

export function selectRememberedBranch<T extends {id: number}>(
    allowedBranches: T[],
    currentBranchId: number | null
): number | null {
    if (currentBranchId !== null
        && allowedBranches.some(branch => branch.id === currentBranchId)) {
        return currentBranchId;
    }

    const remembered = readInventoryWorkspacePreferences().branchId;
    if (typeof remembered === "number"
        && allowedBranches.some(branch => branch.id === remembered)) {
        return remembered;
    }

    return allowedBranches[0]?.id ?? null;
}
