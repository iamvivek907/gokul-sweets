/** Keep each staff member's last explicitly chosen branch across Admin pages and reloads. */
function key(staffId: number) {
    return `gokul-admin-branch:${staffId}`;
}

export function preferredAdminBranchId(staffId: number, branches: Array<{id: number}>, current?: number | null): number | null {
    if (current != null && branches.some(branch => branch.id === current)) return current;
    try {
        const stored = Number(localStorage.getItem(key(staffId)));
        if (stored > 0 && branches.some(branch => branch.id === stored)) return stored;
    } catch { /* Storage can be disabled. */ }
    return branches[0]?.id ?? null;
}

/** Report filters allow an explicit All branches choice. */
export function preferredAdminReportBranchId(staffId: number, branches: Array<{id: number}>): number | null {
    try {
        const value = localStorage.getItem(key(staffId));
        if (value === "all") return null;
        const stored = Number(value);
        if (value && stored > 0 && branches.some(branch => branch.id === stored)) return stored;
    } catch { /* Storage can be disabled. */ }
    return null;
}

export function rememberAdminBranchId(staffId: number, branchId: number | null) {
    try {localStorage.setItem(key(staffId), branchId === null ? "all" : String(branchId));}
    catch { /* The in-memory selection still works. */ }
}
