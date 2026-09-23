"use client";

import {
    useCallback,
    useEffect,
    useMemo,
    useState,
    type ReactNode
} from "react";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    completeBulkInventorySetup,
    getInventoryCatalogue,
    updateBulkInventoryPolicies,
    updateBulkInventoryReadiness
} from "@/services/adminInventoryApi";

import type {
    InventoryCatalogueFilter,
    InventoryCatalogueItem,
    InventoryCataloguePage,
    InventoryControlMode,
    InventoryPolicy,
    InventoryPolicyRequest,
    InventoryUnit
} from "@/types/adminInventory";

import {
    readInventoryWorkspacePreferences,
    selectRememberedBranch,
    updateInventoryWorkspacePreferences
} from "@/lib/inventoryWorkspacePreferences";

import {
    apiQuantityToInput,
    daysToMinutes,
    formatInventoryQuantity,
    hoursToMinutes,
    inputQuantityToApi,
    inventoryInputUnit
} from "@/lib/inventoryUnits";

import InventoryHelp, {
    InventoryColumnHeader,
    InventoryInfo
} from "@/components/admin/inventory/InventoryHelp";

interface Branch {
    id: number;
    code: string;
    name: string;
    address: string;
    active: boolean;
}

const API_BASE =
    process.env.NEXT_PUBLIC_API_BASE_URL
    ?? "http://localhost:8080";

const PAGE_SIZE = 100;
const SETUP_SELECTION_KEY = "gokul-inventory-setup-selection";

function selectionKey(branchId: number, serviceDate: string): string {
    return `${branchId}:${serviceDate}`;
}

function readRememberedSelection(
    branchId: number,
    serviceDate: string
): Set<number> {
    try {
        const raw = window.localStorage.getItem(SETUP_SELECTION_KEY);
        if (!raw) return new Set();
        const all = JSON.parse(raw) as Record<string, number[]>;
        const values = all[selectionKey(branchId, serviceDate)];
        return new Set(Array.isArray(values) ? values : []);
    } catch {
        return new Set();
    }
}

function rememberSelection(
    branchId: number | null,
    serviceDate: string,
    selected: Set<number>
): void {
    if (branchId === null) return;
    try {
        const raw = window.localStorage.getItem(SETUP_SELECTION_KEY);
        const all = raw
            ? JSON.parse(raw) as Record<string, number[]>
            : {};
        all[selectionKey(branchId, serviceDate)] = [...selected];
        window.localStorage.setItem(SETUP_SELECTION_KEY, JSON.stringify(all));
    } catch {
        // Selection persistence is a convenience; backend data remains authoritative.
    }
}

function policyFingerprint(
    policy: NonNullable<InventoryCatalogueItem["policy"]>
): string {
    return JSON.stringify({
        controlMode: policy.controlMode,
        inventoryUnit: policy.inventoryUnit,
        onlineEnabled: policy.onlineEnabled,
        readyStockRequired: policy.readyStockRequired,
        defaultSafetyBuffer: policy.defaultSafetyBuffer,
        maximumDailyAllocation: policy.maximumDailyAllocation,
        bookingHorizonDays: policy.bookingHorizonDays,
        productionLeadMinutes: policy.productionLeadMinutes,
        shelfLifeMinutes: policy.shelfLifeMinutes
    });
}

function todayInIndia(): string {
    return new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Kolkata",
        year: "numeric",
        month: "2-digit",
        day: "2-digit"
    }).format(new Date());
}

function statusClasses(status: string): string {
    switch (status) {
        case "READY":
            return "bg-emerald-50 text-emerald-700 border-emerald-200";
        case "DELAYED":
            return "bg-amber-50 text-amber-800 border-amber-200";
        case "UNAVAILABLE":
            return "bg-red-50 text-red-700 border-red-200";
        case "APPROVED":
            return "bg-blue-50 text-blue-700 border-blue-200";
        default:
            return "bg-[#fff4e5] text-[#7a1625] border-[#eadfd6]";
    }
}

