"use client";
import {orderDisplayNumber} from "@/lib/orderDisplayNumber";
import {useHistoryReturn} from "@/lib/historyReturn";
import {useState} from "react";
import Link from "next/link";
import type {CustomerOrderResponse} from "@/types/order";
import {formatOrderCurrency as money, formatOrderDate, formatOrderTime, getOrderStatusPresentation, getStatusClasses} from "@/lib/orderTracking";
import {formatBusinessTimestamp} from "@/lib/businessTime";
import {formatWeight} from "@/lib/orderQuantity";
import {downloadOrderInvoice, orderDiscount} from "@/lib/orderInvoice";
import OrderCancellation from "./OrderCancellation";
import MobilePageBack from "@/components/customer/MobilePageBack";
import PickupCodeCard from "./PickupCodeCard";
import OrderReviewCard from "./OrderReviewCard";
import {T} from "@/lib/language";

export default function MobileOrderDetail({order, status, onRefresh, trackingEnabled, pastPickupWindow}: {
    order: CustomerOrderResponse; status: ReturnType<typeof getOrderStatusPresentation>;
    onRefresh: () => void; trackingEnabled: boolean; pastPickupWindow: boolean;
}) {
    const [invoiceError, setInvoiceError] = useState("");
    const [downloading, setDownloading] = useState(false);
    const returnTo=useHistoryReturn();
    const paid = order.paymentStatus === "PAID";
    const delivery = order.fulfillmentType === "DELIVERY";
    const completed = order.orderStatus === (delivery ? "DELIVERED" : "PICKED_UP");
    const pickupCode = paid && !delivery && ["CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "PICKUP_WINDOW_EXPIRED"].includes(order.orderStatus);
    const date = delivery ? order.deliveryDate : order.pickupDate;
    const start = delivery ? order.deliveryStartTime : order.pickupStartTime;
    const end = delivery ? order.deliveryEndTime : order.pickupEndTime;
    const discount = orderDiscount(order);
    async function invoice() {
        setDownloading(true); setInvoiceError("");
        try {await downloadOrderInvoice(order);} catch {setInvoiceError("Unable to download the invoice. Please try again.");}
        finally {setDownloading(false);}
    }
    return <section className="mobile-order-detail">
        <nav><MobilePageBack href={returnTo} label="My orders" /><Link href="/menu"><T text="View menu"/></Link></nav>
        <header><h1><T text="Order details" /></h1><p><span data-copyable>{orderDisplayNumber(order)}</span> · {formatBusinessTimestamp(order.createdAt, {day:"numeric", month:"short", hour:"numeric", minute:"2-digit"})} IST</p></header>
        <section data-order-tone={status.tone} className={`mobile-order-status ${getStatusClasses(status.tone)}`} aria-label="Order status"><h2><T text={status.label}/></h2><p><T text={status.message}/></p><p><T text="Payment:"/> <T text={order.paymentStatus ? order.paymentStatus.replaceAll("_", " ") : "No payment attempt recorded"}/></p></section>
        {trackingEnabled && order.delayReportedAt && order.estimatedReadyAt && <section role="status" className="mobile-order-note"><strong><T text="Ready time update"/></strong><p>{formatBusinessTimestamp(order.estimatedReadyAt, {day:"numeric",month:"short",hour:"numeric",minute:"2-digit"})} IST · {order.delayReason}</p><p><T text="Reported"/> {formatBusinessTimestamp(order.delayReportedAt, {hour:"numeric",minute:"2-digit"})} <T text="IST. Confirm collection changes with the branch."/></p></section>}
        {trackingEnabled && pastPickupWindow && !order.estimatedReadyAt && <p className="mobile-order-note"><T text="Your booked pickup window has passed. Contact the branch before travelling; no revised ready time has been reported."/></p>}
        {pickupCode && <PickupCodeCard orderNumber={order.orderNumber} />}
        <section className="mobile-order-panel"><div className="mobile-order-row"><h2>{order.branchName}</h2>{order.branchPhone && <a href={`tel:${order.branchPhone.replace(/[^+\d]/g, "")}`}><T text="Call" /></a>}</div><p>{order.branchAddress}</p>{order.branchAddress&&<a target="_blank" rel="noopener noreferrer" href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${order.branchName}, ${order.branchAddress}`)}`}><T text="Directions"/></a>}
            <div className="mobile-order-window"><strong><T text={delivery ? "Delivery" : "Pickup"}/></strong><span>{date ? formatOrderDate(date) : "Window pending"}{start && end ? ` · ${formatOrderTime(start)}–${formatOrderTime(end)} IST` : ""}</span>{!delivery && order.pickupType === "PRIORITY" && <span><T text="Priority pickup"/></span>}{delivery && <p>{order.deliveryAddressLine}, {order.deliveryLocality} {order.deliveryPostalCode}</p>}</div>
            <h2><T text="Items" /></h2>{order.items.map(item => <div key={item.id} className="mobile-order-row mobile-order-item"><div><strong>{item.productName}</strong><p>{item.saleMode === "WEIGHT" ? `${formatWeight(item.weightGrams)} × ${money(item.unitPrice)}/kg` : `${item.quantity} × ${money(item.unitPrice)}`}</p>{Number(item.mealDiscount??0)>0&&<small><T text="Meal savings"/> −{money(Number(item.mealDiscount))}</small>}</div><span>{money(item.lineTotal)}</span></div>)}
        </section>
        <section className="mobile-order-panel"><h2><T text="Bill summary" /></h2><div className="mobile-order-row"><span><T text="Subtotal"/></span><span>{money(order.subtotal)}</span></div>{order.taxAmount > 0 && <div className="mobile-order-row"><span><T text="Item tax"/></span><span>{money(order.taxAmount)}</span></div>}
            {(order.convenienceFee ?? 0) > 0 && <div className="mobile-order-row"><span><T text="Convenience fee"/><small><T text="Includes"/> {money(order.convenienceFeeTax ?? 0)} <T text="fee tax"/></small></span><span>{money(order.convenienceFee ?? 0)}</span></div>}
            {(order.paymentFee ?? 0) > 0 && <div className="mobile-order-row"><span><T text="Online payment fee"/><small><T text="Includes"/> {money(order.paymentFeeTax ?? 0)} <T text="fee tax"/></small></span><span>{money(order.paymentFee ?? 0)}</span></div>}
            {(order.deliveryFee ?? 0) > 0 && <div className="mobile-order-row"><span><T text="Delivery fee"/></span><span>{money(order.deliveryFee ?? 0)}</span></div>}{order.priorityCharge > 0 && <div className="mobile-order-row"><span><T text="Priority charge"/></span><span>{money(order.priorityCharge)}</span></div>}{discount > 0 && <div className="mobile-order-row"><span><T text="Offer savings"/></span><span>−{money(discount)}</span></div>}
            <div className="mobile-order-row mobile-order-total"><strong><T text={paid ? "Paid total" : "Order total"}/></strong><strong>{money(order.totalAmount)}</strong></div>
        </section>
        {["PAID","REFUND_PENDING","REFUNDED","REFUND_FAILED"].includes(order.paymentStatus??"") && <OrderCancellation orderNumber={order.orderNumber} onChanged={onRefresh} />}
        {order.branchFssaiLicenceNumber && <p className="mobile-order-licence">Gokul Sweets · FSSAI {order.branchFssaiLicenceNumber}</p>}
        {completed && <div id="order-review"><OrderReviewCard key={order.orderNumber} orderNumber={order.orderNumber} /></div>}
        <div className="mobile-order-actions">{order.paymentStatus === "PENDING" && <Link href={`/checkout/payment/${encodeURIComponent(order.orderNumber)}`}><T text="Continue payment"/></Link>}{paid && <button type="button" disabled={downloading} onClick={() => void invoice()}><T text={downloading ? "Downloading…" : "Download invoice"}/></button>}{!paid && ["FAILED", "EXPIRED"].includes(order.paymentStatus ?? "") && <Link href={`/checkout/payment/${encodeURIComponent(order.orderNumber)}`}><T text="Retry checkout"/></Link>}</div>
        {invoiceError && <p role="alert"><T text={invoiceError}/></p>}<p className="mobile-order-licence"><T text="Updated"/> {formatBusinessTimestamp(order.updatedAt, {day:"numeric",month:"short",hour:"numeric",minute:"2-digit"})} IST</p>
    </section>;
}
