import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { mkdir } from "node:fs/promises";
const { chromium } = createRequire(import.meta.url)(
  process.env.PLAYWRIGHT_MODULE ?? "playwright",
);
const base = process.env.BROWSER_BASE ?? "http://127.0.0.1:3311",
  date = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(
    new Date(Date.now() + 86400000),
  );
const branch = {
  id: 1,
  name: "Test branch",
  active: true,
  operational: true,
  pickupAvailable: true,
};
const slot = {
  id: 1,
  branchId: 1,
  slotDate: date,
  startTime: "18:00:00",
  endTime: "19:00:00",
  active: true,
  remainingCapacity: 20,
  priorityEnabled: false,
};
const products = [
  "Paneer Samosa",
  "Kaju Katli",
  "Gulab Jamun",
  "Rasgulla",
  "Dahi small",
  "Dahi large",
].map((name, i) => ({
  id: i + 1,
  name,
  categoryId: 1,
  categoryName: "Our menu",
  description: "Made with a delicate filling and a crisp finish.",
  price: 40 + i * 10,
  comparisonPrice: 100 + i * 10,
  available: true,
  saleMode: "UNIT",
  imageUrl: "/arrival-mithai.webp",
}));
const groups = [
  {
    key: "dahi",
    title: "Dahi",
    choices: [
      { productId: 5, label: "Small" },
      { productId: 6, label: "Large" },
    ],
  },
];
const facts = products.map((p) => ({
  productId: p.id,
  servingInfo: "2 pieces",
  ingredients: "Milk and nuts",
  allergens: "Contains milk and nuts",
  spiceLevel: "MILD",
  photoFocusX: 25,
  photoFocusY: 75,
  chefPick: p.id === 1,
  version: 1,
}));
const browser = await chromium.launch({ headless: true });
try {
  for (const [width, modern] of [
    [320, true],
    [390, true],
    [1280, true],
    [390, false],
  ]) {
    const context = await browser.newContext({
        viewport: { width, height: 844 },
        serviceWorkers: "block",
        reducedMotion: width === 320 ? "reduce" : "no-preference",
      }),
      page = await context.newPage();
    page.setDefaultTimeout(15000);
    let failReviews = false,
      failFacts = false;
    const reviews = [],
      errors = [];
    let factCalls = 0,
      writes = 0;
    page.on("pageerror", (e) => errors.push(e.message));
    await context.addInitScript(
      ({ branch, date, slot }) => {
        localStorage.setItem("gokul-selected-branch", JSON.stringify(branch));
        localStorage.setItem(
          "gokul-pickup-intent",
          JSON.stringify({ branchId: 1, date }),
        );
        localStorage.setItem(
          "gokul-selected-pickup-slot",
          JSON.stringify({ date, slot, pickupType: "NORMAL" }),
        );
        localStorage.setItem("gokul-social-follow-popup-seen", "true");
      },
      { branch, date, slot },
    );
    await context.route("**/api/**", async (route) => {
      const request = route.request(),
        url = new URL(request.url()),
        path = url.pathname,
        headers = {
          "Access-Control-Allow-Origin": base,
          "Access-Control-Allow-Credentials": "true",
          "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
          "Access-Control-Allow-Headers": "content-type",
        };
      if (request.method() === "OPTIONS")
        return route.fulfill({ status: 204, headers });
      let json = [];
      if (path === "/api/storefront/features")
        json = {
          futuristicStorefrontV2: modern,
          checkoutExperienceV2: modern,
          contextualStorefrontV2: modern,
          simplifiedCheckout: true,
          acceptedCheckoutQuote: true,
          smartAvailability: true,
          reviews: true,
          branchExperience: true,
          preHomeIntentGateway: true,
          today: date,
          futureOrderingDays: 30,
        };
      else if (path === "/api/branches")
        json = [branch, { ...branch, id: 2, name: "Second branch" }];
      else if (/^\/api\/branches\/\d+$/.test(path))
        json = { ...branch, id: Number(path.split("/").pop()) };
      else if (path === "/api/menu/catalog")
        json = {
          revision: "1",
          categories: [{ id: 1, name: "Our menu", products }],
        };
      else if (path === "/api/menu")
        json = [{ id: 1, name: "Our menu", products }];
      else if (path === "/api/menu/portion-groups")
        json = { version: 1, groups };
      else if (path === "/api/menu/appearance")
        json = {
          banners: [
            {
              key: "festival",
              titleEn: "Festival favourites",
              titleHi: "त्योहार की मिठास",
              subtitleEn: "Prepared for pickup.",
              subtitleHi: "पिकअप के लिए।",
              buttonLabel: "Explore menu",
              categoryId: 1,
              mediaUrl: "/arrival-mithai.webp",
              mediaType: "image/webp",
              frame: { x: 50, y: 50, zoom: 100, fit: "COVER" },
            },
          ],
          categories: [],
        };
      else if (path === "/api/menu/details") {
        factCalls++;
        assert.ok(url.searchParams.get("productIds").split(",").length <= 100);
        if (failFacts)
          return route.fulfill({
            status: 503,
            headers,
            json: { message: "Unavailable" },
          });
        json = facts;
      } else if (path === "/api/reviews/product-summaries")
        json = request.postDataJSON().productIds.map((productId) => ({
          productId,
          averageRating: 4.5,
          ratingCount: 24,
        }));
      else if (path.endsWith("/storefront-highlights"))
        json = { latestProductIds: [1, 2, 3], trendingProductIds: [2, 1] };
      else if (path.endsWith("/availability"))
        json = {
          today: date,
          maximumDate: date,
          dates: [
            {
              date,
              available: true,
              items: products.map((p) => ({
                productId: p.id,
                available: true,
              })),
              slots: [
                {
                  slot,
                  normalAvailable: true,
                  priorityAvailable: false,
                  issues: [],
                },
              ],
            },
          ],
        };
      else if (path.endsWith("/pickup-slots")) json = [slot];
      else if (path.endsWith("/inventory/check"))
        json = { enforcementEnabled: false, orderable: true, items: [] };
      else if (path.endsWith("/discovery/dish-reviews")) {
        reviews.push(Object.fromEntries(url.searchParams));
        if (failReviews)
          return route.fulfill({
            status: 503,
            headers,
            json: { message: "Try later" },
          });
        json = {
          reviews: Array.from(
            { length: url.searchParams.has("before") ? 2 : 20 },
            (_, i) => ({
              comment: `A carefully prepared meal ${i}.`,
              overallRating: 5,
              displayName: i % 2 ? "Customer" : "Meera",
              itemRatings: [
                {
                  productId: Number(
                    url.searchParams.get("productIds").split(",")[0],
                  ),
                  productName: "Test dish",
                  rating: 4,
                },
              ],
            }),
          ),
          nextBefore: url.searchParams.has("before") ? null : 100,
        };
      } else if (path === "/api/storefront/customer-identity")
        json = { enabled: true };
      else if (path === "/api/customer/identity/me")
        json = {
          authenticated: true,
          name: "Test customer",
          phone: "+919876543210",
        };
      else if (path === "/api/customer/identity/orders/page")
        json = {
          orders: [
            {
              orderNumber: "LAST-1",
              orderStatus: "PICKED_UP",
              branchId: 1,
              branchName: branch.name,
              totalAmount: 80,
              pickupDate: date,
              reviewRating: 5,
            },
          ],
          nextBefore: null,
        };
      else if (path === "/api/customer/identity/orders/LAST-1")
        json = {
          orderNumber: "LAST-1",
          orderStatus: "PICKED_UP",
          branchId: 1,
          branchName: branch.name,
          totalAmount: 80,
          pickupDate: date,
          items: [
            {
              productId: 1,
              productName: products[0].name,
              saleMode: "UNIT",
              quantity: 2,
              unitPrice: 40,
              lineTotal: 80,
            },
          ],
        };
      else if (
        request.method() === "POST" &&
        ["/api/orders", "/api/payments"].includes(path)
      )
        writes++;
      return route.fulfill({ headers, json }).catch(() => {});
    });
    await page.goto(`${base}/menu`);
    await page.locator(".gokul-mobile-launch").waitFor({ state: "hidden" });
    const first = page.locator("#gokul-product-1");
    await first.waitFor();
    if (!modern) {
      assert.equal(await page.locator(".menu-save-dish").count(), 0);
      assert.equal(factCalls, 0);
      await context.close();
      console.log("Legacy menu remains isolated");
      continue;
    }
    await first
      .getByRole("button", { name: "Save Paneer Samosa", exact: true })
      .waitFor();
    {
      await page.evaluate(() => scrollTo(0, 0));
      const card = await first.boundingBox();
      assert.ok(card.y < 754, `first dish above fold: ${card.y}`);
      const campaign = await page
        .getByRole("heading", { name: "Festival favourites", exact: true })
        .boundingBox();
      assert.ok(campaign.y > card.y + card.height);
    }
    await first
      .getByRole("button", { name: "Save Paneer Samosa", exact: true })
      .click();
    await first
      .getByRole("button", { name: "Unsave Paneer Samosa", exact: true })
      .waitFor();
    await page.getByRole("button", { name: "Saved", exact: true }).click();
    await page
      .getByText("Saved on this device for this branch", { exact: true })
      .waitFor();
    assert.equal(await page.locator("#gokul-product-2").count(), 0);
    await page.reload();
    await first.waitFor();
    assert.equal(
      await page.locator("#gokul-product-2").count(),
      0,
      "saved filter survives reload",
    );
    await page.evaluate(() => {
      sessionStorage.setItem(
        "gokul-menu-browse-v1:1",
        JSON.stringify({
          savedAt: Date.now(),
          search: "Kaju",
          categoryId: 1,
          categories: [1],
          maximum: 1,
          portions: true,
          browseId: 1,
          scrollY: 800,
        }),
      );
    });
    await page.goto(`${base}/menu?saved=1`);
    await first.waitFor();
    assert.equal(
      await page.locator("#gokul-menu-search").inputValue(),
      "",
      "explicit saved navigation clears conflicting old browsing preferences",
    );
    await page
      .getByRole("button", { name: "Clear saved dishes", exact: true })
      .click();
    const clear = page.getByRole("dialog", {
      name: "Clear saved dishes?",
      exact: true,
    });
    await clear
      .getByRole("button", { name: "Keep saved dishes", exact: true })
      .click();
    await first.waitFor();
    await page.getByRole("button", { name: "Saved", exact: true }).click();
    const search = page.locator("#gokul-menu-search");
    if (width <= 640) {
      await search.fill("Paneer");
      await page
        .getByRole("listbox", { name: "Menu suggestions", exact: true })
        .waitFor();
      await page
        .getByRole("button", {
          name: "Browse all item categories",
          exact: true,
        })
        .click();
      await page
        .getByRole("dialog", { name: "Filter menu", exact: true })
        .waitFor();
      await page.keyboard.press("Escape");
      assert.equal(
        await search.inputValue(),
        "Paneer",
        "tapping another control from search suggestions must retain the action and query",
      );
    }
    await search.fill("pnaeer");
    await search.press("ArrowDown");
    await search.press("Enter");
    assert.equal(await search.inputValue(), "Paneer Samosa");
    await search.fill("");
    await search.focus();
    await page
      .getByRole("listbox", { name: "Recent searches", exact: true })
      .waitFor();
    await search.press("Tab");
    const clearHistory = page.getByRole("button", {
      name: "Clear recent searches",
      exact: true,
    });
    assert.equal(
      await clearHistory.evaluate((n) => n === document.activeElement),
      true,
    );
    await clearHistory.press("Enter");
    assert.equal(
      await search.evaluate((n) => n === document.activeElement),
      true,
      "clearing history returns keyboard focus to search",
    );
    await search.fill("समोसा");
    await first.waitFor();
    await search.fill("");
    await search.blur();
    await first.locator(".menu-review-trigger").click();
    const review = page.getByRole("dialog", {
      name: "Customer reviews · Paneer Samosa",
      exact: true,
    });
    await review.locator("figure").first().waitFor();
    assert.equal(await review.locator("figure").count(), 20);
    await review
      .getByRole("button", { name: "More reviews", exact: true })
      .click();
    await page.waitForFunction(
      () => document.querySelectorAll(".menu-dish-reviews figure").length === 2,
    );
    assert.equal(reviews.at(-1).before, "100");
    failReviews = true;
    await review.getByLabel("Dish rating", { exact: true }).selectOption("1");
    await review.getByRole("alert").waitFor();
    assert.equal(await review.locator("figure").count(), 0);
    failReviews = false;
    await review
      .getByRole("button", { name: "Try again", exact: true })
      .click();
    await review.locator("figure").first().waitFor();
    assert.equal(reviews.at(-1).stars, "1");
    assert.equal(reviews.at(-1).before, undefined);
    await page.keyboard.press("Escape");
    await review.waitFor({ state: "detached" });
    assert.equal(
      await first
        .locator(".menu-review-trigger")
        .evaluate((n) => n === document.activeElement),
      true,
    );
    const grouped = page.locator("#gokul-product-5");
    await grouped
      .getByRole("button", { name: "Save Dahi · all sizes", exact: true })
      .click();
    await grouped
      .getByRole("button", { name: "Unsave Dahi · all sizes", exact: true })
      .waitFor();
    await grouped
      .getByRole("button", { name: "Choose options for Dahi", exact: true })
      .click();
    const picker = page.getByRole("dialog", { name: "Dahi", exact: true });
    await picker
      .getByRole("button", { name: "Unsave Dahi small", exact: true })
      .waitFor();
    await picker.getByText("About this size", { exact: true }).first().click();
    await picker
      .getByText("Contains milk and nuts", { exact: true })
      .first()
      .waitFor();
    await picker.getByRole("button", { name: "Done", exact: true }).click();
    await page.waitForFunction(
      () =>
        document.querySelector("#gokul-product-1 img")?.style.objectPosition ===
        "25% 75%",
    );
    assert.ok(factCalls <= 3, "facts are shared by cards and filters");
    for (const chip of await page.locator(".menu-availability-chip").all()) {
      if (!(await chip.isVisible())) continue;
      const savedButton = chip.locator("..").locator(".menu-save-dish button");
      if (!(await savedButton.count())) continue;
      const badge = await chip.boundingBox(),
        save = await savedButton.boundingBox();
      assert.ok(
        badge.x + badge.width <= save.x ||
          save.x + save.width <= badge.x ||
          badge.y + badge.height <= save.y ||
          save.y + save.height <= badge.y,
        "pickup badge and saved-dish action must not overlap",
      );
    }

    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
      true,
    );
    await page.evaluate(() => scrollTo(0, 0));
    if (process.env.SCREENSHOT_DIR) {
      await mkdir(process.env.SCREENSHOT_DIR, { recursive: true });
      await page.screenshot({
        path: `${process.env.SCREENSHOT_DIR}/premium-menu-${width}.png`,
      });
    }
    await page.goto(`${base}/`);
    await page
      .getByRole("button", { name: "Review and order again", exact: true })
      .waitFor();
    await page
      .getByRole("button", { name: "Review and order again", exact: true })
      .click();
    const reorder = page.getByRole("dialog", {
      name: "Reorder for pickup",
      exact: true,
    });
    await reorder.waitFor();
    await reorder.getByRole("button", { name: "Cancel", exact: true }).click();
    assert.equal(
      await page.evaluate(() => localStorage.getItem("gokul-cart")),
      null,
    );
    assert.equal(writes, 0);
    assert.deepEqual(errors, []);
    failFacts = true;
    await page.goto(`${base}/menu`);
    await first.waitFor();
    await first
      .getByRole("button", { name: "Add Paneer Samosa to cart", exact: true })
      .waitFor();
    await first.locator(".menu-details-trigger").click();
    const detailsSheet = page.getByRole("dialog", {
      name: "Paneer Samosa",
      exact: true,
    });
    assert.equal(
      await detailsSheet.getByText("Regular price", { exact: true }).count(),
      1,
      "comparison prices have an explicit screen-reader label",
    );
    await detailsSheet
      .getByText(
        "Extra dish details are temporarily unavailable. Check with the branch about ingredients and allergies.",
        { exact: true },
      )
      .waitFor();
    assert.equal(
      await detailsSheet.getByText("Milk and nuts", { exact: true }).count(),
      0,
      "failed facts cannot remain as stale ingredient claims",
    );
    await page.keyboard.press("Escape");
    if (width === 390) {
      await page.evaluate(() => scrollTo({ top: 0, behavior: "instant" }));
      await page.locator(".language-trigger").waitFor({ state: "visible" });
      await page.locator(".language-trigger").click();
      await page.getByRole("button", { name: /हिन्दी.*अपनी भाषा/ }).click();
      await page.getByRole("button", { name: "सेव", exact: true }).waitFor();
      await page.evaluate(
        () => (document.documentElement.style.fontSize = "20px"),
      );
      assert.equal(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
        true,
        "Hindi and larger text reflow without page-wide scrolling",
      );
    }
    await context.close();
    console.log(`Premium customer journeys ${width}px passed`);
  }
} finally {
  await browser.close();
}