export default function AdminInventoryPage() {
    const {profile, authorization, hasPermission} = useAdminAuth();

    const [branches, setBranches] = useState<Branch[]>([]);
    const [branchId, setBranchId] = useState<number | null>(null);
    const [serviceDate, setServiceDate] = useState(todayInIndia);
    const [search, setSearch] = useState("");
    const [categoryId, setCategoryId] = useState<number | "ALL">("ALL");
    const [filter, setFilter] = useState<InventoryCatalogueFilter>("ALL");
    const [page, setPage] = useState(0);
    const [catalogue, setCatalogue] = useState<InventoryCataloguePage | null>(null);
    const [selected, setSelected] = useState<Set<number>>(new Set());
    const [approvedDrafts, setApprovedDrafts] = useState<Record<number, string>>({});
    const [readyDrafts, setReadyDrafts] = useState<Record<number, string>>({});
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [reloadKey, setReloadKey] = useState(0);
    const [showPolicyPanel, setShowPolicyPanel] = useState(false);
    const [policyEditorMessage, setPolicyEditorMessage] = useState<string | null>(null);
    const [editorApproved, setEditorApproved] = useState("");
    const [editorReady, setEditorReady] = useState("");
    const [editorItem, setEditorItem] = useState<InventoryCatalogueItem | null>(null);
    const [showBulkQuantityPanel, setShowBulkQuantityPanel] = useState(false);
    const [bulkGroupInputs, setBulkGroupInputs] = useState<Record<InventoryUnit, string>>({
        GRAM: "",
        PIECE: ""
    });
    const [policyOverrides, setPolicyOverrides] = useState<Record<number, InventoryPolicy>>({});
    const [bulkPolicyGroups, setBulkPolicyGroups] = useState<InventoryCatalogueItem[][]>([]);
    const [bulkPolicyGroupIndex, setBulkPolicyGroupIndex] = useState(0);
    const [policyTargetItems, setPolicyTargetItems] = useState<InventoryCatalogueItem[]>([]);

    const [controlMode, setControlMode] =
        useState<InventoryControlMode>("READY_STOCK");
    const [inventoryUnit, setInventoryUnit] =
        useState<InventoryUnit>("PIECE");
    const [onlineEnabled, setOnlineEnabled] = useState(true);
    const [readyStockRequired, setReadyStockRequired] = useState(true);
    const [safetyBuffer, setSafetyBuffer] = useState("0");
    const [maximumAllocation, setMaximumAllocation] = useState("");
    const [bookingHorizon, setBookingHorizon] = useState("7");
    const [leadHours, setLeadHours] = useState("0");
    const [shelfLifeDays, setShelfLifeDays] = useState("");

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
            .then(allBranches => {
                const active = allBranches.filter(branch => branch.active);
                const allowed = profile.roleName === "OWNER_ADMIN"
                    ? active
                    : active.filter(branch => profile.branchIds.includes(branch.id));
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
            getInventoryCatalogue(
                branchId,
                serviceDate,
                authorization,
                {
                    search,
                    categoryId: categoryId === "ALL" ? undefined : categoryId,
                    filter,
                    page,
                    size: PAGE_SIZE,
                    signal: controller.signal
                }
            )
                .then(result => {
                    setCatalogue(result);
                    setSelected(() => {
                        const remembered = readRememberedSelection(
                            branchId,
                            serviceDate
                        );
                        const visibleIds = new Set(
                            result.content.map(item => item.branchProductId)
                        );
                        const next = new Set(
                            [...remembered].filter(id => visibleIds.has(id))
                        );
                        rememberSelection(branchId, serviceDate, next);
                        return next;
                    });
                    setApprovedDrafts(current => {
                        const next = {...current};
                        result.content.forEach(item => {
                            const unit = item.policy?.inventoryUnit
                                ?? (item.saleMode === "WEIGHT" ? "GRAM" : "PIECE");
                            next[item.branchProductId] = apiQuantityToInput(
                                item.allocation?.approvedQuantity,
                                unit
                            );
                        });
                        return next;
                    });
                    setReadyDrafts(current => {
                        const next = {...current};
                        result.content.forEach(item => {
                            const unit = item.policy?.inventoryUnit
                                ?? (item.saleMode === "WEIGHT" ? "GRAM" : "PIECE");
                            next[item.branchProductId] = apiQuantityToInput(
                                item.allocation?.readyQuantity
                                ?? item.allocation?.approvedQuantity,
                                unit
                            );
                        });
                        return next;
                    });
                    setError(null);
                    setLoading(false);
                })
                .catch(exception => {
                    if (controller.signal.aborted) return;
                    setError(exception instanceof Error
                        ? exception.message
                        : "Unable to load inventory.");
                    setLoading(false);
                });
        }, 250);

        return () => {
            window.clearTimeout(timer);
            controller.abort();
        };
    }, [authorization, branchId, categoryId, filter, page, reloadKey, search, serviceDate]);

    const refresh = useCallback(() => {
        setReloadKey(value => value + 1);
    }, []);

    const selectedItems = useMemo(() => {
        return catalogue?.content.filter(item => selected.has(item.branchProductId)) ?? [];
    }, [catalogue, selected]);

    const selectedUnitGroups = useMemo(() => {
        const groups: Record<InventoryUnit, InventoryCatalogueItem[]> = {
            GRAM: [],
            PIECE: []
        };
        selectedItems.forEach(item => {
            groups[item.saleMode === "WEIGHT" ? "GRAM" : "PIECE"].push(item);
        });
        return groups;
    }, [selectedItems]);

    function effectivePolicy(item: InventoryCatalogueItem): InventoryPolicy | null {
        return policyOverrides[item.branchProductId] ?? item.policy;
    }

    function unitFor(item: InventoryCatalogueItem): InventoryUnit {
        return effectivePolicy(item)?.inventoryUnit
            ?? (item.saleMode === "WEIGHT" ? "GRAM" : "PIECE");
    }

    function toggleItem(id: number) {
        setShowPolicyPanel(false);
        setSelected(current => {
            const next = new Set(current);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            rememberSelection(branchId, serviceDate, next);
            return next;
        });
    }

    function toggleVisible() {
        setShowPolicyPanel(false);
        const visibleIds = catalogue?.content.map(item => item.branchProductId) ?? [];
        const allSelected = visibleIds.length > 0
            && visibleIds.every(id => selected.has(id));
        const next = allSelected ? new Set<number>() : new Set(visibleIds);
        setSelected(next);
        rememberSelection(branchId, serviceDate, next);
    }

    function inferredInventoryUnit(
        items: InventoryCatalogueItem[] = selectedItems
    ): InventoryUnit | null {
        if (items.length === 0) return null;
        const saleModes = new Set(items.map(item => item.saleMode));
        if (saleModes.size > 1) return null;
        return saleModes.has("WEIGHT") ? "GRAM" : "PIECE";
    }

    function resetPolicyForm(unit = inferredInventoryUnit() ?? "PIECE") {
        setControlMode("READY_STOCK");
        setInventoryUnit(unit);
        setOnlineEnabled(true);
        setReadyStockRequired(true);
        setSafetyBuffer("0");
        setMaximumAllocation("");
        setBookingHorizon("7");
        setLeadHours("0");
        setShelfLifeDays("");
    }

    function populatePolicyForm(
        policy: NonNullable<InventoryCatalogueItem["policy"]>
    ) {
        setControlMode(policy.controlMode);
        setInventoryUnit(policy.inventoryUnit);
        setOnlineEnabled(policy.onlineEnabled);
        setReadyStockRequired(policy.readyStockRequired);
        setSafetyBuffer(apiQuantityToInput(
            policy.defaultSafetyBuffer,
            policy.inventoryUnit
        ));
        setMaximumAllocation(apiQuantityToInput(
            policy.maximumDailyAllocation,
            policy.inventoryUnit
        ));
        setBookingHorizon(String(policy.bookingHorizonDays));
        setLeadHours(String(policy.productionLeadMinutes / 60));
        setShelfLifeDays(policy.shelfLifeMinutes === null
            ? ""
            : String(policy.shelfLifeMinutes / (24 * 60)));
    }

    function loadConfiguration(
        items: InventoryCatalogueItem[],
        singleItemEditor = true
    ) {
        setError(null);
        setNotice(null);

        const unit = inferredInventoryUnit(items);
        if (!unit) {
            setError("Select either weight-based products or unit-based products, not both.");
            return;
        }

        const configured = items.filter(item => item.policy !== null);
        if (configured.length === 0) {
            resetPolicyForm(unit);
            setPolicyEditorMessage(
                "New configuration: safe defaults are shown. Review them before saving."
            );
        } else {
            const firstPolicy = configured[0].policy!;
            const allConfigured = configured.length === items.length;
            const samePolicy = configured.every(item =>
                policyFingerprint(item.policy!) === policyFingerprint(firstPolicy)
            );

            if (!allConfigured || !samePolicy) {
                resetPolicyForm(unit);
                setPolicyEditorMessage(
                    "Bulk replacement mode: the selected products have different saved policies. The form starts from safe defaults; saving will apply the entered policy to every selected product."
                );
            } else {
                populatePolicyForm(firstPolicy);
                setPolicyEditorMessage(
                    "Current saved configuration loaded. Adjust only what you want to change, then save."
                );
            }
        }

        setInventoryUnit(unit);
        setPolicyTargetItems(items);
        if (items.length === 1 && singleItemEditor) {
            const item = items[0];
            setEditorItem(item);
            setEditorApproved(
                approvedDrafts[item.branchProductId]
                ?? apiQuantityToInput(item.allocation?.approvedQuantity, unit)
            );
            setEditorReady(
                readyDrafts[item.branchProductId]
                ?? apiQuantityToInput(
                    item.allocation?.readyQuantity
                    ?? item.allocation?.approvedQuantity,
                    unit
                )
            );
        } else {
            setEditorItem(null);
            setEditorApproved("");
            setEditorReady("");
        }
        setShowPolicyPanel(true);
    }

    function openConfiguration() {
        const groups = [
            selectedUnitGroups.GRAM,
            selectedUnitGroups.PIECE
        ].filter(group => group.length > 0);
        if (groups.length === 0) return;
        setBulkPolicyGroups(groups);
        setBulkPolicyGroupIndex(0);
        loadConfiguration(groups[0], false);
    }

    function openItemEditor(item: InventoryCatalogueItem) {
        setBulkPolicyGroups([]);
        setBulkPolicyGroupIndex(0);
        loadConfiguration([item]);
    }

    function closeConfiguration() {
        setShowPolicyPanel(false);
        setEditorItem(null);
        setPolicyTargetItems([]);
    }

    function policyFromEditor(): InventoryPolicyRequest | null {
        const policy: InventoryPolicyRequest = {
            controlMode,
            inventoryUnit,
            onlineEnabled,
            readyStockRequired,
            defaultSafetyBuffer: inputQuantityToApi(safetyBuffer, inventoryUnit),
            maximumDailyAllocation: maximumAllocation === ""
                ? null
                : inputQuantityToApi(maximumAllocation, inventoryUnit),
            bookingHorizonDays: Number(bookingHorizon),
            productionLeadMinutes: hoursToMinutes(leadHours),
            shelfLifeMinutes: daysToMinutes(shelfLifeDays)
        };

        if (!Number.isFinite(policy.defaultSafetyBuffer)
                || policy.defaultSafetyBuffer < 0
                || (policy.maximumDailyAllocation !== null
                    && (!Number.isFinite(policy.maximumDailyAllocation)
                        || policy.maximumDailyAllocation <= 0))
                || !Number.isFinite(policy.bookingHorizonDays)
                || policy.bookingHorizonDays < 0
                || !Number.isFinite(policy.productionLeadMinutes)
                || policy.productionLeadMinutes < 0
                || (policy.shelfLifeMinutes !== null
                    && (!Number.isFinite(policy.shelfLifeMinutes)
                        || policy.shelfLifeMinutes <= 0))) {
            setError("Enter valid non-negative configuration values. Daily maximum and shelf life must be greater than zero when provided.");
            return null;
        }

        return policy;
    }

    async function savePolicies() {
        if (!authorization || !branchId) return;

        const targetItems = editorItem
            ? [editorItem]
            : policyTargetItems.length > 0
                ? policyTargetItems
                : selectedItems;
        if (targetItems.length === 0) return;

        const saleModes = new Set(targetItems.map(item => item.saleMode));
        if (saleModes.size > 1) {
            setError("Configure weight-based and unit-based products separately.");
            return;
        }
        if (saleModes.has("WEIGHT") && inventoryUnit !== "GRAM") {
            setError("Weight-based products must use GRAM inventory.");
            return;
        }
        if (saleModes.has("UNIT") && inventoryUnit !== "PIECE") {
            setError("Unit products must use PIECE inventory.");
            return;
        }

        const policy = policyFromEditor();
        if (!policy) return;

        try {
            setSaving(true);
            setError(null);
            setNotice(null);

            const response = await updateBulkInventoryPolicies(
                branchId,
                targetItems.map(item => item.branchProductId),
                policy,
                authorization
            );

            setPolicyOverrides(current => {
                const next = {...current};
                response.results.forEach(saved => {
                    next[saved.branchProductId] = saved;
                });
                return next;
            });
            const nextGroupIndex = bulkPolicyGroupIndex + 1;
            const hasNextGroup = !editorItem
                && nextGroupIndex < bulkPolicyGroups.length;

            if (hasNextGroup) {
                setBulkPolicyGroupIndex(nextGroupIndex);
                loadConfiguration(bulkPolicyGroups[nextGroupIndex], false);
                setNotice(
                    `${inventoryUnit === "GRAM" ? "Weight" : "Piece"} policy saved. Configure the next unit group.`
                );
            } else {
                setNotice("Inventory policies updated.");
                closeConfiguration();
            }

            if (!editorItem && targetItems.length > 0 && !hasNextGroup) {
                window.setTimeout(() => {
                    setShowBulkQuantityPanel(true);
                }, 0);
            }
            refresh();
        } catch (exception) {
            setError(exception instanceof Error
                ? exception.message
                : "Unable to update inventory policies.");
        } finally {
            setSaving(false);
        }
    }

    async function saveItemAndMakeAvailable() {
        if (!authorization || !branchId || !editorItem) return;

        const expectedUnit: InventoryUnit =
            editorItem.saleMode === "WEIGHT" ? "GRAM" : "PIECE";

        if (inventoryUnit !== expectedUnit) {
            setError(`Inventory unit must be ${expectedUnit === "GRAM" ? "weight" : "pieces"} for this product.`);
            return;
        }

        const policy = policyFromEditor();
        if (!policy) return;

        const approvedQuantity = inputQuantityToApi(editorApproved, inventoryUnit);
        const readyQuantity = inputQuantityToApi(editorReady, inventoryUnit);

        if (!Number.isFinite(approvedQuantity) || approvedQuantity <= 0) {
            setError("Enter an approved quantity greater than zero.");
            return;
        }
        if (!Number.isFinite(readyQuantity) || readyQuantity <= 0) {
            setError("Enter a ready quantity greater than zero.");
            return;
        }
        if (policy.maximumDailyAllocation !== null
                && approvedQuantity > policy.maximumDailyAllocation) {
            setError("Approved quantity cannot exceed the configured daily maximum.");
            return;
        }

        try {
            setSaving(true);
            setError(null);
            setNotice(null);

            await updateBulkInventoryPolicies(
                branchId,
                [editorItem.branchProductId],
                policy,
                authorization
            );

            await completeBulkInventorySetup(
                branchId,
                serviceDate,
                [{
                    branchProductId: editorItem.branchProductId,
                    approvedQuantity,
                    readyQuantity,
                    safetyBufferQuantity: policy.defaultSafetyBuffer,
                    forecastQuantity: editorItem.allocation?.forecastQuantity ?? null,
                    forecastConfidence: editorItem.allocation?.forecastConfidence ?? null,
                    expectedReadyAt: editorItem.allocation?.expectedReadyAt ?? null,
                    note: editorItem.allocation?.note ?? null
                }],
                authorization
            );

            setApprovedDrafts(current => ({
                ...current,
                [editorItem.branchProductId]: editorApproved
            }));
            setReadyDrafts(current => ({
                ...current,
                [editorItem.branchProductId]: editorReady
            }));
            setNotice(`${editorItem.productName} is configured and available for ${serviceDate}.`);
            closeConfiguration();
            refresh();
        } catch (exception) {
            setError(exception instanceof Error
                ? exception.message
                : "Unable to complete this product setup.");
            refresh();
        } finally {
            setSaving(false);
        }
    }

    function openBulkQuantityWorkspace() {
        setError(null);
        setNotice(null);

        const missing = selectedItems.find(item => effectivePolicy(item) === null);
        if (missing) {
            setError(`Configure ${missing.productName} before entering bulk quantities.`);
            return;
        }

        setShowBulkQuantityPanel(true);
    }

    function applyGroupValue(
        unit: InventoryUnit,
        target: "approved" | "ready"
    ) {
        const value = bulkGroupInputs[unit]?.trim();
        if (!value) return;
        const group = selectedUnitGroups[unit];
        if (target === "approved") {
            setApprovedDrafts(current => {
                const next = {...current};
                group.forEach(item => next[item.branchProductId] = value);
                return next;
            });
        } else {
            setReadyDrafts(current => {
                const next = {...current};
                group.forEach(item => next[item.branchProductId] = value);
                return next;
            });
        }
    }

    function fillGroupFromMaximum(unit: InventoryUnit) {
        setApprovedDrafts(current => {
            const next = {...current};
            selectedUnitGroups[unit].forEach(item => {
                const policy = effectivePolicy(item);
                if (policy?.maximumDailyAllocation !== null
                        && policy?.maximumDailyAllocation !== undefined) {
                    next[item.branchProductId] = apiQuantityToInput(
                        policy.maximumDailyAllocation,
                        unit
                    );
                }
            });
            return next;
        });
    }

    function fillGroupFromForecast(unit: InventoryUnit) {
        setApprovedDrafts(current => {
            const next = {...current};
            selectedUnitGroups[unit].forEach(item => {
                if (item.allocation?.forecastQuantity !== null
                        && item.allocation?.forecastQuantity !== undefined) {
                    next[item.branchProductId] = apiQuantityToInput(
                        item.allocation.forecastQuantity,
                        unit
                    );
                }
            });
            return next;
        });
    }

    function copyApprovedToReady(unit: InventoryUnit) {
        setReadyDrafts(current => {
            const next = {...current};
            selectedUnitGroups[unit].forEach(item => {
                next[item.branchProductId] =
                    approvedDrafts[item.branchProductId] ?? "";
            });
            return next;
        });
    }

    function clearGroupQuantities(unit: InventoryUnit) {
        setApprovedDrafts(current => {
            const next = {...current};
            selectedUnitGroups[unit].forEach(item => next[item.branchProductId] = "");
            return next;
        });
        setReadyDrafts(current => {
            const next = {...current};
            selectedUnitGroups[unit].forEach(item => next[item.branchProductId] = "0");
            return next;
        });
    }

    function bulkQuantityError(item: InventoryCatalogueItem): string | null {
        const policy = effectivePolicy(item);
        if (!policy) return "Policy is missing.";

        const unit = unitFor(item);
        const approvedInput = Number(approvedDrafts[item.branchProductId]);
        const readyInput = Number(readyDrafts[item.branchProductId] || "0");
        const approved = inputQuantityToApi(
            approvedDrafts[item.branchProductId],
            unit
        );
        const ready = inputQuantityToApi(
            readyDrafts[item.branchProductId] || "0",
            unit
        );

        if (!Number.isFinite(approved) || approved <= 0) {
            return "Enter an approved quantity greater than zero.";
        }
        if (!Number.isFinite(ready) || ready < 0) {
            return "Ready quantity cannot be negative.";
        }
        if (unit === "PIECE"
                && (!Number.isInteger(approvedInput) || !Number.isInteger(readyInput))) {
            return "Piece quantities must be whole numbers.";
        }
        if (policy.maximumDailyAllocation !== null
                && approved > policy.maximumDailyAllocation) {
            return `Approved quantity exceeds the daily maximum of ${formatInventoryQuantity(policy.maximumDailyAllocation, unit)}.`;
        }
        return null;
    }

    async function confirmBulkQuantities() {
        if (!authorization || !branchId || selectedItems.length === 0) return;

        const invalid = selectedItems.find(item => bulkQuantityError(item) !== null);
        if (invalid) {
            setError(`${invalid.productName}: ${bulkQuantityError(invalid)}`);
            return;
        }

        const items = selectedItems.map(item => {
            const policy = effectivePolicy(item)!;
            const unit = unitFor(item);
            const readyQuantity = inputQuantityToApi(
                readyDrafts[item.branchProductId] || "0",
                unit
            );
            return {
                branchProductId: item.branchProductId,
                approvedQuantity: inputQuantityToApi(
                    approvedDrafts[item.branchProductId],
                    unit
                ),
                readyQuantity,
                markReady: readyQuantity > 0,
                safetyBufferQuantity: policy.defaultSafetyBuffer,
                forecastQuantity: item.allocation?.forecastQuantity ?? null,
                forecastConfidence: item.allocation?.forecastConfidence ?? null,
                expectedReadyAt: item.allocation?.expectedReadyAt ?? null,
                note: item.allocation?.note ?? null
            };
        });

        try {
            setSaving(true);
            setError(null);
            setNotice(null);
            await completeBulkInventorySetup(
                branchId,
                serviceDate,
                items,
                authorization
            );

            const readyCount = items.filter(item => item.markReady).length;
            setNotice(
                `${items.length} products approved; ${readyCount} marked ready and ${items.length - readyCount} left awaiting production.`
            );
            setShowBulkQuantityPanel(false);
            const next = new Set<number>();
            setSelected(next);
            rememberSelection(branchId, serviceDate, next);
            refresh();
        } catch (exception) {
            setError(exception instanceof Error
                ? exception.message
                : "Unable to complete the bulk inventory update.");
        } finally {
            setSaving(false);
        }
    }

    async function changeReadiness(
        status: "READY" | "DELAYED" | "UNAVAILABLE"
    ) {
        if (!authorization || !branchId || selectedItems.length === 0) return;
        const missing = selectedItems.find(item => !item.allocation);
        if (missing) {
            setError(`Approve ${missing.productName} before changing readiness.`);
            return;
        }

        const items = selectedItems.map(item => ({
            branchProductId: item.branchProductId,
            status,
            readyQuantity: status === "UNAVAILABLE"
                ? 0
                : inputQuantityToApi(
                    readyDrafts[item.branchProductId],
                    item.policy?.inventoryUnit
                ),
            expectedReadyAt: item.allocation?.expectedReadyAt ?? null,
            note: status === "DELAYED"
                ? "Preparation delayed by branch operations."
                : item.allocation?.note ?? null
        }));

        if (status === "READY" && items.some(item => !Number.isFinite(item.readyQuantity)
                || item.readyQuantity <= 0)) {
            setError("Enter a ready quantity greater than zero for every selected product.");
            return;
        }

        await performSave(
            () => updateBulkInventoryReadiness(
                branchId,
                serviceDate,
                items,
                authorization
            ),
            `${items.length} item${items.length === 1 ? "" : "s"} marked ${status.toLowerCase()}.`
        );
    }

    async function performSave(
        operation: () => Promise<unknown>,
        successMessage: string
    ) {
        try {
            setSaving(true);
            setError(null);
            setNotice(null);
            await operation();
            setNotice(successMessage);
            refresh();
        } catch (exception) {
            setError(exception instanceof Error
                ? exception.message
                : "Unable to save inventory changes.");
        } finally {
            setSaving(false);
        }
    }

    const selectedBranch = branches.find(branch => branch.id === branchId);

    return (
        <main className="min-w-0 flex-1 bg-[#fffaf3] px-5 py-6 xl:px-10 xl:py-8">
            <section className="rounded-3xl border border-[#eadfd6] bg-white p-6 shadow-sm xl:p-8">
                <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#c88a20]">
                    Inventory operations
                </p>
                <div className="mt-2 flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
                    <div>
                        <h1 className="text-3xl font-bold text-[#241715]">Daily Inventory</h1>
                        <p className="mt-2 max-w-3xl text-sm leading-6 text-[#756763]">
                            Protect online availability while keeping walk-in stock separate. Approve only the quantity committed to online orders for each pickup date.
                        </p>
                    </div>
                    <div className="flex flex-col items-stretch gap-3">
                        <InventoryHelp context="SETUP" />
                        <div className="grid gap-3 sm:grid-cols-2">
                        <label className="text-xs font-bold uppercase tracking-wide text-[#756763]">
                            Branch
                            <select
                                value={branchId ?? ""}
                                onChange={event => {
                                    const next = Number(event.target.value);
                                    setBranchId(next);
                                    updateInventoryWorkspacePreferences({branchId: next});
                                    setPage(0);
                                }}
                                className="mt-2 min-h-11 w-full rounded-xl border border-[#eadfd6] bg-white px-3 text-sm normal-case text-[#241715]"
                            >
                                {branches.map(branch => (
                                    <option key={branch.id} value={branch.id}>{branch.name}</option>
                                ))}
                            </select>
                        </label>
                        <label className="text-xs font-bold uppercase tracking-wide text-[#756763]">
                            Pickup date
                            <input
                                type="date"
                                min={todayInIndia()}
                                value={serviceDate}
                                onChange={event => {
                                    const next = event.target.value;
                                    setServiceDate(next);
                                    updateInventoryWorkspacePreferences({serviceDate: next});
                                    setPage(0);
                                }}
                                className="mt-2 min-h-11 w-full rounded-xl border border-[#eadfd6] bg-white px-3 text-sm normal-case text-[#241715]"
                            />
                        </label>
                        </div>
                    </div>
                </div>
                {selectedBranch && (
                    <p className="mt-5 rounded-xl bg-[#fff4e5] px-4 py-3 text-sm text-[#756763]">
                        <span className="font-bold text-[#241715]">{selectedBranch.name}</span>
                        {" · "}{selectedBranch.address}
                    </p>
                )}
            </section>

            {catalogue && (
                <section className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
                    <SummaryCard label="Products" value={catalogue.summary.totalProducts} active={filter === "ALL"} onClick={() => setFilter("ALL")} />
                    <SummaryCard label="Needs attention" value={catalogue.summary.needsAttention} tone="warning" active={filter === "NEEDS_ATTENTION"} onClick={() => setFilter("NEEDS_ATTENTION")} />
                    <SummaryCard label="Ready" value={catalogue.summary.ready} tone="success" active={filter === "READY"} onClick={() => setFilter("READY")} />
                    <SummaryCard label="Not configured" value={catalogue.summary.notConfigured} active={filter === "NOT_CONFIGURED"} onClick={() => setFilter("NOT_CONFIGURED")} />
                    <SummaryCard label="Unavailable" value={catalogue.summary.unavailable} tone="danger" active={filter === "UNAVAILABLE"} onClick={() => setFilter("UNAVAILABLE")} />
                </section>
            )}

            <section className="mt-6 rounded-3xl border border-[#eadfd6] bg-white shadow-sm">
                <div className="border-b border-[#eadfd6] p-5">
                    <div className="grid gap-3 lg:grid-cols-[minmax(240px,1fr)_220px_220px_auto]">
                        <input
                            type="search"
                            value={search}
                            onChange={event => {
                                setSearch(event.target.value);
                                setPage(0);
                            }}
                            placeholder="Search product, code or category"
                            className="min-h-11 rounded-xl border border-[#eadfd6] px-4 text-sm outline-none focus:border-[#7a1625]"
                        />
                        <select
                            value={categoryId}
                            onChange={event => {
                                setCategoryId(event.target.value === "ALL" ? "ALL" : Number(event.target.value));
                                setPage(0);
                            }}
                            className="min-h-11 rounded-xl border border-[#eadfd6] bg-white px-3 text-sm"
                        >
                            <option value="ALL">All categories</option>
                            {catalogue?.categories.map(category => (
                                <option key={category.id} value={category.id}>{category.name}</option>
                            ))}
                        </select>
                        <select
                            value={filter}
                            onChange={event => {
                                setFilter(event.target.value as InventoryCatalogueFilter);
                                setPage(0);
                            }}
                            className="min-h-11 rounded-xl border border-[#eadfd6] bg-white px-3 text-sm"
                        >
                            <option value="ALL">All statuses</option>
                            <option value="NEEDS_ATTENTION">Needs attention</option>
                            <option value="NOT_CONFIGURED">Not configured</option>
                            <option value="DRAFT">Draft</option>
                            <option value="APPROVED">Approved</option>
                            <option value="READY">Ready</option>
                            <option value="DELAYED">Delayed</option>
                            <option value="UNAVAILABLE">Unavailable</option>
                        </select>
                        <button type="button" onClick={refresh} className="min-h-11 rounded-xl border border-[#eadfd6] px-4 text-sm font-bold text-[#7a1625]">
                            Refresh
                        </button>
                    </div>

                    {selected.size > 0 && canManage && (
                        <div className="fixed bottom-4 left-1/2 z-40 w-[min(980px,calc(100vw-2rem))] -translate-x-1/2 rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-3 shadow-2xl">
                            <div className="flex flex-wrap items-center gap-2">
                                <span className="mr-2 text-sm font-bold text-[#241715]">Bulk actions · {selected.size} selected</span>
                                <ActionButton label="Configure policy" onClick={openConfiguration} />
                                <ActionButton label="Set quantities & finish" onClick={openBulkQuantityWorkspace} disabled={saving} />
                                <ActionButton label="Delayed" onClick={() => changeReadiness("DELAYED")} disabled={saving} />
                                <ActionButton label="Unavailable" danger onClick={() => changeReadiness("UNAVAILABLE")} disabled={saving} />
                                <button type="button" onClick={() => { const next = new Set<number>(); setSelected(next); rememberSelection(branchId, serviceDate, next); closeConfiguration(); }} className="ml-auto px-3 text-sm font-semibold text-[#756763]">Clear</button>
                            </div>
                        </div>
                    )}

                    {showPolicyPanel && (editorItem !== null || selected.size > 0) && (
                        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/45 sm:items-center sm:p-6" role="dialog" aria-modal="true" onMouseDown={event => { if (event.currentTarget === event.target && !saving) closeConfiguration(); }}>
                            <div className="max-h-[92vh] w-full max-w-5xl overflow-y-auto rounded-t-3xl bg-white shadow-2xl sm:rounded-3xl">
                                <div className="sticky top-0 z-10 flex items-start justify-between border-b border-[#eadfd6] bg-white px-5 py-4 sm:px-6">
                                    <div>
                                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#c88a20]">{editorItem ? "Product setup" : `Bulk policy · group ${bulkPolicyGroupIndex + 1} of ${Math.max(bulkPolicyGroups.length, 1)}`}</p>
                                        <h2 className="mt-1 text-xl font-bold text-[#241715]">{editorItem?.productName ?? `${inventoryUnit === "GRAM" ? "Weight" : "Piece"} policy · ${policyTargetItems.length} products`}</h2>
                                        <p className="mt-1 text-xs text-[#756763]">{selectedBranch?.name} · pickup {serviceDate}</p>
                                    </div>
                                    <button type="button" onClick={closeConfiguration} disabled={saving} className="flex h-10 w-10 items-center justify-center rounded-full bg-[#fff4e5] text-xl text-[#7a1625] disabled:opacity-50" aria-label="Close setup">×</button>
                                </div>

                                <div className="p-5 sm:p-6">
                                    {policyEditorMessage && <p className="rounded-xl border border-[#eadfd6] bg-[#fffaf3] px-3 py-2 text-xs leading-5 text-[#7a1625]">{policyEditorMessage}</p>}
                                    <h3 className="mt-5 font-bold text-[#241715]">1. Selling policy</h3>
                                    <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                                        <Field label="Control mode" helpKey="controlMode"><select value={controlMode} onChange={event => setControlMode(event.target.value as InventoryControlMode)} className="field"><option value="READY_STOCK">Ready stock</option><option value="DAILY_PRODUCTION">Daily production</option><option value="MANUAL">Manual allocation</option></select></Field>
                                        <Field label="Inventory unit" helpKey="inventoryUnit"><div className="field flex items-center justify-between"><span className="font-semibold">{inventoryUnit === "GRAM" ? "Weight · kilograms" : "Unit · pieces"}</span><span className="text-xs text-[#756763]">Automatic</span></div></Field>
                                        <Field label={`Safety buffer (${inventoryInputUnit(inventoryUnit)})`} helpKey="safetyBuffer"><input type="number" min="0" step={inventoryUnit === "GRAM" ? ".05" : "1"} value={safetyBuffer} onChange={event => setSafetyBuffer(event.target.value)} className="field" /></Field>
                                        <Field label={`Daily production capacity (${inventoryInputUnit(inventoryUnit)})`} helpKey="dailyMaximum"><input type="number" min="0" step={inventoryUnit === "GRAM" ? ".05" : "1"} value={maximumAllocation} onChange={event => setMaximumAllocation(event.target.value)} placeholder="No maximum" className="field" /></Field>
                                        <Field label="Future ordering window (days)" helpKey="bookingHorizon"><input type="number" min="0" value={bookingHorizon} onChange={event => setBookingHorizon(event.target.value)} className="field" /></Field>
                                        <Field label="Production lead (hours)" helpKey="productionLead"><input type="number" min="0" step=".25" value={leadHours} onChange={event => setLeadHours(event.target.value)} className="field" /></Field>
                                        <Field label="Shelf life (days)" helpKey="shelfLife"><input type="number" min="0" step=".25" value={shelfLifeDays} onChange={event => setShelfLifeDays(event.target.value)} placeholder="Optional" className="field" /></Field>
                                        <div className="flex flex-col justify-end gap-3 text-sm"><label className="flex items-center gap-2"><input type="checkbox" checked={onlineEnabled} onChange={event => setOnlineEnabled(event.target.checked)} />Online ordering enabled <InventoryInfo helpKey="onlineEnabled" /></label><label className="flex items-center gap-2"><input type="checkbox" checked={readyStockRequired} onChange={event => setReadyStockRequired(event.target.checked)} />Ready stock required <InventoryInfo helpKey="readyStockRequired" /></label></div>
                                    </div>

                                    {editorItem && (
                                        <div className="mt-6 rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-4">
                                            <div className="flex flex-wrap items-start justify-between gap-3">
                                                <div><h3 className="font-bold text-[#241715]">2. Quantity for {serviceDate}</h3><p className="mt-1 text-xs text-[#756763]">Complete the daily allocation here—there is no need to return to the table.</p></div>
                                                <button type="button" onClick={() => setEditorReady(editorApproved)} className="rounded-lg border border-[#eadfd6] bg-white px-3 py-2 text-xs font-bold text-[#7a1625]">Ready = approved</button>
                                            </div>
                                            <div className="mt-4 grid gap-3 sm:grid-cols-2">
                                                <Field label={`Approved for online sale (${inventoryInputUnit(inventoryUnit)})`} helpKey="approved"><input type="number" min="0" step={inventoryUnit === "GRAM" ? ".05" : "1"} value={editorApproved} onChange={event => setEditorApproved(event.target.value)} placeholder="Enter quantity" className="field" /></Field>
                                                <Field label={`Physically ready now (${inventoryInputUnit(inventoryUnit)})`} helpKey="ready"><input type="number" min="0" step={inventoryUnit === "GRAM" ? ".05" : "1"} value={editorReady} onChange={event => setEditorReady(event.target.value)} placeholder="Enter quantity" className="field" /></Field>
                                            </div>
                                        </div>
                                    )}

                                    {error && <Message tone="error" text={error} />}
                                </div>

                                <div className="sticky bottom-0 flex flex-wrap justify-end gap-3 border-t border-[#eadfd6] bg-white px-5 py-4 sm:px-6">
                                    <button type="button" onClick={() => { resetPolicyForm(); setPolicyEditorMessage("Form reset to safe defaults. Saved data is unchanged until you save."); }} disabled={saving} className="min-h-11 px-3 text-sm font-semibold text-[#756763] disabled:opacity-50">Reset form</button>
                                    <button type="button" onClick={closeConfiguration} disabled={saving} className="min-h-11 rounded-xl border border-[#eadfd6] bg-white px-5 text-sm font-bold text-[#7a1625] disabled:opacity-50">Cancel</button>
                                    <button type="button" onClick={savePolicies} disabled={saving} className="min-h-11 rounded-xl border border-[#7a1625] bg-white px-5 text-sm font-bold text-[#7a1625] disabled:opacity-50">{editorItem ? "Save policy only" : bulkPolicyGroupIndex + 1 < bulkPolicyGroups.length ? "Save group & continue" : "Save policy & enter quantities"}</button>
                                    {editorItem && <button type="button" onClick={saveItemAndMakeAvailable} disabled={saving} className="min-h-11 rounded-xl bg-[#7a1625] px-6 text-sm font-bold text-white disabled:opacity-50">{saving ? "Saving…" : "Save & make available"}</button>}
                                </div>
                            </div>
                        </div>
                    )}

                    {showBulkQuantityPanel && selectedItems.length > 0 && (
                        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/45 sm:items-center sm:p-6" role="dialog" aria-modal="true" onMouseDown={event => { if (event.currentTarget === event.target && !saving) setShowBulkQuantityPanel(false); }}>
                            <div className="max-h-[94vh] w-full max-w-6xl overflow-y-auto rounded-t-3xl bg-white shadow-2xl sm:rounded-3xl">
                                <div className="sticky top-0 z-20 flex items-start justify-between border-b border-[#eadfd6] bg-white px-5 py-4 sm:px-6">
                                    <div>
                                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#c88a20]">Bulk quantity workspace</p>
                                        <h2 className="mt-1 text-xl font-bold text-[#241715]">Approve {selectedItems.length} products for {serviceDate}</h2>
                                        <p className="mt-1 text-xs text-[#756763]">Products are separated by unit. Ready quantity may stay at zero for products awaiting production.</p>
                                    </div>
                                    <button type="button" onClick={() => setShowBulkQuantityPanel(false)} disabled={saving} className="flex h-10 w-10 items-center justify-center rounded-full bg-[#fff4e5] text-xl text-[#7a1625] disabled:opacity-50" aria-label="Close bulk quantities">×</button>
                                </div>

                                <div className="space-y-5 p-5 sm:p-6">
                                    {(["GRAM", "PIECE"] as InventoryUnit[]).map(unit => {
                                        const group = selectedUnitGroups[unit];
                                        if (group.length === 0) return null;
                                        return (
                                            <section key={unit} className="overflow-hidden rounded-2xl border border-[#eadfd6]">
                                                <div className="bg-[#fffaf3] p-4">
                                                    <div className="flex flex-wrap items-center justify-between gap-3">
                                                        <div>
                                                            <h3 className="font-bold text-[#241715]">{unit === "GRAM" ? "Weight products" : "Piece products"} · {group.length}</h3>
                                                            <p className="mt-1 text-xs text-[#756763]">Enter all values in {inventoryInputUnit(unit)}.</p>
                                                        </div>
                                                        <div className="flex flex-wrap gap-2">
                                                            <button type="button" onClick={() => fillGroupFromMaximum(unit)} className="rounded-lg border border-[#eadfd6] bg-white px-3 py-2 text-xs font-bold text-[#7a1625]">Use each daily maximum</button>
                                                            <button type="button" onClick={() => fillGroupFromForecast(unit)} className="rounded-lg border border-[#eadfd6] bg-white px-3 py-2 text-xs font-bold text-[#7a1625]">Use forecasts</button>
                                                            <button type="button" onClick={() => copyApprovedToReady(unit)} className="rounded-lg border border-[#eadfd6] bg-white px-3 py-2 text-xs font-bold text-[#7a1625]">Ready = approved</button>
                                                            <button type="button" onClick={() => clearGroupQuantities(unit)} className="px-3 py-2 text-xs font-bold text-[#756763]">Clear</button>
                                                        </div>
                                                    </div>
                                                    <div className="mt-3 flex flex-wrap items-end gap-2">
                                                        <label className="text-xs font-bold uppercase tracking-wide text-[#756763]">Same value for this group<input type="number" min="0" step={unit === "GRAM" ? ".05" : "1"} value={bulkGroupInputs[unit]} onChange={event => setBulkGroupInputs(current => ({...current, [unit]: event.target.value}))} placeholder={unit === "GRAM" ? "e.g. 10 kg" : "e.g. 100 pieces"} className="mt-1 min-h-10 w-40 rounded-lg border border-[#eadfd6] bg-white px-3 text-sm normal-case" /></label>
                                                        <button type="button" onClick={() => applyGroupValue(unit, "approved")} className="min-h-10 rounded-lg border border-[#7a1625] bg-white px-3 text-xs font-bold text-[#7a1625]">Apply to approved</button>
                                                        <button type="button" onClick={() => applyGroupValue(unit, "ready")} className="min-h-10 rounded-lg border border-[#7a1625] bg-white px-3 text-xs font-bold text-[#7a1625]">Apply to ready</button>
                                                    </div>
                                                </div>

                                                <div className="divide-y divide-[#f1e8e1]">
                                                    {group.map(item => {
                                                        const policy = effectivePolicy(item);
                                                        const itemError = bulkQuantityError(item);
                                                        const readyValue = readyDrafts[item.branchProductId] ?? "0";
                                                        const readyNumber = inputQuantityToApi(readyValue || "0", unit);
                                                        return (
                                                            <div key={item.branchProductId} className={`grid gap-3 p-4 lg:grid-cols-[minmax(180px,1.4fr)_150px_150px_150px_minmax(180px,1fr)] lg:items-start ${itemError ? "bg-red-50/40" : "bg-white"}`}>
                                                                <div><p className="font-bold text-[#241715]">{item.productName}</p><p className="mt-1 text-xs text-[#756763]">{item.categoryName} · {policy?.controlMode.replaceAll("_", " ").toLowerCase()}</p></div>
                                                                <div><p className="text-[10px] font-bold uppercase tracking-wide text-[#756763]">Daily maximum</p><p className="mt-2 text-sm font-semibold text-[#241715]">{policy?.maximumDailyAllocation === null ? "No maximum" : formatInventoryQuantity(policy?.maximumDailyAllocation, unit)}</p></div>
                                                                <label className="text-[10px] font-bold uppercase tracking-wide text-[#756763]">Approved<input type="number" min="0" step={unit === "GRAM" ? ".05" : "1"} value={approvedDrafts[item.branchProductId] ?? ""} onChange={event => setApprovedDrafts(current => ({...current, [item.branchProductId]: event.target.value}))} className="mt-1 min-h-10 w-full rounded-lg border border-[#eadfd6] bg-white px-3 text-sm font-medium normal-case text-[#241715]" /></label>
                                                                <label className="text-[10px] font-bold uppercase tracking-wide text-[#756763]">Ready now<input type="number" min="0" step={unit === "GRAM" ? ".05" : "1"} value={readyValue} onChange={event => setReadyDrafts(current => ({...current, [item.branchProductId]: event.target.value}))} className="mt-1 min-h-10 w-full rounded-lg border border-[#eadfd6] bg-white px-3 text-sm font-medium normal-case text-[#241715]" /></label>
                                                                <div><span className={`inline-flex rounded-full px-2.5 py-1 text-[11px] font-bold ${readyNumber > 0 ? "bg-emerald-50 text-emerald-700" : "bg-blue-50 text-blue-700"}`}>{readyNumber > 0 ? "Will be ready" : "Approved · awaiting production"}</span>{itemError && <p className="mt-2 text-xs font-semibold leading-5 text-red-700">{itemError}</p>}</div>
                                                            </div>
                                                        );
                                                    })}
                                                </div>
                                            </section>
                                        );
                                    })}

                                    <div className="rounded-2xl bg-blue-50 p-4 text-xs leading-5 text-blue-800"><span className="font-bold">Safe readiness rule: </span>Ready greater than zero marks the product ready. Ready equal to zero approves its allocation but leaves it awaiting production.</div>
                                    {error && <Message tone="error" text={error} />}
                                </div>

                                <div className="sticky bottom-0 flex flex-wrap items-center justify-between gap-3 border-t border-[#eadfd6] bg-white px-5 py-4 sm:px-6">
                                    <p className="text-xs text-[#756763]">The complete batch is validated and saved in one backend transaction.</p>
                                    <div className="flex gap-3"><button type="button" onClick={() => setShowBulkQuantityPanel(false)} disabled={saving} className="min-h-11 rounded-xl border border-[#eadfd6] px-5 text-sm font-bold text-[#756763] disabled:opacity-50">Back</button><button type="button" onClick={confirmBulkQuantities} disabled={saving} className="min-h-11 rounded-xl bg-[#7a1625] px-6 text-sm font-bold text-white disabled:opacity-50">{saving ? "Saving…" : "Confirm bulk update"}</button></div>
                                </div>
                            </div>
                        </div>
                    )}

                    {error && <Message tone="error" text={error} />}
                    {notice && <Message tone="success" text={notice} />}
                </div>

                <div className="overflow-x-auto">
                    <table className="min-w-[1060px] w-full border-collapse text-left">
                        <thead className="bg-[#fffaf3] text-xs uppercase tracking-wide text-[#756763]">
                            <tr>
                                <th className="px-5 py-3"><input type="checkbox" checked={Boolean(catalogue?.content.length) && catalogue!.content.every(item => selected.has(item.branchProductId))} onChange={toggleVisible} aria-label="Select visible products" /></th>
                                <th className="px-3 py-3">Product</th>
                                <th className="px-3 py-3"><InventoryColumnHeader label="Status" helpKey="status" /></th>
                                <th className="px-3 py-3"><InventoryColumnHeader label="Approved" helpKey="approved" /></th>
                                <th className="px-3 py-3"><InventoryColumnHeader label="Ready" helpKey="ready" /></th>
                                <th className="px-3 py-3"><InventoryColumnHeader label="Held / committed" helpKey="held" /></th>
                                <th className="px-3 py-3"><InventoryColumnHeader label="Available online" helpKey="available" /></th>
                                <th className="px-5 py-3"><InventoryColumnHeader label="Attention" helpKey="attention" /></th>
                                <th className="sticky right-0 z-20 w-24 border-l border-[#eadfd6] bg-[#fffaf3] px-3 py-3 text-center shadow-[-10px_0_18px_-16px_rgba(36,23,21,0.35)]">Action</th>
                            </tr>
                        </thead>
                        <tbody>
                            {catalogue?.content.map(item => (
                                <InventoryRow
                                    key={item.branchProductId}
                                    item={item}
                                    checked={selected.has(item.branchProductId)}
                                    canManage={canManage}
                                    approvedDraft={approvedDrafts[item.branchProductId] ?? ""}
                                    readyDraft={readyDrafts[item.branchProductId] ?? ""}
                                    onToggle={() => toggleItem(item.branchProductId)}
                                    onEdit={() => openItemEditor(item)}
                                    onApprovedChange={value => setApprovedDrafts(current => ({...current, [item.branchProductId]: value}))}
                                    onReadyChange={value => setReadyDrafts(current => ({...current, [item.branchProductId]: value}))}
                                />
                            ))}
                        </tbody>
                    </table>
                </div>

                {loading && <div className="p-10 text-center text-sm text-[#756763]">Loading inventory…</div>}
                {!loading && catalogue?.content.length === 0 && <div className="p-10 text-center"><p className="font-bold text-[#241715]">No products match these filters</p><p className="mt-2 text-sm text-[#756763]">Clear a filter or choose another pickup date.</p></div>}

                {catalogue && catalogue.totalPages > 1 && (
                    <div className="flex items-center justify-between border-t border-[#eadfd6] p-5 text-sm">
                        <span className="text-[#756763]">Page {catalogue.page + 1} of {catalogue.totalPages} · {catalogue.totalElements} products</span>
                        <div className="flex gap-2"><button type="button" disabled={page === 0} onClick={() => setPage(value => value - 1)} className="rounded-lg border px-3 py-2 disabled:opacity-40">Previous</button><button type="button" disabled={page + 1 >= catalogue.totalPages} onClick={() => setPage(value => value + 1)} className="rounded-lg border px-3 py-2 disabled:opacity-40">Next</button></div>
                    </div>
                )}
            </section>

            <style jsx global>{`
                .field { min-height: 2.75rem; width: 100%; border-radius: .75rem; border: 1px solid #eadfd6; background: white; padding: 0 .75rem; font-size: .875rem; color: #241715; }
            `}</style>
        </main>
    );
}

