const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const ts = require('typescript');

test('a Disable timeout leaves a fresh deadline for staff recovery and the inbox', async () => {
    const registrationId = 'd1779266-3814-48cf-8f6d-8c23b832c1c3';
    const registration = {owner: '12', registrationId};
    const deadlines = [];
    const reads = [];
    const restored = [];
    const counts = [];
    let stateIndex = 0;
    let cleanup;
    const mocks = {
        react: {
            useEffect: effect => {cleanup = effect();},
            useRef: current => ({current}),
            useState: initial => {
                const index = stateIndex++;
                return [initial, value => {if (index === 6) counts.push(value);}];
            }
        },
        'react/jsx-runtime': {jsx: () => null, jsxs: () => null},
        'next/navigation': {usePathname: () => '/admin'},
        '@/contexts/AdminAuthContext': {useAdminAuth: () => ({profile: {staffId: 12}, hasPermission: () => true})},
        '@/hooks/usePwaInstall': {usePwaInstall: () => ({isStandalone: false})},
        '@/lib/orderPushBinding': {readOrderPushBinding: async () => registration, switchOrderPushOwner: async () => true},
        '@/services/staffAlertsApi': {
            retryStaffAlertCancellation: async (_staffId, signal) => {
                deadlines[0].abort(new DOMException('Disable timed out', 'TimeoutError'));
                signal.throwIfAborted();
            },
            resolveConfirmedStaffAlertDevice: async () => registrationId,
            supportsStaffPush: () => true,
            syncStaffAlertDevice: async (_push, _staffId, signal, id) => {
                signal.throwIfAborted();
                if (id) restored.push(id);
            },
            confirmStaffAlertDevice: async () => true,
            staffAlertsRequest: async (url, {signal}) => {
                signal.throwIfAborted();
                reads.push(url);
                return url.startsWith('/settings')
                    ? {staffId: 12, enabled: true, pushConfigured: true, deviceActive: false}
                    : {unreadCount: 3};
            }
        }
    };
    const exports = {};
    const source = fs.readFileSync(path.join(__dirname, '../components/admin/StaffNotificationBell.tsx'), 'utf8');
    const {outputText} = ts.transpileModule(source, {
        compilerOptions: {module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022}
    });
    vm.runInNewContext(outputText, {
        exports, require: name => mocks[name] ?? {},
        AbortController, Event, Notification: {permission: 'granted'},
        AbortSignal: {
            any: AbortSignal.any,
            timeout() {
                const deadline = new AbortController();
                deadlines.push(deadline);
                return deadline.signal;
            }
        },
        navigator: {
            onLine: true,
            serviceWorker: {
                async getRegistration() {return {pushManager: {getSubscription: async () => ({})}};},
                addEventListener() {}, removeEventListener() {}
            }
        },
        document: {visibilityState: 'visible', addEventListener() {}, removeEventListener() {}},
        window: {setInterval: () => 1, clearInterval() {}, addEventListener() {}, removeEventListener() {}, dispatchEvent() {}}
    });
    exports.default();
    try {
        await new Promise(resolve => setImmediate(resolve));
        assert.equal(deadlines.length, 2);
        assert.deepEqual(reads, [`/settings?deviceId=${registrationId}`, '']);
        assert.deepEqual(restored, [registrationId]);
        assert.deepEqual(counts, [3]);
    } finally {
        cleanup?.();
    }
});
