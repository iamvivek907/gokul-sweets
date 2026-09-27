import assert from "node:assert/strict";
import {createRequire} from "node:module";

const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const browser = await chromium.launch({headless: true});
const context = await browser.newContext();
const page = await context.newPage();
let authenticated = true;

const summary = (orderNumber, createdAt) => ({
    orderNumber, createdAt, orderStatus: "CONFIRMED", branchName: "Hazratganj",
    pickupDate: "2026-09-28", pickupStartTime: "12:00:00", pickupEndTime: "12:30:00",
    pickupType: "SELF_PICKUP", totalAmount: 450
});
const guest = summary("GUEST-100", "2026-09-26T12:00:00");
const verified = summary("VERIFIED-200", "2026-09-27T12:00:00");

try {
    await page.addInitScript(() => {
        localStorage.setItem("gokul-order-history", JSON.stringify([
            {orderNumber: "GUEST-100", createdAt: "2026-09-26T12:00:00"}
        ]));
        localStorage.setItem("gokul-cart-recovery-sentinel", "keep-cart");
    });
    await page.route("**/api/storefront/features", route => route.fulfill({json: {
        truthfulOrderTracking: false, customerHomeV2: true
    }}));
    await page.route("**/api/branches", route => route.fulfill({json: []}));
    await page.route("**/api/orders/history", route => route.fulfill({json: [guest]}));
    await page.route("**/api/storefront/customer-identity", route => route.fulfill({json: {enabled: true}}));
    await page.route("**/api/customer/identity/me", route => route.fulfill({json: {authenticated}}));
    await page.route("**/api/customer/identity/orders", route => route.fulfill({json: [guest, verified]}));

    const base = process.env.BROWSER_BASE ?? "http://127.0.0.1:3309";
    await page.goto(`${base}/orders`);
    await page.getByText("VERIFIED-200", {exact: true}).waitFor();
    assert.equal(await page.getByText("GUEST-100", {exact: true}).count(), 1,
        "Guest orders shared with the verified response must be deduplicated.");

    authenticated = false;
    await page.reload();
    await page.getByText("GUEST-100", {exact: true}).waitFor();
    assert.equal(await page.getByText("VERIFIED-200", {exact: true}).count(), 0,
        "Signing out must hide orders recovered from the verified session.");
    assert.equal(await page.evaluate(() => localStorage.getItem("gokul-cart-recovery-sentinel")), "keep-cart");
    assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem("gokul-order-history"))),
        [{orderNumber: "GUEST-100", createdAt: "2026-09-26T12:00:00"}]);
    console.log("PASS: verified recovery merges once and sign-out retains guest history and cart.");
} finally {
    await browser.close();
}
