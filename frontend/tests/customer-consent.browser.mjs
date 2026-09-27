import assert from "node:assert/strict";
import {createRequire} from "node:module";

const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const browser = await chromium.launch({headless: true});
const page = await browser.newPage();
let authenticated = false;
let consentEnabled = false;
let marketing = false;
const decisions = () => Object.fromEntries(
    ["MARKETING", "OCCASION_REMINDERS", "COARSE_AREA_ANALYTICS"]
        .map(purpose => [purpose, {granted: purpose === "MARKETING" && marketing,
            policyVersion: "2026-09", recordedAt: null}])
);

try {
    await page.route("**/api/storefront/customer-identity", route => route.fulfill({json: {enabled: true}}));
    await page.route("**/api/storefront/features", route => route.fulfill({json: {customerHomeV2: true}}));
    await page.route("**/api/branches", route => route.fulfill({json: []}));
    await page.route("**/api/customer/identity/me", route => route.fulfill({json: {authenticated}}));
    await page.route("**/api/customer/identity/consents", route => consentEnabled
        ? route.fulfill({json: decisions()}) : route.fulfill({status: 404, json: {}}));
    await page.route("**/api/customer/identity/consents/MARKETING", async route => {
        marketing = (await route.request().postDataJSON()).granted;
        await route.fulfill({json: decisions().MARKETING});
    });
    const base = process.env.BROWSER_BASE ?? "http://127.0.0.1:3309";
    await page.goto(`${base}/profile`);
    assert.equal(await page.getByRole("region", {name: "Privacy choices"}).count(), 0,
        "Guests must not see optional consent controls.");

    authenticated = true;
    await page.reload();
    assert.equal(await page.getByRole("region", {name: "Privacy choices"}).count(), 0,
        "The default-OFF backend must hide the controls.");

    consentEnabled = true;
    await page.reload();
    const choices = page.getByRole("region", {name: "Privacy choices"});
    await choices.waitFor();
    const checkbox = choices.getByRole("checkbox", {name: "Offers and news"});
    assert.equal(await checkbox.isChecked(), false);
    await checkbox.check();
    assert.equal(marketing, true);
    await checkbox.uncheck();
    assert.equal(marketing, false);
    console.log("PASS: guest and flag-OFF hide controls; verified grant and withdrawal persist.");
} finally {
    await browser.close();
}
