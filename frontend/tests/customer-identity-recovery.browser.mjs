import assert from "node:assert/strict";
import {createRequire} from "node:module";

const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const browser = await chromium.launch({headless: true});
const context = await browser.newContext();
const page = await context.newPage();
let authenticated = true;
const base = process.env.BROWSER_BASE ?? "http://127.0.0.1:3311";
const headers = {"Access-Control-Allow-Origin":base,"Access-Control-Allow-Credentials":"true","Access-Control-Allow-Methods":"GET,POST,OPTIONS","Access-Control-Allow-Headers":"content-type"};

const summary = (orderNumber, createdAt) => ({
    orderNumber, createdAt, branchId: 1, orderStatus: "CONFIRMED", branchName: "Hazratganj",
    pickupDate: "2026-09-28", pickupStartTime: "12:00:00", pickupEndTime: "12:30:00",
    pickupType: "SELF_PICKUP", totalAmount: 450
});
const guest = summary("GUEST-100", "2026-09-26T12:00:00");
const verified = summary("VERIFIED-200", "2026-09-27T12:00:00");

try {
    await page.addInitScript(() => {
        localStorage.setItem("gokul-selected-branch", JSON.stringify({id:1,name:"Hazratganj",active:true,operational:true,pickupAvailable:true}));
        localStorage.setItem("gokul-order-history", JSON.stringify([
            {orderNumber: "GUEST-100", createdAt: "2026-09-26T12:00:00"}
        ]));
        localStorage.setItem("gokul-cart-recovery-sentinel", "keep-cart");
    });
    await page.route("**/api/storefront/features", route => route.fulfill({headers,json: {
        truthfulOrderTracking: false, customerHomeV2: true
    }}));
    await page.route("**/api/branches", route => route.fulfill({headers,json: [{id:1,name:"Hazratganj",active:true,operational:true,pickupAvailable:true}]}));
    await page.route("**/api/branches/1", route => route.fulfill({headers,json: {id:1,name:"Hazratganj",active:true,operational:true,pickupAvailable:true}}));
    await page.route("**/api/orders/history", route => route.fulfill({headers,json: [guest]}));
    await page.route("**/api/storefront/customer-identity", route => route.fulfill({headers,json: {enabled: true}}));
    await page.route("**/api/customer/identity/me", route => route.fulfill({headers,json: {authenticated}}));
    await page.route("**/api/customer/identity/orders/page?*", route => route.fulfill({headers,json: {orders:[guest, verified],nextBefore:null}}));

    await page.route("**/api/**", route => route.request().method() === "OPTIONS" ? route.fulfill({status:204,headers}) : route.fallback());
    await page.goto(`${base}/orders`);
    await page.locator('a[href^="/orders/VERIFIED-200?"]').waitFor();
    assert.equal(await page.locator('a[href^="/orders/GUEST-100"]').count(), 1,
        "Guest orders shared with the verified response must be deduplicated.");

    authenticated = false;
    await page.reload();
    await page.locator("article").filter({hasText:"GUEST-100"}).getByRole("button",{name:"View Order",exact:true}).waitFor();
    assert.equal(await page.locator('a[href^="/orders/VERIFIED-200?"]').count(), 0,
        "Signing out must hide orders recovered from the verified session.");
    assert.equal(await page.evaluate(() => localStorage.getItem("gokul-cart-recovery-sentinel")), "keep-cart");
    assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem("gokul-order-history"))),
        [{orderNumber: "GUEST-100", createdAt: "2026-09-26T12:00:00"}]);
    console.log("PASS: verified recovery merges once and sign-out retains guest history and cart.");
} finally {
    await browser.close();
}
