import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const {chromium} = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const browser = await chromium.launch({headless: true}), base = process.env.BROWSER_BASE ?? 'http://127.0.0.1:3311';
try {
    for (const width of [320, 390, 1280]) {
        const context = await browser.newContext({viewport: {width, height: 900}, serviceWorkers: 'block'}), page = await context.newPage();
        const branch = {id: 1, name: 'Tour branch', code: 'TOUR', active: true, operational: true, pickupAvailable: true};
        await context.route('**/api/**', async route => {
            const request = route.request(), path = new URL(request.url()).pathname;
            const headers = {'Access-Control-Allow-Origin': base, 'Access-Control-Allow-Credentials': 'true', 'Access-Control-Allow-Headers': 'content-type', 'Access-Control-Allow-Methods': 'GET,POST,OPTIONS'};
            if (request.method() === 'OPTIONS') return route.fulfill({status: 204, headers});
            assert.equal(request.method(), 'GET', 'the guide never writes orders, payments or branch data');
            let json = [];
            if (path === '/api/storefront/features') json = {futuristicStorefrontV2: true, checkoutExperienceV2: true};
            else if (path === '/api/branches') json = [branch];
            else if (path === '/api/branches/1') json = branch;
            else if (path === '/api/storefront/customer-identity') json = {enabled: false, guestCheckoutEnabled: true};
            else if (path === '/api/customer/identity/me') json = {authenticated: false};
            return route.fulfill({json, headers});
        });
        await context.addInitScript(branch => {
            localStorage.setItem('gokul-selected-branch', JSON.stringify(branch));
        }, branch);
        await page.goto(`${base}/branches`);
        await page.locator('.gokul-mobile-launch').waitFor({state: 'hidden'});
        await page.getByRole('button', {name: 'Show me how', exact: true}).click();
        const dialog = page.locator('.ordering-tour-dialog');
        await dialog.getByRole('heading', {name: 'Choose your branch', exact: true}).waitFor();
        assert.equal(await dialog.evaluate(node => node.matches(':modal')), true);
        assert.equal(await page.evaluate(() => document.body.style.overflow), 'hidden');
        // The existing social popup is scheduled after 4.5 seconds. A fresh
        // customer reading the guide must not have two competing prompts.
        await page.waitForTimeout(4700);
        assert.equal(await page.locator('#social-follow-title').count(), 0, 'social promotion waits until the guide is closed');
        if (process.env.SCREENSHOT_DIR) {
            await mkdir(process.env.SCREENSHOT_DIR, {recursive: true});
            await page.screenshot({path: `${process.env.SCREENSHOT_DIR}/ordering-tour-${width}.png`});
        }
        await dialog.getByRole('button', {name: 'Next', exact: true}).click();
        await dialog.getByRole('heading', {name: 'Check pickup date & time', exact: true}).waitFor();
        await dialog.getByRole('button', {name: 'Previous', exact: true}).click();
        await dialog.getByRole('heading', {name: 'Choose your branch', exact: true}).waitFor();
        await page.keyboard.press('Escape'); await dialog.waitFor({state: 'hidden'});
        assert.notEqual(await page.evaluate(() => document.body.style.overflow), 'hidden');
        assert.equal(await page.getByRole('button', {name: 'How to order', exact: true}).evaluate(node => node === document.activeElement), true, 'removed invitation restores focus to the replay control');
        await page.reload(); await page.locator('.gokul-mobile-launch').waitFor({state: 'hidden'});
        assert.equal(await page.locator('.ordering-tour-invite').count(), 0, 'starting/skipping persists across refresh');
        await page.getByRole('button', {name: 'How to order', exact: true}).click();
        for (let step = 0; step < 3; step++) await dialog.getByRole('button', {name: 'Next', exact: true}).click();
        await dialog.getByRole('heading', {name: 'Pay, then collect', exact: true}).waitFor();
        assert.ok((await dialog.boundingBox()).width <= width - 24);
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
        await dialog.getByRole('button', {name: 'Got it', exact: true}).click();
        await dialog.waitFor({state: 'hidden'});
        assert.equal(await page.getByRole('button', {name: 'How to order', exact: true}).evaluate(node => node === document.activeElement), true, 'replay returns focus to its original trigger');
        await page.evaluate(() => {
            localStorage.removeItem('gokul-ordering-tour:v1');
            localStorage.setItem('gokul-cart', JSON.stringify({branchId: 1, items: [{product: {id: 1, name: 'Sweet', price: 100, available: true, saleMode: 'UNIT'}, quantity: 1, weightGrams: null}]}));
        });
        await page.reload(); await page.getByRole('button', {name: 'How to order', exact: true}).waitFor();
        assert.equal(await page.locator('.ordering-tour-invite').count(), 0, 'a returning cart is not interrupted');
        await page.goto(`${base}/checkout/customer`);
        await page.locator('.gokul-mobile-launch').waitFor({state: 'hidden'});
        assert.equal(await page.locator('.ordering-tour-invite,.ordering-tour-replay,.ordering-tour-dialog').count(), 0, 'checkout has no tour');
        await context.close();
    }
    console.log('PASS: optional four-step guide, refresh persistence, replay, mobile fit, keyboard dismissal and checkout/cart isolation.');
} finally { await browser.close(); }
