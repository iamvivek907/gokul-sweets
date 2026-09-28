const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const ts = require("typescript");
const Module = require("node:module");
const path = require("node:path");
const filename = path.resolve(__dirname, "../lib/accountMilestones.ts");
const compiled = ts.transpileModule(fs.readFileSync(filename, "utf8"), {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
});
const loaded = new Module(filename, module);
loaded._compile(compiled.outputText, filename);
const {currentMilestone} = loaded.exports;

test("ticks unlock only at the first, fifth and twentieth paid orders", () => {
    assert.equal(currentMilestone(0), null);
    assert.equal(currentMilestone(1).title, "First visit");
    assert.equal(currentMilestone(4).title, "First visit");
    assert.equal(currentMilestone(5).title, "Regular");
    assert.equal(currentMilestone(19).title, "Regular");
    assert.equal(currentMilestone(20).title, "Gokul favourite");
});
