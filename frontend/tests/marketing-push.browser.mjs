import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE??'playwright');
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH});
const base=process.env.BROWSER_BASE??'http://127.0.0.1:3311';
try {
    for(const width of [320,390,1280]) {
        const context=await browser.newContext({viewport:{width,height:900},serviceWorkers:'block'});
        const page=await context.newPage();const errors=[];let evaluations=0;
        page.on('pageerror',error=>errors.push(error.message));
        const configs=new Map([1,2].map(id=>[id,{enabled:false,language:'HINGLISH',weeklyLimit:3,lunch:true,snacks:true,dinner:true,sweets:true,version:0}]));
        await context.route('**/api/**',async route=>{
            const request=route.request(),url=new URL(request.url());
            const headers={'access-control-allow-origin':base,'access-control-allow-credentials':'true','access-control-allow-headers':'Content-Type,X-Staff-CSRF,X-Requested-With','access-control-expose-headers':'X-Staff-CSRF','X-Staff-CSRF':'ci-marketing-csrf','access-control-allow-methods':'GET,POST,PUT,OPTIONS'};
            if(request.method()==='OPTIONS')return route.fulfill({status:204,headers});
            let body={};
            if(url.pathname==='/api/admin/auth/me')body={staffId:77,username:'owner',fullName:'Owner',roleName:'OWNER_ADMIN',branchIds:[1,2],permissions:['REBATE_MANAGE']};
            else if(url.pathname==='/api/branches')body=[{id:1,name:'Main branch',active:true},{id:2,name:'Second branch',active:true}];
            else if(url.pathname.startsWith('/api/admin/marketing-push/')) {
                const id=Number(url.pathname.split('/')[4]);
                if(request.method()==='PUT') {
                    const input=request.postDataJSON();assert.equal(input.version,configs.get(id).version);
                    configs.set(id,{...input,version:input.version+1});body=configs.get(id);
                }else if(url.pathname.endsWith('/evaluate')) {assert.equal(configs.get(id).enabled,true);evaluations++;body={queued:1};}
                else body={settings:configs.get(id),pushConfigured:true,masterEnabled:true,currentWindow:'SNACKS',samples:[{key:'SNACKS:EN:0',topic:'SNACKS',title:'Chai needs a plus-one ☕',body:'Explore Gokul snacks.'}],recent:[]};
            }
            return route.fulfill({json:body,headers});
        });
        await page.goto(`${base}/admin/marketing-push`);
        await page.getByRole('heading',{name:'Smart, cheerful nudges'}).waitFor();
        const toggle=page.getByRole('checkbox',{name:'Enable automatic promotional push'});
        await toggle.waitFor();assert.equal(await toggle.isChecked(),false);
        assert.equal(await page.getByRole('button',{name:'Evaluate eligible customers now'}).isDisabled(),true);
        await toggle.check();
        await page.getByRole('button',{name:'Save settings',exact:true}).click();
        await page.getByText('Automatic campaigns enabled for this branch.',{exact:true}).waitFor();
        await page.getByRole('button',{name:'Evaluate eligible customers now'}).click();
        await page.getByRole('status').filter({hasText:'1 message queued'}).waitFor();assert.equal(evaluations,1);
        await page.getByRole('combobox',{name:'Branch',exact:true}).selectOption('2');
        await page.getByRole('button',{name:'Evaluate eligible customers now'}).waitFor();
        assert.equal(await toggle.isChecked(),false);
        await page.reload();await toggle.waitFor();
        assert.equal(await page.getByRole('combobox',{name:'Branch',exact:true}).inputValue(),'2');
        assert.equal(await toggle.isChecked(),false);
        assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
        assert.deepEqual(errors,[]);await context.close();
    }
    console.log('Smart notifications admin: enable, evaluate, branch isolation and 320/390/1280px checks passed');
}finally{await browser.close();}
