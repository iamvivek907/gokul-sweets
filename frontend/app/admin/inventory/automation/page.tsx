"use client";

import Link from "next/link";
import {adminManagementApi} from "@/services/adminManagementApi";
import {
    useEffect,
    useMemo,
    useState,
    type ReactNode
} from "react";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";
import {
    generateInventoryAllocations,
    getInventoryAutomationWorkspace,
    updateInventoryAutomationRule
} from "@/services/adminInventoryAutomationApi";
import type {
    AutomationWindow,
    InventoryAutomationMode,
    InventoryAutomationRule,
    InventoryAutomationRuleRequest,
    InventoryAutomationWorkspace,
    InventorySeasonalMode
} from "@/types/adminInventoryAutomation";
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
import InventoryHelp, {
    InventoryInfo
} from "@/components/admin/inventory/InventoryHelp";

interface Branch {
    id: number;
    name: string;
    active: boolean;
}

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const formControlClass =
    "mt-2 min-h-11 w-full rounded-xl border border-[#eadfd6] bg-white px-3 text-sm outline-none focus:border-[#7a1625]";
const dayOptions = [
    {label: "Mon", bit: 1}, {label: "Tue", bit: 2}, {label: "Wed", bit: 4},
    {label: "Thu", bit: 8}, {label: "Fri", bit: 16}, {label: "Sat", bit: 32},
    {label: "Sun", bit: 64}
];

function indiaDate(offsetDays = 0): string {
    const date = new Date();
    date.setDate(date.getDate() + offsetDays);
    return new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Kolkata", year: "numeric", month: "2-digit", day: "2-digit"
    }).format(date);
}

function modeLabel(mode: InventoryAutomationMode): string {
    if (mode === "SUGGEST_ONLY") return "Suggest only";
    if (mode === "CREATE_DRAFT") return "Create draft";
    return "Auto-approve guaranteed";
}

