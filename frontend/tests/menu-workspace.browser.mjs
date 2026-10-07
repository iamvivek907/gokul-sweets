import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { mkdirSync } from "node:fs";
const { chromium } = createRequire(import.meta.url)(
  process.env.PLAYWRIGHT_MODULE ?? "playwright",
);
const browser = await chromium.launch({
  headless: true,
  executablePath: process.env.CHROMIUM_PATH,
});
const base = process.env.BROWSER_BASE ?? "http://127.0.0.1:3311";
const shots =
  process.env.WORKSPACE_SCREENSHOTS ?? "/tmp/gokul-workspace-screenshots";
mkdirSync(shots, { recursive: true });
try {
  for (const width of [320, 390, 1280]) {
    const context = await browser.newContext({
      viewport: { width, height: 900 },
      serviceWorkers: "block",
    });
    const page = await context.newPage();
    page.setDefaultTimeout(15000);
    let imageCalls = 0;
    let deleteCalls = 0, deleted = false;
    let paginationDeletion = false, deletedLast = false;
    const errors = [];
    page.on("pageerror", (e) => errors.push(e.message));
    let releaseBranch, releaseCatalogue;
    let failAppearanceReload = false;
    let delayCatalogue = false,
      appearanceConflicts = 0;
    let delayBranch = false;
    let conflict = false,
      priceWrites = 0,
      creates = 0,
      bulkFailures = true,
      groups = { version: 0, groups: [], total: 0, page: 0, totalPages: 0 };
    let appearance = {
      version: 0,
      draft: { banners: [], categories: [] },
      live: { banners: [], categories: [] },
      publishedAt: null,
    };
    const items = [1, 2, 3].map((id) => ({
      branchProductId: id,
      productId: id,
      code: `SKU-${id}`,
      name: ["Kaju Katli", "Samosa", "Rasmalai"][id - 1],
      description: "Freshly prepared",
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
      imageUrl: null,
      productVersion: 0,
      branchVersion: 0,
      allocationVersion: 0,
      policyVersion: 0,
      policy: {
        controlMode: "DAILY_PRODUCTION",
        inventoryUnit: "PIECE",
        readyStockRequired: false,
      },
      allocation: {
        inventoryUnit: "PIECE",
        approvedQuantity: 10,
        readyQuantity: 10,
        safetyBufferQuantity: 0,
        heldQuantity: 2,
        committedQuantity: 1,
        availableQuantity: 7,
        unavailableReason: null,
      },
    }));
    await context.route("**/api/**", async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = url.pathname;
      if (
        delayBranch &&
        path === "/api/admin/branches/2/menu/workspace" &&
        req.method() === "GET"
      ) {
        await new Promise((resolve) => {
          releaseBranch = resolve;
        });
      }
      if (
        delayCatalogue &&
        path === "/api/admin/branches/1/menu/workspace" &&
        req.method() === "GET"
      )
        await new Promise((resolve) => {
          releaseCatalogue = resolve;
        });
      const headers = {
        "Access-Control-Allow-Origin": base,
        "Access-Control-Allow-Credentials": "true",
        "Access-Control-Allow-Headers": "content-type,x-staff-csrf",
        "Access-Control-Allow-Methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS",
        "Access-Control-Expose-Headers": "X-Staff-CSRF",
        "X-Staff-CSRF": "test-csrf",
      };
      if (req.method() === "OPTIONS")
        return route.fulfill({ status: 204, headers });
      if (path.endsWith("/image") && req.method() === "POST") {
        imageCalls++;
        return route.fulfill({ status: 204, headers });
      }
      if (/\/workspace\/1$/.test(path) && req.method() === "DELETE") {
        deleteCalls++;
        assert.equal(Number(url.searchParams.get("version")), items[0].branchVersion);
        if (deleteCalls === 1) return route.fulfill({status: 409, headers, json: {message: "This item has inventory history. Set it unavailable instead."}});
        deleted = true;
        return route.fulfill({status: 204, headers});
      }
      if (paginationDeletion && /\/workspace\/3$/.test(path) && req.method() === "DELETE") {
        assert.equal(Number(url.searchParams.get("version")), items[2].branchVersion);
        deletedLast = true;
        return route.fulfill({status: 204, headers});
      }
      let json = {};
      if (path === "/api/admin/auth/me")
        json = {
          staffId: 77,
          username: "manager",
          fullName: "Menu Manager",
          roleName: "OWNER_ADMIN",
          branchIds: [1, 2],
          permissions: ["MENU_MANAGE", "INVENTORY_VIEW", "INVENTORY_MANAGE"],
        };
      else if (path === "/api/branches")
        json = [
          { id: 1, name: "Tamkuhi Road", active: true },
          { id: 2, name: "Seorahi", active: true },
        ];
      else if (path.endsWith("/workspace/groups")) {
        if (req.method() === "PUT") {
          const b = req.postDataJSON();
          assert.equal(b.version, groups.version);
          groups = {
            ...groups,
            version: groups.version + 1,
            groups: [b.group],
            total: 1,
            totalPages: 1,
          };
          json = { version: groups.version };
        } else json = groups;
      } else if (path.includes("/groups/product/"))
        json = { version: groups.version, group: null };
      else if (path.endsWith("/appearance")) {
        if (req.method() === "GET" && failAppearanceReload) {
          failAppearanceReload = false;
          return route.fulfill({
            status: 503,
            headers,
            json: { message: "Temporary reload failure" },
          });
        }
        if (req.method() === "PUT") {
          const b = req.postDataJSON();
          if (b.version !== appearance.version) {
            appearanceConflicts++;
            return route.fulfill({
              status: 409,
              headers,
              json: { message: "Appearance changed; draft retained." },
            });
          }
          assert.equal(b.version, appearance.version);
          appearance = {
            ...appearance,
            version: appearance.version + 1,
            draft: b.config,
            ...(b.publish
              ? { live: b.config, publishedAt: new Date().toISOString() }
              : {}),
          };
        }
        json = appearance;
      } else if (path.endsWith("/branch")) {
        const b = req.postDataJSON(),
          id = Number(path.split("/").at(-2));
        if (conflict || (b.available !== undefined && id === 2 && bulkFailures))
          return route.fulfill({
            status: 409,
            headers,
            json: { message: "Concurrent change; draft retained." },
          });
        assert.equal(b.version, items[id - 1].branchVersion);
        if (b.priceOverride) {
          items[id - 1].priceOverride = b.priceOverride;
          items[id - 1].effectivePrice = b.priceOverride;
          priceWrites++;
        }
        if (b.available !== undefined) items[id - 1].available = b.available;
        items[id - 1].branchVersion++;
        return route.fulfill({ status: 204, headers });
      } else if (path.includes("/stock/")) {
        const b = req.postDataJSON();
        assert.equal(b.reason, "Fresh production");
        assert.equal(b.version, 0);
        return route.fulfill({ status: 204, headers });
      } else if (path.endsWith("/workspace")) {
        if (req.method() === "POST") {
          creates++;
          assert.equal(req.postDataJSON().details.name, "New sweet");
          assert.deepEqual(req.postDataJSON().branchIds, [1]);
          json = { productId: 99 };
        } else {
          if (paginationDeletion) return route.fulfill({headers, json: {
            content: url.searchParams.get("page") === "1" ? (deletedLast ? [] : [items[2]]) : [items[1]],
            totalElements: deletedLast ? 25 : 26,
            page: Number(url.searchParams.get("page")), totalPages: deletedLast ? 1 : 2,
            categories: [{id:1,name:"Sweets"}], branchCategories: [{id:1,name:"Sweets"}], taxes: [],
          }});
          const search = url.searchParams.get("search")?.toLowerCase() ?? "";
          const filtered = items.filter((i) =>
            (!deleted || i.productId !== 1) && `${i.name} ${i.code}`.toLowerCase().includes(search),
          );
          json = {
            content:
              url.searchParams.get("filter") === "COUNT_SKU" &&
              url.searchParams.get("page") === "1"
                ? [
                    {
                      ...items[0],
                      productId: 4,
                      name: "Extra SKU",
                      code: "SKU-4",
                    },
                  ]
                : filtered,
            totalElements: filtered.length,
            page: 0,
            totalPages: url.searchParams.get("filter") === "COUNT_SKU" ? 2 : 1,
            categories: [
              { id: 1, name: "Sweets" },
              { id: 2, name: "Other branch category" },
            ],
            branchCategories: [{ id: 1, name: "Sweets" }],
            taxes: [{ id: 1, name: "Sweets tax" }],
          };
        }
      } else if (path === "/api/storefront/features") json = {};
      else json = [];
      await route.fulfill({ headers, json });
    });
    await page.goto(`${base}/admin/menu/workspace`);
    await page
      .getByRole("heading", { name: "Kaju Katli", exact: true })
      .waitFor();
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
      true,
    );
    await page.screenshot({
      path: `${shots}/overview-${width}.png`,
      fullPage: true,
    });
    const first = page.locator("article").filter({
      has: page.getByRole("heading", { name: "Kaju Katli", exact: true }),
    });
    await page.locator('input[type="date"]').fill("2030-10-07");
    delayBranch = true;
    await page.getByLabel("Selected branch").selectOption("2");
    assert.equal(
      await page
        .getByRole("button", { name: "+ Add product", exact: true })
        .isDisabled(),
      true,
    );
    assert.equal(
      await page
        .getByRole("heading", { name: "Kaju Katli", exact: true })
        .count(),
      0,
    );
    await page.waitForFunction(
      () =>
        document.querySelector('[aria-label="Selected branch"]').value === "2",
    );
    for (let i = 0; i < 50 && !releaseBranch; i++)
      await page.waitForTimeout(20);
    assert.ok(releaseBranch);
    delayBranch = false;
    releaseBranch();
    await page
      .getByRole("button", { name: "+ Add product", exact: true })
      .waitFor();
    await page.getByLabel("Selected branch").selectOption("1");
    await first.getByRole("button", { name: "Price", exact: true }).click();
    let dialog = page.getByRole("dialog");
    await dialog.waitFor();
    await dialog.getByLabel("Branch override").fill("125");
    await page.reload();
    dialog = page.getByRole("dialog");
    await dialog.waitFor();
    assert.equal(
      await dialog.getByLabel("Branch override").inputValue(),
      "125",
    );
    assert.equal(
      await page.locator('input[type="date"]').inputValue(),
      "2030-10-07",
    );
    conflict = true;
    await dialog
      .getByRole("button", { name: "Save changes", exact: true })
      .click();
    await dialog.getByRole("alert").waitFor();
    assert.equal(
      await dialog.getByLabel("Branch override").inputValue(),
      "125",
    );
    conflict = false;
    await dialog
      .getByRole("button", { name: "Save changes", exact: true })
      .click();
    await dialog.waitFor({ state: "hidden" });
    assert.equal(priceWrites, 1);
    await first.getByText("₹125 / piece", { exact: true }).waitFor();
    await page
      .getByRole("button", { name: "+ Add product", exact: true })
      .click();
    dialog = page.getByRole("dialog");
    await dialog.getByLabel("Name *", { exact: true }).fill("New sweet");
    await dialog.getByLabel("Product code *", { exact: true }).fill("NEW-1");
    await dialog.getByLabel("Base price").fill("200");
    assert.equal(
      await dialog
        .getByRole("button", { name: "Create product", exact: true })
        .isVisible(),
      true,
    );
    const footer = await dialog
      .getByRole("button", { name: "Create product", exact: true })
      .boundingBox();
    assert.ok(
      footer.y + footer.height <= 900,
      "Save button must stay inside viewport",
    );
    await page.screenshot({ path: `${shots}/add-product-${width}.png` });
    await dialog
      .getByRole("button", { name: "Create product", exact: true })
      .click();
    await dialog.waitFor({ state: "hidden" });
    assert.equal(creates, 1);
    await page.getByLabel("Select Kaju Katli", { exact: true }).check();
    await page.getByLabel("Select Samosa", { exact: true }).check();
    await page
      .getByRole("button", { name: "Set availability", exact: true })
      .click();
    dialog = page.getByRole("dialog");
    await dialog
      .getByRole("button", { name: "Confirm update", exact: true })
      .click();
    await dialog
      .getByText("Samosa: Concurrent change; draft retained.")
      .waitFor();
    await page.screenshot({
      path: `${shots}/bulk-results-${width}.png`,
      fullPage: true,
    });
    bulkFailures = false;
    await dialog
      .getByRole("button", { name: "Retry failed only", exact: true })
      .click();
    await dialog.getByText("Samosa: Updated").waitFor();
    assert.equal(items[0].branchVersion, 2);
    assert.equal(items[1].branchVersion, 1);
    await dialog.getByRole("button", { name: "Close", exact: true }).click();
    await first.getByRole("button", { name: "Stock", exact: true }).click();
    dialog = page.getByRole("dialog");
    await dialog.getByLabel("Adjustment reason").fill("Fresh production");
    await page.screenshot({
      path: `${shots}/inventory-${width}.png`,
      fullPage: true,
    });
    await dialog
      .getByRole("button", { name: "Save changes", exact: true })
      .click();
    await dialog.waitFor({ state: "hidden" });
    await page
      .getByRole("button", { name: "Groups & sizes", exact: true })
      .click();
    await page
      .getByRole("button", { name: "+ New group", exact: true })
      .click();
    dialog = page.getByRole("dialog");
    await dialog
      .getByLabel("Customer-facing group name")
      .fill("Sweet portions");
    await dialog
      .getByRole("button", { name: "Next SKUs", exact: true })
      .click();
    await dialog.getByRole("button", { name: /Extra SKU.*Link/ }).waitFor();
    await dialog
      .getByRole("button", { name: "Previous SKUs", exact: true })
      .click();
    await dialog.getByRole("button", { name: /Kaju Katli.*Link/ }).click();
    await dialog.getByRole("button", { name: /Samosa.*Link/ }).click();
    await page.reload();
    dialog = page.getByRole("dialog");
    await dialog.waitFor();
    assert.equal(
      await dialog.getByLabel("Customer-facing group name").inputValue(),
      "Sweet portions",
    );
    assert.equal(
      await dialog.getByLabel("Option · SKU #1", { exact: true }).inputValue(),
      "Kaju Katli",
    );
    await page.screenshot({
      path: `${shots}/group-editor-${width}.png`,
      fullPage: true,
    });
    await dialog
      .getByRole("button", { name: "Save & create next", exact: true })
      .click();
    await page.waitForFunction(() =>
      Array.from(document.querySelectorAll("dialog input")).some(
        (input) => input.value === "" && input.maxLength === 100,
      ),
    );
    assert.equal(
      await dialog.getByLabel("Customer-facing group name").inputValue(),
      "",
    );
    assert.equal(
      await dialog.getByLabel("Option · SKU #1", { exact: true }).count(),
      0,
    );
    await dialog.getByRole("button", { name: "Cancel", exact: true }).click();
    await dialog.waitFor({ state: "hidden" });
    await page.getByRole("heading", { name: "Sweet portions" }).waitFor();
    assert.equal(groups.groups[0].choices.length, 2);
    await page
      .getByRole("button", { name: "Menu appearance", exact: true })
      .click();
    await page
      .getByRole("button", { name: "+ Add banner", exact: true })
      .click();
    dialog = page.getByRole("dialog");
    await dialog
      .getByLabel("English title", { exact: true })
      .fill("Made for your sweet moments");
    await dialog
      .getByLabel("Hindi title", { exact: true })
      .fill("हर खुशी में मिठास");
    appearance = {
      ...appearance,
      version: appearance.version + 1,
      draft: {
        ...appearance.draft,
        categories: [{ id: 1, order: 2, imageUrl: null }],
      },
    };
    delayCatalogue = true;
    await page.reload();
    dialog = page.getByRole("dialog");
    await dialog.waitFor();
    assert.equal(
      await page
        .getByRole("button", { name: "Category images & order", exact: true })
        .isDisabled(),
      true,
    );
    for (let i = 0; i < 50 && !releaseCatalogue; i++)
      await page.waitForTimeout(20);
    assert.ok(releaseCatalogue);
    delayCatalogue = false;
    releaseCatalogue();
    assert.equal(
      await dialog.getByLabel("English title", { exact: true }).inputValue(),
      "Made for your sweet moments",
    );
    await dialog
      .getByRole("button", { name: "Save draft", exact: true })
      .click();
    await dialog.getByRole("alert").waitFor();
    assert.equal(appearanceConflicts, 1);
    failAppearanceReload = true;
    await dialog.getByRole("button", { name: "Cancel", exact: true }).click();
    await dialog.waitFor({ state: "hidden" });
    await page
      .getByRole("button", { name: "Retry appearance reload", exact: true })
      .waitFor();
    assert.equal(
      await page
        .getByRole("button", { name: "+ Add banner", exact: true })
        .isDisabled(),
      true,
    );
    await page
      .getByRole("button", { name: "Retry appearance reload", exact: true })
      .click();
    await page
      .getByRole("button", { name: "+ Add banner", exact: true })
      .click();
    dialog = page.getByRole("dialog");
    await dialog
      .getByLabel("English title", { exact: true })
      .fill("Made for your sweet moments");
    await dialog
      .getByLabel("Hindi title", { exact: true })
      .fill("हर खुशी में मिठास");
    await page.screenshot({
      path: `${shots}/banner-preview-${width}.png`,
      fullPage: true,
    });
    await dialog
      .getByRole("button", { name: "Save draft", exact: true })
      .click();
    await dialog.waitFor({ state: "hidden" });
    assert.equal(appearance.live.banners.length, 0);
    assert.equal(appearance.draft.categories.length, 1);
    await page
      .getByRole("button", { name: "Review & publish", exact: true })
      .click();
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "Publish now", exact: true })
      .click();
    await page.getByRole("dialog").waitFor({ state: "hidden" });
    assert.equal(appearance.live.banners.length, 1);
    assert.deepEqual(errors, []);
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
      true,
    );
    await page
      .getByRole("button", { name: "Category images & order", exact: true })
      .click();
    dialog = page.getByRole("dialog");
    assert.equal(
      await dialog.getByText("Other branch category", { exact: true }).count(),
      0,
    );
    await dialog
      .getByRole("button", { name: "Save draft", exact: true })
      .click();
    await dialog.waitFor({ state: "hidden" });
    assert.deepEqual(
      appearance.draft.categories.map((c) => c.id),
      [1],
    );
    await page.evaluate(() => {
      const key = "menu-workspace:77";
      const state = JSON.parse(sessionStorage.getItem(key));
      sessionStorage.setItem(
        key,
        JSON.stringify({
          ...state,
          tab: "items",
          editor: { mode: "add", item: null },
          groupEditor: null,
          selected: [],
        }),
      );
      sessionStorage.setItem(
        key + ":product:1:add:new:",
        JSON.stringify({
          createdId: 99,
          name: "Created sweet",
          code: "CREATED-99",
          category: 1,
          price: "100",
        }),
      );
    });
    await page.reload();
    dialog = page.getByRole("dialog");
    await dialog.waitFor();
    assert.equal(
      await dialog.getByLabel("Name *", { exact: true }).isDisabled(),
      true,
    );
    assert.equal(
      await dialog.locator('input[type="file"]').isDisabled(),
      false,
    );
    const createsBefore = creates,
      imagesBefore = imageCalls;
    await dialog
      .getByRole("button", { name: "Retry photo", exact: true })
      .click();
    await dialog.getByRole("alert").waitFor();
    assert.equal(imageCalls, imagesBefore);
    const fixture = await page.evaluate(() => {
      const c = document.createElement("canvas");
      c.width = 240;
      c.height = 120;
      const ctx = c.getContext("2d");
      for (const [x, color] of [
        [0, "red"],
        [80, "lime"],
        [160, "blue"],
      ]) {
        ctx.fillStyle = color;
        ctx.fillRect(x, 0, 80, 120);
      }
      return c.toDataURL("image/png").split(",")[1];
    });
    await dialog.locator('input[type="file"]').setInputFiles({
      name: "landscape.png",
      mimeType: "image/png",
      buffer: Buffer.from(fixture, "base64"),
    });
    async function pixels(points) {
      await dialog
        .getByRole("button", { name: "Apply crop", exact: true })
        .click();
      const image = dialog.getByAltText("Cropped product photo");
      await image.waitFor();
      return image.evaluate(async (img, points) => {
        await img.decode();
        const c = document.createElement("canvas");
        c.width = 800;
        c.height = 800;
        const ctx = c.getContext("2d");
        ctx.drawImage(img, 0, 0);
        return points.map(([x, y]) =>
          Array.from(ctx.getImageData(x, y, 1, 1).data).slice(0, 3),
        );
      }, points);
    }
    let sampled = await pixels([
      [10, 400],
      [400, 400],
      [790, 400],
      [400, 10],
    ]);
    assert.ok(sampled[0][0] > 240 && sampled[0][1] < 20);
    assert.ok(sampled[1][1] > 240);
    assert.ok(sampled[2][2] > 240);
    assert.ok(sampled[3].every((n) => n > 240));
    await dialog
      .getByRole("button", { name: "Rotate 90°", exact: true })
      .click();
    sampled = await pixels([
      [400, 10],
      [400, 790],
      [10, 400],
    ]);
    assert.ok(sampled[0][0] > 240);
    assert.ok(sampled[1][2] > 240);
    assert.ok(sampled[2].every((n) => n > 240));
    await dialog.getByRole("button", { name: "Fill", exact: true }).click();
    sampled = await pixels([[400, 10]]);
    assert.ok(sampled[0].some((n) => n < 200));
    await dialog.getByRole("button", { name: "Fit", exact: true }).click();
    await page.evaluate(() => {
      const original = HTMLImageElement.prototype.decode;
      window.holdDecode = true;
      HTMLImageElement.prototype.decode = function () {
        const decode = original.bind(this);
        if (window.holdDecode) {
          window.holdDecode = false;
          return new Promise((resolve) => {
            window.releaseDecode = () => decode().then(resolve);
          });
        }
        return decode();
      };
    });
    await dialog
      .getByRole("button", { name: "Apply crop", exact: true })
      .click();
    await page.waitForFunction(
      () => typeof window.releaseDecode === "function",
    );
    for (const name of ["Rotate 90°", "Fit", "Fill"])
      assert.equal(
        await dialog.getByRole("button", { name, exact: true }).isDisabled(),
        true,
      );
    assert.equal(await dialog.locator('input[type="file"]').isDisabled(), true);
    assert.equal(await dialog.locator("[inert]").count(), 1);
    await dialog
      .getByRole("button", { name: "Rotate 90°", exact: true })
      .evaluate((button) => button.click());
    await page.evaluate(() => window.releaseDecode());
    await dialog
      .getByRole("button", { name: "Apply crop", exact: true })
      .waitFor();
    await page.waitForFunction(
      () =>
        !Array.from(document.querySelectorAll("button")).find(
          (b) => b.textContent === "Apply crop",
        ).disabled,
    );
    const finalPixel = await dialog
      .getByAltText("Cropped product photo")
      .evaluate(async (image) => {
        await image.decode();
        const canvas = document.createElement("canvas");
        canvas.width = 800;
        canvas.height = 800;
        const ctx = canvas.getContext("2d");
        ctx.drawImage(image, 0, 0);
        return Array.from(ctx.getImageData(10, 400, 1, 1).data).slice(0, 3);
      });
    assert.ok(finalPixel.every((n) => n > 240));
    assert.equal(
      await dialog
        .getByRole("button", { name: "Rotate 90°", exact: true })
        .isDisabled(),
      false,
    );
    await dialog
      .getByRole("button", { name: "Retry photo", exact: true })
      .click();
    await dialog.waitFor({ state: "hidden" });
    assert.equal(creates, createsBefore);
    assert.equal(imageCalls, imagesBefore + 1);
    await first.getByRole("button", {name: "Delete from branch", exact: true}).click();
    dialog = page.getByRole("dialog");
    await dialog.getByText("This cannot be undone.", {exact: false}).waitFor();
    await dialog.getByRole("button", {name: "Cancel", exact: true}).click();
    assert.equal(deleteCalls, 0);
    await first.getByRole("button", {name: "Delete from branch", exact: true}).click();
    await dialog.getByRole("button", {name: "Yes, permanently delete", exact: true}).click();
    await dialog.getByRole("alert").filter({hasText: "inventory history"}).waitFor();
    await dialog.getByRole("button", {name: "Yes, permanently delete", exact: true}).click();
    await dialog.waitFor({state: "hidden"});
    await page.getByRole("heading", {name: "Kaju Katli", exact: true}).waitFor({state: "hidden"});
    assert.equal(deleteCalls, 2);
    paginationDeletion = true;
    await page.reload();
    await page.getByRole("heading", {name: "Samosa", exact: true}).waitFor();
    await page.getByRole("button", {name: "Next", exact: true}).click();
    const lastItem = page.locator("article").filter({has: page.getByRole("heading", {name: "Rasmalai", exact: true})});
    await lastItem.getByRole("button", {name: "Delete from branch", exact: true}).click();
    await page.getByRole("dialog").getByRole("button", {name: "Yes, permanently delete", exact: true}).click();
    await page.getByRole("heading", {name: "Samosa", exact: true}).waitFor();
    assert.equal(await page.getByRole("button", {name: "Previous", exact: true}).isDisabled(), true);
    assert.equal(await page.getByRole("button", {name: "Next", exact: true}).isDisabled(), true);
    await context.close();
    console.log(
      `Workspace ${width}px: branch categories, partial photo recovery, fit/rotated-fit/fill pixels, appearance loading/conflict recovery, refresh drafts/date/tab, branch loading safety, paginated SKUs, popup recovery, create, stock, partial bulk retry, grouping and draft/publish passed`,
    );
  }
} finally {
  await browser.close();
}
