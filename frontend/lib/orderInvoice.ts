import {orderDisplayNumber} from "@/lib/orderDisplayNumber";
import type {CustomerOrderResponse} from "@/types/order";
import {formatWeight} from "./orderQuantity";
import {formatBusinessTimestamp} from "./businessTime";

export function orderDiscount(order: CustomerOrderResponse): number {
    return Math.max(0, Math.round((Number(order.subtotal) + Number(order.taxAmount) + Number(order.priorityCharge)
        + Number(order.convenienceFee ?? 0) + Number(order.paymentFee ?? 0) + Number(order.deliveryFee ?? 0) - Number(order.totalAmount)) * 100) / 100);
}

/** Uses the already-authorized, persisted order amounts; never a cart or checkout estimate. */
export async function downloadOrderInvoice(order: CustomerOrderResponse): Promise<void> {
    if (order.paymentStatus !== "PAID") throw new Error("Invoice is available after payment confirmation.");
    const money = (amount: number) => `₹${Number(amount).toFixed(2)}`;
    const {createInvoicePdf} = await import("./orderInvoicePdf");
    const data = await createInvoicePdf({
        orderNumber: orderDisplayNumber(order),
        placed: `${formatBusinessTimestamp(order.createdAt, {day:"numeric",month:"short",year:"numeric",hour:"numeric",minute:"2-digit"})} IST`,
        branch: [order.branchName, order.branchAddress,
            order.branchPhone ? `Contact: ${order.branchPhone}` : "",
            order.branchFssaiLicenceNumber ? `FSSAI: ${order.branchFssaiLicenceNumber}` : ""].filter((line): line is string => Boolean(line)),
        customer: [order.customerName, order.maskedCustomerPhone].filter(Boolean),
        items: order.items.map(item => ({
            name: item.productName,
            quantity: item.saleMode === "WEIGHT" ? formatWeight(item.weightGrams) : String(item.quantity),
            rate: money(item.unitPrice) + (item.saleMode === "WEIGHT" ? "/kg" : ""),
            amount: money(item.lineTotal),
            note: Number(item.mealDiscount ?? 0) > 0 ? `Included meal savings: -${money(Number(item.mealDiscount))} before item tax` : undefined,
        })),
        charges: [
        `Subtotal: ${money(order.subtotal)}`, ...(order.taxAmount > 0 ? [`Item tax: ${money(order.taxAmount)}`] : []),
        ...((order.convenienceFee ?? 0) > 0 ? [`Convenience fee: ${money(order.convenienceFee ?? 0)} (includes ${money(order.convenienceFeeTax ?? 0)} tax)`] : []),
        ...((order.paymentFee ?? 0) > 0 ? [`Online payment fee: ${money(order.paymentFee ?? 0)} (includes ${money(order.paymentFeeTax ?? 0)} tax)`] : []),
        ...((order.deliveryFee ?? 0) > 0 ? [`Delivery fee: ${money(order.deliveryFee ?? 0)}`] : []),
        ...(order.priorityCharge > 0 ? [`Priority charge: ${money(order.priorityCharge)}`] : []),
        ...((order.loyaltyDiscount??0)>0 ? [`Reward savings (${order.loyaltyCoins??0} coins): -${money(order.loyaltyDiscount??0)}`] : []),
        ...(orderDiscount(order)-(order.loyaltyDiscount??0) > 0 ? [`Offer savings: -${money(orderDiscount(order)-(order.loyaltyDiscount??0))}`] : []),
        ].map(line => {
            const [label, value] = line.split(": ", 2);
            const [amount, note] = value.split(" (");
            return {label, amount, note: note?.slice(0, -1)};
        }),
        paidTotal: money(order.totalAmount),
    });
    const url = URL.createObjectURL(data);
    const link = document.createElement("a"); link.href = url; link.download = `Gokul-${(order.customerOrderNumber?.toString() ?? order.orderNumber).replace(/[^a-zA-Z0-9_-]/g, "_")}-invoice.pdf`;
    document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
