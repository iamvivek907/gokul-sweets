// Run only against the isolated loopback test backend. Never run against a deployed shop.
import assert from "node:assert/strict";
import {createRequire} from "node:module";
const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const base = process.env.BROWSER_BASE ?? "http://127.0.0.1:3309";
const api = process.env.BROWSER_API ?? "http://127.0.0.1:18309";
const branches = await (await fetch(`${api}/api/branches`)).json();
const original = branches.find(branch => branch.active);
assert.ok(original, "Seed a branch on the isolated test backend.");
const menu = await (await fetch(`${api}/api/menu?branchId=${original.id}`)).json();
const product = menu.flatMap(category => category.products).find(value => value.available);
assert.ok(product, "Seed an available product on the isolated test backend.");
const nextBranch = {...original, id: original.id + 1_000_000, name: "Synthetic second branch"};
const browser = await chromium.launch({headless: true});
const page = await browser.newPage({viewport: {width: 390, height: 844}});
const cart = {branchId: original.id, items: [{product, quantity: 1,
    weightGrams: product.saleMode === "WEIGHT" ? product.minimumWeightGrams ?? 250 : null}]};
await page.addInitScript(({branch, cart}) => {
    localStorage.setItem("gokul-selected-branch", JSON.stringify(branch));
    localStorage.setItem("gokul-cart", JSON.stringify(cart));
    localStorage.setItem("gokul-social-follow-popup-seen", "true");
}, {branch: original, cart});
await page.route("**/api/storefront/features", route => route.fulfill({json: {
    smartAvailability: false, smartPickupSelection: false, customerHomeV2: false,
    homepageCampaigns: false, persistentPickupContext: false, cartSwitchPreview: true,
    inventoryAutomationV2: false, futureOrderingDays: 30, today: "2026-09-26"
}}));
await page.route("**/api/branches", route => route.fulfill({json: [original, nextBranch]}));
await page.route(`**/api/menu?branchId=${nextBranch.id}`, route => route.fulfill({json: [
    {...menu[0], products: [{...product, price: product.price + 10}]}
]}));
await page.route(`**/api/branches/${nextBranch.id}/pickup-slots?*`, route => route.fulfill({json: [
    {id: 9999999, branchId: nextBranch.id, active: true, remainingCapacity: 10,
        priorityEnabled: false, priorityRemainingCapacity: 0, priorityCharge: 0,
        startTime: "12:00:00", endTime: "12:30:00"}
]}));
await page.route(`**/api/branches/${nextBranch.id}/inventory/check`, route => route.fulfill({json: {
    enforcementEnabled: true, requestedDate: "2026-09-26", orderable: true,
    items: [{productId: product.id, productName: product.name, orderable: true, availableQuantity: 5000}]
}}));
const snapshot = () => page.evaluate(() => ({branch: localStorage.getItem("gokul-selected-branch"),
    cart: localStorage.getItem("gokul-cart")}));
const openPreview = async () => {
    await page.locator('button[popovertarget="branch-selector-popover"]').click();
    await page.getByRole("button", {name: /Synthetic second branch/}).click();
    await page.getByRole("dialog", {name: "Review cart before switching"}).getByText("Item subtotal:").waitFor();
};
try {
    await page.goto(base);
    const before = await snapshot();
    await openPreview();
    await page.getByRole("button", {name: "Keep current selection"}).click();
    assert.deepEqual(await snapshot(), before, "Cancel preserves branch and cart byte-for-byte.");

    await page.route(`**/api/branches/${nextBranch.id}/inventory/check`, route => route.fulfill({status: 503}));
    await page.locator('button[popovertarget="branch-selector-popover"]').click();
    await page.getByRole("button", {name: /Synthetic second branch/}).click();
    await page.getByText("We couldn't verify this switch.").waitFor();
    assert.deepEqual(await snapshot(), before, "Network failure cannot switch or lose cart.");
    await page.getByRole("button", {name: "Keep current selection"}).click();
    await page.unroute(`**/api/branches/${nextBranch.id}/inventory/check`);

    await openPreview();
    await page.evaluate(() => {
        const value = JSON.parse(localStorage.getItem("gokul-cart"));
        value.items[0].quantity += 1;
        localStorage.setItem("gokul-cart", JSON.stringify(value));
        window.dispatchEvent(new Event("gokul-cart-change"));
    });
    await page.getByRole("button", {name: "Accept and switch"}).click();
    await page.getByText("Your cart or pickup changed.").waitFor();
    assert.equal((await snapshot()).branch, before.branch, "Stale preview cannot change branch.");
    console.log("PASS: cancel, API failure and concurrent cart edit preserve customer state.");
} finally {await browser.close();}
