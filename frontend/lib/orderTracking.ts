import type {
    CustomerOrderSummaryResponse,
    OrderStatus
} from "@/types/order";


export type OrderHistoryFilter =
    | "ALL"
    | "ACTIVE"
    | "COMPLETED"
    | "ISSUES";


export type OrderStatusTone =
    | "amber"
    | "blue"
    | "green"
    | "red"
    | "gray";


export interface OrderStatusPresentation {
    label: string;
    message: string;
    tone: OrderStatusTone;
}


const ACTIVE_STATUSES:
    ReadonlySet<OrderStatus> =
    new Set([
        "PENDING_PAYMENT",
        "CONFIRMED",
        "PREPARING",
        "READY_FOR_PICKUP"
    ]);


const ISSUE_STATUSES:
    ReadonlySet<OrderStatus> =
    new Set([
        "PAYMENT_FAILED",
        "CANCELLED",
        "NO_SHOW",
        "PICKUP_WINDOW_EXPIRED"
    ]);


export function getOrderStatusPresentation(
    status: OrderStatus
): OrderStatusPresentation {

    switch (status) {

        case "PENDING_PAYMENT":
            return {
                label: "Awaiting Payment",
                message: "Your pickup time is reserved while checkout is completed.",
                tone: "amber"
            };

        case "CONFIRMED":
            return {
                label: "Order Confirmed",
                message: "The shop has received your order.",
                tone: "blue"
            };

        case "PREPARING":
            return {
                label: "Preparing",
                message: "Your order is being freshly prepared.",
                tone: "blue"
            };

        case "READY_FOR_PICKUP":
            return {
                label: "Ready for Pickup",
                message: "Your order is ready. Please collect it from the selected branch.",
                tone: "green"
            };

        case "PICKED_UP":
            return {
                label: "Picked Up",
                message: "This order has been collected successfully.",
                tone: "green"
            };

        case "PAYMENT_FAILED":
            return {
                label: "Payment Failed",
                message: "Payment was not completed for this order.",
                tone: "red"
            };

        case "CANCELLED":
            return {
                label: "Cancelled",
                message: "This order was cancelled.",
                tone: "gray"
            };

        case "NO_SHOW":
            return {
                label: "Not Collected",
                message: "The order was not collected during the pickup window.",
                tone: "red"
            };

        case "PICKUP_WINDOW_EXPIRED":
            return {
                label: "Pickup Window Expired",
                message: "The selected pickup window has passed.",
                tone: "red"
            };
    }
}


export function getStatusClasses(
    tone: OrderStatusTone
): string {

    switch (tone) {
        case "green":
            return "border-green-200 bg-green-50 text-green-700";
        case "blue":
            return "border-blue-200 bg-blue-50 text-blue-700";
        case "red":
            return "border-red-200 bg-red-50 text-red-700";
        case "gray":
            return "border-gray-200 bg-gray-50 text-gray-700";
        case "amber":
            return "border-amber-200 bg-amber-50 text-amber-800";
    }
}


export function isActiveOrderStatus(
    status: OrderStatus
): boolean {
    return ACTIVE_STATUSES.has(status);
}


export function isIssueOrderStatus(
    status: OrderStatus
): boolean {
    return ISSUE_STATUSES.has(status);
}


export function matchesOrderFilter(
    order: CustomerOrderSummaryResponse,
    filter: OrderHistoryFilter
): boolean {

    switch (filter) {
        case "ACTIVE":
            return isActiveOrderStatus(order.orderStatus);
        case "COMPLETED":
            return order.orderStatus === "PICKED_UP";
        case "ISSUES":
            return isIssueOrderStatus(order.orderStatus);
        case "ALL":
            return true;
    }
}


export function formatOrderCurrency(
    value: number
): string {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style: "currency",
            currency: "INR",
            maximumFractionDigits: 2
        }
    ).format(value);
}


export function formatOrderDate(
    value: string
): string {

    const date =
        new Date(`${value}T00:00:00`);

    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return new Intl.DateTimeFormat(
        "en-IN",
        {
            day: "numeric",
            month: "short",
            year: "numeric"
        }
    ).format(date);
}


export function formatOrderTime(
    value: string
): string {

    const [hour, minute] =
        value.split(":");

    const date = new Date();
    date.setHours(Number(hour), Number(minute), 0, 0);

    return new Intl.DateTimeFormat(
        "en-IN",
        {
            hour: "numeric",
            minute: "2-digit"
        }
    ).format(date);
}


export function getOrderMonthLabel(
    createdAt: string
): string {

    const date = new Date(createdAt);

    if (Number.isNaN(date.getTime())) {
        return "Earlier Orders";
    }

    return new Intl.DateTimeFormat(
        "en-IN",
        {
            month: "long",
            year: "numeric"
        }
    ).format(date);
}
