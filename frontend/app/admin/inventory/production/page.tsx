"use client";

import {
    useEffect,
    useMemo,
    useState
} from "react";

import Link from "next/link";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    getProductionPlan,
    recordProducedStock,
    recordStockAdjustment,
    recordWastage
} from "@/services/adminProductionApi";

import type {
    ProductionAction,
    ProductionPlan,
    ProductionPlanItem,
    ProductionPriority
} from "@/types/adminProduction";

import {
    readInventoryWorkspacePreferences,
    selectRememberedBranch,
    updateInventoryWorkspacePreferences
} from "@/lib/inventoryWorkspacePreferences";

import {
    apiQuantityToInput,
    formatInventoryQuantity,
    inputQuantityToApi,
    inventoryInputUnit
} from "@/lib/inventoryUnits";
import InventoryHelp from "@/components/admin/inventory/InventoryHelp";

interface Branch {
    id: number;
    code: string;
    name: string;
    address: string;
    active: boolean;
}

interface ActionState {
    action: ProductionAction;
    item: ProductionPlanItem;
}

const API_BASE =
    process.env.NEXT_PUBLIC_API_BASE_URL
    ?? "http://localhost:8080";

const priorities: Array<ProductionPriority | "ALL"> = [
    "ALL",
    "CRITICAL",
    "NEEDS_PRODUCTION",
    "FORECAST_TOP_UP",
    "READY",
    "DELAYED",
    "DISCREPANCY"
];

function todayInIndia(): string {
    return new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Kolkata",
        year: "numeric",
        month: "2-digit",
        day: "2-digit"
    }).format(new Date());
}

function priorityLabel(priority: ProductionPriority): string {
    return priority.split("_").map(word =>
        word.charAt(0) + word.slice(1).toLowerCase()
    ).join(" ");
}

function priorityClasses(priority: ProductionPriority): string {
    switch (priority) {
        case "CRITICAL":
        case "DISCREPANCY":
            return "border-red-200 bg-red-50 text-red-700";
        case "DELAYED":
            return "border-orange-200 bg-orange-50 text-orange-700";
        case "NEEDS_PRODUCTION":
            return "border-amber-200 bg-amber-50 text-amber-800";
        case "FORECAST_TOP_UP":
            return "border-blue-200 bg-blue-50 text-blue-700";
        default:
            return "border-emerald-200 bg-emerald-50 text-emerald-700";
    }
}

function actionTitle(action: ProductionAction): string {
    if (action === "PRODUCED") return "Record prepared stock";
    if (action === "WASTAGE") return "Record wastage";
    return "Correct physical stock";
}

