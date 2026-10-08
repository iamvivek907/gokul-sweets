import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium} = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const browser = await chromium.launch({headless: true}), base = process.env.BROWSER_BASE ?? 'http://127.0.0.1:3311';
try {
    for (const width of [390, 1280]) {
        const context = await browser.newContext({viewport: {width, height: 900}, serviceWorkers: 'block'}), page = await context.newPage();
        let status = 'PENDING', creates = 0, gatewayVisits = 0;
        let providerDown = false, providerSettled = false, paymentExpired = false, lookupGate = null, lookupStarted = null;
        const branch = {id: 1, code: 'TEST', name: 'History branch', active: true, operational: true, pickupAvailable: true};
        const payment = () => ({paymentId: 10, orderNumber: 'TEST-HISTORY', provider: 'PHONEPE', paymentStatus: status, amount: 200, currency: 'INR', paymentUrl: null, providerOrderId: 'test', expiresAt: new Date(Date.now() + (paymentExpired ? -1000 : 600000)).toISOString()});
        const order = () => ({id: 1, orderNumber: 'TEST-HISTORY', branchId: 1, branchName: branch.name, pickupDate: '2026-10-10', pickupStartTime: '18:00:00', pickupEndTime: '19:00:00', pickupType: 'NORMAL', fulfillmentType: 'PICKUP', customerName: 'Test customer', customerPhone: '9876543210', orderStatus: status === 'PAID' ? 'CONFIRMED' : status === 'PENDING' ? 'PENDING_PAYMENT' : 'CANCELLED', paymentStatus: status, subtotal: 200, totalAmount: 200, taxAmount: 0, priorityCharge: 0, items: [], reservationExpiresAt: new Date(Date.now() + 600000).toISOString(), createdAt: new Date().toISOString()});
        await context.route('https://gateway.example.invalid/**', async route => {gatewayVisits++; await route.fulfill({contentType: 'text/html', body: '<h1>Secure gateway</h1>'});});
        await context.route('**/api/**', async route => {
            const req = route.request(), path = new URL(req.url()).pathname;
            const headers = {'Access-Control-Allow-Origin': base, 'Access-Control-Allow-Credentials': 'true', 'Access-Control-Allow-Headers': 'content-type,idempotency-key', 'Access-Control-Allow-Methods': 'GET,POST,OPTIONS'};
            if (req.method() === 'OPTIONS') return route.fulfill({status: 204, headers});
            let json = [];
            if (path === '/api/storefront/features') json = {futuristicStorefrontV2: true, checkoutExperienceV2: true, simplifiedCheckout: true, acceptedCheckoutQuote: true, paymentPollingV2: true, paidCartRecovery: true};
            else if (path === '/api/storefront/customer-identity') json = {enabled: false, guestCheckoutEnabled: true};
            else if (path === '/api/customer/identity/me') json = {authenticated: false};
            else if (path === '/api/branches') json = [branch];
            else if (path === '/api/branches/1') json = branch;
            else if (path === '/api/payments/order/TEST-HISTORY') {if (lookupGate) {lookupStarted(); await lookupGate;} json = {payment: payment()};}
            else if (path === '/api/payments/10/refresh') {if (providerDown) return route.fulfill({status: 503, json: {message: 'Provider temporarily unavailable'}, headers}); if(providerSettled)status='PAID'; json = payment();}
            else if (path === '/api/payments/10/cancel-checkout') {status = 'EXPIRED'; json = payment();}
            else if (path === '/api/orders/TEST-HISTORY') json = order();
            else if (path === '/api/payments' && req.method() === 'POST') {creates++; json = payment();}
            return route.fulfill({json, headers});
        });
        await context.addInitScript(branch => {localStorage.setItem('gokul-selected-branch', JSON.stringify(branch)); localStorage.setItem('gokul-social-follow-popup-seen', 'true'); localStorage.setItem('gokul-ordering-tour:v1', 'seen');}, branch);
        const seed = async () => page.evaluate(payment => localStorage.setItem('gokul-pending-payment', JSON.stringify({...payment, paymentStatus: 'PENDING', paymentUrl: 'https://gateway.example.invalid/pay', cartFingerprint: ''})), payment());
        const pay = () => page.getByRole('button', {name: width <= 640 ? 'Continue payment' : /^(?:Pay|Retry payment) ₹/, exact: width <= 640});
        await page.goto(`${base}/branches`); await seed();
        await page.goto(`${base}/checkout/payment/TEST-HISTORY`); await pay().waitFor();
        await pay().click(); await page.waitForURL('https://gateway.example.invalid/pay');
        await page.goBack(); await page.waitForURL(`${base}/branches`);
        assert.equal(gatewayVisits, 1, 'Back skips the replaced merchant Pay entry');
        if (width <= 640) {
            await seed(); await page.goto(`${base}/checkout/payment/TEST-HISTORY`); await pay().waitFor();
            await page.getByRole('link', {name: 'Back to menu', exact: true}).click();
            await page.waitForURL('**/menu');
            await page.goBack(); await page.waitForURL(`${base}/branches`);
            assert.equal(status, 'EXPIRED', 'cancellation replaces its old Pay entry');
            status = 'PENDING';
        }
        await seed(); await page.goto(`${base}/checkout/payment/TEST-HISTORY`); await pay().waitFor();
        await page.evaluate(() => localStorage.setItem('gokul-payment-gateway-opened:TEST-HISTORY', '10'));
        providerDown = true; await pay().click();
        await page.getByRole('alert').filter({hasText: 'Provider temporarily unavailable'}).waitFor();
        assert.equal(gatewayVisits, 1, 'provider uncertainty blocks a deliberate gateway reopen');
        assert.equal(await page.evaluate(() => localStorage.getItem('gokul-payment-gateway-opened:TEST-HISTORY')), '10', 'failed preflight preserves reconciliation marker');
        await page.evaluate(() => localStorage.removeItem('gokul-payment-gateway-opened:TEST-HISTORY'));
        const missingMarkerFailure=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/payments/10/refresh'&&response.status()===503);
        await pay().click();
        await missingMarkerFailure;
        await page.getByRole('alert').filter({hasText: 'Provider temporarily unavailable'}).waitFor();
        assert.equal(gatewayVisits, 1, 'missing browser marker does not bypass failed provider confirmation');
        providerDown = false;
        // Delay the pre-Pay server read while another tab changes the cart.
        let releaseLookup;
        lookupGate = new Promise(resolve => {releaseLookup = resolve;});
        const readStarted = new Promise(resolve => {lookupStarted = resolve;});
        await pay().click(); await readStarted;
        await page.evaluate(() => {
            localStorage.setItem('gokul-cart', JSON.stringify({branchId: 1, items: [{product: {id: 1, name: 'Sweet', price: 100, available: true, saleMode: 'UNIT'}, quantity: 2, weightGrams: null}]}));
            window.dispatchEvent(new StorageEvent('storage', {key: 'gokul-cart'}));
        });
        lookupGate = null; releaseLookup();
        await page.getByRole('alert').filter({hasText: 'Your checkout changed while payment was being checked'}).waitFor();
        assert.equal(gatewayVisits, 1, 'changed cart cannot launch a gateway from an obsolete preflight');
        assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('gokul-cart')).items[0].quantity), 2);
        await page.evaluate(() => {localStorage.removeItem('gokul-cart'); window.dispatchEvent(new StorageEvent('storage', {key: 'gokul-cart'}));});
        paymentExpired = true; await pay().click();
        await page.getByRole('alert').filter({hasText: 'This payment window has closed'}).waitFor();
        assert.equal(gatewayVisits, 1, 'a deadline reached during the read cannot open checkout');
        paymentExpired = false;
        // Provider settled but its callback has not updated the database yet.
        await seed(); await page.goto(`${base}/checkout/payment/TEST-HISTORY`); await pay().waitFor();
        await page.evaluate(() => localStorage.removeItem('gokul-payment-gateway-opened:TEST-HISTORY'));
        providerSettled = true; await pay().click(); await page.waitForURL('**/orders/TEST-HISTORY');providerSettled=false;
        assert.equal(gatewayVisits, 1, 'a stale Pay button cannot reopen the gateway after success');
        assert.equal(await page.locator('.ordering-tour-dialog,.ordering-tour-invite').count(), 0);
        await seed(); await page.goto(`${base}/checkout/payment/TEST-HISTORY`); await page.waitForURL('**/orders/TEST-HISTORY');
        assert.equal(creates, 0, 'paid history recovery never creates another payment');
        status = 'EXPIRED'; await seed(); await page.goto(`${base}/checkout/payment/TEST-HISTORY`);
        await page.locator('.gokul-mobile-launch').waitFor({state: 'hidden'});
        if (width <= 640) await page.getByRole('link', {name: 'View order', exact: true}).waitFor();
        else await page.getByRole('button', {name: 'View Order Details', exact: true}).waitFor();
        assert.equal(await pay().count(), 0, 'cancelled history has no Pay action');
        // Simulate a cache restoration while the page is still pending.
        status = 'PENDING'; await seed(); await page.goto(`${base}/checkout/payment/TEST-HISTORY`); await pay().waitFor();
        await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
        status = 'PAID'; await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pageshow', {persisted: true})));
        await page.waitForURL('**/orders/TEST-HISTORY').catch(async error => {console.error('Cache restoration state:', page.url(), await page.locator('body').innerText()); throw error;});
        assert.equal(gatewayVisits, 1); assert.equal(creates, 0);
        console.log(`PASS: payment history and restoration at ${width}px`);
        await context.close();
    }
    console.log('PASS: PhonePe history replacement, stale paid/cancelled snapshots, pre-Pay recheck and browser-cache restoration.');
} finally { await browser.close(); }
