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
await page.route(`**/api/menu?branchId=${nextBranch.id}`, route => route.fulfill({json: menu}));
const snapshot = () => page.evaluate(() => ({branch: localStorage.getItem("gokul-selected-branch"),
    cart: localStorage.getItem("gokul-cart")}));
const openReview = async () => {
    await page.locator('button[popovertarget="branch-selector-popover"]').click();
    await page.getByRole("button", {name: /Synthetic second branch/}).click();
    await page.getByRole("dialog", {name: /Start at Synthetic second branch/}).waitFor();
};
try {
    await page.goto(base);
    const before = await snapshot();
    await openReview();
    await page.getByRole("button", {name: "Keep my cart"}).click();
    assert.deepEqual(await snapshot(), before, "Cancel preserves branch and cart byte-for-byte.");

    await openReview();
    await page.evaluate(() => {
        const value = JSON.parse(localStorage.getItem("gokul-cart"));
        value.items[0].quantity += 1;
        localStorage.setItem("gokul-cart", JSON.stringify(value));
        window.dispatchEvent(new Event("gokul-cart-change"));
    });
    await page.getByRole("button", {name: "Clear cart and switch"}).click();
    await page.getByText("Your cart changed.").waitFor();
    assert.equal((await snapshot()).branch, before.branch, "Stale review cannot change branch.");
    await page.getByRole("button", {name: "Keep my cart"}).click();

    await openReview();
    await page.getByRole("button", {name: "Clear cart and switch"}).click();
    await page.waitForURL("**/menu");
    const after = await snapshot();
    assert.equal(JSON.parse(after.branch).id, nextBranch.id);
    assert.equal(after.cart, null, "The new branch opens with an empty cart.");
    console.log("PASS: cancellation and stale cart preserve state; confirmed switch opens new menu with empty cart.");
} finally {await browser.close();}
