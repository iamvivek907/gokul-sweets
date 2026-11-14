const assert = require("node:assert/strict");
const {readFileSync} = require("node:fs");
const test = require("node:test");
const vm = require("node:vm");
const ts = require("typescript");

function setup() {
    const window = new EventTarget();
    const requests = [];
    const exports = {};
    const source = ts.transpileModule(readFileSync("services/adminApi.ts", "utf8"), {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
    }).outputText;
    vm.runInNewContext(source, {
        exports, window, Event, AbortSignal, Headers,
        require: () => ({ADMIN_API_BASE_URL: "https://api.test"}),
        fetch: (url, init) => new Promise((resolve, reject) => requests.push({url, init, resolve, reject}))
    });
    return {api: exports, window, requests};
}

const profile = {staffId: 1, username: "owner"};
function response(status, token, body) {
    return new Response(body ? JSON.stringify(body) : null, {
        status,
        headers: {"Content-Type": "application/json", ...(token ? {"X-Staff-CSRF": token} : {})}
    });
}

async function verify({api, requests}, token = "csrf") {
    const checking = api.fetchAdminProfile();
    requests.at(-1).resolve(response(200, token, profile));
    assert.deepEqual(await checking, profile);
}

test("network/server and permission failures do not erase valid write credentials", async () => {
    const f = setup();
    await verify(f);
    for (const status of [503, 403]) {
        const check = f.api.fetchAdminProfile();
        f.requests.at(-1).resolve(response(status));
        await assert.rejects(check, /Unable to verify/);
    }
    const write = f.api.adminFetch("/api/admin/staff", "staff-session", {method: "POST"});
    assert.equal(f.requests.at(-1).init.headers.get("X-Staff-CSRF"), "csrf");
    f.requests.at(-1).resolve(response(201));
    await write;
});

test("only a confirmed 401 from the session endpoint clears write credentials", async () => {
    const f = setup();
    await verify(f);
    let checks = 0;
    f.window.addEventListener(f.api.staffSessionCheckEvent, () => checks++);
    const write = f.api.adminFetch("/api/admin/staff", "staff-session", {method: "POST"});
    f.requests.at(-1).resolve(response(401));
    assert.equal((await write).status, 401);
    assert.equal(checks, 1);
    const check = f.api.fetchAdminProfile();
    f.requests.at(-1).resolve(response(401));
    assert.equal(await check, null);
    await assert.rejects(f.api.adminFetch("/api/admin/staff", "staff-session", {method: "POST"}), /Unable to verify/);
    assert.equal(checks, 2);
});

test("fresh-sign-in challenge is distinct from permissions and the write is never replayed", async () => {
    const f = setup();
    await verify(f);
    let prompts = 0;
    f.window.addEventListener(f.api.staffReauthenticationEvent, () => prompts++);
    const mutate = () => f.api.adminFetch("/api/admin/staff/2/password", "staff-session", {method: "PATCH"});
    let write = mutate();
    f.requests.at(-1).resolve(response(403, null, {message: "Permission denied"}));
    assert.equal((await write).status, 403);
    assert.equal(prompts, 0);
    write = mutate();
    f.requests.at(-1).resolve(response(403, null, {code: "STAFF_REAUTH_REQUIRED"}));
    await assert.rejects(write, /Confirm your sign-in/);
    assert.equal(prompts, 1);
    assert.equal(f.requests.length, 3);
});

test("late responses from the old session cannot invalidate or replace fresh sign-in", async () => {
    const f = setup();
    await verify(f);
    let checks = 0;
    f.window.addEventListener(f.api.staffSessionCheckEvent, () => checks++);
    const staleCheck = f.api.fetchAdminProfile();
    const staleWrite = f.api.adminFetch("/api/admin/staff", "staff-session");
    const login = f.api.authenticateAdmin("owner", "test-password", "123456");
    f.requests[3].resolve(response(200, "new-csrf", {profile}));
    await login;
    f.requests[1].resolve(response(401));
    await assert.rejects(staleCheck, /A newer staff session/);
    f.requests[2].resolve(response(401));
    await staleWrite;
    assert.equal(checks, 0);
    const retry = f.api.adminFetch("/api/admin/staff", "staff-session", {method: "POST"});
    assert.equal(f.requests.at(-1).init.headers.get("X-Staff-CSRF"), "new-csrf");
    f.requests.at(-1).resolve(response(201));
    await retry;
});

test("failed sign-out preserves a verified session and can be retried", async () => {
    const f = setup();
    await verify(f);
    const failed = f.api.logoutAdmin();
    f.requests.at(-1).resolve(response(503));
    await new Promise(setImmediate);
    assert.match(f.requests.at(-1).url, /auth\/me$/);
    f.requests.at(-1).resolve(response(200, "csrf", profile));
    await assert.rejects(failed, /Sign-out could not be confirmed/);
    const retry = f.api.logoutAdmin();
    assert.equal(f.requests.at(-1).init.headers.get("X-Staff-CSRF"), "csrf");
    f.requests.at(-1).resolve(response(204));
    await retry;
});

test("an interrupted sign-out succeeds if the server confirms the session ended", async () => {
    const f = setup();
    await verify(f);
    const logout = f.api.logoutAdmin();
    f.requests.at(-1).reject(new TypeError("connection interrupted"));
    await new Promise(setImmediate);
    f.requests.at(-1).resolve(response(401));
    await logout;
    await assert.rejects(f.api.adminFetch("/api/admin/staff", "staff-session", {method: "POST"}), /Unable to verify/);
});

test("a CSRF rejection requests fresh credentials without replaying the write", async () => {
    const f = setup();
    await verify(f);
    let recoveries = 0;
    f.window.addEventListener(f.api.staffCsrfRejectedEvent, () => recoveries++);
    const write = f.api.adminFetch("/api/admin/staff", "staff-session", {method: "POST"});
    f.requests.at(-1).resolve(response(403, null, {code: "STAFF_CSRF_INVALID"}));
    await assert.rejects(write, /Refreshing your session/);
    assert.equal(recoveries, 1);
    assert.equal(f.requests.length, 2);
});

test("a verification from before a cross-tab session change cannot overwrite the new token", async () => {
    const f = setup();
    await verify(f);
    const stale = f.api.fetchAdminProfile();
    const staleRequest = f.requests.at(-1);
    f.api.invalidateAdminSessionCheck();
    await verify(f, "fresh-csrf");
    staleRequest.resolve(response(200, "old-csrf", profile));
    await assert.rejects(stale, /A newer staff session/);
    const write = f.api.adminFetch("/api/admin/staff", "staff-session", {method: "POST"});
    assert.equal(f.requests.at(-1).init.headers.get("X-Staff-CSRF"), "fresh-csrf");
    f.requests.at(-1).resolve(response(201));
    await write;
});
