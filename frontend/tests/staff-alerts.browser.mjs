import assert from "node:assert/strict";
import {createRequire} from "node:module";
const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const browser = await chromium.launch({headless: true});
const context = await browser.newContext({viewport: {width: 390, height: 844}, timezoneId: "America/Los_Angeles"});
let enabled = true, registered = false, read = false, denied = true, failDisable = true, failRead = true;
let orderStatus = "CONFIRMED", firstPreparation = true, mutations = 0;
const registrationId = "d1779266-3814-48cf-8f6d-8c23b832c1c3";
const csrf = "test-only-staff-csrf";
const base = process.env.BROWSER_BASE ?? "http://127.0.0.1:3311";
await context.addInitScript(() => {
    window._permissionRequests = 0;
    class Notification {static permission = "default"; static async requestPermission() {
        window._permissionRequests++; this.permission = window._denyPush ? "denied" : "granted"; return this.permission;
    }}
    Object.defineProperty(window, "Notification", {value: Notification, configurable: true});
    const subscription = {endpoint: "https://fcm.googleapis.com/fcm/send/staff-browser-test", toJSON: () => ({keys: {p256dh: "test-key", auth: "test-auth"}}), async unsubscribe() {return true;}};
    const registration = {pushManager: {async getSubscription() {return subscription;}, async subscribe() {return subscription;}}};
    Object.defineProperty(navigator, "serviceWorker", {value: {async register() {return registration;}, ready: Promise.resolve(registration), async getRegistration() {return registration;}, async getRegistrations() {return [];}}});
});
const order = () => ({orderNumber: "STAFF-ORDER", branchId: 1, branchName: "Main branch", branchAddress: "Test branch",
    customerName: "Test customer", customerPhone: "9876543210", pickupDate: "2026-10-01", pickupStartTime: "18:00", pickupEndTime: "18:30",
    fulfillmentType: "PICKUP", pickupType: "NORMAL", orderStatus, paymentStatus: "PAID", subtotal: 100, taxAmount: 0, totalAmount: 100,
    items: [{productName: "Paneer", saleMode: "WEIGHT", weightGrams: 1000, quantity: 1}], createdAt: "2026-09-30T12:00:00", updatedAt: "2026-09-30T12:00:00"});