function SummaryCard({label, value, tone = "normal", active, onClick}: {label: string; value: number; tone?: "normal" | "warning" | "success" | "danger"; active: boolean; onClick: () => void}) {
    const toneClass = tone === "warning" ? "text-amber-700" : tone === "success" ? "text-emerald-700" : tone === "danger" ? "text-red-700" : "text-[#7a1625]";
    return <button type="button" onClick={onClick} className={`rounded-2xl border bg-white p-4 text-left shadow-sm transition ${active ? "border-[#7a1625] ring-2 ring-[#7a1625]/10" : "border-[#eadfd6] hover:border-[#c88a20]"}`}><p className="text-xs font-bold uppercase tracking-wide text-[#756763]">{label}</p><p className={`mt-2 text-2xl font-bold ${toneClass}`}>{value}</p></button>;
}

function ActionButton({label, onClick, disabled, danger}: {label: string; onClick: () => void; disabled?: boolean; danger?: boolean}) {
    return <button type="button" onClick={onClick} disabled={disabled} className={`min-h-9 rounded-lg px-3 text-xs font-bold disabled:opacity-50 ${danger ? "border border-red-200 bg-white text-red-700" : "bg-white text-[#7a1625] shadow-sm"}`}>{label}</button>;
}

function Field({label, helpKey, children}: {label: string; helpKey?: string; children: ReactNode}) {
    return <label className="text-xs font-bold uppercase tracking-wide text-[#756763]"><span className="inline-flex items-center gap-2">{label}{helpKey && <InventoryInfo helpKey={helpKey} />}</span><span className="mt-2 block normal-case">{children}</span></label>;
}

