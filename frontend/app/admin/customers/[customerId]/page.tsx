"use client";

import Link
    from "next/link";

import {
    use,
    useEffect,
    useState
} from "react";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    getAdminCustomer,
    getAdminCustomerOrders
} from "@/services/adminCustomerApi";

import type {
    AdminCustomerDetail,
    AdminCustomerOrderHistoryResponse
} from "@/types/adminCustomer";


export default function AdminCustomerDetailPage({
    params
}: {
    params: Promise<{
        customerId: string;
    }>;
}) {

    const {
        customerId:
            customerIdParam
    } =
        use(
            params
        );


    const customerId =
        Number(
            customerIdParam
        );


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
        customer,
        setCustomer
    ] =
        useState<AdminCustomerDetail | null>(
            null
        );


    const [
        orders,
        setOrders
    ] =
        useState<AdminCustomerOrderHistoryResponse | null>(
            null
        );


    const [
        page,
        setPage
    ] =
        useState(0);


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
                ||
                !Number.isInteger(
                    customerId
                )
                ||
                customerId <= 0
            ) {

                return;
            }


            const controller =
                new AbortController();


            Promise.all([
                getAdminCustomer(
                    customerId,
                    authorization,
                    controller.signal
                ),
                getAdminCustomerOrders(
                    customerId,
                    authorization,
                    page,
                    20,
                    controller.signal
                )
            ])
                .then(
                    ([
                        customerResult,
                        orderResult
                    ]) => {

                        setCustomer(
                            customerResult
                        );


                        setOrders(
                            orderResult
                        );


                        setError(
                            null
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
                                : "Unable to load customer details."
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
            customerId,
            page
        ]
    );


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


    return (
        <div className="px-4 py-6 sm:px-6 sm:py-8 lg:px-8">

            <div className="mx-auto max-w-7xl">

                <Link
                    href="/admin/customers"
                    className="
                        inline-flex
                        min-h-10
                        items-center
                        text-sm
                        font-semibold
                        text-[#7a1625]

                        hover:underline
                    "
                >
                    ← Back to Customers
                </Link>


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


                {
                    loading
                    &&
                    customer === null
                        ? (

                            <div className="mt-6 text-sm text-[#756763]">
                                Loading customer...
                            </div>

                        )
                        : customer
                            ? (

                                <>

                                    <div
                                        className="
                                            mt-4
                                            rounded-2xl
                                            border
                                            border-[#eadfd6]
                                            bg-white
                                            p-5
                                            sm:p-6
                                        "
                                    >

                                        <div
                                            className="
                                                flex
                                                flex-col
                                                gap-4
                                                lg:flex-row
                                                lg:items-start
                                                lg:justify-between
                                            "
                                        >

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
                                                    Customer Profile
                                                </p>


                                                <h1
                                                    className="
                                                        mt-2
                                                        text-3xl
                                                        font-bold
                                                        tracking-tight
                                                        text-[#241715]
                                                    "
                                                >
                                                    {
                                                        customer.latestName
                                                        ?? "Guest customer"
                                                    }
                                                </h1>


                                                <p className="mt-2 text-sm text-[#756763]">
                                                    {customer.normalizedPhone} · Customer ID {customer.id}
                                                </p>

                                            </div>


                                            <span
                                                className={`
                                                    inline-flex
                                                    w-fit
                                                    rounded-full
                                                    px-3
                                                    py-1.5
                                                    text-xs
                                                    font-semibold

                                                    ${
                                                        customer.verificationStatus === "VERIFIED"
                                                            ? "bg-green-50 text-green-800"
                                                            : customer.verificationStatus === "MERGED"
                                                                ? "bg-gray-100 text-gray-700"
                                                                : "bg-amber-50 text-amber-800"
                                                    }
                                                `}
                                            >
                                                {
                                                    customer.verificationStatus === "UNVERIFIED"
                                                        ? "Guest / Unverified"
                                                        : customer.verificationStatus
                                                }
                                            </span>

                                        </div>


                                        <div
                                            className="
                                                mt-6
                                                grid
                                                gap-3
                                                sm:grid-cols-2
                                                xl:grid-cols-4
                                            "
                                        >

                                            <Metric
                                                label="Completed Purchases"
                                                value={
                                                    customer.completedPurchaseCount
                                                        .toLocaleString(
                                                            "en-IN"
                                                        )
                                                }
                                            />

                                            <Metric
                                                label="Lifetime Spend"
                                                value={
                                                    formatMoney(
                                                        customer.lifetimeSpend
                                                    )
                                                }
                                            />

                                            <Metric
                                                label="Average Order Value"
                                                value={
                                                    formatMoney(
                                                        customer.averageOrderValue
                                                    )
                                                }
                                            />

                                            <Metric
                                                label="All Checkout Orders"
                                                value={
                                                    customer.orderCount
                                                        .toLocaleString(
                                                            "en-IN"
                                                        )
                                                }
                                            />

                                        </div>


                                        <div
                                            className="
                                                mt-6
                                                grid
                                                gap-4
                                                border-t
                                                border-[#eadfd6]
                                                pt-5
                                                text-sm
                                                sm:grid-cols-3
                                            "
                                        >

                                            <Info
                                                label="First Seen"
                                                value={
                                                    formatDateTime(
                                                        customer.firstSeenAt
                                                    )
                                                }
                                            />

                                            <Info
                                                label="Last Seen"
                                                value={
                                                    formatDateTime(
                                                        customer.lastSeenAt
                                                    )
                                                }
                                            />

                                            <Info
                                                label="Last Completed Purchase"
                                                value={
                                                    formatDateTime(
                                                        customer.lastPurchaseAt
                                                    )
                                                }
                                            />

                                        </div>

                                    </div>


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
                                                border-b
                                                border-[#eadfd6]
                                                bg-[#fffaf3]
                                                px-5
                                                py-4
                                            "
                                        >

                                            <h2 className="font-bold text-[#241715]">
                                                Order History
                                            </h2>


                                            <p className="mt-1 text-xs text-[#756763]">
                                                All checkout attempts are shown here. Purchase metrics above count PICKED_UP orders only.
                                            </p>

                                        </div>


                                        {
                                            orders?.content.length
                                                ? (

                                                    <div className="overflow-x-auto">

                                                        <table className="min-w-full">

                                                            <thead>

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
                                                                    <th className="px-5 py-3">Order</th>
                                                                    <th className="px-5 py-3">Branch</th>
                                                                    <th className="px-5 py-3">Pickup</th>
                                                                    <th className="px-5 py-3">Status</th>
                                                                    <th className="px-5 py-3 text-right">Discount</th>
                                                                    <th className="px-5 py-3 text-right">Total</th>
                                                                    <th className="px-5 py-3">Created</th>
                                                                </tr>

                                                            </thead>


                                                            <tbody className="divide-y divide-[#eadfd6]">

                                                                {
                                                                    orders.content.map(
                                                                        order => (

                                                                            <tr
                                                                                key={
                                                                                    order.orderId
                                                                                }
                                                                                className="hover:bg-[#fffaf3]"
                                                                            >

                                                                                <td className="px-5 py-4 font-semibold text-[#241715]">
                                                                                    {order.orderNumber}
                                                                                </td>

                                                                                <td className="px-5 py-4 text-sm text-[#756763]">
                                                                                    {order.branchName}
                                                                                </td>

                                                                                <td className="whitespace-nowrap px-5 py-4 text-sm text-[#756763]">
                                                                                    {formatPickup(order.pickupDate, order.pickupStartTime)}
                                                                                </td>

                                                                                <td className="px-5 py-4 text-sm font-semibold text-[#241715]">
                                                                                    {friendlyStatus(order.orderStatus)}
                                                                                </td>

                                                                                <td className="px-5 py-4 text-right text-sm text-[#756763]">
                                                                                    {
                                                                                        order.rebateDiscountAmount > 0
                                                                                            ? `-${formatMoney(order.rebateDiscountAmount)}`
                                                                                            : "—"
                                                                                    }
                                                                                </td>

                                                                                <td className="px-5 py-4 text-right font-bold text-[#7a1625]">
                                                                                    {formatMoney(order.totalAmount)}
                                                                                </td>

                                                                                <td className="whitespace-nowrap px-5 py-4 text-sm text-[#756763]">
                                                                                    {formatDateTime(order.createdAt)}
                                                                                </td>

                                                                            </tr>

                                                                        )
                                                                    )
                                                                }

                                                            </tbody>

                                                        </table>

                                                    </div>

                                                )
                                                : (

                                                    <div className="p-8 text-sm text-[#756763]">
                                                        No orders found for this customer.
                                                    </div>

                                                )
                                        }

                                    </div>


                                    {
                                        orders
                                        &&
                                        orders.totalPages > 1
                                        && (

                                            <div
                                                className="
                                                    mt-5
                                                    flex
                                                    items-center
                                                    justify-between
                                                    gap-3
                                                "
                                            >

                                                <p className="text-sm text-[#756763]">
                                                    Page {orders.page + 1} of {orders.totalPages}
                                                </p>


                                                <div className="flex gap-2">

                                                    <button
                                                        type="button"
                                                        disabled={
                                                            orders.page === 0
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
                                                            orders.page + 1
                                                            >= orders.totalPages
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

                                </>

                            )
                            : null
                }

            </div>

        </div>
    );
}


function Metric({
    label,
    value
}: {
    label: string;
    value: string;
}) {

    return (
        <div className="rounded-xl border border-[#eadfd6] bg-[#fffaf3] p-4">

            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-[#756763]">
                {label}
            </p>

            <p className="mt-2 text-2xl font-bold text-[#241715]">
                {value}
            </p>

        </div>
    );
}


function Info({
    label,
    value
}: {
    label: string;
    value: string;
}) {

    return (
        <div>

            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-[#756763]">
                {label}
            </p>

            <p className="mt-1 font-semibold text-[#241715]">
                {value}
            </p>

        </div>
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


function formatPickup(
    date: string,
    time: string
) {

    return `${date} ${time.slice(0, 5)}`;
}


function friendlyStatus(
    value: string
) {

    return value
        .replaceAll(
            "_",
            " "
        );
}


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
