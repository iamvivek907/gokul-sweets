// Uses only synthetic fixtures in the disposable loopback backend; never run against production.
import assert from "node:assert/strict";
import {createRequire} from "node:module";
const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const base = process.env.BROWSER_BASE ?? "http://127.0.0.1:3309";
const api = process.env.BROWSER_API ?? "http://127.0.0.1:18309";
const branchId = Number(process.env.BROWSER_BRANCH ?? 1001);
const productName = process.env.BROWSER_PRODUCT ?? "Test Gulab Jamun";
const branches = await (await fetch(`${api}/api/branches`)).json();
const branch = branches.find(value => value.id === branchId);
assert.ok(branch, "Seed the isolated test database first.");
const browser = await chromium.launch({headless: true});
const context = await browser.newContext({viewport: {width: 390, height: 844}, reducedMotion: "reduce"});
const page = await context.newPage();
const runtimeErrors = [];
page.on("pageerror", error => runtimeErrors.push(error.message));
await page.addInitScript(branch => {
    localStorage.setItem("gokul-selected-branch", JSON.stringify(branch));
    localStorage.setItem("gokul-customer-details", JSON.stringify({name: "Synthetic Customer", phone: "9000000000"}));
    localStorage.setItem("gokul-social-follow-popup-seen", "true");
}, branch);
try {
    await page.goto(base);
    await page.getByRole("heading", {name: "Sweet moments. Savour every bite."}).waitFor();
    await page.getByRole("link", {name: "Order Now", exact: true}).click();
    await page.getByRole("button", {name: `Add ${productName} to cart`}).first().click();
    const dialog = page.getByRole("dialog", {name: productName});
    await dialog.waitFor();
    await dialog.getByRole("button", {name: "500 g", exact: true}).click();
    const buttons = dialog.getByRole("button");
    await buttons.last().focus();
    await page.keyboard.press("Tab");
    assert.ok(await dialog.evaluate(node => node.contains(document.activeElement)), "Weight dialog must trap keyboard focus.");
    await dialog.getByRole("button", {name: /Add|Update/}).last().click();
    await dialog.waitFor({state: "hidden"});
    assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem("gokul-cart")).items[0].weightGrams), 500);
    const originalCart = await page.evaluate(() => localStorage.getItem("gokul-cart"));
    const originalCustomer = await page.evaluate(() => localStorage.getItem("gokul-customer-details"));
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), "Mobile storefront must not overflow horizontally.");
    await page.goto(`${base}/checkout/pickup`);
    await page.getByRole("heading", {name: "When would you like to collect?"}).waitFor();
    const availableDate = page.getByRole("button").filter({hasText: /Times available$/}).first();
    await availableDate.click();
    await page.getByLabel("Pickup time", {exact: true}).selectOption(await page.locator('#smart-pickup-time option[value$=":NORMAL"]').first().getAttribute("value"));
    await page.getByRole("button", {name: "Continue", exact: true}).waitFor();
    await page.getByRole("button", {name: "Continue", exact: true}).click();
    await page.waitForURL("**/checkout/customer");
    assert.equal(await page.evaluate(() => localStorage.getItem("gokul-cart")), originalCart);
    assert.equal(await page.evaluate(() => localStorage.getItem("gokul-customer-details")), originalCustomer);

    // A slow response for an old quantity cannot override the current cart's availability.
    let releaseOld;
    const oldRequest = new Promise(resolve => {releaseOld = resolve;});
    let markStarted;
    const started = new Promise(resolve => {markStarted = resolve;});
    let calls = 0;
    await page.route(`**/api/branches/${branchId}/availability`, async route => {
        calls++;
        if (calls === 1) {
            markStarted();
            await oldRequest;
            await route.fulfill({json: {fulfilmentType: "PICKUP", dates: [], today: "2026-01-01", maximumDate: "2026-01-01"}});
        } else {
            const response = await route.fetch();
            await route.fulfill({response});
        }
    });
    await page.goto(`${base}/checkout/pickup`);
    await page.getByText("Checking dates and times for your cart...").waitFor();
    await Promise.race([started, new Promise((_, reject) => setTimeout(() => reject(new Error("Preview request did not start")), 10_000))]);
    await page.evaluate(() => {
        const cart = JSON.parse(localStorage.getItem("gokul-cart"));
        cart.items[0].weightGrams = 250;
        localStorage.setItem("gokul-cart", JSON.stringify(cart));
        window.dispatchEvent(new Event("gokul-cart-change"));
    });
    await page.getByRole("heading", {name: "Choose a date", exact: true}).waitFor();
    releaseOld();
    await page.waitForTimeout(200);
    assert.ok(await page.getByRole("button").filter({hasText: /Times available$/}).count() > 0, "Stale response must not erase current alternatives.");
    await page.unroute(`**/api/branches/${branchId}/availability`);

    await page.route(`**/api/branches/${branchId}/availability`, route => route.fulfill({status: 503, json: {message: "Synthetic unavailable-check failure"}}));
    const preservedCart = await page.evaluate(() => localStorage.getItem("gokul-cart"));
    await page.reload();
    await page.getByText("We couldn't check pickup times. Your cart is saved. Try again.").waitFor();
    await page.getByRole("button", {name: "Use standard pickup selection"}).click();
    await page.locator("#pickup-date").waitFor();
    assert.equal(await page.evaluate(() => localStorage.getItem("gokul-cart")), preservedCart);
    assert.equal(await page.evaluate(() => localStorage.getItem("gokul-customer-details")), originalCustomer);
    await page.unroute(`**/api/branches/${branchId}/availability`);

    await page.route("**/api/storefront/features", route => route.fulfill({json: {
        smartAvailability: false, smartPickupSelection: false, customerHomeV2: false, homepageCampaigns: false,
        inventoryAutomationV2: false, futureOrderingDays: 30, today: "2026-09-22"
    }}));
    await page.goto(base);
    await page.getByRole("heading", {name: "Your favourites, ready when you are."}).waitFor();
    await page.goto(`${base}/checkout/pickup`);
    await page.locator("#pickup-date").waitFor();
    await page.unroute("**/api/storefront/features");
    assert.deepEqual(runtimeErrors, [], "Browser runtime errors");
    console.log("PASS: mobile quick-add/weight price flow, keyboard focus, cart/customer preservation, selectable alternatives, stale responses, explicit API-failure fallback, flag-off home/pickup.");
} finally {
    await browser.close();
}
