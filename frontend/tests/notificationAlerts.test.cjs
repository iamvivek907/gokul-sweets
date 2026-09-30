const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const ts = require('typescript');
const vm = require('node:vm');
const path = require('node:path');
const compiled = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../lib/notificationAlerts.ts'), 'utf8'), {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText;
function load(context = {}) {
    const exports = {};
    vm.runInNewContext(compiled, {exports, Date, Intl, Number, String, Uint8Array, atob, ...context});
    return exports;
}
test('quiet hours follow India midnight and exact boundaries regardless of runner timezone', () => {
    const {inQuietHours, minuteToTime, timeToMinute} = load();
    const settings = {quietHoursEnabled: true, quietStartMinute: 1320, quietEndMinute: 480};
    for (const [instant, quiet] of [['2026-09-30T16:29:59Z', false], ['2026-09-30T16:30:00Z', true],
        ['2026-09-30T18:30:00Z', true], ['2026-10-01T02:29:59Z', true], ['2026-10-01T02:30:00Z', false]])
        assert.equal(inQuietHours(settings, new Date(instant)), quiet);
    assert.equal(inQuietHours({...settings, quietHoursEnabled: false}, new Date('2026-09-30T18:30:00Z')), false);
    assert.equal(minuteToTime(480), '08:00'); assert.equal(timeToMinute('22:00'), 1320);
});
test('sound requires explicit activation and remains silent when blocked or the page is hidden', async () => {
    let played = 0;
    const document = {visibilityState: 'visible'};
    class AudioContext {
        state = 'suspended'; currentTime = 0; destination = {};
        async resume() {this.state = 'running';}
        createGain() {return {gain: {setValueAtTime() {}, exponentialRampToValueAtTime() {}}, connect() {}};}
        createOscillator() {return {frequency: {}, connect() {}, start() {played++;}, stop() {}};}
    }
    const sound = load({window: {AudioContext}, AudioContext, document});
    assert.equal(sound.playChime(), false);
    await sound.activateChime(); assert.equal(played, 1);
    document.visibilityState = 'hidden'; assert.equal(sound.playChime(), false);
    class BlockedAudio extends AudioContext {async resume() {throw new Error('Autoplay blocked');}}
    const blocked = load({window: {AudioContext: BlockedAudio}, AudioContext: BlockedAudio, document});
    await assert.rejects(blocked.activateChime()); assert.equal(blocked.playChime(), false);
});
