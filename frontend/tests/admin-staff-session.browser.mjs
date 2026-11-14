import assert from "node:assert/strict";
import {createRequire} from "node:module";

const {chromium} = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const browser = await chromium.launch({headless: true});
const base = process.env.BROWSER_BASE ?? "http://127.0.0.1:3311";
const context = await browser.newContext({serviceWorkers: "block"});
const page = await context.newPage();
const owner = {id: 7, username: "owner", fullName: "Test Owner", roleName: "OWNER_ADMIN", branchIds: [], phone: null, active: true};
const manager = {id: 8, username: "manager", fullName: "Test Manager", roleName: "MANAGER", branchIds: [1], phone: null, active: true};
const profile = {...owner, staffId: owner.id, permissions: ["STAFF_MANAGE"]};
let sessionStatus = 503;
const csrf = "initial-token";
let createAttempts = 0;
let loseCreateResponse = false;
const logoutStatus = 503;
let resetAttempts = 0;
let staffReads = 0;
let created = false;
let reset = false;

await context.route("**/api/**", async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    const headers = {
        "Access-Control-Allow-Origin": base,
        "Access-Control-Allow-Credentials": "true",
        "Access-Control-Allow-Methods": "GET, POST, PATCH, OPTIONS",
        "Access-Control-Allow-Headers": "content-type, x-staff-csrf",
        "Access-Control-Expose-Headers": "X-Staff-CSRF",
        "X-Staff-CSRF": csrf,
    };
    const respond = (status, json) => route.fulfill({status, headers, ...(json ? {json} : {})});
    if (method === "OPTIONS") return respond(204);
    if (path === "/api/admin/auth/me") return respond(sessionStatus, sessionStatus === 200 ? profile : {});
    if (path === "/api/admin/auth/logout") return respond(logoutStatus, logoutStatus === 204 ? undefined : {});
    if (path === "/api/admin/auth/login") throw new Error("Admin saves should not trigger a second sign-in.");
    if (path === "/api/admin/staff/options") return respond(200, {
        roles: [{name: "MANAGER", permissions: []}, {name: "OWNER_ADMIN", permissions: []}],
        branches: [{id: 1, code: "MAIN", name: "Main branch", active: true}],
    });
    if (path === "/api/admin/staff" && method === "GET") {
        staffReads++;
        return respond(200, created ? [owner, manager] : [owner]);
    }
    const isCreate = path === "/api/admin/staff" && method === "POST";
    const isReset = path === "/api/admin/staff/7/password" && method === "PATCH";
    if (isCreate || isReset) {
        if (isCreate) createAttempts++;
        if (isReset) resetAttempts++;
        assert.equal(request.headers()["x-staff-csrf"], csrf);
        if (isCreate) {
            assert.equal(request.postDataJSON().roleName, "MANAGER");
            assert.deepEqual(request.postDataJSON().branchIds, [1]);
            created = true;
            if (loseCreateResponse) return route.abort("failed");
            return respond(201, manager);
        }
        assert.deepEqual(request.postDataJSON(), {newPassword: "updated-test-password"});
        reset = true;
        return respond(204);
    }
    return respond(200, []);
});

function staffInput(label) {
    return page.locator("label").filter({hasText: new RegExp(`^${label}$`)}).locator("..").locator("input");
}

try {
    await page.goto(`${base}/admin/staff`);
    await page.getByText("Could not verify your admin session. Check your connection.").waitFor();
    assert.match(page.url(), /\/admin\/staff$/);
    sessionStatus = 200;
    await page.getByRole("button", {name: "Retry", exact: true}).click();
    await page.getByRole("button", {name: "Add Staff", exact: true}).click();
    await staffInput("Username").fill("manager");
    await staffInput("Full Name").fill("Test Manager");
    await staffInput("Temporary Password").fill("temporary-test-password");
    await staffInput("Confirm Password").fill("temporary-test-password");
    await page.getByRole("checkbox", {name: /Main branch/}).check();
    await page.getByRole("checkbox", {name: "Enable Payroll"}).uncheck();
    sessionStatus = 503;
    await page.evaluate(() => window.dispatchEvent(new Event("focus")));
    await page.getByRole("button", {name: "Retry connection"}).waitFor();
    assert.equal(await staffInput("Full Name").inputValue(), "Test Manager");
    sessionStatus = 200;
    loseCreateResponse = true;
    await page.getByRole("button", {name: "Create Staff", exact: true}).click();
    await page.getByRole("alert").filter({hasText: "The server did not confirm this save."}).waitFor();
    assert.equal(staffReads, 1);
    await page.getByRole("button", {name: "Refresh staff list", exact: true}).click();
    await page.getByRole("button", {name: /Test Manager/}).waitFor();
    assert.equal(await staffInput("Full Name").inputValue(), "Test Manager");
    assert.equal(await staffInput("Temporary Password").inputValue(), "temporary-test-password");
    assert.equal(await page.getByRole("checkbox", {name: /Main branch/}).isChecked(), true);
    assert.equal(staffReads, 2);
    assert.equal(createAttempts, 1, "checking the list does not replay the mutation");
    assert.equal(created, true);

    const logout = page.getByRole("button", {name: "Logout", exact: true});
    await logout.click();
    await page.getByRole("alert").filter({hasText: "Sign-out could not be confirmed."}).waitFor();
    assert.match(page.url(), /\/admin\/staff$/);
    assert.equal(await staffInput("Full Name").inputValue(), "Test Manager");

    await page.getByRole("button", {name: /Test Owner/}).click();
    await page.getByRole("button", {name: "Reset Password", exact: true}).click();
    await page.getByPlaceholder("New password (minimum 8 characters)", {exact: true}).fill("updated-test-password");
    await page.getByPlaceholder("Confirm new password", {exact: true}).fill("updated-test-password");
    await page.getByRole("button", {name: "Confirm Reset", exact: true}).click();
    await page.getByText("Your password was reset. Sign in again with your new password.", {exact: false}).waitFor();
    assert.equal(reset, true);
    assert.equal(resetAttempts, 1);

    sessionStatus = 401;
    await page.evaluate(() => window.dispatchEvent(new Event("focus")));
    await page.waitForURL("**/admin/login");
} finally {
    await context.close();
    await browser.close();
}