await context.route("**/api/**", async route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    if (request.method() === "OPTIONS") return route.fulfill({status: 204, headers: {"Access-Control-Allow-Origin": base, "Access-Control-Allow-Credentials": "true", "Access-Control-Allow-Methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS", "Access-Control-Allow-Headers": "content-type,x-staff-csrf"}});
    let json = [];
    if (path === "/api/admin/auth/me") return route.fulfill({json: {staffId: 12, username: "test-staff", fullName: "Kitchen staff", roleName: "KITCHEN", phone: null,
        branchIds: [1], permissions: ["ORDER_VIEW", "ORDER_START_PREPARATION", "ORDER_MARK_READY"]}, headers: {"X-Staff-CSRF": csrf, "Access-Control-Expose-Headers": "X-Staff-CSRF"}});
    if (path === "/api/admin/notifications/settings") json = {enabled, environment: "DEV", staffId: 12, pushConfigured: true,
        applicationServerKey: Buffer.concat([Buffer.from([4]), Buffer.alloc(64)]).toString("base64url"), deviceActive: registered,
        emailConfigured: false, reminderMinutes: 10, escalationMinutes: 5};
    else if (path === "/api/admin/notifications") json = {messages: [{event: {id: 1, orderNumber: "STAFF-ORDER", branchId: 1,
        kind: "PREPARATION_OVERDUE", title: "Preparation is overdue", message: "Order STAFF-ORDER · Main branch. Booked pickup 01 Oct 2026, 6:00 pm IST has passed. Take action.",
        createdAt: "2026-10-01T12:30:00Z"}, readAt: read ? "2026-10-01T12:31:00Z" : null, actionRequired: orderStatus === "CONFIRMED", emailState: null, pushState: null}], unreadCount: read ? 0 : 1, nextBefore: null};
    else if (path === "/api/admin/notifications/push-subscriptions") {
        assert.equal(request.headers()["x-staff-csrf"], csrf); registered = true; json = {id: registrationId};
    } else if (path === `/api/admin/notifications/push-subscriptions/${registrationId}`) {
        assert.equal(request.headers()["x-staff-csrf"], csrf);
        if (failDisable) {failDisable = false; return route.abort("internetdisconnected");}
        registered = false; return route.fulfill({status: 204});
    } else if (path === "/api/admin/notifications/1/read") {
        assert.equal(request.headers()["x-staff-csrf"], csrf);
        if (failRead) {failRead = false; return route.abort("internetdisconnected");}
        read = true; return route.fulfill({status: 204});
    } else if (path === "/api/admin/orders/STAFF-ORDER") json = order();
    else if (path === "/api/admin/orders/STAFF-ORDER/status") {
        mutations++; assert.equal(request.headers()["x-staff-csrf"], csrf);
        if (firstPreparation) {firstPreparation = false; return route.fulfill({status: 409, json: {message: "Preparation window is not open yet."}});}
        orderStatus = request.postDataJSON().status; json = order();
    } else if (path === "/api/admin/orders/STAFF-ORDER/delay") {
        assert.equal(request.headers()["x-staff-csrf"], csrf);
        assert.equal(request.postDataJSON().estimatedReadyAt, "2026-10-01T18:15"); json = order();
    }
    return route.fulfill({json});
});
const page = await context.newPage();
page.on("pageerror", error => console.error("BROWSER", error.message));
try {
    await page.goto(`${base}/admin/staff-notifications`);
    await page.getByRole("heading", {name: "Get alerts when the portal is closed"}).waitFor();
    await page.getByRole("link", {name: "Staff alerts, 1 unread"}).waitFor();
    assert.equal(await page.evaluate(() => window._permissionRequests), 0);
    await page.evaluate(value => {window._denyPush = value;}, denied);
    await page.getByRole("button", {name: "Enable staff push for this browser"}).click();
    await page.getByText("Push permission was not granted.", {exact: false}).waitFor();
    assert.equal(registered, false);
    denied = false; await page.reload();
    await page.getByRole("heading", {name: "Get alerts when the portal is closed"}).waitFor();
    await page.evaluate(value => {window._denyPush = value;}, denied);
    await page.getByRole("button", {name: "Enable staff push for this browser"}).click();
    await page.getByRole("button", {name: "Check staff registration"}).waitFor();
    await page.getByRole("button", {name: "Disable staff push"}).click();
    await page.getByRole("alert").waitFor(); assert.equal(registered, true);
    assert.equal(await page.getByRole("button", {name: "Disable staff push"}).count(), 1);
    await page.getByRole("button", {name: "Disable staff push"}).click();
    await page.getByText("Staff push disabled for this browser.", {exact: false}).waitFor(); assert.equal(registered, false);
    await page.getByRole("button", {name: "Mark as read"}).click(); await page.getByRole("alert").waitFor();
    assert.equal(read, false); assert.equal(mutations, 0);
    await page.getByRole("button", {name: "Mark as read"}).click();
    await page.getByRole("link", {name: "Staff alerts, 0 unread"}).waitFor();
    assert.equal(await page.getByText("Action needed", {exact: true}).count(), 1); assert.equal(mutations, 0);
    await page.getByText("Email escalation is not configured", {exact: false}).waitFor();
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    if (process.env.ALERT_SCREENSHOT_DIR) await page.screenshot({path: `${process.env.ALERT_SCREENSHOT_DIR}/staff-mobile.png`, fullPage: true});
    await page.getByRole("link", {name: "Open order STAFF-ORDER"}).click();
    await page.getByRole("button", {name: "Start preparation"}).waitFor();
    await page.getByRole("button", {name: "Start preparation"}).click();
    await page.getByText("Preparation window is not open yet.", {exact: true}).waitFor(); assert.equal(orderStatus, "CONFIRMED");
    await page.getByRole("button", {name: "Start preparation"}).click();
    await page.getByRole("button", {name: "Mark ready"}).waitFor();
    await page.getByLabel("Revised ready time (IST)").fill("2026-10-01T18:15");
    await page.getByLabel("Customer explanation").fill("Preparation needs fifteen more minutes.");
    await page.getByRole("button", {name: "Publish revised estimate"}).click();
    await page.getByRole("button", {name: "Mark ready"}).click();
    await page.getByText("READY FOR PICKUP", {exact: true}).waitFor();
    await page.setViewportSize({width: 1440, height: 1000});
    if (process.env.ALERT_SCREENSHOT_DIR) await page.screenshot({path: `${process.env.ALERT_SCREENSHOT_DIR}/staff-order-desktop.png`, fullPage: true});
    enabled = false; await page.goto(`${base}/admin/staff-notifications`);
    await page.getByText("Staff alerts are not enabled yet.", {exact: false}).waitFor();
    assert.equal(await page.getByRole("button", {name: "Enable staff push for this browser"}).count(), 0);
    assert.equal(await page.getByRole("link", {name: /Staff alerts,/}).count(), 0);
    console.log("PASS: staff permission gesture, denial, registration, honest offline revoke/read, CSRF, modern mobile layout, exact order/actions, IST estimate and flag OFF.");
} catch (error) {console.error("URL", page.url(), "BODY", (await page.locator("body").innerText()).slice(0,2500)); throw error;} finally {await browser.close();}
