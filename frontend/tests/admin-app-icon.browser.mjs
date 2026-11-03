import assert from "node:assert/strict";
import {createRequire} from "node:module";

const {chromium} = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const base = process.env.BROWSER_BASE ?? "http://127.0.0.1:3311";
const browser = await chromium.launch({headless: true});
const revision = "9afbc2ee-a870-4bc0-8a70-f044d99ccf7a";
try {
    const context = await browser.newContext({
        viewport: {width: 390, height: 844}, hasTouch: true, serviceWorkers: "block"
    });
    const page = await context.newPage();
    const previousRevision = "32435d1d-cc39-4d9c-a00b-a75ece40eb89";
    let published = previousRevision;
    let reads = 0;
    let failNextPublish = true;
    let receivedPng = false;
    let releaseInitialRead;
    const initialRead = new Promise(resolve => {releaseInitialRead = resolve;});
    await context.route("**/api/**", async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        const headers = {
            "Access-Control-Allow-Origin": base, "Access-Control-Allow-Credentials": "true",
            "Access-Control-Allow-Headers": "content-type,x-staff-csrf",
            "Access-Control-Allow-Methods": "GET,POST,DELETE,OPTIONS",
            "Access-Control-Expose-Headers": "X-Staff-CSRF", "X-Staff-CSRF": "test-csrf"
        };
        if (request.method() === "OPTIONS") return route.fulfill({status: 204, headers});
        if (path === "/api/admin/auth/me") return route.fulfill({headers, json: {
            staffId: 1, username: "owner", fullName: "Test owner", roleName: "OWNER_ADMIN",
            branchIds: [], permissions: []
        }});
        if (path === "/api/storefront/app-icon") {
            const snapshot = published;
            if (++reads === 1) await initialRead;
            await route.fulfill({headers, json: {revision: snapshot}}).catch(() => {});
            return;
        }
        if (path === "/api/admin/app-icon" && request.method() === "POST") {
            const bytes = request.postDataBuffer();
            const png = bytes.indexOf(Buffer.from("89504e470d0a1a0a", "hex"));
            receivedPng = png >= 0 && bytes.readUInt32BE(png + 16) === 512 && bytes.readUInt32BE(png + 20) === 512;
            assert.equal(request.headers()["x-staff-csrf"], "test-csrf");
            if (failNextPublish) {
                failNextPublish = false;
                return route.fulfill({status: 503, headers, json: {message: "Could not publish the icon. Try again."}});
            }
            published = revision;
            return route.fulfill({headers, json: {revision}});
        }
        if (path === "/api/admin/app-icon" && request.method() === "DELETE") {
            published = null;
            return route.fulfill({status: 204, headers});
        }
        return route.fulfill({headers, json: []});
    });
    const initialRequest = page.waitForRequest("**/api/storefront/app-icon");
    await page.goto(base + "/admin/app-icon");
    await initialRequest;
    const oversized = Buffer.alloc(33);
    Buffer.from("89504e470d0a1a0a0000000d49484452", "hex").copy(oversized);
    oversized.writeUInt32BE(8192, 16);
    oversized.writeUInt32BE(8192, 20);
    await page.getByLabel("Upload artwork").setInputFiles({name: "oversized.png", mimeType: "image/png", buffer: oversized});
    await page.getByRole("alert").filter({hasText: "4096 pixels"}).waitFor();
    await page.getByLabel("Upload artwork").setInputFiles("public/icons/icon-512.png");
    const canvas = page.getByLabel("Icon crop preview");
    await canvas.waitFor();
    await page.waitForFunction(() => document.querySelector("canvas")?.getContext("2d").getImageData(10, 10, 1, 1).data[3] === 255);
    const original = await canvas.evaluate(node => node.toDataURL());
    const adaptive = page.getByRole("img", {name: "Android adaptive draft preview"});
    assert.deepEqual(await adaptive.evaluate(node => Array.from(node.getContext("2d").getImageData(50, 50, 1, 1).data)), [255, 255, 255, 255]);
    const originalAdaptive = await adaptive.evaluate(node => node.toDataURL());
    assert.equal(await page.getByRole("slider").count(), 0);
    await canvas.scrollIntoViewIfNeeded();
    const bounds = await canvas.boundingBox();
    const x = bounds.x + bounds.width / 2;
    const y = bounds.y + bounds.height / 2;
    const cdp = await context.newCDPSession(page);
    const touch = (type, spread) => cdp.send("Input.dispatchTouchEvent", {type, touchPoints: spread
        ? [{id: 1, x: x - spread, y}, {id: 2, x: x + spread, y}] : []});
    await touch("touchStart", 20);
    await touch("touchMove", 42);
    await touch("touchEnd", 0);
    assert.notEqual(await canvas.evaluate(node => node.toDataURL()), original, "pinch changes the exported preview");
    assert.notEqual(await adaptive.evaluate(node => node.toDataURL()), originalAdaptive, "Android preview follows the gesture");
    const canceled = page.waitForEvent("requestfailed", {predicate: request => request.url().endsWith("/api/storefront/app-icon")});
    await page.getByRole("button", {name: "Publish app icon"}).click();
    await page.getByRole("alert").filter({hasText: "Could not publish the icon."}).waitFor();
    releaseInitialRead();
    await canceled;
    await page.waitForFunction(revision => document.querySelector('img[alt="Current Gokul home-screen icon"]')?.getAttribute("src")?.includes(revision), previousRevision);
    await page.getByRole("button", {name: "Restore default icon"}).waitFor();
    await canvas.waitFor();
    await page.getByRole("button", {name: "Publish app icon"}).click();
    await page.getByText("App icon published.", {exact: false}).waitFor();
    assert.match(await page.getByAltText("Published Android adaptive icon").getAttribute("src"), /pwa-icon\/maskable/);
    assert.ok(receivedPng, "upload contains the cropped 512px PNG");
    assert.match(await page.getByAltText("Current Gokul home-screen icon").getAttribute("src"), new RegExp(revision));
    await page.getByRole("button", {name: "Restore default icon"}).click();
    await page.getByRole("button", {name: "Restore default", exact: true}).click();
    await page.getByText("Default icon restored.").waitFor();
    assert.equal(published, null);
    console.log("Owner icon upload, pinch crop, publication and default restoration passed");
} finally {
    await browser.close();
}
