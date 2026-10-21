import assert from "node:assert/strict";
import {createRequire} from "node:module";
const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const browser = await chromium.launch({headless: true, executablePath: process.env.CHROMIUM_PATH});
const context = await browser.newContext({viewport: {width: 390, height: 844}, timezoneId: "America/Los_Angeles"});
let settings = {soundEnabled: false, quietHoursEnabled: false, quietStartMinute: 1320, quietEndMinute: 480,
    scopeId: "test-alert-subject", pushConfigured: true, applicationServerKey: Buffer.concat([Buffer.from([4]), Buffer.alloc(64)]).toString("base64url")};
let authenticated = true, marketing = false, consentWrites = 0, failSubscription = false, registrationActive = true;
let enabled = true, nextId = 42, revoked = false, subscriptions = 0;
await context.addInitScript(() => {
    Object.defineProperty(navigator, "standalone", {value: true, configurable: true});
    window._permissionRequests = 0; window._pushSubscriptions = 0; window._chimes = 0; window._denyPush = false; window._blockAudio = true;
    window._visibility = "visible";
    Object.defineProperty(document, "visibilityState", {get: () => window._visibility});
    class Notification {static permission = localStorage.getItem("gokul-push-device:test-alert-subject") ? "granted" : "default"; static async requestPermission() {
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
    const registration = {pushManager: {async getSubscription() {return subscribed ?? (Notification.permission === "granted" && localStorage.getItem("gokul-push-device:test-alert-subject") ? subscription : null);}, async subscribe(options) {
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
    else if (path === "/api/customer/identity/me") json = {authenticated, phone: "+919876543210", name: "Alert customer"};
    else if (path === "/api/customer/identity/orders/page") json = {orders: [], nextBefore: null};
    else if (path === "/api/customer/identity/account") json = {paidOrders: 0, favouriteProductIds: [], addresses: [], preferences: {dietaryNotes: null, preferredBranchId: null}};
    else if (path === "/api/customer/identity/notification-preferences") json = {offerInboxEnabled: false, marketingConsentGranted: false};
    else if (path === "/api/customer/identity/notifications") json = {messages: [{id: nextId, kind: "READY_FOR_PICKUP", targetType: "ORDER", targetId: "TEST-ORDER", title: "Your order is ready for pickup", message: "Order TEST-ORDER · Main branch. Your order is ready. Open your order for branch and booked pickup details.", createdAt: new Date().toISOString(), readAt: null}], unreadCount: 1, nextBefore: null};
    else if (path === "/api/customer/identity/notification-alerts") {
        if (route.request().method() === "PUT") settings = {...settings, ...route.request().postDataJSON()};
        json = settings;
    } else if (path === "/api/customer/identity/consents") json = Object.fromEntries(["MARKETING", "OCCASION_REMINDERS", "COARSE_AREA_ANALYTICS"].map(key => [key, {granted: key === "MARKETING" && marketing, policyVersion: "test", recordedAt: null}]));
    else if (path === "/api/customer/identity/consents/MARKETING") {consentWrites++; marketing = route.request().postDataJSON().granted; json = {granted: marketing, policyVersion: "test", recordedAt: null};}
    else if (path === "/api/customer/identity/privacy-requests") json = [];
    else if (path === "/api/customer/identity/push-subscriptions") {subscriptions++; if (failSubscription) return route.fulfill({status: 503, json: {message: "Retry"}}); json = {id: "browser-registration"};}
    else if (path === "/api/customer/identity/push-subscriptions/browser-registration") {if (route.request().method() === "GET") json = {enabled: registrationActive}; else {revoked = true; return route.fulfill({status: 204});}}
    return route.fulfill({json});
});

const page = await context.newPage();
const base = process.env.BROWSER_BASE ?? "http://127.0.0.1:3311";
const card = page.getByRole("region", {name: "Enable app notifications"});
async function fits(width) {
    await page.setViewportSize({width, height: 844});
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, `No overflow at ${width}px`);
}
try {
    await page.goto(`${base}/profile`);
    await card.waitFor();
    assert.equal(await page.evaluate(() => window._permissionRequests), 0, "Never prompt automatically");
    await fits(320); await fits(390); await fits(1280);
    await page.setViewportSize({width: 390, height: 844});
    await card.getByRole("button", {name: "Not now"}).click({trial: true});
    await page.screenshot({path: "/tmp/gokul-notification-opt-in.png", fullPage: false});
    await card.getByRole("button", {name: "Not now"}).click();
    await page.getByRole("button", {name: "Edit details", exact: true}).first().click();
    await page.getByRole("link", {name: "Privacy & your data", exact: true}).click();
    await page.waitForURL("**/profile/privacy");
    assert.equal(await card.count(), 0, "Skip lasts across client navigation");
    await page.getByRole("region", {name: "Privacy choices"}).waitFor();
    await page.evaluate(() => {
        window._visibility = "hidden"; document.dispatchEvent(new Event("visibilitychange"));
        const now = Date.now; Date.now = () => now() + 360_000;
        window._visibility = "visible"; document.dispatchEvent(new Event("visibilitychange")); Date.now = now;
    });
    await card.waitFor();
    assert.equal(await page.evaluate(() => window._permissionRequests), 0);
    await card.getByRole("button", {name: "Not now"}).click();
    const choices = page.getByRole("region", {name: "Privacy choices"});
    const offers = choices.getByRole("group", {name: "Offers and news"});
    assert.equal(await offers.getByRole("radio", {name: "Off", exact: true}).isChecked(), true);
    await offers.getByRole("radio", {name: "On", exact: true}).click();
    await page.waitForFunction(() => document.querySelector('[aria-label="Privacy choices"] input[aria-label="On"]')?.checked);
    await offers.getByRole("radio", {name: "Off", exact: true}).click();
    await page.waitForTimeout(200);
    assert.equal(marketing, false); assert.equal(consentWrites, 2);
    for (const width of [320, 390, 1280]) await fits(width);
    await page.setViewportSize({width: 390, height: 844});
    await page.evaluate(() => scrollTo(0, 0));
    await page.screenshot({path: "/tmp/gokul-privacy-choices.png", fullPage: true});
    await page.reload(); await card.waitFor();
    assert.equal(await page.evaluate(() => window._permissionRequests), 0, "New visit shows card without native prompt");
    await page.evaluate(() => {Notification.permission = "denied";});
    await card.getByRole("button", {name: "Enable notifications"}).click();
    await card.getByText("Open your phone’s Settings", {exact: false}).waitFor();
    assert.equal(await page.evaluate(() => window._permissionRequests), 0, "Denied permissions require settings, not repeated requests");
    await page.evaluate(() => {Notification.permission = "default";});
    failSubscription = true;
    await card.getByRole("button", {name: "Show me how"}).click();
    await card.getByText("We couldn’t finish enabling notifications.", {exact: false}).waitFor();
    assert.equal(await page.evaluate(() => window._permissionRequests), 1);
    assert.equal(await page.evaluate(() => localStorage.getItem("gokul-push-device:test-alert-subject")), null);
    failSubscription = false;
    await card.getByRole("button", {name: "Enable notifications"}).click();
    await card.waitFor({state: "detached"});
    assert.equal(await page.evaluate(() => window._permissionRequests), 1, "Retry does not repeat a granted permission request");
    assert.equal(marketing, false); assert.equal(consentWrites, 2, "Push registration never changes marketing consent");
    assert.equal(subscriptions, 2); assert.equal(revoked, false);
    await page.reload();
    await page.waitForTimeout(1800);
    assert.equal(await card.count(), 0, "A confirmed enabled device stays hidden on the next launch");
    assert.equal(await page.evaluate(() => window._permissionRequests), 0);
    assert.equal(subscriptions, 2, "Returning only reads registration status");
    registrationActive = false;
    await page.reload(); await card.waitFor();
    assert.equal(await page.evaluate(() => window._permissionRequests), 0, "Expired sessions require an explicit registration action");
    await page.evaluate(() => {localStorage.removeItem("gokul-push-device:test-alert-subject");});
    authenticated = false;
    await page.reload(); await card.waitFor();
    await card.getByRole("link", {name: "Sign in to enable notifications"}).waitFor();
    assert.equal(await card.getByRole("button", {name: "Enable notifications"}).count(), 0);
    enabled = false;
    await page.reload(); await page.waitForTimeout(1600);
    assert.equal(await card.count(), 0, "Feature OFF never prompts");
    console.log("PASS: PWA visit opt-in, skip/navigation/relaunch, denial guidance, retry, consent isolation, radios and 320/390/1280px layouts");
} finally {await browser.close();}
