import assert from "node:assert/strict";
import {createRequire} from "node:module";
const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const browser = await chromium.launch({headless: true});
const context = await browser.newContext({viewport: {width: 390, height: 844}, reducedMotion: "reduce"});
const page = await context.newPage();
const now = Date.now();
const base = {type: "HERO", active: true, displayOrder: 1, startAt: null, endAt: null, updatedAt: new Date(now).toISOString(),
    ctaLabel: "Explore Menu", ctaTarget: "/menu", mediaUrl: "/synthetic-test.mp4", mediaType: "video/mp4",
    fallbackMediaUrl: "/synthetic-poster.png"};
const fixtures = [
    {...base, id: 1, title: "Expired campaign", endAt: new Date(now - 1000).toISOString()},
    {...base, id: 2, title: "Scheduled campaign", startAt: new Date(now - 1000).toISOString()},
    {...base, id: 3, title: "Next active campaign", displayOrder: 2},
    {...base, id: 4, title: "Future campaign", startAt: new Date(now + 3600_000).toISOString()}
];
const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j0LcAAAAASUVORK5CYII=", "base64");
try {
    await page.route("**/api/storefront/campaigns", route => route.fulfill({json: fixtures}));
    await page.route("**/_next/image**", route => route.fulfill({contentType: "image/png", body: png}));
    await page.goto(process.env.BROWSER_BASE ?? "http://127.0.0.1:3309");
    await page.getByRole("heading", {name: "Scheduled campaign", exact: true}).waitFor();
    assert.equal(await page.locator("video").count(), 0, "Reduced motion uses static fallback instead of video.");
    assert.equal(await page.getByRole("heading", {name: "Expired campaign"}).count(), 0);
    assert.equal(await page.getByRole("heading", {name: "Future campaign"}).count(), 0);
    const hero = page.getByRole("img", {name: "Scheduled campaign"});
    assert.ok((await hero.getAttribute("src")).includes("poster"));
    await hero.dispatchEvent("error");
    await page.getByRole("heading", {name: "Next active campaign", exact: true}).waitFor();
    await page.getByRole("img", {name: "Next active campaign"}).dispatchEvent("error");
    await page.getByRole("heading", {name: "Sweet moments. Savour every bite."}).waitFor();
    await page.unroute("**/api/storefront/campaigns");
    await page.route("**/api/storefront/campaigns", route => route.fulfill({status: 503, json: {message: "Synthetic campaign outage"}}));
    await page.reload();
    await page.getByRole("heading", {name: "Sweet moments. Savour every bite."}).waitFor();
    await page.getByRole("link", {name: "Order Now", exact: true}).waitFor();
    console.log("PASS: campaign schedules, priority, reduced motion, media-failure next/default fallback, optional API-failure ordering CTA.");
} finally {await browser.close();}