export default function AdminProductionPage() {
    const {
        profile,
        authorization,
        hasPermission
    } = useAdminAuth();

    const [branches, setBranches] = useState<Branch[]>([]);
    const [branchId, setBranchId] = useState<number | null>(null);
    const [serviceDate, setServiceDate] = useState(todayInIndia);
    const [plan, setPlan] = useState<ProductionPlan | null>(null);
    const [search, setSearch] = useState("");
    const [priority, setPriority] =
        useState<ProductionPriority | "ALL">("ALL");
    const [category, setCategory] = useState("ALL");
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [reloadKey, setReloadKey] = useState(0);
    const [actionState, setActionState] = useState<ActionState | null>(null);
    const [quantityValue, setQuantityValue] = useState("");
    const [reason, setReason] = useState("");

    const canManage = hasPermission("INVENTORY_MANAGE");

    useEffect(() => {
        if (!profile) return;
        const controller = new AbortController();

        fetch(`${API_BASE}/api/branches`, {
            signal: controller.signal,
            cache: "no-store"
        })
            .then(response => {
                if (!response.ok) throw new Error("Unable to load branches.");
                return response.json() as Promise<Branch[]>;
            })
            .then(result => {
                const active = result.filter(item => item.active);
                const allowed = profile.roleName === "OWNER_ADMIN"
                    ? active
                    : active.filter(item => profile.branchIds.includes(item.id));
                setBranches(allowed);
                setBranchId(current => {
                    const next = selectRememberedBranch(allowed, current);
                    updateInventoryWorkspacePreferences({branchId: next});
                    return next;
                });
                const rememberedDate = readInventoryWorkspacePreferences().serviceDate;
                if (rememberedDate) setServiceDate(rememberedDate);
            })
            .catch(exception => {
                if (controller.signal.aborted) return;
                setError(exception instanceof Error
                    ? exception.message
                    : "Unable to load branches.");
                setLoading(false);
            });

        return () => controller.abort();
    }, [profile]);

    useEffect(() => {
        if (!branchId || !authorization) return;
        const controller = new AbortController();

        const timer = window.setTimeout(() => {
            setLoading(true);
            getProductionPlan(
                branchId,
                serviceDate,
                authorization,
                controller.signal
            )
                .then(result => {
                    setPlan(result);
                    setError(null);
                    setLoading(false);
                })
                .catch(exception => {
                    if (controller.signal.aborted) return;
                    setError(exception instanceof Error
                        ? exception.message
                        : "Unable to load the production plan.");
                    setLoading(false);
                });
        }, 100);

        return () => {
            window.clearTimeout(timer);
            controller.abort();
        };
    }, [authorization, branchId, reloadKey, serviceDate]);

    const categories = useMemo(() => {
        return [...new Set(plan?.items.map(item => item.categoryName) ?? [])]
            .sort((first, second) => first.localeCompare(second));
    }, [plan]);

    const filteredItems = useMemo(() => {
        const term = search.trim().toLowerCase();
        return plan?.items.filter(item => {
            const matchesSearch = !term
                || item.productName.toLowerCase().includes(term)
                || item.productCode.toLowerCase().includes(term);
            const matchesPriority = priority === "ALL"
                || item.priority === priority;
            const matchesCategory = category === "ALL"
                || item.categoryName === category;
            return matchesSearch && matchesPriority && matchesCategory;
        }) ?? [];
    }, [category, plan, priority, search]);

    function openAction(
        action: ProductionAction,
        item: ProductionPlanItem
    ) {
        const suggested = item.minimumToPrepareQuantity > 0
            ? item.minimumToPrepareQuantity
            : item.suggestedToPrepareQuantity;
        setActionState({action, item});
        setQuantityValue(action === "PRODUCED" && suggested > 0
            ? apiQuantityToInput(suggested, item.inventoryUnit)
            : "");
        setReason("");
        setError(null);
    }

    async function submitAction() {
        if (!actionState || !authorization || !branchId) return;
        const entered = inputQuantityToApi(
            quantityValue,
            actionState.item.inventoryUnit
        );
        const needsPositive = actionState.action !== "ADJUSTMENT";

        if (!Number.isFinite(entered)
                || entered === 0
                || (needsPositive && entered < 0)) {
            setError(actionState.action === "ADJUSTMENT"
                ? "Enter a non-zero correction. Use a minus sign to reduce stock."
                : "Enter a quantity greater than zero.");
            return;
        }
        if (actionState.item.inventoryUnit === "PIECE"
                && !Number.isInteger(entered)) {
            setError("Piece quantities must be whole numbers.");
            return;
        }
        if (actionState.action !== "PRODUCED" && !reason.trim()) {
            setError("Please enter a reason for this stock movement.");
            return;
        }

        setSaving(true);
        setError(null);
        try {
            if (actionState.action === "PRODUCED") {
                await recordProducedStock(
                    branchId,
                    actionState.item.branchProductId,
                    serviceDate,
                    entered,
                    reason.trim() || null,
                    authorization
                );
            } else if (actionState.action === "WASTAGE") {
                await recordWastage(
                    branchId,
                    actionState.item.branchProductId,
                    serviceDate,
                    entered,
                    reason.trim(),
                    authorization
                );
            } else {
                await recordStockAdjustment(
                    branchId,
                    actionState.item.branchProductId,
                    serviceDate,
                    entered,
                    reason.trim(),
                    authorization
                );
            }

            setNotice(`${actionTitle(actionState.action)} saved for ${actionState.item.productName}.`);
            setActionState(null);
            setReloadKey(value => value + 1);
        } catch (exception) {
            setError(exception instanceof Error
                ? exception.message
                : "Unable to save this stock movement.");
        } finally {
            setSaving(false);
        }
    }

    const summary = plan?.summary;

    return (
        <main className="min-w-0 flex-1 bg-[#fffaf3] px-4 pb-6 pt-12 sm:px-6 lg:px-8">
            <div className="mx-auto max-w-[1500px]">
                <section className="rounded-3xl border border-[#eadfd6] bg-white p-6 shadow-sm lg:p-8">
                    <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
                        <div>
                            <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#c88a20]">
                                Inventory operations
                            </p>
                            <h1 className="mt-2 text-3xl font-bold text-[#241715]">
                                Production &amp; Reconciliation
                            </h1>
                            <p className="mt-2 max-w-3xl text-sm leading-6 text-[#756763]">
                                Prepare against confirmed demand, compare it with physical stock,
                                and record every production, wastage, or correction movement.
                            </p>
                        </div>

                        <div className="flex flex-wrap items-center gap-3 self-start xl:self-auto">
                            <InventoryHelp context="PRODUCTION" />
                            <Link
                                href="/admin/inventory"
                                className="inline-flex min-h-11 items-center justify-center rounded-xl border border-[#eadfd6] bg-white px-5 text-sm font-bold text-[#7a1625] transition hover:border-[#c88a20] hover:bg-[#fff4e5]"
                            >
                                ← Inventory dashboard
                            </Link>

                            <Link
                                href="/admin/inventory/setup"
                                className="inline-flex min-h-11 items-center justify-center rounded-xl bg-[#7a1625] px-5 text-sm font-bold text-white transition hover:bg-[#5d0f1b]"
                            >
                                Inventory Setup
                            </Link>
                        </div>
                    </div>

                    <div className="mt-6 grid gap-4 md:grid-cols-3">
                        <label className="text-sm font-semibold text-[#241715]">
                            Branch
                            <select
                                value={branchId ?? ""}
                                onChange={event => {
                                    const next = Number(event.target.value);
                                    setBranchId(next);
                                    updateInventoryWorkspacePreferences({branchId: next});
                                }}
                                className="mt-2 min-h-11 w-full rounded-xl border border-[#eadfd6] bg-white px-3 outline-none focus:border-[#7a1625]"
                            >
                                {branches.map(branch => (
                                    <option key={branch.id} value={branch.id}>
                                        {branch.name}
                                    </option>
                                ))}
                            </select>
                        </label>

                        <label className="text-sm font-semibold text-[#241715]">
                            Production date
                            <input
                                type="date"
                                value={serviceDate}
                                onChange={event => {
                                    const next = event.target.value;
                                    setServiceDate(next);
                                    updateInventoryWorkspacePreferences({serviceDate: next});
                                }}
                                className="mt-2 min-h-11 w-full rounded-xl border border-[#eadfd6] bg-white px-3 outline-none focus:border-[#7a1625]"
                            />
                        </label>

                        <div className="flex items-end">
                            <button
                                type="button"
                                onClick={() => setReloadKey(value => value + 1)}
                                className="min-h-11 w-full rounded-xl bg-[#7a1625] px-4 text-sm font-bold text-white transition active:scale-[0.99]"
                            >
                                Refresh live plan
                            </button>
                        </div>
                    </div>
                </section>

                <section className="mt-6 grid grid-cols-2 gap-3 lg:grid-cols-4 xl:grid-cols-7">
                    <SummaryCard label="Products" value={summary?.totalItems ?? 0} />
                    <SummaryCard label="Critical" value={summary?.criticalItems ?? 0} tone="red" />
                    <SummaryCard label="Must prepare" value={summary?.needsProductionItems ?? 0} tone="amber" />
                    <SummaryCard label="Forecast top-up" value={summary?.forecastTopUpItems ?? 0} tone="blue" />
                    <SummaryCard label="Ready" value={summary?.readyItems ?? 0} tone="green" />
                    <SummaryCard label="Delayed" value={summary?.delayedItems ?? 0} tone="amber" />
                    <SummaryCard label="Discrepancy" value={summary?.discrepancyItems ?? 0} tone="red" />
                </section>

                <section className="mt-6 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-900">
                    <strong>Demand rule:</strong> confirmed paid quantities drive the minimum
                    production requirement. Temporary checkout holds are shown separately and
                    reserve online availability, but are not sent to production until confirmed.
                </section>

                {notice && (
                    <div className="mt-4 flex items-start justify-between gap-4 rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-medium text-emerald-800">
                        <span>{notice}</span>
                        <button type="button" onClick={() => setNotice(null)} aria-label="Dismiss">×</button>
                    </div>
                )}

                {error && (
                    <div className="mt-4 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm font-medium text-red-700">
                        {error}
                    </div>
                )}

                <section className="mt-6 rounded-3xl border border-[#eadfd6] bg-white p-4 shadow-sm sm:p-6">
                    <div className="grid gap-3 lg:grid-cols-[minmax(220px,1fr)_220px_220px]">
                        <input
                            type="search"
                            value={search}
                            onChange={event => setSearch(event.target.value)}
                            placeholder="Search product or code"
                            className="min-h-11 rounded-xl border border-[#eadfd6] px-4 text-sm outline-none focus:border-[#7a1625]"
                        />
                        <select
                            value={category}
                            onChange={event => setCategory(event.target.value)}
                            className="min-h-11 rounded-xl border border-[#eadfd6] bg-white px-3 text-sm outline-none focus:border-[#7a1625]"
                        >
                            <option value="ALL">All categories</option>
                            {categories.map(value => (
                                <option key={value} value={value}>{value}</option>
                            ))}
                        </select>
                        <select
                            value={priority}
                            onChange={event => setPriority(event.target.value as ProductionPriority | "ALL")}
                            className="min-h-11 rounded-xl border border-[#eadfd6] bg-white px-3 text-sm outline-none focus:border-[#7a1625]"
                        >
                            {priorities.map(value => (
                                <option key={value} value={value}>
                                    {value === "ALL" ? "All priorities" : priorityLabel(value)}
                                </option>
                            ))}
                        </select>
                    </div>

                    {loading ? (
                        <div className="mt-6 space-y-3">
                            {[1, 2, 3, 4].map(value => (
                                <div key={value} className="h-28 animate-pulse rounded-2xl bg-[#fff4e5]" />
                            ))}
                        </div>
                    ) : filteredItems.length === 0 ? (
                        <div className="mt-6 rounded-2xl border border-dashed border-[#d9c8bc] px-5 py-14 text-center">
                            <h2 className="text-lg font-bold text-[#241715]">No production rows found</h2>
                            <p className="mt-2 text-sm text-[#756763]">
                                Approve daily allocations in Inventory setup or change the filters.
                            </p>
                        </div>
                    ) : (
                        <div className="mt-6 space-y-4">
                            {filteredItems.map(item => (
                                <ProductionRow
                                    key={item.branchProductId}
                                    item={item}
                                    canManage={canManage}
                                    onAction={openAction}
                                />
                            ))}
                        </div>
                    )}
                </section>
            </div>

            {actionState && (
                <div
                    className="fixed inset-0 z-50 flex items-end justify-center bg-black/45 p-0 sm:items-center sm:p-6"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="movement-title"
                >
                    <div className="w-full max-w-lg rounded-t-3xl bg-white p-6 shadow-2xl sm:rounded-3xl">
                        <div className="flex items-start justify-between gap-4">
                            <div>
                                <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#c88a20]">
                                    Stock movement
                                </p>
                                <h2 id="movement-title" className="mt-1 text-xl font-bold text-[#241715]">
                                    {actionTitle(actionState.action)}
                                </h2>
                                <p className="mt-1 text-sm text-[#756763]">
                                    {actionState.item.productName} · {serviceDate}
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={() => setActionState(null)}
                                className="flex h-10 w-10 items-center justify-center rounded-full bg-[#fff4e5] text-xl text-[#7a1625]"
                                aria-label="Close"
                            >
                                ×
                            </button>
                        </div>

                        <div className="mt-5 rounded-2xl bg-[#fffaf3] p-4 text-sm text-[#756763]">
                            Physical on hand: <strong className="text-[#241715]">
                                {formatInventoryQuantity(actionState.item.physicalOnHandQuantity, actionState.item.inventoryUnit)}
                            </strong>
                        </div>

                        <label className="mt-5 block text-sm font-semibold text-[#241715]">
                            {actionState.action === "ADJUSTMENT"
                                ? "Quantity change"
                                : "Quantity"}
                            <input
                                type="number"
                                value={quantityValue}
                                onChange={event => setQuantityValue(event.target.value)}
                                min={actionState.action === "ADJUSTMENT" ? undefined : 0}
                                step={actionState.item.inventoryUnit === "GRAM" ? .05 : 1}
                                placeholder={actionState.action === "ADJUSTMENT" ? "Example: -2 or 5" : "Enter quantity"}
                                className="mt-2 min-h-12 w-full rounded-xl border border-[#eadfd6] px-4 outline-none focus:border-[#7a1625]"
                            />
                            <span className="mt-2 block text-xs font-normal text-[#756763]">
                                Enter {inventoryInputUnit(actionState.item.inventoryUnit)}.
                                {actionState.action === "ADJUSTMENT"
                                    ? " Use a negative value to reduce stock."
                                    : ""}
                            </span>
                        </label>

                        <label className="mt-4 block text-sm font-semibold text-[#241715]">
                            {actionState.action === "PRODUCED" ? "Note (optional)" : "Reason"}
                            <textarea
                                value={reason}
                                onChange={event => setReason(event.target.value)}
                                maxLength={500}
                                rows={3}
                                placeholder={actionState.action === "PRODUCED"
                                    ? "Batch or preparation note"
                                    : "Required for the audit trail"}
                                className="mt-2 w-full resize-none rounded-xl border border-[#eadfd6] p-4 outline-none focus:border-[#7a1625]"
                            />
                        </label>

                        <div className="mt-6 flex gap-3">
                            <button
                                type="button"
                                onClick={() => setActionState(null)}
                                disabled={saving}
                                className="min-h-12 flex-1 rounded-xl border border-[#eadfd6] font-bold text-[#756763]"
                            >
                                Cancel
                            </button>
                            <button
                                type="button"
                                onClick={submitAction}
                                disabled={saving}
                                className="min-h-12 flex-[1.5] rounded-xl bg-[#7a1625] px-4 font-bold text-white disabled:opacity-60"
                            >
                                {saving ? "Saving…" : "Save movement"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </main>
    );
}

function SummaryCard({
    label,
    value,
    tone = "neutral"
}: {
    label: string;
    value: number;
    tone?: "neutral" | "red" | "amber" | "blue" | "green";
}) {
    const colours = {
        neutral: "border-[#eadfd6] bg-white text-[#241715]",
        red: "border-red-200 bg-red-50 text-red-700",
        amber: "border-amber-200 bg-amber-50 text-amber-800",
        blue: "border-blue-200 bg-blue-50 text-blue-700",
        green: "border-emerald-200 bg-emerald-50 text-emerald-700"
    };
    return (
        <div className={`rounded-2xl border p-4 ${colours[tone]}`}>
            <p className="text-2xl font-bold">{value}</p>
            <p className="mt-1 text-xs font-semibold">{label}</p>
        </div>
    );
}

function ProductionRow({
    item,
    canManage,
    onAction
}: {
    item: ProductionPlanItem;
    canManage: boolean;
    onAction: (action: ProductionAction, item: ProductionPlanItem) => void;
}) {
    return (
        <article className="rounded-2xl border border-[#eadfd6] p-4 sm:p-5">
            <div className="flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
                <div className="min-w-0 xl:w-64">
                    <div className="flex flex-wrap items-center gap-2">
                        <span className={`rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide ${priorityClasses(item.priority)}`}>
                            {priorityLabel(item.priority)}
                        </span>
                        <span className="text-xs font-semibold text-[#c88a20]">
                            {item.categoryName}
                        </span>
                    </div>
                    <h2 className="mt-2 truncate text-lg font-bold text-[#241715]">
                        {item.productName}
                    </h2>
                    <p className="mt-1 text-xs text-[#756763]">
                        {item.productCode} · {item.inventoryUnit === "GRAM" ? "Weight" : "Pieces"}
                    </p>
                    {item.note && (
                        <p className="mt-3 rounded-lg bg-[#fffaf3] p-2 text-xs leading-5 text-[#756763]">
                            {item.note}
                        </p>
                    )}
                </div>

                <div className="grid flex-1 grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
                    <Metric label="Confirmed outstanding" value={formatInventoryQuantity(item.outstandingConfirmedQuantity, item.inventoryUnit)} strong />
                    <Metric label="Temporary holds" value={formatInventoryQuantity(item.heldQuantity, item.inventoryUnit)} />
                    <Metric label="Physical on hand" value={formatInventoryQuantity(item.physicalOnHandQuantity, item.inventoryUnit)} strong />
                    <Metric label="Must prepare" value={formatInventoryQuantity(item.minimumToPrepareQuantity, item.inventoryUnit)} alert={item.minimumToPrepareQuantity > 0} />
                    <Metric label="Suggested total" value={formatInventoryQuantity(item.suggestedToPrepareQuantity, item.inventoryUnit)} />
                    <Metric label="Wasted" value={formatInventoryQuantity(item.wastedQuantity, item.inventoryUnit)} />
                </div>

                {canManage && item.controlMode !== "SLOT_CAPACITY" && (
                    <div className="grid shrink-0 grid-cols-3 gap-2 xl:w-72">
                        <ActionButton label="Produced" onClick={() => onAction("PRODUCED", item)} />
                        <ActionButton label="Waste" onClick={() => onAction("WASTAGE", item)} secondary />
                        <ActionButton label="Correct" onClick={() => onAction("ADJUSTMENT", item)} secondary />
                    </div>
                )}
                {item.controlMode === "SLOT_CAPACITY" && (
                    <div className="shrink-0 rounded-xl border border-blue-200 bg-blue-50 p-3 text-xs font-semibold text-blue-700 xl:w-72">
                        Capacity-managed item. Update its slot capacity instead of recording physical production.
                    </div>
                )}
            </div>

            <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 border-t border-[#f0e7df] pt-3 text-xs text-[#756763]">
                <span>Approved: <strong className="text-[#241715]">{formatInventoryQuantity(item.approvedQuantity, item.inventoryUnit)}</strong></span>
                <span>Total ready recorded: <strong className="text-[#241715]">{formatInventoryQuantity(item.readyQuantity, item.inventoryUnit)}</strong></span>
                <span>Fulfilled: <strong className="text-[#241715]">{formatInventoryQuantity(item.fulfilledQuantity, item.inventoryUnit)}</strong></span>
                <span>Forecast: <strong className="text-[#241715]">{formatInventoryQuantity(item.forecastQuantity, item.inventoryUnit)}</strong></span>
                <span>Safety: <strong className="text-[#241715]">{formatInventoryQuantity(item.safetyBufferQuantity, item.inventoryUnit)}</strong></span>
            </div>
        </article>
    );
}

function Metric({
    label,
    value,
    strong = false,
    alert = false
}: {
    label: string;
    value: string;
    strong?: boolean;
    alert?: boolean;
}) {
    return (
        <div className={`rounded-xl p-3 ${alert ? "bg-amber-50" : "bg-[#fffaf3]"}`}>
            <p className="text-[10px] font-bold uppercase tracking-wide text-[#756763]">{label}</p>
            <p className={`mt-1 text-sm ${strong || alert ? "font-bold text-[#241715]" : "font-semibold text-[#756763]"}`}>
                {value}
            </p>
        </div>
    );
}

function ActionButton({
    label,
    onClick,
    secondary = false
}: {
    label: string;
    onClick: () => void;
    secondary?: boolean;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            className={secondary
                ? "min-h-10 rounded-xl border border-[#eadfd6] px-2 text-xs font-bold text-[#7a1625] hover:bg-[#fff4e5]"
                : "min-h-10 rounded-xl bg-[#7a1625] px-2 text-xs font-bold text-white hover:bg-[#5d0f1b]"}
        >
            {label}
        </button>
    );
}
