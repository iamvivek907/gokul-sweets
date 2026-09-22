"use client";

import Link
    from "next/link";

import {
    useEffect,
    useState
} from "react";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    getAdminCustomers
} from "@/services/adminCustomerApi";

import type {
    AdminCustomerDirectoryItem,
    AdminCustomerDirectoryResponse,
    CustomerVerificationStatus
} from "@/types/adminCustomer";


type StatusFilter =
    | "ALL"
    | CustomerVerificationStatus;


export default function AdminCustomersPage() {

    const {
        profile,
        authorization,
        hasPermission
    } =
        useAdminAuth();


    const canView =
        hasPermission(
            "CUSTOMER_VIEW"
        );


    const [
        searchInput,
        setSearchInput
    ] =
        useState("");


    const [
        search,
        setSearch
    ] =
        useState("");


    const [
        status,
        setStatus
    ] =
        useState<StatusFilter>(
            "ALL"
        );


    const [
        page,
        setPage
    ] =
        useState(0);


    const [
        size,
        setSize
    ] =
        useState(20);


    const [
        result,
        setResult
    ] =
        useState<AdminCustomerDirectoryResponse | null>(
            null
        );


    const [
        loading,
        setLoading
    ] =
        useState(true);


    const [
        error,
        setError
    ] =
        useState<string | null>(
            null
        );


    useEffect(
        () => {

            if (
                authorization === null
                ||
                !canView
            ) {

                return;
            }


            const controller =
                new AbortController();


            getAdminCustomers(
                authorization,
                {
                    search:
                        search
                        || undefined,

                    status:
                        status === "ALL"
                            ? undefined
                            : status,

                    page,

                    size
                },
                controller.signal
            )
                .then(
                    value => {

                        setResult(
                            value
                        );


                        setLoading(
                            false
                        );
                    }
                )
                .catch(
                    exception => {

                        if (
                            exception instanceof DOMException
                            &&
                            exception.name === "AbortError"
                        ) {

                            return;
                        }


                        setError(
                            exception instanceof Error
                                ? exception.message
                                : "Unable to load customers."
                        );


                        setLoading(
                            false
                        );
                    }
                );


            return () => {

                controller.abort();
            };

        },
        [
            authorization,
            canView,
            search,
            status,
            page,
            size
        ]
    );


    function runSearch() {

        setPage(
            0
        );


        setSearch(
            searchInput.trim()
        );
    }


    if (
        profile
        &&
        !canView
    ) {

        return (
            <div className="px-4 py-6 sm:px-6 lg:px-8">

                <div
                    className="
                        mx-auto
                        max-w-7xl
                        rounded-2xl
                        border
                        border-red-200
                        bg-red-50
                        p-6
                        text-red-800
                    "
                >
                    You do not have permission to view customers.
                </div>

            </div>
        );
    }


    const customers =
        result?.content
        ?? [];


    return (
        <div className="px-4 py-6 sm:px-6 sm:py-8 lg:px-8">

            <div className="mx-auto max-w-7xl">

                <div>

                    <p
                        className="
                            text-xs
                            font-semibold
                            uppercase
                            tracking-[0.16em]
                            text-[#c88a20]
                        "
                    >
                        Customers
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
                        Customer Directory
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
                        Search guest and verified customer identities without loading the complete customer database into the browser.
                    </p>

                </div>


                <div
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
                            gap-3
                            lg:grid-cols-[minmax(0,1fr)_220px_120px]
                        "
                    >

                        <div
                            className="
                                flex
                                gap-2
                            "
                        >

                            <input
                                value={
                                    searchInput
                                }
                                onChange={
                                    event =>
                                        setSearchInput(
                                            event.target.value
                                        )
                                }
                                onKeyDown={
                                    event => {

                                        if (
                                            event.key
                                            === "Enter"
                                        ) {

                                            runSearch();
                                        }
                                    }
                                }
                                placeholder="Search name, phone or customer ID"
                                className={INPUT_CLASS}
                            />


                            <button
                                type="button"
                                onClick={
                                    runSearch
                                }
                                className="
                                    min-h-11
                                    shrink-0
                                    rounded-xl
                                    bg-[#7a1625]
                                    px-4
                                    text-sm
                                    font-semibold
                                    text-white

                                    hover:bg-[#5d0f1b]
                                "
                            >
                                Search
                            </button>

                        </div>


                        <select
                            value={
                                status
                            }
                            onChange={
                                event => {

                                    setStatus(
                                        event.target.value as
                                            StatusFilter
                                    );


                                    setPage(
                                        0
                                    );
                                }
                            }
                            className={INPUT_CLASS}
                        >
                            <option value="ALL">
                                All identities
                            </option>

                            <option value="UNVERIFIED">
                                Guest / Unverified
                            </option>

                            <option value="VERIFIED">
                                Verified
                            </option>

                            <option value="MERGED">
                                Merged
                            </option>
                        </select>


                        <select
                            value={
                                size
                            }
                            onChange={
                                event => {

                                    setSize(
                                        Number(
                                            event.target.value
                                        )
                                    );


                                    setPage(
                                        0
                                    );
                                }
                            }
                            className={INPUT_CLASS}
                        >
                            <option value="20">
                                20 rows
                            </option>

                            <option value="50">
                                50 rows
                            </option>

                            <option value="100">
                                100 rows
                            </option>
                        </select>

                    </div>

                </div>


                {
                    error
                    && (

                        <div
                            role="alert"
                            className="
                                mt-5
                                rounded-xl
                                border
                                border-red-200
                                bg-red-50
                                px-4
                                py-3
                                text-sm
                                font-medium
                                text-red-700
                            "
                        >
                            {error}
                        </div>

                    )
                }


                <div
                    className="
                        mt-6
                        overflow-hidden
                        rounded-2xl
                        border
                        border-[#eadfd6]
                        bg-white
                    "
                >

                    <div
                        className="
                            flex
                            flex-col
                            gap-2
                            border-b
                            border-[#eadfd6]
                            bg-[#fffaf3]
                            px-5
                            py-4
                            sm:flex-row
                            sm:items-center
                            sm:justify-between
                        "
                    >

                        <p
                            className="
                                text-sm
                                font-bold
                                text-[#241715]
                            "
                        >
                            {
                                result
                                    ? `${result.totalElements.toLocaleString("en-IN")} customer contacts`
                                    : "Customer contacts"
                            }
                        </p>


                        <p
                            className="
                                text-xs
                                text-[#756763]
                            "
                        >
                            Purchase metrics count PICKED_UP orders only.
                        </p>

                    </div>


                    {
                        loading
                            ? (

                                <div className="p-8 text-sm text-[#756763]">
                                    Loading customers...
                                </div>

                            )
                            : customers.length === 0
                                ? (

                                    <div className="p-8 text-sm text-[#756763]">
                                        No customers match these filters.
                                    </div>

                                )
                                : (

                                    <div className="overflow-x-auto">

                                        <table className="min-w-full">

                                            <thead className="bg-white">

                                                <tr
                                                    className="
                                                        border-b
                                                        border-[#eadfd6]
                                                        text-left
                                                        text-xs
                                                        font-semibold
                                                        uppercase
                                                        tracking-[0.08em]
                                                        text-[#756763]
                                                    "
                                                >
                                                    <th className="px-5 py-3">
                                                        Customer
                                                    </th>

                                                    <th className="px-5 py-3">
                                                        Status
                                                    </th>

                                                    <th className="px-5 py-3 text-right">
                                                        Orders
                                                    </th>

                                                    <th className="px-5 py-3 text-right">
                                                        Purchases
                                                    </th>

                                                    <th className="px-5 py-3 text-right">
                                                        Lifetime Spend
                                                    </th>

                                                    <th className="px-5 py-3">
                                                        Last Purchase
                                                    </th>

                                                    <th className="px-5 py-3">
                                                        Last Seen
                                                    </th>
                                                </tr>

                                            </thead>


                                            <tbody
                                                className="
                                                    divide-y
                                                    divide-[#eadfd6]
                                                "
                                            >

                                                {
                                                    customers.map(
                                                        customer => (

                                                            <CustomerRow
                                                                key={
                                                                    customer.id
                                                                }
                                                                customer={
                                                                    customer
                                                                }
                                                            />

                                                        )
                                                    )
                                                }

                                            </tbody>

                                        </table>

                                    </div>

                                )
                    }

                </div>


                {
                    result
                    && result.totalPages > 0
                    && (

                        <div
                            className="
                                mt-5
                                flex
                                flex-col
                                gap-3
                                sm:flex-row
                                sm:items-center
                                sm:justify-between
                            "
                        >

                            <p className="text-sm text-[#756763]">
                                Page {result.page + 1} of {result.totalPages}
                            </p>


                            <div className="flex gap-2">

                                <button
                                    type="button"
                                    disabled={
                                        loading
                                        ||
                                        result.page === 0
                                    }
                                    onClick={
                                        () =>
                                            setPage(
                                                current =>
                                                    Math.max(
                                                        current - 1,
                                                        0
                                                    )
                                            )
                                    }
                                    className={PAGE_BUTTON_CLASS}
                                >
                                    Previous
                                </button>


                                <button
                                    type="button"
                                    disabled={
                                        loading
                                        ||
                                        result.page + 1
                                            >= result.totalPages
                                    }
                                    onClick={
                                        () =>
                                            setPage(
                                                current =>
                                                    current + 1
                                            )
                                    }
                                    className={PAGE_BUTTON_CLASS}
                                >
                                    Next
                                </button>

                            </div>

                        </div>

                    )
                }

            </div>

        </div>
    );
}


function CustomerRow({
    customer
}: {
    customer: AdminCustomerDirectoryItem;
}) {

    return (
        <tr className="hover:bg-[#fffaf3]">

            <td className="px-5 py-4">

                <Link
                    href={
                        `/admin/customers/${customer.id}`
                    }
                    className="
                        block
                        rounded-lg
                        outline-none

                        focus-visible:ring-4
                        focus-visible:ring-[#c88a20]/30
                    "
                >

                    <p
                        className="
                            font-bold
                            text-[#241715]
                            hover:text-[#7a1625]
                        "
                    >
                        {
                            customer.latestName
                            ?? "Guest customer"
                        }
                    </p>


                    <p
                        className="
                            mt-1
                            text-xs
                            text-[#756763]
                        "
                    >
                        {customer.normalizedPhone} · ID {customer.id}
                    </p>

                </Link>

            </td>


            <td className="px-5 py-4">

                <span
                    className={`
                        inline-flex
                        rounded-full
                        px-2.5
                        py-1
                        text-xs
                        font-semibold

                        ${
                            customer.verificationStatus
                            === "VERIFIED"
                                ? "bg-green-50 text-green-800"
                                : customer.verificationStatus
                                    === "MERGED"
                                    ? "bg-gray-100 text-gray-700"
                                    : "bg-amber-50 text-amber-800"
                        }
                    `}
                >
                    {
                        customer.verificationStatus
                        === "UNVERIFIED"
                            ? "Guest / Unverified"
                            : customer.verificationStatus
                    }
                </span>

            </td>


            <td className="px-5 py-4 text-right font-semibold text-[#241715]">
                {customer.orderCount.toLocaleString("en-IN")}
            </td>


            <td className="px-5 py-4 text-right font-semibold text-[#241715]">
                {customer.completedPurchaseCount.toLocaleString("en-IN")}
            </td>


            <td className="px-5 py-4 text-right font-bold text-[#7a1625]">
                {formatMoney(customer.lifetimeSpend)}
            </td>


            <td className="whitespace-nowrap px-5 py-4 text-sm text-[#756763]">
                {formatDateTime(customer.lastPurchaseAt)}
            </td>


            <td className="whitespace-nowrap px-5 py-4 text-sm text-[#756763]">
                {formatDateTime(customer.lastSeenAt)}
            </td>

        </tr>
    );
}


function formatMoney(
    value: number
) {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style: "currency",
            currency: "INR",
            maximumFractionDigits: 2
        }
    ).format(
        value
    );
}


function formatDateTime(
    value: string | null
) {

    if (!value) {

        return "—";
    }


    return new Intl.DateTimeFormat(
        "en-IN",
        {
            timeZone: "Asia/Kolkata",
            dateStyle: "medium",
            timeStyle: "short"
        }
    ).format(
        new Date(
            value
        )
    );
}


const INPUT_CLASS =
    `
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
    `;


const PAGE_BUTTON_CLASS =
    `
        min-h-11
        rounded-xl
        border
        border-[#eadfd6]
        bg-white
        px-4
        text-sm
        font-semibold
        text-[#7a1625]

        hover:bg-[#fffaf3]

        disabled:cursor-not-allowed
        disabled:opacity-50
    `;