export default function InventoryAutomationPage() {
    const {profile, authorization, hasPermission} = useAdminAuth();
    const [branches, setBranches] = useState<Branch[]>([]);
    const [branchId, setBranchId] = useState<number | null>(null);
    const [workspace, setWorkspace] = useState<InventoryAutomationWorkspace | null>(null);
    const [search, setSearch] = useState("");
    const [statusFilter, setStatusFilter] = useState<"ALL" | "ACTIVE" | "INACTIVE">("ALL");
    const [fromDate, setFromDate] = useState(() => indiaDate());
    const [throughDate, setThroughDate] = useState(() => indiaDate(13));
    const [editing, setEditing] = useState<InventoryAutomationRule | null>(null);
    const [draft, setDraft] = useState<InventoryAutomationRuleRequest | null>(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [reloadKey, setReloadKey] = useState(0);

    const canManage = hasPermission("INVENTORY_MANAGE");

    useEffect(() => {
        if (!profile) return;
        const controller = new AbortController();
        fetch(`${API_BASE}/api/branches`, {cache: "no-store", signal: controller.signal})
            .then(response => {
                if (!response.ok) throw new Error("Unable to load branches.");
                return response.json() as Promise<Branch[]>;
            })
            .then(result => {
                const active = result.filter(branch => branch.active);
                const allowed = profile.roleName === "OWNER_ADMIN"
                    ? active
                    : active.filter(branch => profile.branchIds.includes(branch.id));
                setBranches(allowed);
                setBranchId(current => {
                    const next = selectRememberedBranch(allowed, current);
                    updateInventoryWorkspacePreferences({branchId: next});
                    return next;
                });
                const preferences = readInventoryWorkspacePreferences();
                if (preferences.automationFromDate) setFromDate(preferences.automationFromDate);
                if (preferences.automationThroughDate) setThroughDate(preferences.automationThroughDate);
                if (allowed.length === 0) {
                    setError("No active branch is available for your account.");
                    setLoading(false);
                }
            })
            .catch(exception => {
                if (controller.signal.aborted) return;
                setError(exception instanceof Error ? exception.message : "Unable to load branches.");
                setLoading(false);
            });
        return () => controller.abort();
    }, [profile]);

    useEffect(() => {
        if (!branchId || !authorization) return;
        const controller = new AbortController();
        const timer = window.setTimeout(() => {
            setLoading(true);
            getInventoryAutomationWorkspace(branchId, authorization, controller.signal)
                .then(result => {
                    setWorkspace(result);
                    setError(null);
                    setLoading(false);
                })
                .catch(exception => {
                    if (controller.signal.aborted) return;
                    setError(exception instanceof Error
                        ? exception.message
                        : "Unable to load inventory automation.");
                    setLoading(false);
                });
        }, 100);
        return () => {
            window.clearTimeout(timer);
            controller.abort();
        };
    }, [authorization, branchId, reloadKey]);

    const filteredRules = useMemo(() => {
        const term = search.trim().toLowerCase();
        return workspace?.rules.filter(rule => {
            const matchesSearch = !term
                || rule.productName.toLowerCase().includes(term)
                || rule.productCode.toLowerCase().includes(term)
                || rule.categoryName.toLowerCase().includes(term);
            const matchesStatus = statusFilter === "ALL"
                || (statusFilter === "ACTIVE" ? rule.active : !rule.active);
            return matchesSearch && matchesStatus;
        }) ?? [];
    }, [search, statusFilter, workspace]);

    const activeCount = workspace?.rules.filter(rule => rule.active).length ?? 0;
    const seasonalCount = workspace?.rules.filter(rule =>
        rule.active && rule.seasonalMode === "WINDOW_ONLY"
    ).length ?? 0;
    const autoApprovedCount = workspace?.rules.filter(rule =>
        rule.active && rule.automationMode === "AUTO_APPROVE_GUARANTEED"
    ).length ?? 0;

    function openRule(rule: InventoryAutomationRule) {
        setEditing(rule);
        setDraft({
            automationMode: rule.automationMode,
            guaranteedQuantity: Number(apiQuantityToInput(rule.guaranteedQuantity, rule.inventoryUnit)),
            forecastEnabled: rule.forecastEnabled,
            lookbackWeeks: rule.lookbackWeeks,
            minimumHistoryDays: rule.minimumHistoryDays,
            demandMultiplier: rule.demandMultiplier,
            maximumSuggestedQuantity: rule.maximumSuggestedQuantity === null
                ? null
                : Number(apiQuantityToInput(rule.maximumSuggestedQuantity, rule.inventoryUnit)),
            availableDaysMask: rule.availableDaysMask,
            seasonalMode: rule.seasonalMode,
            generationHorizonDays: rule.generationHorizonDays,
            active: rule.active,
            windows: rule.windows.map(window => ({...window}))
        });
        setError(null);
    }

    async function saveRule() {
        if (!editing || !draft || !branchId || !authorization) return;
        if (draft.automationMode === "AUTO_APPROVE_GUARANTEED"
                && draft.guaranteedQuantity <= 0) {
            setError("Auto approval requires a guaranteed quantity greater than zero.");
            return;
        }
        if (draft.seasonalMode === "WINDOW_ONLY"
                && !draft.windows.some(window => window.active)) {
            setError("Add at least one active date window for a seasonal product.");
            return;
        }
        setSaving(true);
        setError(null);
        try {
            const request: InventoryAutomationRuleRequest = {
                ...draft,
                guaranteedQuantity: inputQuantityToApi(
                    String(draft.guaranteedQuantity), editing.inventoryUnit
                ),
                maximumSuggestedQuantity: draft.maximumSuggestedQuantity === null
                    ? null
                    : inputQuantityToApi(
                        String(draft.maximumSuggestedQuantity), editing.inventoryUnit
                    )
            };
            await updateInventoryAutomationRule(
                branchId, editing.branchProductId, request, authorization
            );
            setNotice(`Automation rule saved for ${editing.productName}.`);
            setEditing(null);
            setDraft(null);
            setReloadKey(value => value + 1);
        } catch (exception) {
            setError(exception instanceof Error ? exception.message : "Unable to save rule.");
        } finally {
            setSaving(false);
        }
    }

    async function runGeneration() {
        if (!branchId || !authorization) return;
        if (throughDate < fromDate) {
            setError("Through date must be on or after the start date.");
            return;
        }
        setSaving(true);
        setError(null);
        try {
            const run = await generateInventoryAllocations(
                branchId, fromDate, throughDate, authorization
            );
            setNotice(
                `Generation completed: ${run.createdCount} created, ${run.updatedCount} updated, ${run.suggestedCount} suggested, ${run.skippedCount} safely skipped.`
            );
            setReloadKey(value => value + 1);
        } catch (exception) {
            setError(exception instanceof Error ? exception.message : "Unable to generate allocations.");
        } finally {
            setSaving(false);
        }
    }

    return (
        <main className="min-w-0 flex-1 bg-[#fffaf3] px-4 pb-8 pt-12 sm:px-6 lg:px-8">
            <div className="mx-auto max-w-[1500px]">
                <section className="rounded-3xl border border-[#eadfd6] bg-white p-6 shadow-sm lg:p-8">
                    <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
                        <div>
                            <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#c88a20]">
                                Inventory intelligence
                            </p>
                            <h1 className="mt-2 text-3xl font-bold text-[#241715]">
                                Daily Inventory Automation
                            </h1>
                            <p className="mt-2 max-w-3xl text-sm leading-6 text-[#756763]">
                                Generate safe daily allocations, seasonal schedules, and demand suggestions without allowing forecasts to become sellable stock automatically.
                            </p>
                        </div>
                        <div className="flex flex-wrap gap-3">
                            <InventoryHelp context="AUTOMATION" />
                            <Link
                                href="/admin/inventory"
                                className="inline-flex min-h-11 w-fit items-center rounded-xl border border-[#eadfd6] px-5 text-sm font-bold text-[#7a1625] hover:bg-[#fff4e5]"
                            >
                                ← Inventory dashboard
                            </Link>
                        </div>
                    </div>

                    <div className="mt-6 grid gap-4 md:grid-cols-[1fr_1fr_auto]">
                        <label className="text-sm font-semibold text-[#241715]">
                            Branch
                            <select
                                value={branchId ?? ""}
                                onChange={event => {
                                    const next = Number(event.target.value);
                                    setBranchId(next);
                                    updateInventoryWorkspacePreferences({branchId: next});
                                }}
                                className="mt-2 min-h-11 w-full rounded-xl border border-[#eadfd6] bg-white px-3"
                            >
                                {branches.map(branch => (
                                    <option key={branch.id} value={branch.id}>{branch.name}</option>
                                ))}
                            </select>
                        </label>
                        <div className="grid grid-cols-2 gap-3">
                            <label className="text-sm font-semibold text-[#241715]">
                                From
                                <input type="date" value={fromDate} min={indiaDate()}
                                    onChange={event => {
                                        const next = event.target.value;
                                        setFromDate(next);
                                        updateInventoryWorkspacePreferences({automationFromDate: next});
                                    }}
                                    className="mt-2 min-h-11 w-full rounded-xl border border-[#eadfd6] px-3" />
                            </label>
                            <label className="text-sm font-semibold text-[#241715]">
                                Through
                                <input type="date" value={throughDate} min={fromDate}
                                    onChange={event => {
                                        const next = event.target.value;
                                        setThroughDate(next);
                                        updateInventoryWorkspacePreferences({automationThroughDate: next});
                                    }}
                                    className="mt-2 min-h-11 w-full rounded-xl border border-[#eadfd6] px-3" />
                            </label>
                        </div>
                        <button type="button" onClick={runGeneration}
                            disabled={!canManage || saving || activeCount === 0}
                            className="min-h-11 self-end rounded-xl bg-[#7a1625] px-6 text-sm font-bold text-white disabled:cursor-not-allowed disabled:opacity-50">
                            {saving ? "Generating…" : "Generate allocations"}
                        </button>
                    </div>
                </section>

                <section className="mt-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
                    <Stat label="Configured products" value={workspace?.rules.length ?? 0} />
                    <Stat label="Automation active" value={activeCount} tone="green" />
                    <Stat label="Seasonal products" value={seasonalCount} tone="amber" />
                    <Stat label="Guaranteed auto-approval" value={autoApprovedCount} tone="red" />
                </section>

                <div className="mt-6 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-900">
                    <strong>Safety boundary:</strong> forecasts are production suggestions. Only the guaranteed quantity configured under <strong>Auto-approve guaranteed</strong> becomes an approved allocation. Existing customer commitments and physical-stock activity are never overwritten.
                </div>
                <section className="mt-4 rounded-2xl border border-[#eadfd6] bg-white p-4 text-sm leading-6">
                    <h2 className="font-bold">Make future pickup bookable</h2>
                    <p><Link className="underline" href="/admin/inventory/setup">1. Set product policies</Link>: online enabled, preparation time, booking window and separate safety buffer.</p>
                    <p>2. Configure explicit guaranteed production and generate dates here. A rule alone is not stock; drafts still need approval. A ready-stock policy also needs actual production marked ready.</p>
                    <p><Link className="underline" href="/admin/inventory">3. Review daily availability</Link> for the requested date. Manual/stock-active rows are protected, not silently replaced by generation.</p>
                    <p><Link className="underline" href="/admin/pickup-scheduling">4. Schedule pickup times</Link> for the same date range. Product, branch and global windows all apply.</p>
                </section>

                {notice && <div className="mt-4 rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-medium text-emerald-800">{notice}</div>}
                {error && <div className="mt-4 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm font-medium text-red-700">{error}</div>}

                <section className="mt-6 rounded-3xl border border-[#eadfd6] bg-white p-5 shadow-sm lg:p-6">
                    <div className="flex flex-col gap-3 md:flex-row">
                        <input type="search" value={search} onChange={event => setSearch(event.target.value)}
                            placeholder="Search product, code, or category"
                            className="min-h-11 flex-1 rounded-xl border border-[#eadfd6] px-4 text-sm" />
                        <select value={statusFilter}
                            onChange={event => setStatusFilter(event.target.value as typeof statusFilter)}
                            className="min-h-11 rounded-xl border border-[#eadfd6] bg-white px-4 text-sm">
                            <option value="ALL">All rules</option>
                            <option value="ACTIVE">Active</option>
                            <option value="INACTIVE">Inactive</option>
                        </select>
                    </div>

                    {loading ? (
                        <div className="mt-5 space-y-3">{[1, 2, 3].map(value => <div key={value} className="h-24 animate-pulse rounded-2xl bg-[#fff4e5]" />)}</div>
                    ) : filteredRules.length === 0 ? (
                        <div className="mt-5 rounded-2xl border border-dashed border-[#d9c8bc] p-12 text-center text-sm text-[#756763]">
                            Configure online inventory policies first, or change your filters.
                        </div>
                    ) : (
                        <div className="mt-5 space-y-3">
                            {filteredRules.map(rule => (
                                <article key={rule.branchProductId} className="flex flex-col gap-4 rounded-2xl border border-[#eadfd6] p-4 lg:flex-row lg:items-center lg:justify-between">
                                    <div className="min-w-0 lg:w-72">
                                        <p className="text-[10px] font-bold uppercase tracking-wide text-[#c88a20]">{rule.categoryName}</p>
                                        <h2 className="mt-1 truncate font-bold text-[#241715]">{rule.productName}</h2>
                                        <p className="mt-1 text-xs text-[#756763]">{rule.productCode} · {rule.inventoryUnit}</p>
                                        <p className="mt-2 text-xs">{rule.readyStockRequired ? "Actual ready stock required after approval" : "Approved production can be booked"} · up to {rule.bookingHorizonDays} days ahead · {rule.productionLeadMinutes} min preparation</p>
                                    </div>
                                    <div className="grid flex-1 grid-cols-2 gap-3 md:grid-cols-4">
                                        <RuleMetric label="Mode" value={modeLabel(rule.automationMode)} />
                                        <RuleMetric label="Guaranteed" value={formatInventoryQuantity(rule.guaranteedQuantity, rule.inventoryUnit)} />
                                        <RuleMetric label="Schedule" value={rule.seasonalMode === "WINDOW_ONLY" ? `${rule.windows.length} window(s)` : rule.seasonalMode.replaceAll("_", " ")} />
                                        <RuleMetric label="Status" value={rule.active ? "Active" : "Needs setup"} active={rule.active} />
                                    </div>
                                    {canManage && (
                                        <button type="button" onClick={() => openRule(rule)}
                                            className="min-h-10 rounded-xl border border-[#eadfd6] px-5 text-sm font-bold text-[#7a1625] hover:bg-[#fff4e5]">
                                            Configure
                                        </button>
                                    )}
                                </article>
                            ))}
                        </div>
                    )}
                </section>

                <section className="mt-6 rounded-3xl border border-[#eadfd6] bg-white p-5 shadow-sm lg:p-6">
                    <h2 className="text-xl font-bold text-[#241715]">Recent generation runs</h2>
                    <div className="mt-4 space-y-3">
                        {(workspace?.recentRuns ?? []).length === 0 ? (
                            <p className="rounded-xl bg-[#fffaf3] p-4 text-sm text-[#756763]">No automation run has been performed for this branch.</p>
                        ) : workspace?.recentRuns.map(run => (
                            <div key={run.id} className="grid gap-3 rounded-xl border border-[#eadfd6] p-4 text-sm md:grid-cols-[1fr_auto_auto_auto] md:items-center">
                                <div>
                                    <p className="font-bold text-[#241715]">{run.fromDate} – {run.throughDate}</p>
                                    <p className="mt-1 text-xs text-[#756763]">{run.triggerType} · {run.status} · {run.initiatedBy}</p>
                                </div>
                                <span className="text-emerald-700">{run.createdCount} created</span>
                                <span className="text-blue-700">{run.updatedCount + run.suggestedCount} refreshed/suggested</span>
                                <span className={run.errorCount > 0 ? "text-red-700" : "text-[#756763]"}>{run.skippedCount} skipped · {run.errorCount} errors</span>
                                {authorization && branchId && <RunExplanation branchId={branchId} runId={run.id} authorization={authorization} />}
                            </div>
                        ))}
                    </div>
                </section>
            </div>

            {editing && draft && (
                <RuleEditor rule={editing} draft={draft} setDraft={setDraft}
                    saving={saving} onCancel={() => { setEditing(null); setDraft(null); }}
                    onSave={saveRule} />
            )}
        </main>
    );
}

function RunExplanation({branchId, runId, authorization}: {branchId: number; runId: number; authorization: string}) {
    const [rows, setRows] = useState<{productName: string; outcome: string; message: string; firstDate: string; lastDate: string; dates: number}[] | null>(null);
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);
    async function load() {
        if (loading || rows) return;
        setLoading(true); setError("");
        try {setRows(await adminManagementApi(`/api/admin/inventory/branches/${branchId}/automation/runs/${runId}/explanation`, authorization));}
        catch (error) {setError(error instanceof Error ? error.message : "Unable to load run details.");}
        finally {setLoading(false);}
    }
    return <details className="md:col-span-4" onToggle={event => {if (event.currentTarget.open) void load();}}>
        <summary className="min-h-11 cursor-pointer py-2 underline">View product/date outcomes and skipped reasons</summary>
        {loading && <p role="status">Loading run details...</p>}
        {error && <p role="alert">{error} <button onClick={load} className="min-h-11 underline">Retry</button></p>}
        {rows?.map((row, index) => <p key={index} className="mt-2 rounded-lg bg-[#fffaf3] p-3"><strong>{row.productName}</strong> · {row.outcome} · {row.dates} date(s), {row.firstDate} to {row.lastDate}<br />{row.message}</p>)}
    </details>;
}

