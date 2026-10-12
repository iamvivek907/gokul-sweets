// Loopback synthetic fixtures only; campaign admin requests are intercepted and never reach R2.
import assert from "node:assert/strict";
import {createRequire} from "node:module";
const {chromium} = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const base = "http://127.0.0.1:3409", api = "http://127.0.0.1:18409";
const artifacts = process.env.REVISION_ARTIFACTS;
const branch = (await (await fetch(`${api}/api/branches`)).json()).find(value => value.id === 2001);
const features = await (await fetch(`${api}/api/storefront/features`)).json();
const date = new Date(`${features.today}T12:00:00Z`); date.setUTCDate(date.getUTCDate() + 2);
const later = date.toISOString().slice(0, 10);
const browser = await chromium.launch();
const errors = [];
async function pageFor(width = 390) {
    const context = await browser.newContext({viewport: {width, height: width < 600 ? 844 : 1000}, reducedMotion: "reduce"});
    await context.addInitScript(branch => {
        localStorage.setItem("gokul-selected-branch", JSON.stringify(branch));
        localStorage.setItem("gokul-social-follow-popup-seen", "true");
        localStorage.setItem("gokul-customer-details", JSON.stringify({name: "Saved customer", phone: "9000000000"}));
    }, branch);
    const page = await context.newPage(); page.on("pageerror", error => errors.push(error.message));
    return page;
}
async function shot(page, name) {
    if (artifacts) {
        await page.screenshot({path: `${artifacts}/${name}.png`, fullPage: true});
        await page.evaluate(() => window.scrollTo(0, 0));
        await page.screenshot({path: `${artifacts}/${name}-viewport.png`});
    }
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${name} overflow`);
}
try {
    const page = await pageFor();
    await page.goto(base);
    await page.getByRole("heading", {name: "Sweet moments. Savour every bite."}).waitFor();
    assert.equal(await page.getByRole("button", {name: /Add .* to cart/}).count(), 0, "Home must not duplicate shopping grid");
    await shot(page, "revision-home-mobile");
    await page.getByRole("link", {name: "Order Now", exact: true}).click();
    await page.waitForURL("**/menu");
    await page.getByRole("button", {name: "Add Revision Kaju Katli to cart"}).click();
    const dialog = page.getByRole("dialog", {name: "Revision Kaju Katli"});
    await dialog.getByRole("button", {name: "500 g", exact: true}).click();
    await dialog.getByRole("button", {name: /Add|Update/}).last().click();
    await page.getByRole("button", {name: "Add Revision Samosa to cart"}).click();
    await page.getByLabel("Pickup date", {exact: true}).fill(later);
    await page.getByText(/Pickup options available/).first().waitFor();
    await shot(page, "revision-menu-mobile");
    const cart = await page.evaluate(() => localStorage.getItem("gokul-cart"));
    const customer = await page.evaluate(() => localStorage.getItem("gokul-customer-details"));
    let checks = 0;
    page.on("request", request => {if (request.url().endsWith("/availability")) checks++;});
    await page.goto(`${base}/checkout/pickup`);
    await page.locator('#smart-pickup-time option[value$=":NORMAL"]').first().waitFor({state: "attached"});
    const beforeSelection = checks;
    await page.getByLabel("Pickup time", {exact: true}).selectOption(await page.locator('#smart-pickup-time option[value$=":NORMAL"]').first().getAttribute("value"));
    await page.waitForTimeout(500);
    assert.equal(checks, beforeSelection, "Time selection must not recompute full horizon");
    await shot(page, "revision-pickup-mobile");
    await page.getByRole("button", {name: "Continue", exact: true}).click();
    await page.waitForURL("**/checkout/customer");
    assert.equal(await page.evaluate(() => localStorage.getItem("gokul-cart")), cart);
    assert.equal(await page.evaluate(() => localStorage.getItem("gokul-customer-details")), customer);
    await page.goto(`${base}/menu`);
    await page.getByText(/Pickup options available/).first().waitFor();
    assert.equal(await page.getByText(/Your saved pickup time no longer fits/).count(), 0,
        "Unchosen draft/ready-required products must not invalidate the saved cart pickup");

    const partial = await pageFor();
    await partial.addInitScript(({cart, later}) => {
        const value = JSON.parse(cart); value.items.find(item => item.product.id === 2002).weightGrams = 950;
        localStorage.setItem("gokul-cart", JSON.stringify(value));
        localStorage.setItem("gokul-pickup-intent", JSON.stringify({branchId: 2001, date: later}));
    }, {cart, later});
    await partial.goto(`${base}/cart`);
    await partial.getByRole("heading", {name: "Review these items for your pickup"}).waitFor();
    const issue = partial.getByRole("status").filter({hasText: "Review these items"});
    assert.match(await issue.innerText(), /Revision Kaju Katli.*smaller quantity/);
    assert.doesNotMatch(await issue.innerText(), /Revision Samosa:/);
    await shot(partial, "revision-mixed-cart-mobile");
    await partial.getByRole("button", {name: /Change Revision Kaju Katli quantity/}).click();
    const editWeight = partial.getByRole("dialog", {name: "Revision Kaju Katli"});
    await editWeight.getByRole("button", {name: "250 g", exact: true}).click();
    await editWeight.getByRole("button", {name: /Add|Update/}).last().click();
    await issue.waitFor({state: "hidden"});
    assert.equal(await partial.evaluate(() => JSON.parse(localStorage.getItem("gokul-cart")).items.length), 2);

    await page.route("**/api/storefront/features", route => route.fulfill({status: 503, json: {message: "test failure"}}));
    await page.goto(`${base}/checkout/pickup`);
    await page.getByRole("heading", {name: "When would you like to collect?"}).waitFor();
    await page.getByText(/Keeping your pickup layout/).waitFor();
    assert.equal(await page.locator("#pickup-date").count(), 0, "A settings outage must not switch layout");
    await page.unroute("**/api/storefront/features");
    await page.route("**/api/branches/2001/availability", route => route.fulfill({status: 503, json: {message: "raw diagnostic not for customer"}}));
    await page.reload();
    await page.getByText("We couldn't check pickup times. Your cart is saved. Try again.").waitFor();
    assert.equal(await page.getByText("raw diagnostic not for customer").count(), 0);
    assert.equal(await page.evaluate(() => localStorage.getItem("gokul-cart")), cart);
    await page.unroute("**/api/branches/2001/availability");

    const desktop = await pageFor(1440);
    await desktop.goto(base); await desktop.getByRole("heading", {name: "Sweet moments. Savour every bite."}).waitFor();
    await shot(desktop, "revision-home-desktop");
    // New sessions show a loading state rather than flashing the legacy layout.
    const slow = await pageFor();
    let releaseFeatures;
    const waitFeatures = new Promise(resolve => {releaseFeatures = resolve;});
    await slow.route("**/api/storefront/features", async route => {await waitFeatures; await route.fulfill({json: features});});
    await slow.goto(`${base}/checkout/pickup`);
    await slow.getByText("Loading pickup options...", {exact: true}).waitFor();
    assert.equal(await slow.locator("#pickup-date").count(), 0);
    releaseFeatures();
    await slow.getByRole("heading", {name: "When would you like to collect?"}).waitFor();

    // Synthetic labelled artwork exercises responsive image geometry, not actual food photography.
    for (const width of [390, 1440]) {
        const visual = await pageFor(width);
        await visual.route("**/api/menu?branchId=2001", async route => {
            const response = await route.fetch(), data = await response.json();
            for (const category of data) for (const product of category.products) product.imageUrl = `/synthetic-product-${product.id}.svg`;
            await route.fulfill({json: data});
        });
        await visual.route("**/synthetic-product-*.svg", route => route.fulfill({contentType: "image/svg+xml",
            body: '<svg xmlns="http://www.w3.org/2000/svg" width="900" height="900"><rect width="900" height="900" fill="#d4a675"/><circle cx="450" cy="450" r="330" fill="#f7eddf"/><circle cx="450" cy="450" r="240" fill="#9b5b39"/><text x="450" y="450" text-anchor="middle" font-size="38" fill="white">Synthetic media fixture</text></svg>'}));
        await visual.goto(base);
        await visual.getByText("On the menu", {exact: true}).waitFor();
        await visual.getByRole("link", {name: "Take a look"}).waitFor();
        await shot(visual, `revision-home-media-${width}`);
        await visual.getByRole("img").first().dispatchEvent("error");
        await visual.getByRole("link", {name: "Order Now", exact: true}).waitFor();
        await visual.close();
    }
    const authorization = "Basic " + Buffer.from("revision-owner:test-only").toString("base64");
    const profile = await (await fetch(`${api}/api/admin/auth/me`, {headers: {Authorization: authorization}})).json();
    await desktop.addInitScript(session => sessionStorage.setItem("gokul-admin-session", JSON.stringify(session)), {authorization, profile});
    let saved = null, drafts = 0, puts = 0, uploads = 0, publishAttempts = 0;
    const uploadKeys = new Set();
    const image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jBuoAAAAASUVORK5CYII=";
    await desktop.route("**/api/admin/homepage-campaigns**", async route => {
        const request = route.request(), method = request.method();
        if (method === "GET") return route.fulfill({json: saved ? [saved] : []});
        if (request.url().includes("/media")) {
            uploads++;
            const key = request.headers()["idempotency-key"];
            if (!uploadKeys.has(key)) {puts++; uploadKeys.add(key);}
            saved = {...saved, mediaUrl: image, mediaType: "image/png"};
            return route.fulfill({json: saved});
        }
        const body = request.postDataJSON();
        if (method === "POST") {drafts++; saved = {...body, id: 8001, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString()};}
        else if (body.active && publishAttempts++ === 0) return route.fulfill({status: 400, json: {message: "Synthetic publish failure. Retry publication."}});
        else saved = {...saved, ...body};
        return route.fulfill({json: saved});
    });
    await desktop.goto(`${base}/admin/homepage-campaigns`);
    await desktop.getByLabel("Title", {exact: true}).fill("Preview campaign");
    await desktop.getByLabel("Banner image or video", {exact: true}).setInputFiles({name: "banner.png", mimeType: "image/png", buffer: Buffer.from(image.split(",")[1], "base64")});
    await desktop.getByAltText("Campaign preview").waitFor();
    assert.equal(drafts, 0); assert.equal(puts, 0);
    await shot(desktop, "revision-campaign-desktop");
    await desktop.getByRole("button", {name: "Publish campaign", exact: true}).evaluate(button => {button.click(); button.click();});
    await desktop.getByText(/Synthetic publish failure/).waitFor();
    assert.equal(drafts, 1); assert.equal(puts, 1); assert.equal(uploads, 1);
    await desktop.getByRole("button", {name: "Publish campaign", exact: true}).click();
    await desktop.getByText(/Campaign published/).waitFor();
    assert.equal(drafts, 1); assert.equal(puts, 1); assert.equal(uploads, 1);
    await desktop.getByLabel("Title", {exact: true}).fill("Metadata change");
    await desktop.getByRole("button", {name: "Save & publish", exact: true}).click();
    await desktop.getByText(/Campaign published/).waitFor();
    assert.equal(puts, 1); assert.equal(uploads, 1);
    const nav = desktop.getByRole("navigation", {name: "Admin tools"});
    assert.equal(await nav.getByRole("button", {name: "Storefront", exact: true}).getAttribute("aria-expanded"), "true");
    await nav.getByRole("button", {name: "Menu", exact: true}).click();
    await nav.getByRole("link", {name: "Tax categories", exact: true}).waitFor();
    await shot(desktop, "revision-navigation-desktop");
    await nav.getByRole("link", {name: "Product images", exact: true}).click();
    await desktop.getByRole("heading", {name: "Menu Image Management", exact: true}).waitFor();
    await desktop.getByLabel("Search products", {exact: false}).fill("Kaju").catch(async () => desktop.locator("#product-search").fill("Kaju"));
    await desktop.getByRole("link", {name: "Back to menu"}).click();
    await desktop.getByRole("link", {name: /Image/}).first().click();
    await desktop.locator("#product-search").waitFor();
    assert.equal(await desktop.locator("#product-search").inputValue(), "Kaju");

    // The page writes to the real existing rebate API, never a second discount engine.
    await desktop.goto(`${base}/admin/notifications`);
    const offerCode = `BROWSER${Date.now()}`;
    await desktop.getByLabel("Offer code", {exact: true}).fill(offerCode);
    await desktop.getByLabel("Offer name", {exact: true}).fill("Browser scheduled discount");
    await desktop.getByLabel("Branch", {exact: true}).selectOption("2001");
    await desktop.getByLabel("Discount (%)", {exact: true}).fill("10");
    await desktop.getByLabel("Maximum discount (INR)", {exact: true}).fill("50");
    await desktop.getByLabel("Starts (India time)", {exact: true}).fill(`${features.today}T00:00`);
    await desktop.getByLabel("Ends (India time)", {exact: true}).fill(`${later}T23:00`);
    let offerCreates = 0;
    desktop.on("request", request => {if (request.url().endsWith("/api/admin/rebates") && request.method() === "POST") offerCreates++;});
    await desktop.getByRole("button", {name: "Create offer", exact: true}).evaluate(button => {button.click(); button.click();});
    await desktop.getByText("Offer created and enabled for its scheduled dates.", {exact: true}).waitFor();
    assert.equal(offerCreates, 1);
    const offer = desktop.getByRole("article").filter({hasText: offerCode});
    await offer.getByRole("button", {name: "Deactivate", exact: true}).click();
    await desktop.getByText("Offer disabled.", {exact: true}).waitFor();
    await offer.getByRole("button", {name: "Edit", exact: true}).click();
    await desktop.getByLabel("Offer name", {exact: true}).fill("Browser updated discount");
    await desktop.getByRole("button", {name: "Save offer", exact: true}).click();
    await desktop.getByText("Offer updated. Existing eligibility rules still apply.", {exact: true}).waitFor();
    const updatedOffer = desktop.getByRole("article").filter({hasText: offerCode});
    await updatedOffer.getByRole("button", {name: "Activate", exact: true}).waitFor();
    await shot(desktop, "revision-offers-desktop");
    for (const type of ["FIXED_AMOUNT", "SLAB"]) {
        await desktop.getByLabel("Offer code", {exact: true}).fill(`${offerCode}${type}`);
        await desktop.getByLabel("Offer name", {exact: true}).fill(`Browser ${type}`);
        await desktop.getByLabel("Branch", {exact: true}).selectOption("2001");
        await desktop.getByLabel("Discount type", {exact: true}).selectOption(type);
        if (type === "FIXED_AMOUNT") {
            await desktop.getByLabel("Discount (INR)", {exact: true}).fill("15");
            await desktop.getByLabel("Visibility", {exact: true}).selectOption("CODE_ONLY");
            await desktop.getByLabel("Audience", {exact: true}).selectOption("CUSTOMER");
            await desktop.getByLabel("Customer phone numbers", {exact: true}).fill("9000000000");
        } else {
            await desktop.getByLabel("Minimum spend 1 (INR)", {exact: true}).fill("100");
            await desktop.getByLabel("Discount 1 (INR)", {exact: true}).fill("10");
            await desktop.getByRole("button", {name: "Add slab", exact: true}).click();
            await desktop.getByLabel("Minimum spend 2 (INR)", {exact: true}).fill("200");
            await desktop.getByLabel("Discount 2 (INR)", {exact: true}).fill("25");
        }
        await desktop.getByLabel("Starts (India time)", {exact: true}).fill(`${features.today}T00:00`);
        await desktop.getByLabel("Ends (India time)", {exact: true}).fill(`${later}T23:00`);
        await desktop.getByRole("button", {name: "Create offer", exact: true}).click();
        await desktop.getByRole("article").filter({hasText: `${offerCode}${type}`}).waitFor();
    }

    const clerk = await pageFor();
    const clerkAuth = "Basic " + Buffer.from("revision-clerk:test-only").toString("base64");
    const clerkProfile = await (await fetch(`${api}/api/admin/auth/me`, {headers: {Authorization: clerkAuth}})).json();
    await clerk.addInitScript(session => sessionStorage.setItem("gokul-admin-session", JSON.stringify(session)), {authorization: clerkAuth, profile: clerkProfile});
    await clerk.goto(`${base}/admin/inventory/automation`);
    await clerk.getByRole("button", {name: "Admin menu", exact: true}).click();
    const mobileNav = clerk.getByRole("navigation", {name: "Admin tools"});
    assert.equal(await mobileNav.getByRole("button", {name: "Menu", exact: true}).count(), 0);
    assert.equal(await mobileNav.getByRole("button", {name: "Storefront", exact: true}).count(), 0);
    await mobileNav.getByRole("link", {name: "Future production"}).waitFor();
    await shot(clerk, "revision-navigation-restricted-mobile");
    await clerk.keyboard.press("Escape");
    assert.equal(await clerk.getByRole("dialog", {name: "Admin navigation"}).count(), 0);
    await clerk.goto(`${base}/admin/notifications`);
    await clerk.getByText("You do not have permission to view or manage offers.", {exact: true}).waitFor();
    assert.deepEqual(errors, []);
    console.log("PASS: curated home, menu early date check, mixed cart, dropdown pickup, no selection request storm, flag error stability, saved inputs, campaign preview/double-submit/retry/metadata PUT counts, offer create/edit/status/double-submit, grouped owner/restricted navigation and image return.");
} finally {await browser.close();}
