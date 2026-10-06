const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const ts = require('typescript');
const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../services/adminMenuImportApi.ts'), 'utf8'),
 {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}}).outputText;
const id = '12345678-1234-1234-1234-123456789abc';
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };
function setup(mode = 'success') {
 let now = 0, sequence = 0;
 const timers = new Map(), storage = new Map(), calls = [];
 const key = 'gokul-menu-import-job:1';
 storage.set(key, JSON.stringify({id, operation: 'IMPORT'}));
 const pending = signal => new Promise((resolve, reject) => {
  if (signal.aborted) reject(signal.reason);
  else signal.addEventListener('abort', () => reject(signal.reason), {once: true});
 });
 const api = {};
 vm.runInNewContext(code, {exports: api, AbortController, DOMException, Error, Date,
  crypto: {randomUUID: () => id},
  FormData: class {append() {}},
  sessionStorage: {getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key)},
  setTimeout: (fn, ms) => { const timer = ++sequence; timers.set(timer, {fn, at: now + ms}); return timer; },
  clearTimeout: timer => timers.delete(timer),
  require: () => ({adminFetch: async (url, authorization, options) => {
   calls.push({url, options});
   if (!url.includes('/jobs/') && !url.includes('/submissions/')) {
    if (mode === 'initial-request-stall') return pending(options.signal);
    if (mode === 'initial-body-stall') return {status: 202, ok: true, json: () => pending(options.signal)};
    if (mode === 'initial-error-body-stall') return {status: 400, ok: false, json: () => pending(options.signal)};
    if (mode === 'sync-body-stall') return {status: 200, ok: true, json: () => pending(options.signal)};
    if (mode === 'sync-success') return {status: 200, ok: true, json: async () => ({valid: true})};
    return {status: 202, ok: true, json: async () => ({id, status: 'QUEUED'})};
   }
   if (mode === 'request-stall') return pending(options.signal);
   if (mode === 'forbidden') return {ok: false, status: 403};
   if (mode === 'submission-not-found') return {ok: false, status: 404};
   if (mode === 'sync-unrecoverable') return {ok: false, status: 409};
   return {ok: true, json: () => mode === 'body-stall' ? pending(options.signal)
    : Promise.resolve({id, status: mode === 'processing' ? 'PROCESSING' : mode === 'failed' ? 'FAILED' : 'SUCCEEDED',
     result: JSON.stringify({productsCreated: 1}), error: 'Row 2, column base_price is invalid.'})};
  }})});
 async function tick(ms) {
  const target = now + ms;
  await flush();
  while (true) {
   const due = [...timers.entries()].filter(([, timer]) => timer.at <= target).sort((a, b) => a[1].at - b[1].at)[0];
   if (!due) break;
   now = due[1].at; timers.delete(due[0]); due[1].fn(); await flush();
  }
  now = target; await flush();
 }
 return {api, storage, calls, timers, tick, key};
}
for (const mode of ['request-stall', 'body-stall']) {
 test(`${mode} stops after eight seconds and retains the stored job`, async () => {
  const f = setup(mode);
  const outcome = f.api.resumeMenuImportJob(1, 'staff').catch(error => error);
  await f.tick(10000);
  const error = await outcome;
  assert.match(error.message, /Resume status check/); assert.match(error.message, new RegExp(id));
  assert.equal(f.calls[0].options.signal.aborted, true);
  assert.equal(f.api.getPendingMenuImportJob(1).id, id); assert.equal(f.timers.size, 0);
 });
}
test('overall deadline aborts repeated processing responses without depending on Date.now', async () => {
 const f = setup('processing'); const outcome = f.api.resumeMenuImportJob(1, 'staff').catch(error => error);
 await f.tick(600000); assert.match((await outcome).message, /ten-minute limit/);
 assert.ok(f.calls.length > 1); assert.equal(f.api.getPendingMenuImportJob(1).id, id); assert.equal(f.timers.size, 0);
});
test('caller abort stops waiting and retains the worker job', async () => {
 const f = setup(), controller = new AbortController();
 const outcome = f.api.resumeMenuImportJob(1, 'staff', controller.signal).catch(error => error);
 await flush(); controller.abort(); await flush();
 assert.match((await outcome).message, /was stopped/); assert.equal(f.calls.length, 0);
 assert.equal(f.timers.size, 0); assert.equal(f.api.getPendingMenuImportJob(1).id, id);
});
test('resume reads the original job without another upload and clears a successful record', async () => {
 const f = setup(); const outcome = f.api.resumeMenuImportJob(1, 'staff');
 await f.tick(2000); const recovered = await outcome;
 assert.equal(recovered.operation, 'IMPORT'); assert.equal(recovered.result.productsCreated, 1);
 assert.equal(f.calls.length, 1); assert.match(f.calls[0].url, new RegExp(`/jobs/${id}$`));
 assert.equal(f.api.getPendingMenuImportJob(1), null); assert.equal(f.timers.size, 0);
});
test('failed validation preserves actionable error and releases recovery record', async () => {
 const f = setup('failed'); const outcome = f.api.resumeMenuImportJob(1, 'staff').catch(error => error);
 await f.tick(2000); assert.match((await outcome).message, /Row 2, column base_price/);
 assert.equal(f.api.getPendingMenuImportJob(1), null); assert.equal(f.timers.size, 0);
});
test('new async uploads persist their operation before polling', async () => {
 const f = setup('request-stall'); f.storage.clear();
 const outcome = f.api.validateMenuImport(1, {}, 'staff').catch(error => error);
 await flush(); assert.equal(f.api.getPendingMenuImportJob(1).operation, 'VALIDATE');
 await f.tick(10000); assert.match((await outcome).message, /Resume status check/); assert.equal(f.calls.length, 2);
});
test('lost permissions clear the stale record so another upload is not blocked', async () => {
 const f = setup('forbidden'); const outcome = f.api.resumeMenuImportJob(1, 'staff').catch(error => error);
 await f.tick(2000); await outcome; assert.equal(f.api.getPendingMenuImportJob(1), null);
});