function Message({tone, text}: {tone: "error" | "success"; text: string}) {
    return <p className={`mt-4 rounded-xl border px-4 py-3 text-sm ${tone === "error" ? "border-red-200 bg-red-50 text-red-700" : "border-emerald-200 bg-emerald-50 text-emerald-700"}`}>{text}</p>;
}

function InventoryRow({item, checked, canManage, approvedDraft, readyDraft, onToggle, onEdit, onApprovedChange, onReadyChange}: {item: InventoryCatalogueItem; checked: boolean; canManage: boolean; approvedDraft: string; readyDraft: string; onToggle: () => void; onEdit: () => void; onApprovedChange: (value: string) => void; onReadyChange: (value: string) => void}) {
    const unit = item.policy?.inventoryUnit ?? (item.saleMode === "WEIGHT" ? "GRAM" : "PIECE");
    const status = item.allocation?.status ?? (item.policy ? "NOT APPROVED" : "NOT CONFIGURED");
    return (
        <tr className={`border-t border-[#f1e8e1] align-top ${checked ? "bg-[#fffaf3]" : "bg-white"}`}>
            <td className="px-5 py-4"><input type="checkbox" checked={checked} onChange={onToggle} aria-label={`Select ${item.productName}`} /></td>
            <td className="px-3 py-4"><p className="font-bold text-[#241715]">{item.productName}</p><p className="mt-1 text-xs text-[#756763]">{item.categoryName} · {item.productCode}</p><p className="mt-1 text-[11px] font-semibold uppercase tracking-wide text-[#c88a20]">{item.saleMode === "WEIGHT" ? "Sold by weight" : "Sold by unit"}</p></td>
            <td className="px-3 py-4"><span className={`inline-flex rounded-full border px-2.5 py-1 text-[11px] font-bold ${statusClasses(status)}`}>{status.replaceAll("_", " ")}</span>{item.policy && !item.policy.onlineEnabled && <p className="mt-2 text-xs text-[#756763]">Online disabled</p>}</td>
            <td className="px-3 py-4"><input type="number" min="0" step={unit === "GRAM" ? ".05" : "1"} disabled={!canManage || !item.policy} value={approvedDraft} onChange={event => onApprovedChange(event.target.value)} placeholder="0" className="w-28 rounded-lg border border-[#eadfd6] px-3 py-2 text-sm disabled:bg-gray-50" /><p className="mt-1 text-[11px] text-[#756763]">{inventoryInputUnit(unit)}</p></td>
            <td className="px-3 py-4"><input type="number" min="0" step={unit === "GRAM" ? ".05" : "1"} disabled={!canManage || !item.policy} value={readyDraft} onChange={event => onReadyChange(event.target.value)} placeholder="0" className="w-28 rounded-lg border border-[#eadfd6] px-3 py-2 text-sm disabled:bg-gray-50" /><p className="mt-1 text-[11px] text-[#756763]">Recorded: {formatInventoryQuantity(item.allocation?.readyQuantity, unit)}</p></td>
            <td className="px-3 py-4 text-sm text-[#241715]"><p>{formatInventoryQuantity(item.allocation?.heldQuantity, unit)} held</p><p className="mt-1 text-[#756763]">{formatInventoryQuantity(item.allocation?.committedQuantity, unit)} committed</p></td>
            <td className="px-3 py-4"><p className="font-bold text-[#241715]">{formatInventoryQuantity(item.allocation?.availableQuantity, unit)}</p><p className="mt-1 text-xs text-[#756763]">Buffer: {formatInventoryQuantity(item.allocation?.safetyBufferQuantity ?? item.policy?.defaultSafetyBuffer, unit)}</p></td>
            <td className="px-5 py-4">{item.needsAttention ? <div className="max-w-xs rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-800"><span className="font-bold">Action required</span><br />{item.attentionMessage}</div> : <span className="text-sm font-semibold text-emerald-700">No action needed</span>}</td>
            <td className={`sticky right-0 z-10 w-24 border-l border-[#eadfd6] px-3 py-4 text-center shadow-[-10px_0_18px_-16px_rgba(36,23,21,0.35)] ${checked ? "bg-[#fffaf3]" : "bg-white"}`}><button type="button" onClick={onEdit} disabled={!canManage} className="min-h-10 whitespace-nowrap rounded-xl border border-[#7a1625] bg-white px-3 text-xs font-bold text-[#7a1625] transition hover:bg-[#fff4e5] disabled:opacity-40">{item.policy ? "Edit" : "Set up"}</button></td>
        </tr>
    );
}
