import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium} = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BROWSER_BASE ?? 'http://127.0.0.1:3311';
const browser = await chromium.launch({headless: true, executablePath: process.env.CHROMIUM_PATH});
const headers = {'Access-Control-Allow-Origin':base,'Access-Control-Allow-Credentials':'true','Access-Control-Allow-Headers':'content-type,x-staff-csrf','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Access-Control-Expose-Headers':'X-Staff-CSRF','X-Staff-CSRF':'test-csrf'};
try {
  for (const width of [320,390,1280]) {
    const context = await browser.newContext({viewport:{width,height:950},serviceWorkers:'block'});
    const page = await context.newPage();
    page.setDefaultTimeout(15000);
    const errors = []; page.on('pageerror', e => errors.push(e.message));
    let allowed = true, reads = 0, writes = 0;
    let categories = [];
    await context.route('**/api/**', async route => {
      const req = route.request(), url = new URL(req.url());
      if (req.method() === 'OPTIONS') return route.fulfill({status:204,headers});
      if (url.pathname === '/api/admin/auth/me') return route.fulfill({headers,json:{staffId:7,username:'manager',fullName:'Manager',roleName:'MANAGER',branchIds:[1],permissions:allowed?['MENU_MANAGE']:[]}});
      if (url.pathname === '/api/admin/categories') {
        if (req.method() === 'GET') {reads++; return route.fulfill({headers,json:{content:categories,number:0,totalPages:categories.length?1:0}});}
        writes++; assert.equal(req.headers()['x-staff-csrf'],'test-csrf');
        const body = req.postDataJSON();
        if (categories.some(c => c.code === body.code)) return route.fulfill({status:409,headers,json:{message:'Category code already exists.'}});
        const created = {id:categories.length+1,...body,active:true}; categories.push(created);
        return route.fulfill({status:201,headers,json:created});
      }
      return route.fulfill({headers,json:[]});
    });
    await page.goto(`${base}/admin/menu/categories`);
    await page.getByText('No categories on this page.',{exact:false}).waitFor();
    await page.getByLabel('Category name',{exact:true}).fill('Sweets');
    await page.getByLabel('Category code',{exact:true}).fill('sweets');
    await page.getByRole('button',{name:'Create category',exact:true}).click();
    await page.getByRole('status').filter({hasText:'Sweets created'}).waitFor();
    await page.getByRole('list').filter({hasText:'SWEETS'}).waitFor();
    assert.equal(writes,1); assert.equal(categories[0].code,'SWEETS');
    await page.getByLabel('Category name',{exact:true}).fill('Duplicate name');
    await page.getByLabel('Category code',{exact:true}).fill('SWEETS');
    await page.getByRole('button',{name:'Create category',exact:true}).click();
    await page.getByRole('alert').filter({hasText:'already exists'}).waitFor();
    assert.equal(await page.getByLabel('Category name',{exact:true}).inputValue(),'Duplicate name');
    assert.equal(categories.length,1);
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
    assert.deepEqual(await page.getByRole('button',{name:'Create category',exact:true}).evaluate(el=>({color:getComputedStyle(el).color,background:getComputedStyle(el).backgroundColor})),{color:'rgb(255, 255, 255)',background:'rgb(122, 22, 37)'});
    allowed=false; const before=reads; await page.reload();
    await page.getByText('Menu management permission is required.',{exact:true}).waitFor();
    assert.equal(reads,before); assert.equal(await page.getByRole('button',{name:'Create category',exact:true}).count(),0);
    assert.deepEqual(errors,[]);
    await context.close(); console.log(`Menu category creation, duplicate recovery and permission checks passed at ${width}px`);
  }
} finally {await browser.close();}
