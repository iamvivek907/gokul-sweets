import type {CustomerOrderResponse} from "@/types/order";
import {formatWeight} from "./orderQuantity";
import {formatBusinessTimestamp} from "./businessTime";

export function orderDiscount(order: CustomerOrderResponse): number {
    return Math.max(0, Math.round((Number(order.subtotal) + Number(order.taxAmount) + Number(order.priorityCharge)
        + Number(order.convenienceFee ?? 0) + Number(order.paymentFee ?? 0) - Number(order.totalAmount)) * 100) / 100);
}

/** Uses the already-authorized, persisted order amounts; never a cart or checkout estimate. */
export async function downloadOrderInvoice(order: CustomerOrderResponse): Promise<void> {
    if (order.paymentStatus !== "PAID") throw new Error("Invoice is available after payment confirmation.");
    await document.fonts.ready;
    const money = (amount: number) => `INR ${Number(amount).toFixed(2)}`;
    const lines = ["GOKUL SWEETS", "Order invoice", order.orderNumber, order.branchName, order.branchAddress,
        ...(order.branchPhone ? [`Contact: ${order.branchPhone}`] : []),
        ...(order.branchFssaiLicenceNumber ? [`FSSAI: ${order.branchFssaiLicenceNumber}`] : []),
        `Placed: ${formatBusinessTimestamp(order.createdAt, {day:"numeric",month:"short",year:"numeric",hour:"numeric",minute:"2-digit"})} IST`,
        `Customer: ${order.customerName} (${order.maskedCustomerPhone})`, "",
        ...order.items.flatMap(item => [item.productName,
            `${item.saleMode === "WEIGHT" ? formatWeight(item.weightGrams) : `${item.quantity} units`} @ ${money(item.unitPrice)}${item.saleMode === "WEIGHT" ? "/kg" : ""}   |   ${money(item.lineTotal)}`]), "",
        `Subtotal: ${money(order.subtotal)}`, ...(order.taxAmount > 0 ? [`Item tax: ${money(order.taxAmount)}`] : []),
        ...((order.convenienceFee ?? 0) > 0 ? [`Convenience fee: ${money(order.convenienceFee ?? 0)} (includes ${money(order.convenienceFeeTax ?? 0)} tax)`] : []),
        ...((order.paymentFee ?? 0) > 0 ? [`Online payment fee: ${money(order.paymentFee ?? 0)} (includes ${money(order.paymentFeeTax ?? 0)} tax)`] : []),
        ...(order.priorityCharge > 0 ? [`Priority charge: ${money(order.priorityCharge)}`] : []),
        ...(orderDiscount(order) > 0 ? [`Offer savings: -${money(orderDiscount(order))}`] : []),
        `PAID TOTAL: ${money(order.totalAmount)}`, "Payment confirmed", "Generated from your recorded order. Retain for your records."];
    // Raster pages use the storefront/device fonts for Unicode names.
    const canvas = document.createElement("canvas"); canvas.width = 1240; canvas.height = 1754;
    const ctx = canvas.getContext("2d"); if (!ctx) throw new Error("Canvas unavailable");
    const font = `24px ${getComputedStyle(document.body).fontFamily}`;
    await document.fonts.load(font, lines.join(" "));
    ctx.font = font;
    const wrapped = lines.flatMap(line => {
        const result: string[] = []; let current = "";
        for (const character of line) {
            if (ctx.measureText(current + character).width > 1080) {result.push(current); current = "";}
            current += character;
        }
        result.push(current); return result;
    });
    const images: Uint8Array[] = [];
    for (let offset = 0; offset < wrapped.length; offset += 42) {
        ctx.fillStyle = "#ffffff"; ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.fillStyle = "#173c39"; ctx.font = font;
        wrapped.slice(offset, offset + 42).forEach((line, index) => {
            ctx.font = line === "GOKUL SWEETS" || line.startsWith("PAID TOTAL:") ? `bold ${font}` : font;
            ctx.fillText(line, 80, 90 + index * 37);
        });
        ctx.font = font;
        ctx.fillText(`Page ${images.length + 1}`, 80, 1670);
        images.push(Uint8Array.from(atob(canvas.toDataURL("image/jpeg", .92).split(",")[1]), c => c.charCodeAt(0)));
    }
    const encoder = new TextEncoder(); const parts: Uint8Array[] = []; let length = 0;
    const offsets: number[] = [0];
    const append = (data: string | Uint8Array) => {const bytes = typeof data === "string" ? encoder.encode(data) : data; parts.push(bytes); length += bytes.length;};
    const object = (id: number, body: string, stream?: Uint8Array) => {
        offsets[id] = length; append(`${id} 0 obj\n${body}`);
        if (stream) {append("\nstream\n"); append(stream); append("\nendstream");}
        append("\nendobj\n");
    };
    append("%PDF-1.4\n");
    object(1, "<< /Type /Catalog /Pages 2 0 R >>");
    object(2, `<< /Type /Pages /Count ${images.length} /Kids [${images.map((_, index) => `${3 + index * 3} 0 R`).join(" ")}] >>`);
    images.forEach((jpg, index) => {
        const id = 3 + index * 3;
        object(id, `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /XObject << /Invoice ${id + 1} 0 R >> >> /Contents ${id + 2} 0 R >>`);
        object(id + 1, `<< /Type /XObject /Subtype /Image /Width 1240 /Height 1754 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpg.length} >>`, jpg);
        const commands = encoder.encode("q 595 0 0 842 0 0 cm /Invoice Do Q");
        object(id + 2, `<< /Length ${commands.length} >>`, commands);
    });
    const xref = length;
    append(`xref\n0 ${offsets.length}\n0000000000 65535 f \n`);
    offsets.slice(1).forEach(offset => append(`${String(offset).padStart(10, "0")} 00000 n \n`));
    append(`trailer\n<< /Size ${offsets.length} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
    const data = new Uint8Array(length); let cursor = 0;
    for (const part of parts) {data.set(part, cursor); cursor += part.length;}
    const url = URL.createObjectURL(new Blob([data], {type:"application/pdf"}));
    const link = document.createElement("a"); link.href = url; link.download = `Gokul-${order.orderNumber.replace(/[^a-zA-Z0-9_-]/g, "_")}-invoice.pdf`;
    document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
