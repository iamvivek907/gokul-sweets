import assert from "node:assert/strict";
import {createRequire} from "node:module";
const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const browser = await chromium.launch({headless: true});
const context = await browser.newContext({viewport: {width: 390, height: 844}, timezoneId: "America/Los_Angeles"});
let settings = {soundEnabled: false, quietHoursEnabled: false, quietStartMinute: 1320, quietEndMinute: 480,
    scopeId: "test-alert-subject", pushConfigured: true, applicationServerKey: Buffer.concat([Buffer.from([4]), Buffer.alloc(64)]).toString("base64url")};
let enabled = true, nextId = 42, revoked = false, subscriptions = 0;
await context.addInitScript(() => {
    window._permissionRequests = 0; window._pushSubscriptions = 0; window._chimes = 0; window._denyPush = true; window._blockAudio = true;
    Object.defineProperty(document, "visibilityState", {get: () => "visible"});
    class Notification {static permission = "default"; static async requestPermission() {
        window._permissionRequests++; this.permission = window._denyPush ? "denied" : "granted"; return this.permission;
    }}
    Object.defineProperty(window, "Notification", {value: Notification, configurable: true});
    class AudioContext {
        state = "suspended"; currentTime = 0; destination = {};
        async resume() {if (window._blockAudio) throw new Error("Autoplay blocked"); this.state = "running";}
        createGain() {return {gain: {setValueAtTime() {}, exponentialRampToValueAtTime() {}}, connect() {}};}
        createOscillator() {return {frequency: {}, connect() {}, start() {window._chimes++;}, stop() {}};}
    }
    Object.defineProperty(window, "AudioContext", {value: AudioContext, configurable: true});
    let subscribed = null;
    const subscription = {endpoint: "https://fcm.googleapis.com/fcm/send/browser-test", toJSON: () => ({keys: {p256dh: "test-key", auth: "test-auth"}}), async unsubscribe() {subscribed = null; return true;}};
    const registration = {pushManager: {async getSubscription() {return subscribed;}, async subscribe(options) {
        if (!options.userVisibleOnly) throw new Error("Invisible subscription");
        window._pushSubscriptions++; subscribed = subscription; return subscription;
    }}, async unregister() {return true;}};
    Object.defineProperty(navigator, "serviceWorker", {value: {async register() {return registration;}, ready: Promise.resolve(registration),
        async getRegistration() {return registration;}, async getRegistrations() {return [];}}});
});
await context.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname;
    let json = [];
    if (path === "/api/storefront/features") json = {customerAccountHub: true, customerHomeV2: true, notificationInbox: true, notificationAlerts: enabled, futuristicStorefrontV2: true};
    else if (path === "/api/storefront/customer-identity") json = {enabled: true};
    else if (path === "/api/customer/identity/me") json = {authenticated: true, phone: "+919876543210", name: "Alert customer"};
    else if (path === "/api/customer/identity/account") json = {paidOrders: 0, favouriteProductIds: [], addresses: [], preferences: {dietaryNotes: null, preferredBranchId: null}};
    else if (path === "/api/customer/identity/notification-preferences") json = {offerInboxEnabled: false, marketingConsentGranted: false};
    else if (path === "/api/customer/identity/notifications") json = {messages: [{id: nextId, targetType: "ORDER", targetId: "TEST-ORDER", title: "Payment received", message: "Open your order", createdAt: new Date().toISOString(), readAt: null}], unreadCount: 1, nextBefore: null};
    else if (path === "/api/customer/identity/notification-alerts") {
        if (route.request().method() === "PUT") settings = {...settings, ...route.request().postDataJSON()};
        json = settings;
    } else if (path === "/api/customer/identity/push-subscriptions") {subscriptions++; json = {id: "browser-registration"};}
    else if (path === "/api/customer/identity/push-subscriptions/browser-registration") {revoked = true; return route.fulfill({status: 204});}
    return route.fulfill({json});
});
const page = await context.newPage();
const base = process.env.BROWSER_BASE ?? "http://127.0.0.1:3311";
try {
    await page.goto(`${base}/profile#account-notifications`);
    await page.getByRole("heading", {name: "Browser alerts and sound"}).waitFor();
    assert.equal(await page.evaluate(() => window._permissionRequests), 0);
    assert.equal(await page.evaluate(() => window._chimes), 0);
    await page.getByRole("button", {name: "Enable push for this browser"}).click();
    await page.getByText("Browser alerts are not allowed.", {exact: false}).waitFor();
    assert.equal(subscriptions, 0);
    await page.reload();
    await page.getByRole("heading", {name: "Browser alerts and sound"}).waitFor();
    await page.evaluate(() => {window._denyPush = false;});
    await page.getByRole("button", {name: "Enable push for this browser"}).click();
    await page.getByRole("button", {name: "Disable push for this browser"}).waitFor();
    assert.equal(subscriptions, 1);
    await page.getByRole("button", {name: "Disable push for this browser"}).click();
    await page.getByText("Push disabled for this browser.", {exact: false}).waitFor();
    assert.equal(revoked, true);
    await page.getByRole("button", {name: "Activate and test sound in this tab"}).click();
    await page.getByText("Sound is blocked or unsupported.", {exact: false}).waitFor();
    await page.evaluate(() => {window._blockAudio = false;});
    await page.getByRole("button", {name: "Activate and test sound in this tab"}).click();
    await page.getByText("Sound activated in this tab.", {exact: false}).waitFor();
    await page.getByRole("checkbox", {name: "Allow in-page chimes"}).check();
    await page.getByRole("button", {name: "Save alert preferences"}).click();
    await page.getByText("Alert preferences saved.", {exact: true}).waitFor();
    await page.waitForFunction(() => Object.keys(localStorage).some(key => key.startsWith("gokul-alert-cursor:")));
    nextId++;
    await page.evaluate(() => window.dispatchEvent(new Event("gokul-alert-preferences-changed")));
    await page.waitForFunction(() => window._chimes === 2);
    const second = await context.newPage();
    await second.goto(`${base}/profile#account-notifications`);
    await second.getByRole("heading", {name: "Browser alerts and sound"}).waitFor();
    await second.evaluate(() => {window._blockAudio = false;});
    await second.getByRole("button", {name: "Activate and test sound in this tab"}).click();
    await second.getByText("Sound activated in this tab.", {exact: false}).waitFor();
    const before = await page.evaluate(() => window._chimes) + await second.evaluate(() => window._chimes);
    nextId++;
    await Promise.all([page, second].map(tab => tab.evaluate(() => window.dispatchEvent(new Event("gokul-alert-preferences-changed")))));
    await page.waitForTimeout(500);
    const after = await page.evaluate(() => window._chimes) + await second.evaluate(() => window._chimes);
    assert.equal(after - before, 1, "Multiple visible tabs must emit only one chime");
    const minute = new Intl.DateTimeFormat("en-GB", {timeZone: "Asia/Kolkata", hour: "2-digit", minute: "2-digit", hourCycle: "h23"}).format(new Date());
    const [hour, min] = minute.split(":").map(Number); const now = hour * 60 + min;
    settings = {...settings, quietHoursEnabled: true, quietStartMinute: (now + 1435) % 1440, quietEndMinute: (now + 5) % 1440}; nextId++;
    await Promise.all([page, second].map(tab => tab.evaluate(() => window.dispatchEvent(new Event("gokul-alert-preferences-changed")))));
    await page.waitForTimeout(500);
    assert.equal(await page.evaluate(() => window._chimes) + await second.evaluate(() => window._chimes), after);
    await second.close();
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    await page.setViewportSize({width: 1440, height: 1000});
    enabled = false; await page.evaluate(() => sessionStorage.clear()); await page.reload();
    await page.getByRole("heading", {name: /Notification inbox/}).waitFor();
    assert.equal(await page.getByRole("heading", {name: "Browser alerts and sound"}).count(), 0);
    assert.equal(await page.evaluate(() => window._permissionRequests), 0);
    console.log("PASS: explicit permission, denied fallback, subscribe/revoke, blocked sound, multi-tab dedup, quiet hours, mobile and flag OFF.");
} finally {await browser.close();}
