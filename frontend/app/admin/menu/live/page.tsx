"use client";

import {
    useCallback,
    useEffect,
    useMemo,
    useState
} from "react";

import Link
    from "next/link";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    getAdminBranchMenu,
    updateAdminBranchProduct
} from "@/services/adminMenuApi";

import type {
    AdminBranchProduct
} from "@/types/adminMenu";


interface Branch {
    id: number;
    code: string;
    name: string;
    address: string;
    city: string;
    state: string;
    pincode: string;
    phone: string;
    latitude: number | null;
    longitude: number | null;
    openingTime: string;
    closingTime: string;
    active: boolean;
}


type AvailabilityFilter =
    "ALL"
    | "AVAILABLE"
    | "UNAVAILABLE";


const API_BASE =
    process.env.NEXT_PUBLIC_API_BASE_URL
    ?? "http://localhost:8080";


function formatPrice(
    price: number
) {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style: "currency",
            currency: "INR",
            maximumFractionDigits: 2
        }
    ).format(
        price
    );
}


export default function AdminLiveMenuPage() {

    const {
        profile,
        authorization
    } =
        useAdminAuth();


    const [
        branches,
        setBranches
    ] =
        useState<Branch[]>(
            []
        );


    const [
        selectedBranchId,
        setSelectedBranchId
    ] =
        useState<number | null>(
            null
        );


    const [
        menu,
        setMenu
    ] =
        useState<AdminBranchProduct[]>(
            []
        );


    const [
        search,
        setSearch
    ] =
        useState("");


    const [
        categoryId,
        setCategoryId
    ] =
        useState<number | "ALL">(
            "ALL"
        );


    const [
        availabilityFilter,
        setAvailabilityFilter
    ] =
        useState<AvailabilityFilter>(
            "ALL"
        );


    const [
        branchesLoading,
        setBranchesLoading
    ] =
        useState(true);


    const [
        menuLoading,
        setMenuLoading
    ] =
        useState(false);


    const [
        error,
        setError
    ] =
        useState<string | null>(
            null
        );


    /*
     * =========================================================
     * LOAD BRANCHES
     * =========================================================
     */

    useEffect(
        () => {

            if (!profile) {
                return;
            }


            const currentProfile =
                profile;


            const controller =
                new AbortController();


            async function loadBranches() {

                try {

                    const response =
                        await fetch(
                            `${API_BASE}/api/branches`,
                            {
                                signal:
                                    controller.signal,

                                cache:
                                    "no-store"
                            }
                        );


                    if (!response.ok) {

                        throw new Error(
                            "Unable to load branches."
                        );
                    }


                    const allBranches:
                        Branch[] =
                        await response.json();


                    const activeBranches =
                        allBranches.filter(
                            branch =>
                                branch.active
                        );


                    const allowedBranches =
                        currentProfile.roleName
                        === "OWNER_ADMIN"
                            ? activeBranches
                            : activeBranches.filter(
                                branch =>
                                    currentProfile.branchIds.includes(
                                        branch.id
                                    )
                            );


                    setBranches(
                        allowedBranches
                    );


                    if (
                        allowedBranches.length
                        > 0
                    ) {

                        setSelectedBranchId(
                            current =>
                                current
                                ?? allowedBranches[0].id
                        );
                    }

                } catch (exception) {

                    if (
                        exception
                        instanceof DOMException
                        &&
                        exception.name
                        === "AbortError"
                    ) {

                        return;
                    }


                    setError(
                        exception
                        instanceof Error
                            ? exception.message
                            : "Unable to load branches."
                    );

                } finally {

                    setBranchesLoading(
                        false
                    );
                }
            }


            void loadBranches();


            return () => {

                controller.abort();
            };

        },
        [
            profile
        ]
    );


    /*
     * =========================================================
     * REUSABLE MENU LOADER
     * =========================================================
     */

    const reloadMenu =
        useCallback(
            async (
                branchId: number,
                adminAuthorization: string,
                signal?: AbortSignal,
                showLoader: boolean = false
            ) => {

                if (
                    showLoader
                ) {

                    setMenuLoading(
                        true
                    );
                }


                try {

                    const response =
                        await getAdminBranchMenu(
                            branchId,
                            adminAuthorization,
                            signal
                        );


                    setMenu(
                        response
                    );


                    return response;

                } finally {

                    if (
                        showLoader
                    ) {

                        setMenuLoading(
                            false
                        );
                    }
                }
            },
            []
        );


    /*
     * =========================================================
     * LOAD MENU WHEN BRANCH CHANGES
     * =========================================================
     */

    useEffect(
        () => {

            if (
                selectedBranchId
                === null
                ||
                authorization
                === null
            ) {

                return;
            }


            const currentBranchId =
                selectedBranchId;


            const currentAuthorization =
                authorization;


            const controller =
                new AbortController();


            async function loadSelectedBranchMenu() {

                setError(
                    null
                );


                try {

                    await reloadMenu(
                        currentBranchId,
                        currentAuthorization,
                        controller.signal,
                        true
                    );

                } catch (exception) {

                    if (
                        exception
                        instanceof DOMException
                        &&
                        exception.name
                        === "AbortError"
                    ) {

                        return;
                    }


                    setError(
                        exception
                        instanceof Error
                            ? exception.message
                            : "Unable to load menu."
                    );
                }
            }


            void loadSelectedBranchMenu();


            return () => {

                controller.abort();
            };

        },
        [
            selectedBranchId,
            authorization,
            reloadMenu
        ]
    );


    /*
     * =========================================================
     * CATEGORY OPTIONS
     * =========================================================
     */

    const categories =
        useMemo(
            () => {

                const map =
                    new Map<
                        number,
                        {
                            id: number;
                            name: string;
                        }
                    >();


                menu.forEach(
                    item => {

                        map.set(
                            item.categoryId,
                            {
                                id:
                                    item.categoryId,

                                name:
                                    item.categoryName
                            }
                        );
                    }
                );


                return Array
                    .from(
                        map.values()
                    )
                    .sort(
                        (
                            first,
                            second
                        ) =>
                            first.name.localeCompare(
                                second.name
                            )
                    );
            },
            [
                menu
            ]
        );


    /*
     * =========================================================
     * FILTER MENU
     * =========================================================
     */

    const filteredMenu =
        useMemo(
            () => {

                const normalizedSearch =
                    search
                        .trim()
                        .toLowerCase();


                return menu.filter(
                    item => {

                        if (
                            categoryId
                            !== "ALL"
                            &&
                            item.categoryId
                            !== categoryId
                        ) {

                            return false;
                        }


                        if (
                            availabilityFilter
                            === "AVAILABLE"
                            &&
                            !item.available
                        ) {

                            return false;
                        }


                        if (
                            availabilityFilter
                            === "UNAVAILABLE"
                            &&
                            item.available
                        ) {

                            return false;
                        }


                        if (
                            normalizedSearch
                        ) {

                            const searchable =
                                [
                                    item.productName,
                                    item.productCode,
                                    item.categoryName
                                ]
                                    .join(
                                        " "
                                    )
                                    .toLowerCase();


                            if (
                                !searchable.includes(
                                    normalizedSearch
                                )
                            ) {

                                return false;
                            }
                        }


                        return true;
                    }
                );
            },
            [
                menu,
                search,
                categoryId,
                availabilityFilter
            ]
        );


    function replaceUpdatedProduct(
        updated:
            AdminBranchProduct
    ) {

        setMenu(
            currentMenu =>
                currentMenu.map(
                    item =>
                        item.productId
                        === updated.productId
                            ? updated
                            : item
                )
        );
    }


    async function synchronizeAfterUpdate(
        updated:
            AdminBranchProduct
    ) {

        replaceUpdatedProduct(
            updated
        );


        if (
            selectedBranchId
            === null
            ||
            authorization
            === null
        ) {

            return;
        }


        await reloadMenu(
            selectedBranchId,
            authorization
        );
    }


    const selectedBranch =
        branches.find(
            branch =>
                branch.id
                === selectedBranchId
        );


    return (
        <div
            className="
                px-4
                py-6

                sm:px-6
                sm:py-8

                lg:px-8
            "
        >

            <div
                className="
                    mx-auto
                    w-full
                    max-w-7xl
                "
            >

                <div
                    className="
                        flex
                        flex-col
                        gap-5

                        lg:flex-row
                        lg:items-start
                        lg:justify-between
                    "
                >

                    <div
                        className="
                            min-w-0
                        "
                    >

                        <Link
                            href="/admin/menu"
                            className="
                                text-sm
                                font-semibold
                                text-[#7a1625]

                                hover:underline
                            "
                        >
                            ← Menu Management
                        </Link>


                        <p
                            className="
                                mt-5
                                text-xs
                                font-semibold
                                uppercase
                                tracking-[0.16em]
                                text-[#c88a20]
                            "
                        >
                            Daily operations
                        </p>


                        <h1
                            className="
                                mt-2
                                text-3xl
                                font-bold
                                tracking-tight
                                text-[#241715]

                                sm:text-4xl
                            "
                        >
                            Live Menu
                        </h1>


                        <p
                            className="
                                mt-3
                                max-w-3xl
                                text-sm
                                leading-6
                                text-[#756763]
                            "
                        >
                            Control product availability, branch pricing and display order without uploading a spreadsheet.
                        </p>

                    </div>


                    <div
                        className="
                            w-full
                            shrink-0

                            lg:w-80
                        "
                    >

                        <label
                            htmlFor="branch"
                            className="
                                mb-2
                                block
                                text-sm
                                font-semibold
                                text-[#241715]
                            "
                        >
                            Branch
                        </label>


                        <select
                            id="branch"
                            value={
                                selectedBranchId
                                ?? ""
                            }
                            disabled={
                                branchesLoading
                                ||
                                branches.length
                                === 0
                            }
                            onChange={
                                event => {

                                    const value =
                                        Number(
                                            event.target.value
                                        );


                                    setSelectedBranchId(
                                        value
                                    );


                                    setCategoryId(
                                        "ALL"
                                    );


                                    setSearch(
                                        ""
                                    );


                                    setAvailabilityFilter(
                                        "ALL"
                                    );
                                }
                            }
                            className="
                                min-h-12
                                w-full
                                rounded-xl
                                border
                                border-[#eadfd6]
                                bg-white
                                px-4
                                text-sm
                                font-semibold
                                text-[#241715]
                                outline-none

                                focus:border-[#c88a20]
                                focus:ring-4
                                focus:ring-[#f6dfad]/40

                                disabled:bg-[#f8f4f1]
                            "
                        >

                            {
                                branches.length
                                === 0
                                && (

                                    <option value="">
                                        No branch available
                                    </option>

                                )
                            }


                            {
                                branches.map(
                                    branch => (

                                        <option
                                            key={
                                                branch.id
                                            }
                                            value={
                                                branch.id
                                            }
                                        >
                                            {
                                                branch.name
                                            }
                                        </option>

                                    )
                                )
                            }

                        </select>

                    </div>

                </div>


                {
                    selectedBranch
                    && (

                        <div
                            className="
                                mt-6
                                rounded-2xl
                                border
                                border-[#eadfd6]
                                bg-white
                                px-5
                                py-4
                            "
                        >

                            <div
                                className="
                                    flex
                                    flex-col
                                    gap-2

                                    sm:flex-row
                                    sm:items-center
                                    sm:justify-between
                                "
                            >

                                <div
                                    className="
                                        min-w-0
                                    "
                                >

                                    <p
                                        className="
                                            font-bold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            selectedBranch.name
                                        }
                                    </p>


                                    <p
                                        className="
                                            mt-1
                                            text-sm
                                            text-[#756763]
                                        "
                                    >
                                        {
                                            selectedBranch.code
                                        }
                                        {" • "}
                                        {
                                            selectedBranch.address
                                        }
                                    </p>

                                </div>


                                <p
                                    className="
                                        shrink-0
                                        text-sm
                                        font-semibold
                                        text-[#7a1625]
                                    "
                                >
                                    {
                                        menu.length
                                    } menu items
                                </p>

                            </div>

                        </div>

                    )
                }


                <section
                    className="
                        mt-6
                        rounded-2xl
                        border
                        border-[#eadfd6]
                        bg-white
                        p-4

                        sm:p-5
                    "
                >

                    <div
                        className="
                            grid
                            gap-4

                            md:grid-cols-3
                        "
                    >

                        <div>

                            <label
                                htmlFor="menu-search"
                                className="
                                    mb-2
                                    block
                                    text-sm
                                    font-semibold
                                    text-[#241715]
                                "
                            >
                                Search
                            </label>


                            <input
                                id="menu-search"
                                type="search"
                                value={
                                    search
                                }
                                onChange={
                                    event =>
                                        setSearch(
                                            event.target.value
                                        )
                                }
                                placeholder="Product, code or category"
                                className="
                                    min-h-11
                                    w-full
                                    rounded-xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    px-4
                                    text-sm
                                    text-[#241715]
                                    outline-none

                                    focus:border-[#c88a20]
                                    focus:ring-4
                                    focus:ring-[#f6dfad]/40
                                "
                            />

                        </div>


                        <div>

                            <label
                                htmlFor="category-filter"
                                className="
                                    mb-2
                                    block
                                    text-sm
                                    font-semibold
                                    text-[#241715]
                                "
                            >
                                Category
                            </label>


                            <select
                                id="category-filter"
                                value={
                                    categoryId
                                }
                                onChange={
                                    event => {

                                        const value =
                                            event.target.value;


                                        setCategoryId(
                                            value
                                            === "ALL"
                                                ? "ALL"
                                                : Number(
                                                    value
                                                )
                                        );
                                    }
                                }
                                className="
                                    min-h-11
                                    w-full
                                    rounded-xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    px-4
                                    text-sm
                                    text-[#241715]
                                    outline-none

                                    focus:border-[#c88a20]
                                    focus:ring-4
                                    focus:ring-[#f6dfad]/40
                                "
                            >

                                <option value="ALL">
                                    All categories
                                </option>


                                {
                                    categories.map(
                                        category => (

                                            <option
                                                key={
                                                    category.id
                                                }
                                                value={
                                                    category.id
                                                }
                                            >
                                                {
                                                    category.name
                                                }
                                            </option>

                                        )
                                    )
                                }

                            </select>

                        </div>


                        <div>

                            <label
                                htmlFor="availability-filter"
                                className="
                                    mb-2
                                    block
                                    text-sm
                                    font-semibold
                                    text-[#241715]
                                "
                            >
                                Availability
                            </label>


                            <select
                                id="availability-filter"
                                value={
                                    availabilityFilter
                                }
                                onChange={
                                    event =>
                                        setAvailabilityFilter(
                                            event.target.value as AvailabilityFilter
                                        )
                                }
                                className="
                                    min-h-11
                                    w-full
                                    rounded-xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    px-4
                                    text-sm
                                    text-[#241715]
                                    outline-none

                                    focus:border-[#c88a20]
                                    focus:ring-4
                                    focus:ring-[#f6dfad]/40
                                "
                            >

                                <option value="ALL">
                                    All items
                                </option>

                                <option value="AVAILABLE">
                                    Available
                                </option>

                                <option value="UNAVAILABLE">
                                    Unavailable
                                </option>

                            </select>

                        </div>

                    </div>

                </section>


                {
                    error
                    && (

                        <div
                            role="alert"
                            className="
                                mt-6
                                rounded-2xl
                                border
                                border-red-200
                                bg-red-50
                                px-5
                                py-4
                                text-sm
                                font-medium
                                text-red-700
                            "
                        >
                            {error}
                        </div>

                    )
                }


                {
                    menuLoading
                    ? (

                        <div
                            className="
                                mt-6
                                rounded-2xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-8
                                text-center
                                text-sm
                                font-semibold
                                text-[#756763]
                            "
                        >
                            Loading branch menu...
                        </div>

                    )
                    : filteredMenu.length
                    === 0
                        ? (

                            <div
                                className="
                                    mt-6
                                    rounded-2xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    p-8
                                    text-center
                                "
                            >

                                <p
                                    className="
                                        font-semibold
                                        text-[#241715]
                                    "
                                >
                                    No menu items found.
                                </p>


                                <p
                                    className="
                                        mt-2
                                        text-sm
                                        text-[#756763]
                                    "
                                >
                                    Try changing the search or filters.
                                </p>

                            </div>

                        )
                        : (

                            <div
                                className="
                                    mt-6
                                    space-y-5
                                "
                            >

                                {
                                    filteredMenu.map(
                                        item => (

                                            <MenuProductRow
                                                key={
                                                    [
                                                        item.branchProductId,
                                                        item.priceOverride
                                                            ?? "base",
                                                        item.displayOrder,
                                                        item.available
                                                    ].join(
                                                        "-"
                                                    )
                                                }
                                                item={
                                                    item
                                                }
                                                branchId={
                                                    selectedBranchId!
                                                }
                                                authorization={
                                                    authorization!
                                                }
                                                onUpdated={
                                                    synchronizeAfterUpdate
                                                }
                                            />

                                        )
                                    )
                                }

                            </div>

                        )
                }

            </div>

        </div>
    );
}


function MenuProductRow({
    item,
    branchId,
    authorization,
    onUpdated
}: {
    item: AdminBranchProduct;

    branchId: number;

    authorization: string;

    onUpdated: (
        updated: AdminBranchProduct
    ) => Promise<void>;
}) {

    const [
        priceOverride,
        setPriceOverride
    ] =
        useState(
            item.priceOverride
            !== null
                ? String(
                    item.priceOverride
                )
                : ""
        );


    const [
        displayOrder,
        setDisplayOrder
    ] =
        useState(
            String(
                item.displayOrder
            )
        );


    const [
        savingAvailability,
        setSavingAvailability
    ] =
        useState(false);


    const [
        savingPrice,
        setSavingPrice
    ] =
        useState(false);


    const [
        savingOrder,
        setSavingOrder
    ] =
        useState(false);


    const [
        rowError,
        setRowError
    ] =
        useState<string | null>(
            null
        );


    const [
        successMessage,
        setSuccessMessage
    ] =
        useState<string | null>(
            null
        );


    function beginUpdate() {

        setRowError(
            null
        );


        setSuccessMessage(
            null
        );
    }


    async function toggleAvailability() {

        if (
            savingAvailability
        ) {

            return;
        }


        setSavingAvailability(
            true
        );


        beginUpdate();


        try {

            const updated =
                await updateAdminBranchProduct(
                    branchId,
                    item.productId,
                    {
                        available:
                            !item.available
                    },
                    authorization
                );


            await onUpdated(
                updated
            );


            setSuccessMessage(
                updated.available
                    ? "Item marked available successfully."
                    : "Item marked unavailable successfully."
            );

        } catch (exception) {

            setRowError(
                exception
                instanceof Error
                    ? exception.message
                    : "Unable to update availability."
            );

        } finally {

            setSavingAvailability(
                false
            );
        }
    }


    async function savePrice() {

        if (
            savingPrice
        ) {

            return;
        }


        const trimmed =
            priceOverride.trim();


        if (!trimmed) {

            setRowError(
                "Enter a branch price or use Reset to Base Price."
            );


            setSuccessMessage(
                null
            );


            return;
        }


        const value =
            Number(
                trimmed
            );


        if (
            !Number.isFinite(
                value
            )
            ||
            value <= 0
        ) {

            setRowError(
                "Branch price must be greater than zero."
            );


            setSuccessMessage(
                null
            );


            return;
        }


        setSavingPrice(
            true
        );


        beginUpdate();


        try {

            const updated =
                await updateAdminBranchProduct(
                    branchId,
                    item.productId,
                    {
                        priceOverride:
                            value
                    },
                    authorization
                );


            setPriceOverride(
                updated.priceOverride
                !== null
                    ? String(
                        updated.priceOverride
                    )
                    : ""
            );


            await onUpdated(
                updated
            );


            setSuccessMessage(
                "Branch price updated successfully."
            );

        } catch (exception) {

            setRowError(
                exception
                instanceof Error
                    ? exception.message
                    : "Unable to update branch price."
            );

        } finally {

            setSavingPrice(
                false
            );
        }
    }


    async function resetPrice() {

        if (
            savingPrice
        ) {

            return;
        }


        setSavingPrice(
            true
        );


        beginUpdate();


        try {

            const updated =
                await updateAdminBranchProduct(
                    branchId,
                    item.productId,
                    {
                        clearPriceOverride:
                            true
                    },
                    authorization
                );


            setPriceOverride(
                ""
            );


            await onUpdated(
                updated
            );


            setSuccessMessage(
                "Branch price reset to the base price."
            );

        } catch (exception) {

            setRowError(
                exception
                instanceof Error
                    ? exception.message
                    : "Unable to reset branch price."
            );

        } finally {

            setSavingPrice(
                false
            );
        }
    }


    async function saveDisplayOrder() {

        if (
            savingOrder
        ) {

            return;
        }


        const value =
            Number(
                displayOrder
            );


        if (
            !Number.isInteger(
                value
            )
            ||
            value < 0
        ) {

            setRowError(
                "Display order must be zero or greater."
            );


            setSuccessMessage(
                null
            );


            return;
        }


        setSavingOrder(
            true
        );


        beginUpdate();


        try {

            const updated =
                await updateAdminBranchProduct(
                    branchId,
                    item.productId,
                    {
                        displayOrder:
                            value
                    },
                    authorization
                );


            setDisplayOrder(
                String(
                    updated.displayOrder
                )
            );


            await onUpdated(
                updated
            );


            setSuccessMessage(
                "Display order updated successfully."
            );

        } catch (exception) {

            setRowError(
                exception
                instanceof Error
                    ? exception.message
                    : "Unable to update display order."
            );

        } finally {

            setSavingOrder(
                false
            );
        }
    }


    return (
        <article
            className="
                w-full
                overflow-hidden
                rounded-2xl
                border
                border-[#eadfd6]
                bg-white
            "
        >

            {/* PRODUCT INFORMATION */}

            <div
                className="
                    w-full
                    border-b
                    border-[#eadfd6]
                    p-5

                    sm:p-6
                "
            >

                <div
                    className="
                        flex
                        flex-wrap
                        items-center
                        gap-2
                    "
                >

                    <h2
                        className="
                            text-lg
                            font-bold
                            text-[#241715]

                            sm:text-xl
                        "
                    >
                        {
                            item.productName
                        }
                    </h2>


                    <span
                        className="
                            rounded-full
                            bg-[#fffaf3]
                            px-3
                            py-1
                            text-xs
                            font-semibold
                            text-[#756763]
                        "
                    >
                        {
                            item.categoryName
                        }
                    </span>


                    {
                        !item.productActive
                        && (

                            <span
                                className="
                                    rounded-full
                                    bg-red-50
                                    px-3
                                    py-1
                                    text-xs
                                    font-semibold
                                    text-red-700
                                "
                            >
                                Product inactive
                            </span>

                        )
                    }


                    {
                        !item.categoryActive
                        && (

                            <span
                                className="
                                    rounded-full
                                    bg-red-50
                                    px-3
                                    py-1
                                    text-xs
                                    font-semibold
                                    text-red-700
                                "
                            >
                                Category inactive
                            </span>

                        )
                    }

                </div>


                <p
                    className="
                        mt-1
                        text-xs
                        font-medium
                        text-[#9a8983]
                    "
                >
                    {
                        item.productCode
                    }
                </p>


                {
                    item.productDescription
                    && (

                        <p
                            className="
                                mt-3
                                max-w-4xl
                                text-sm
                                leading-6
                                text-[#756763]
                            "
                        >
                            {
                                item.productDescription
                            }
                        </p>

                    )
                }


                <div
                    className="
                        mt-4
                        flex
                        flex-wrap
                        gap-x-8
                        gap-y-2
                        text-sm
                    "
                >

                    <p
                        className="
                            text-[#756763]
                        "
                    >
                        Base price:{" "}

                        <strong
                            className="
                                text-[#241715]
                            "
                        >
                            {
                                formatPrice(
                                    item.basePrice
                                )
                            }
                        </strong>
                    </p>


                    <p
                        className="
                            text-[#756763]
                        "
                    >
                        Selling price:{" "}

                        <strong
                            className="
                                text-[#7a1625]
                            "
                        >
                            {
                                formatPrice(
                                    item.effectivePrice
                                )
                            }
                        </strong>
                    </p>


                    <p
                        className="
                            text-[#756763]
                        "
                    >
                        Pricing:{" "}

                        <strong
                            className="
                                text-[#241715]
                            "
                        >
                            {
                                item.priceOverride
                                !== null
                                    ? "Branch override"
                                    : "Base price"
                            }
                        </strong>
                    </p>

                </div>

            </div>


            {/* ADMIN CONTROLS */}

            <div
                className="
                    grid
                    w-full
                    grid-cols-1
                    gap-4
                    bg-[#fffdf9]
                    p-5

                    md:grid-cols-2

                    xl:grid-cols-3

                    sm:p-6
                "
            >

                {/* AVAILABILITY */}

                <div
                    className="
                        min-w-0
                        rounded-xl
                        border
                        border-[#eadfd6]
                        bg-[#fffaf3]
                        p-4
                    "
                >

                    <p
                        className="
                            text-xs
                            font-semibold
                            uppercase
                            tracking-wide
                            text-[#756763]
                        "
                    >
                        Availability
                    </p>


                    <button
                        type="button"
                        disabled={
                            savingAvailability
                        }
                        onClick={
                            toggleAvailability
                        }
                        className={[
                            "mt-3 min-h-11 w-full rounded-xl px-4 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-60",

                            item.available
                                ? "bg-green-100 text-green-800 hover:bg-green-200"
                                : "bg-red-100 text-red-800 hover:bg-red-200"
                        ].join(
                            " "
                        )}
                    >
                        {
                            savingAvailability
                                ? "Saving..."
                                : item.available
                                    ? "Available"
                                    : "Unavailable"
                        }
                    </button>

                </div>


                {/* BRANCH PRICE */}

                <div
                    className="
                        min-w-0
                        rounded-xl
                        border
                        border-[#eadfd6]
                        bg-[#fffaf3]
                        p-4
                    "
                >

                    <label
                        htmlFor={
                            `price-${item.productId}`
                        }
                        className="
                            block
                            text-xs
                            font-semibold
                            uppercase
                            tracking-wide
                            text-[#756763]
                        "
                    >
                        Branch price
                    </label>


                    <input
                        id={
                            `price-${item.productId}`
                        }
                        type="number"
                        min="0.01"
                        step="0.01"
                        value={
                            priceOverride
                        }
                        onChange={
                            event => {

                                setPriceOverride(
                                    event.target.value
                                );


                                setSuccessMessage(
                                    null
                                );


                                setRowError(
                                    null
                                );
                            }
                        }
                        placeholder={
                            String(
                                item.basePrice
                            )
                        }
                        className="
                            mt-3
                            block
                            min-h-11
                            w-full
                            min-w-0
                            rounded-lg
                            border
                            border-[#eadfd6]
                            bg-white
                            px-3
                            text-sm
                            text-[#241715]
                            outline-none

                            focus:border-[#c88a20]
                            focus:ring-4
                            focus:ring-[#f6dfad]/30
                        "
                    />


                    <div
                        className="
                            mt-2
                            grid
                            grid-cols-[minmax(0,1fr)_auto]
                            gap-2
                        "
                    >

                        <button
                            type="button"
                            disabled={
                                savingPrice
                            }
                            onClick={
                                savePrice
                            }
                            className="
                                min-h-10
                                rounded-lg
                                bg-[#7a1625]
                                px-4
                                text-xs
                                font-semibold
                                text-white

                                hover:bg-[#5d0f1b]

                                disabled:cursor-not-allowed
                                disabled:opacity-60
                            "
                        >
                            {
                                savingPrice
                                    ? "Saving..."
                                    : "Save"
                            }
                        </button>


                        <button
                            type="button"
                            disabled={
                                savingPrice
                                ||
                                item.priceOverride
                                === null
                            }
                            onClick={
                                resetPrice
                            }
                            className="
                                min-h-10
                                rounded-lg
                                border
                                border-[#eadfd6]
                                bg-white
                                px-4
                                text-xs
                                font-semibold
                                text-[#7a1625]

                                hover:bg-[#fff1e9]

                                disabled:cursor-not-allowed
                                disabled:opacity-50
                            "
                        >
                            Reset
                        </button>

                    </div>

                </div>


                {/* DISPLAY ORDER */}

                <div
                    className="
                        min-w-0
                        rounded-xl
                        border
                        border-[#eadfd6]
                        bg-[#fffaf3]
                        p-4

                        md:col-span-2
                        xl:col-span-1
                    "
                >

                    <label
                        htmlFor={
                            `display-order-${item.productId}`
                        }
                        className="
                            block
                            text-xs
                            font-semibold
                            uppercase
                            tracking-wide
                            text-[#756763]
                        "
                    >
                        Display order
                    </label>


                    <input
                        id={
                            `display-order-${item.productId}`
                        }
                        type="number"
                        min="0"
                        step="1"
                        value={
                            displayOrder
                        }
                        onChange={
                            event => {

                                setDisplayOrder(
                                    event.target.value
                                );


                                setSuccessMessage(
                                    null
                                );


                                setRowError(
                                    null
                                );
                            }
                        }
                        className="
                            mt-3
                            block
                            min-h-11
                            w-full
                            min-w-0
                            rounded-lg
                            border
                            border-[#eadfd6]
                            bg-white
                            px-3
                            text-sm
                            text-[#241715]
                            outline-none

                            focus:border-[#c88a20]
                            focus:ring-4
                            focus:ring-[#f6dfad]/30
                        "
                    />


                    <button
                        type="button"
                        disabled={
                            savingOrder
                        }
                        onClick={
                            saveDisplayOrder
                        }
                        className="
                            mt-2
                            min-h-10
                            w-full
                            rounded-lg
                            border
                            border-[#7a1625]
                            bg-white
                            px-4
                            text-xs
                            font-semibold
                            text-[#7a1625]

                            hover:bg-[#fff1e9]

                            disabled:cursor-not-allowed
                            disabled:opacity-60
                        "
                    >
                        {
                            savingOrder
                                ? "Saving..."
                                : "Save order"
                        }
                    </button>

                </div>

            </div>


            {
                successMessage
                && (

                    <div
                        role="status"
                        className="
                            border-t
                            border-green-200
                            bg-green-50
                            px-5
                            py-3
                            text-sm
                            font-semibold
                            text-green-800

                            sm:px-6
                        "
                    >
                        ✓ {successMessage}
                    </div>

                )
            }


            {
                rowError
                && (

                    <div
                        role="alert"
                        className="
                            border-t
                            border-red-200
                            bg-red-50
                            px-5
                            py-3
                            text-sm
                            font-medium
                            text-red-700

                            sm:px-6
                        "
                    >
                        {
                            rowError
                        }
                    </div>

                )
            }

        </article>
    );
}