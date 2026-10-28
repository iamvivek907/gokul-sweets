import assert from "node:assert/strict";
import { createRequire } from "node:module";
const { chromium } = createRequire(import.meta.url)(
  process.env.PLAYWRIGHT_MODULE ?? "playwright",
);
const base = process.env.BROWSER_BASE ?? "http://127.0.0.1:3311",
  browser = await chromium.launch({ headless: true });
try {
  for (const width of [320, 390, 1280]) {
    const context = await browser.newContext({
        viewport: { width, height: 900 },
        serviceWorkers: "block",
      }),
      page = await context.newPage();
    page.setDefaultTimeout(15000);
    let conflict = false,
      forbidden = false,
      performanceFailed = false,
      performanceCalls = 0;
    let facts = {
      productId: 1,
      servingInfo: null,
      ingredients: null,
      allergens: null,
      spiceLevel: null,
      photoFocusX: 50,
      photoFocusY: 50,
      chefPick: false,
      version: 0,
    };
    const errors = [];
    page.on("pageerror", (e) => errors.push(e.message));
    const item = {
      branchProductId: 1,
      productId: 1,
      code: "TEST-1",
      name: "Kaju Katli",
      description: "Kaju Katli. Served per piece.",
      categoryId: 1,
      categoryName: "Sweets",
      basePrice: 100,
      priceOverride: null,
      effectivePrice: 100,
      available: true,
      active: true,
      saleMode: "UNIT",
      minimumWeightGrams: null,
      weightStepGrams: null,
      taxCategoryId: 1,
      imageUrl: "/arrival-mithai.webp",
      productVersion: 0,
      branchVersion: 0,
      allocationVersion: 0,
      policyVersion: 0,
      policy: {
        controlMode: "DAILY_PRODUCTION",
        inventoryUnit: "PIECE",
        readyStockRequired: false,
      },
      allocation: null,
    };
    await context.route("**/api/**", async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = url.pathname,
        headers = {
          "Access-Control-Allow-Origin": base,
          "Access-Control-Allow-Credentials": "true",
          "Access-Control-Allow-Headers": "content-type,x-staff-csrf",
          "Access-Control-Allow-Methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS",
          "Access-Control-Expose-Headers": "X-Staff-CSRF",
          "X-Staff-CSRF": "test-csrf",
        };
      if (req.method() === "OPTIONS")
        return route.fulfill({ status: 204, headers });
      let json = [];
      if (path === "/api/admin/auth/me")
        json = {
          staffId: 77,
          username: "manager",
          fullName: "Test Manager",
          roleName: "OWNER_ADMIN",
          branchIds: [1],
          permissions: ["MENU_MANAGE", "INVENTORY_VIEW", "INVENTORY_MANAGE"],
        };
      else if (path === "/api/branches")
        json = [{ id: 1, name: "Test branch", active: true }];
      else if (path === "/api/admin/branches/1/menu/workspace")
        json = {
          content: [item],
          totalElements: 1,
          page: 0,
          totalPages: 1,
          categories: [{ id: 1, name: "Sweets" }],
          branchCategories: [{ id: 1, name: "Sweets" }],
          taxes: [{ id: 1, name: "Tax" }],
        };
      else if (path.endsWith("/workspace/groups"))
        json = { version: 0, groups: [], total: 0, page: 0, totalPages: 0 };
      else if (path.endsWith("/groups/product/1"))
        json = { version: 0, group: null };
      else if (path === "/api/admin/branches/1/menu/products/1/details") {
        if (req.method() === "PUT") {
          const input = req.postDataJSON();
          assert.equal(input.version, facts.version);
          assert.equal(input.productId, undefined);
          assert.equal(req.headers()["x-staff-csrf"], "test-csrf");
          if (conflict)
            return route.fulfill({
              status: 409,
              headers,
              json: { message: "Changed" },
            });
          if (forbidden)
            return route.fulfill({
              status: 403,
              headers,
              json: { message: "Denied" },
            });
          facts = { ...facts, ...input, version: facts.version + 1 };
        }
        json = facts;
      } else if (path === "/api/admin/storefront/performance") {
        performanceCalls++;
        if (performanceFailed)
          return route.fulfill({
            status: 503,
            headers,
            json: { message: "Unavailable" },
          });
        json = {
          startedAt: new Date().toISOString(),
          metrics:
            performanceCalls === 1
              ? []
              : [
                  {
                    page: "menu",
                    name: "LCP",
                    samples: 8,
                    unit: "ms",
                    p75UpperBound: 2500,
                    p95UpperBound: 4000,
                  },
                ],
        };
      } else if (path === "/api/storefront/features") json = {};
      return route.fulfill({ headers, json }).catch(() => {});
    });
    await page.goto(`${base}/admin/menu/workspace`);
    await page
      .getByRole("heading", { name: "Kaju Katli", exact: true })
      .waitFor();
    assert.equal(performanceCalls, 0);
    const report = page
      .locator(".admin-menu-quality")
      .filter({
        has: page.getByText("Storefront experience · sampled performance", {
          exact: true,
        }),
      });
    await report.locator("summary").click();
    await report
      .getByRole("button", { name: "Refresh performance", exact: true })
      .click();
    await report
      .getByText("No timing samples yet.", { exact: false })
      .waitFor();
    performanceFailed = true;
    await report
      .getByRole("button", { name: "Refresh performance", exact: true })
      .click();
    await report.getByRole("alert").waitFor();
    performanceFailed = false;
    await report
      .getByRole("button", { name: "Refresh performance", exact: true })
      .click();
    await report
      .getByRole("cell", { name: "8 · few samples", exact: true })
      .waitFor();
    await report.locator("summary").click();
    const quality = page
      .locator(".admin-menu-quality")
      .filter({
        has: page.getByText("Menu content checklist · this page", {
          exact: true,
        }),
      });
    await quality.locator("summary").click();
    await quality
      .getByRole("button", { name: "Edit Kaju Katli", exact: true })
      .click();
    const dialog = page.getByRole("dialog"),
      editor = dialog.locator(".admin-dish-details");
    await editor.locator("summary").click();
    const save = editor.getByRole("button", {
      name: "Save dish information",
      exact: true,
    });
    await page.waitForFunction(
      () =>
        !Array.from(
          document.querySelectorAll(".admin-dish-details button"),
        ).find((n) => n.textContent === "Save dish information")?.disabled,
    );
    await editor
      .getByLabel("Serving information", { exact: true })
      .fill("4 pieces");
    await editor
      .getByLabel("Ingredients", { exact: true })
      .fill("Milk and cashews");
    await editor
      .getByLabel("Allergen information", { exact: true })
      .fill("Contains nuts");
    await editor
      .getByLabel("Spice level", { exact: true })
      .selectOption("MILD");
    await editor
      .getByLabel("Feature as an owner-selected chef’s pick", { exact: true })
      .check();
    const ranges = editor.locator("input[type=range]");
    await ranges.nth(0).fill("25");
    await ranges.nth(1).fill("75");
    assert.equal(
      await editor
        .getByAltText("Phone crop", { exact: true })
        .evaluate((n) => n.style.objectPosition),
      "25% 75%",
    );
    assert.equal(
      await editor
        .getByAltText("Desktop crop", { exact: true })
        .evaluate((n) => n.style.objectPosition),
      "25% 75%",
    );
    conflict = true;
    await save.click();
    await editor
      .getByRole("status")
      .filter({ hasText: "Dish details changed" })
      .waitFor();
    assert.equal(
      await editor
        .getByLabel("Serving information", { exact: true })
        .inputValue(),
      "4 pieces",
    );
    conflict = false;
    forbidden = true;
    await save.click();
    await editor
      .getByRole("status")
      .filter({ hasText: "permission" })
      .waitFor();
    forbidden = false;
    await save.click();
    await editor
      .getByText("Dish information saved.", { exact: true })
      .waitFor();
    assert.equal(facts.version, 1);
    assert.equal(facts.chefPick, true);
    assert.equal(facts.photoFocusY, 75);
    await editor
      .getByLabel("Serving information", { exact: true })
      .fill("Discard this draft");
    await editor
      .getByRole("button", { name: "Reload saved details", exact: true })
      .click();
    await page.waitForFunction(
      () =>
        document.querySelector(".admin-dish-details input")?.value ===
        "4 pieces",
    );
    await dialog.getByRole("button", { name: "Cancel", exact: true }).click();
    await dialog.waitFor({ state: "hidden" });
    assert.deepEqual(errors, []);
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
      true,
    );
    await context.close();
    console.log(`Premium admin controls ${width}px passed`);
  }
} finally {
  await browser.close();
}
