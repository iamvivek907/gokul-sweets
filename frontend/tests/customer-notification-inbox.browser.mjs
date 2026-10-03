import assert from "node:assert/strict";
import {createRequire} from "node:module";
const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const browser = await chromium.launch({headless: true});
const context = await browser.newContext({viewport: {width: 390, height: 844}, timezoneId: "America/Los_Angeles"});
const page = await context.newPage();
let enabled = true, authenticated = true, failLoad = false, failRead = false, read = false, reads = 0;
const message = {id: 42, eventKey: "payment:1:PAID", kind: "PAYMENT_PAID", targetType: "ORDER", targetId: "GKS-EXACT-42",
    title: "Payment received", message: "Your payment was verified. Open this order for its current fulfilment status.",
    createdAt: "2026-09-29T18:35:00Z", deliveryState: "AVAILABLE"};
try {
    await page.route("**/api/**", async route => {
        const path = new URL(route.request().url()).pathname;
        let json = [];
        if (path === "/api/storefront/features") json = {customerAccountHub: true, customerHomeV2: true, notificationInbox: enabled, futuristicStorefrontV2: true};
        else if (path === "/api/storefront/customer-identity") json = {enabled: true};
        else if (path === "/api/customer/identity/me") json = {authenticated, phone: "+919876543210", name: "Test customer"};
        else if (path === "/api/customer/identity/account") json = {paidOrders: 0, favouriteProductIds: [], addresses: [], preferences: {dietaryNotes: null, preferredBranchId: null}};
        else if (path === "/api/customer/identity/notifications") {
            if (failLoad) return route.abort("failed");
            json = {messages: [{...message, readAt: read ? "2026-09-29T18:40:00Z" : null},
                {...message, id: 43, title: "Occasion deposit received", targetType: "OCCASION", targetId: "request-43", readAt: "2026-09-29T18:40:00Z"}, {...message,id:44,kind:"PICKED_UP",targetId:"COMPLETED",title:"Pickup completed",readAt:"2026-09-29T18:40:00Z"}], unreadCount: read ? 0 : 1, nextBefore: null, readThrough: 44};
        } else if (path.endsWith("/notifications/read-target") || path.endsWith("/notifications/read-all")) {
            const input = route.request().postDataJSON();
            if (path.endsWith("read-target")) assert.equal(input.targetId, "GKS-EXACT-42");
            else assert.equal(input.throughId, 44);
            reads++; read = true; return route.fulfill({status: 204});
        } else if (path === "/api/customer/identity/notification-preferences") json = {offerInboxEnabled: false, marketingConsentGranted: false};
        else if (path.endsWith("/notifications/42/read")) {
            if (failRead) return route.abort("failed");
            reads++; read = true; return route.fulfill({status: 204});
        }
        return route.fulfill({json});
    });
    const base = process.env.BROWSER_BASE ?? "http://127.0.0.1:3311";
    await page.goto(`${base}/profile`);
    await page.getByRole("link", {name: "Notification inbox", exact: true}).click();
    await page.getByRole("heading", {name: "Payment received"}).waitFor();
    const back=page.getByRole('link',{name:'Back to previous page'});
    assert.equal(new URL(page.url()).pathname,'/notifications');
    assert.equal(await back.getAttribute('href'),'/profile');
    await page.reload();await page.getByRole('heading',{name:'Payment received'}).waitFor();
    assert.equal(await back.getAttribute('href'),'/profile');
    await back.click();await page.waitForURL('**/profile');
    for(const origin of ['/menu?category=sweets#menu-products','/','/profile#account-orders']) {
        await page.goto(`${base}${origin}`);
        await page.getByRole('link',{name:/^Notifications, .* unread$/}).first().click();
        await page.getByRole('heading',{name:'Payment received'}).waitFor();
        assert.equal(await back.getAttribute('href'),origin);
        await back.click();await page.waitForURL(url=>url.pathname+url.search+url.hash===origin);assert.equal(new URL(page.url()).pathname+new URL(page.url()).search+new URL(page.url()).hash,origin);
    }
    await page.goto(`${base}/profile#account-notifications`);await page.waitForURL('**/notifications?from=*');
    await page.getByRole('heading',{name:'Payment received'}).waitFor();
    assert.equal(await back.getAttribute('href'),'/profile');
    for(const origin of ['https://example.com','//example.com','/\\example.com','/notifications']) {
        await page.goto(`${base}/notifications?from=${encodeURIComponent(origin)}`);
        await page.getByRole('heading',{name:'Payment received'}).waitFor();
        assert.equal(await back.getAttribute('href'),'/profile');
    }
    assert.equal(await page.locator('a[href="/orders/GKS-EXACT-42"]').getAttribute("href"), "/orders/GKS-EXACT-42");
    assert.equal(await page.getByRole("link", {name: "View bulk request"}).getAttribute("href"), "/occasions/requests?enquiry=request-43");
    assert.match(await page.locator("time").first().textContent(), /30 Sept|30 Sep/);
    assert.match(await page.locator("time").first().textContent(), /12:05.*am.*IST/i);
    await page.getByText("Notification settings", {exact: true}).click();
    assert.equal(await page.getByRole("checkbox", {name: "Include optional offers in my inbox when available"}).isDisabled(), true);
    const review=page.getByRole("link",{name:"Share an optional review"});
    assert.equal(await review.evaluate(element=>getComputedStyle(element).color),"rgb(255, 250, 242)");
    assert.equal(await review.evaluate(element=>getComputedStyle(element).backgroundColor),"rgb(20, 57, 54)");
    failRead = true;
    await page.getByRole("button", {name: "Mark as read"}).click();
    await page.getByRole("alert").filter({hasText: "could not confirm"}).waitFor();
    assert.equal(reads, 0);
    assert.equal(await page.getByRole("button", {name: "Mark as read"}).count(), 1);
    failRead = false;
    await page.getByRole("button", {name: "Mark as read"}).click();
    await page.getByRole("heading", {name: /Notification inbox.*0 unread/}).waitFor();
    assert.equal(reads, 1);
    assert.equal(await page.getByRole("button", {name: "Mark as read"}).count(), 0);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    if (process.env.NOTIFICATION_SCREENSHOT_DIR) await page.screenshot({path: `${process.env.NOTIFICATION_SCREENSHOT_DIR}/notifications-mobile.png`, fullPage:true});
    read = false;
    await page.getByRole("button", {name: "Refresh inbox"}).click();
    await page.getByRole("heading", {name: /Notification inbox.*1 unread/}).waitFor();
    await page.locator('a[href="/orders/GKS-EXACT-42"]').evaluate(link => link.addEventListener("click", event => event.preventDefault(), {once:true}));
    await page.locator('a[href="/orders/GKS-EXACT-42"]').click();
    await page.getByRole("heading", {name: /Notification inbox.*0 unread/}).waitFor();
    assert.equal(reads, 2);
    read = false;
    await page.getByRole("button", {name: "Refresh inbox"}).click();
    await page.getByRole("heading", {name: /Notification inbox.*1 unread/}).waitFor();
    await page.getByRole("button", {name: "Mark all read"}).click();
    await page.getByRole("heading", {name: /Notification inbox.*0 unread/}).waitFor();
    assert.equal(reads, 3);
    await page.setViewportSize({width: 1440, height: 1000});
    if (process.env.NOTIFICATION_SCREENSHOT_DIR) await page.screenshot({path: `${process.env.NOTIFICATION_SCREENSHOT_DIR}/notifications-desktop.png`, fullPage:true});
    failLoad = true;
    await page.getByRole("button", {name: "Refresh inbox"}).click();
    await page.getByRole("alert").filter({hasText: "could not confirm"}).waitFor();
    assert.equal(await page.getByRole("heading", {name: "Payment received"}).count(), 1);
    failLoad = false;
    enabled = false;
    await page.evaluate(() => sessionStorage.clear());
    await page.reload();
    await page.getByText("Notifications are not available yet. Check Order history for current updates.", {exact:true}).waitFor();
    assert.equal(await page.getByRole("link", {name: "Notification inbox", exact: true}).count(), 0);
    enabled = true; authenticated = false;
    await page.evaluate(() => sessionStorage.clear());
    await page.reload();
    await page.getByRole("button", {name: "Verify with SMS", exact:true}).waitFor();
    assert.equal(await page.getByRole("heading", {name: "Payment received"}).count(), 0);
    console.log("PASS: exact links, IST, read acknowledgement, offline recovery, consent, mobile layout, flag-OFF and guest isolation.");
} finally {await browser.close();}
