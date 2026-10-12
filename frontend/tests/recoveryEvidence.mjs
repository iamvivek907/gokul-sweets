import {mkdir} from 'node:fs/promises';
import {join} from 'node:path';

// Only CI/evidence runs record traces. Successful contexts discard them; failures
// retain the exact DOM/network/timer sequence before the browser is closed.
export async function recoveryContext(browser, options) {
    const context=await browser.newContext(options);
    if(process.env.SCREENSHOT_DIR)await context.tracing.start({screenshots:true,snapshots:true,sources:true});
    return context;
}
export async function recoveryFailure(browser, name) {
    const directory=process.env.SCREENSHOT_DIR;
    if(!directory)return;
    await mkdir(directory,{recursive:true});
    for(const [index,context] of browser.contexts().entries()) {
        for(const page of context.pages())console.error(`${name}: failure URL ${page.url()}`);
        try {await context.tracing.stop({path:join(directory,`${name}-${Date.now()}-${index}.zip`)});}
        catch(error){console.error('Unable to save recovery trace:',error.message);}
    }
}