for (const mode of ['initial-request-stall', 'initial-body-stall', 'sync-body-stall']) {
 test(`${mode} stops at one minute and preserves the pre-upload recovery identifier`, async () => {
  const f = setup(mode); f.storage.clear();
  const outcome = f.api.validateMenuImport(1, {}, 'staff').catch(error => error);
  await flush();
  assert.equal(f.api.getPendingMenuImportJob(1).id, id);
  assert.equal(f.api.getPendingMenuImportJob(1).submission, true);
  await f.tick(60000);
  assert.match((await outcome).message, /one minute.*Resume status check/);
  assert.equal(f.calls[0].options.signal.aborted, true);
  assert.equal(f.timers.size, 0);
 });
}
test('an error response body is also bounded and a rejected submission is cleared', async () => {
 const f = setup('initial-error-body-stall'); f.storage.clear();
 const outcome = f.api.importMenuFile(1, {}, 'staff').catch(error => error);
 await f.tick(60000); assert.match((await outcome).message, /one minute/);
 assert.equal(f.api.getPendingMenuImportJob(1), null); assert.equal(f.timers.size, 0);
});
test('synchronous success clears the provisional recovery record', async () => {
 const f = setup('sync-success'); f.storage.clear();
 const result = await f.api.validateMenuImport(1, {}, 'staff');
 assert.equal(result.valid, true); assert.equal(f.api.getPendingMenuImportJob(1), null);
 assert.equal(f.timers.size, 0);
});
test('lost acknowledgement resumes through the submission alias without uploading again', async () => {
 const f = setup(); f.storage.set(f.key, JSON.stringify({id, operation: 'IMPORT', submission: true}));
 const outcome = f.api.resumeMenuImportJob(1, 'staff'); await f.tick(2000);
 assert.equal((await outcome).result.productsCreated, 1);
 assert.match(f.calls[0].url, new RegExp(`/submissions/${id}$`));
 assert.equal(f.calls.length, 1); assert.equal(f.api.getPendingMenuImportJob(1), null);
});
test('an unacknowledged submission remains recoverable after an initial 404', async () => {
 const f = setup('submission-not-found'); f.storage.set(f.key, JSON.stringify({id, operation: 'IMPORT', submission: true}));
 const outcome = f.api.resumeMenuImportJob(1, 'staff').catch(error => error); await f.tick(2000);
 assert.match((await outcome).message, /Resume status check/);
 assert.equal(f.api.getPendingMenuImportJob(1).submission, true); assert.equal(f.timers.size, 0);
});
test('synchronous unknown outcomes explain checking the menu and release recovery', async () => {
 const f = setup('sync-unrecoverable'); f.storage.set(f.key, JSON.stringify({id, operation: 'IMPORT', submission: true}));
 const outcome = f.api.resumeMenuImportJob(1, 'staff').catch(error => error); await f.tick(2000);
 assert.match((await outcome).message, /synchronous uploads.*Check the menu/);
 assert.equal(f.api.getPendingMenuImportJob(1), null); assert.equal(f.timers.size, 0);
});
test('caller cancellation during submission releases timers and retains recovery', async () => {
 const f = setup('initial-request-stall'); f.storage.clear(); const controller = new AbortController();
 const outcome = f.api.importMenuFile(1, {}, 'staff', controller.signal).catch(error => error);
 await flush(); controller.abort(); await flush(); assert.match((await outcome).message, /was stopped/);
 assert.equal(f.timers.size, 0); assert.equal(f.api.getPendingMenuImportJob(1).submission, true);
});
test('a successful acknowledgement removes the upload timer while long polling uses its own deadline', async () => {
 const f = setup('processing'); f.storage.clear();
 const outcome = f.api.importMenuFile(1, {}, 'staff').catch(error => error);
 await f.tick(600000); assert.match((await outcome).message, /ten-minute limit/);
 assert.equal(f.api.getPendingMenuImportJob(1).submission, undefined);
 assert.equal(f.calls[0].options.signal.aborted, false); assert.equal(f.timers.size, 0);
});