function RuleEditor({rule, draft, setDraft, saving, onCancel, onSave}: {
    rule: InventoryAutomationRule;
    draft: InventoryAutomationRuleRequest;
    setDraft: (value: InventoryAutomationRuleRequest) => void;
    saving: boolean;
    onCancel: () => void;
    onSave: () => void;
}) {
    function update<K extends keyof InventoryAutomationRuleRequest>(key: K, value: InventoryAutomationRuleRequest[K]) {
        setDraft({...draft, [key]: value});
    }
    function addWindow() {
        update("windows", [...draft.windows, {name: "Festival availability", startDate: indiaDate(), endDate: indiaDate(), active: true}]);
    }
    function updateWindow(index: number, patch: Partial<AutomationWindow>) {
        update("windows", draft.windows.map((window, current) => current === index ? {...window, ...patch} : window));
    }

    return (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/45 sm:items-center sm:p-6" role="dialog" aria-modal="true">
            <div className="max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-t-3xl bg-white p-6 shadow-2xl sm:rounded-3xl">
                <div className="flex items-start justify-between gap-4">
                    <div><p className="text-xs font-bold uppercase tracking-wide text-[#c88a20]">Automation rule</p><h2 className="mt-1 text-2xl font-bold text-[#241715]">{rule.productName}</h2></div>
                    <button type="button" onClick={onCancel} className="h-10 w-10 rounded-full bg-[#fff4e5] text-xl text-[#7a1625]">×</button>
                </div>

                <div className="mt-6 grid gap-4 md:grid-cols-2">
                    <Field label="Automation mode" helpKey="automationMode">
                        <select value={draft.automationMode} onChange={event => update("automationMode", event.target.value as InventoryAutomationMode)} className={formControlClass}>
                            <option value="SUGGEST_ONLY">Suggest only — no allocation change</option>
                            <option value="CREATE_DRAFT">Create draft — admin approval required</option>
                            <option value="AUTO_APPROVE_GUARANTEED">Auto-approve guaranteed quantity</option>
                        </select>
                    </Field>
                    <Field label={`Guaranteed online quantity (${inventoryInputUnit(rule.inventoryUnit)})`} helpKey="guaranteed">
                        <input type="number" min="0" step={rule.inventoryUnit === "GRAM" ? .05 : 1} value={draft.guaranteedQuantity} onChange={event => update("guaranteedQuantity", Number(event.target.value))} className={formControlClass} />
                    </Field>
                    <Field label="Lookback weeks" helpKey="lookbackWeeks">
                        <input type="number" min="1" max="52" value={draft.lookbackWeeks} onChange={event => update("lookbackWeeks", Number(event.target.value))} className={formControlClass} />
                    </Field>
                    <Field label="Minimum comparable days" helpKey="minimumComparableDays">
                        <input type="number" min="1" max="52" value={draft.minimumHistoryDays} onChange={event => update("minimumHistoryDays", Number(event.target.value))} className={formControlClass} />
                    </Field>
                    <Field label="Demand multiplier" helpKey="demandMultiplier">
                        <input type="number" min="0.001" step="0.05" value={draft.demandMultiplier} onChange={event => update("demandMultiplier", Number(event.target.value))} className={formControlClass} />
                    </Field>
                    <Field label="Maximum forecast suggestion" helpKey="forecastMaximum">
                        <input type="number" min={rule.inventoryUnit === "GRAM" ? .05 : 1} step={rule.inventoryUnit === "GRAM" ? .05 : 1} value={draft.maximumSuggestedQuantity ?? ""} onChange={event => update("maximumSuggestedQuantity", event.target.value === "" ? null : Number(event.target.value))} className={formControlClass} placeholder="Use policy maximum" />
                    </Field>
                    <Field label="Availability type" helpKey="availabilityType">
                        <select value={draft.seasonalMode} onChange={event => update("seasonalMode", event.target.value as InventorySeasonalMode)} className={formControlClass}>
                            <option value="ALWAYS">Normal scheduled product</option>
                            <option value="WINDOW_ONLY">Festival/date-window only</option>
                            <option value="MANUAL_ONLY">Manual allocation only</option>
                        </select>
                    </Field>
                    <Field label="Automatic planning window (days)" helpKey="generationHorizon">
                        <input type="number" min="0" max="365" value={draft.generationHorizonDays ?? ""} onChange={event => update("generationHorizonDays", event.target.value === "" ? null : Number(event.target.value))} className={formControlClass} placeholder="Use policy horizon" />
                    </Field>
                </div>

                <div className="mt-5"><p className="inline-flex items-center gap-1.5 text-sm font-bold text-[#241715]">Selling days <InventoryInfo helpKey="sellingDays" /></p><div className="mt-2 flex flex-wrap gap-2">{dayOptions.map(day => {
                    const selected = (draft.availableDaysMask & day.bit) !== 0;
                    return <button key={day.bit} type="button" onClick={() => update("availableDaysMask", selected ? draft.availableDaysMask & ~day.bit : draft.availableDaysMask | day.bit)} className={selected ? "rounded-full bg-[#7a1625] px-4 py-2 text-xs font-bold text-white" : "rounded-full border border-[#eadfd6] px-4 py-2 text-xs font-bold text-[#756763]"}>{day.label}</button>;
                })}</div></div>

                {draft.seasonalMode === "WINDOW_ONLY" && (
                    <div className="mt-6 rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-4">
                        <div className="flex items-center justify-between"><h3 className="inline-flex items-center gap-1.5 font-bold text-[#241715]">Festival/date windows <InventoryInfo helpKey="festivalWindows" /></h3><button type="button" onClick={addWindow} className="text-sm font-bold text-[#7a1625]">+ Add window</button></div>
                        <div className="mt-3 space-y-3">{draft.windows.map((window, index) => (
                            <div key={index} className="grid gap-2 rounded-xl bg-white p-3 md:grid-cols-[1fr_150px_150px_auto]">
                                <input value={window.name} onChange={event => updateWindow(index, {name: event.target.value})} className={formControlClass} placeholder="Festival name" />
                                <input type="date" value={window.startDate} onChange={event => updateWindow(index, {startDate: event.target.value})} className={formControlClass} />
                                <input type="date" value={window.endDate} onChange={event => updateWindow(index, {endDate: event.target.value})} className={formControlClass} />
                                <button type="button" onClick={() => update("windows", draft.windows.filter((_, current) => current !== index))} className="px-3 text-sm font-bold text-red-600">Remove</button>
                            </div>
                        ))}</div>
                    </div>
                )}

                <div className="mt-5 grid gap-3 sm:grid-cols-2">
                    <label className="flex items-center gap-3 rounded-xl border border-[#eadfd6] p-4 text-sm font-semibold text-[#241715]"><input type="checkbox" checked={draft.forecastEnabled} onChange={event => update("forecastEnabled", event.target.checked)} /> <span className="inline-flex items-center gap-1.5">Forecast enabled <InventoryInfo helpKey="forecastEnabled" /></span></label>
                    <label className="flex items-center gap-3 rounded-xl border border-[#eadfd6] p-4 text-sm font-semibold text-[#241715]"><input type="checkbox" checked={draft.active} onChange={event => update("active", event.target.checked)} /> <span className="inline-flex items-center gap-1.5">Automation active <InventoryInfo helpKey="automationActive" /></span></label>
                </div>
                <div className="mt-6 flex gap-3"><button type="button" onClick={onCancel} className="min-h-12 flex-1 rounded-xl border border-[#eadfd6] font-bold text-[#756763]">Cancel</button><button type="button" onClick={onSave} disabled={saving || draft.availableDaysMask === 0} className="min-h-12 flex-[1.5] rounded-xl bg-[#7a1625] font-bold text-white disabled:opacity-50">{saving ? "Saving…" : "Save rule"}</button></div>
            </div>
        </div>
    );
}

function Field({label, helpKey, children}: {label: string; helpKey: string; children: ReactNode}) {
    return <label className="text-sm font-semibold text-[#241715]"><span className="inline-flex items-center gap-1.5">{label}<InventoryInfo helpKey={helpKey} /></span>{children}</label>;
}

function Stat({label, value, tone = "neutral"}: {label: string; value: number; tone?: "neutral" | "green" | "amber" | "red"}) {
    const colours = {neutral: "border-[#eadfd6] bg-white", green: "border-emerald-200 bg-emerald-50", amber: "border-amber-200 bg-amber-50", red: "border-red-200 bg-red-50"};
    return <div className={`rounded-2xl border p-4 ${colours[tone]}`}><p className="text-2xl font-bold text-[#241715]">{value}</p><p className="mt-1 text-xs font-semibold text-[#756763]">{label}</p></div>;
}

function RuleMetric({label, value, active}: {label: string; value: string; active?: boolean}) {
    return <div className="rounded-xl bg-[#fffaf3] p-3"><p className="text-[10px] font-bold uppercase tracking-wide text-[#756763]">{label}</p><p className={`mt-1 text-xs font-bold ${active ? "text-emerald-700" : "text-[#241715]"}`}>{value}</p></div>;
}
