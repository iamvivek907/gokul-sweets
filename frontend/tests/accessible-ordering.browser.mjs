import assert from "node:assert/strict";
import {createRequire} from "node:module";

const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const browser = await chromium.launch({headless: true});
const context = await browser.newContext({viewport: {width: 320, height: 640}, reducedMotion: "reduce"});
const page = await context.newPage();
try {
    await page.route("**/api/storefront/features", route => route.fulfill({json: {
        customerHomeV2: true, homepageCampaigns: false, preHomeIntentGateway: false,
        accessibleOrderingV2: true, contextualStorefrontV2: false, smartAvailability: false,
        today: "2026-09-26", futureOrderingDays: 30
    }}));
    await page.route("**/api/branches", route => route.fulfill({json: []}));
    await page.goto(process.env.BROWSER_BASE ?? "http://127.0.0.1:3309");
    await page.locator('html[data-accessible-ordering="true"]').waitFor();
    const viewport = await page.locator('meta[name="viewport"]').getAttribute("content");
    assert.ok(!viewport.includes("maximum-scale=1"), "Customers must be able to pinch zoom.");
    assert.ok(await page.getByRole("link", {name: /Order Now/}).isVisible(), "Ordering remains available at narrow width.");
    await context.setOffline(true);
    await page.getByText("You're offline", {exact: true}).waitFor();
    assert.ok(await page.getByRole("link", {name: /Order Now/}).isVisible(), "Offline state keeps the navigation visible.");
    console.log("PASS: mobile zoom, reduced-motion style, and offline navigation recovery.");
} finally {await browser.close();}
