const assert = require('node:assert/strict');
const {test} = require('node:test');
const {mkdtempSync, mkdirSync, writeFileSync, rmSync} = require('node:fs');
const {tmpdir} = require('node:os');
const {join, dirname} = require('node:path');
const {getRootDirs} = require(join(dirname(require.resolve('@next/eslint-plugin-next')), 'utils/get-root-dirs.js'));
const {ESLint} = require('eslint');

function fixture(t) {
    const root = mkdtempSync(join(tmpdir(), 'gokul-next-lint-'));
    t.after(() => rmSync(root, {recursive: true, force: true}));
    for (const name of ['customer', 'staff']) mkdirSync(join(root, name, 'app', 'orders'), {recursive: true});
    writeFileSync(join(root, 'customer', 'app', 'orders', 'page.tsx'), 'export default function Page() { return null; }');
    mkdirSync(join(root, 'customer', 'pages'));
    writeFileSync(join(root, 'customer', 'pages', 'orders.tsx'), 'export default function Page() { return null; }');
    writeFileSync(join(root, 'not-a-directory.txt'), 'not a Next.js root');
    return root;
}

test('Next lint root discovery preserves directory-only glob, brace, array and Windows-path handling', t => {
    const root = fixture(t);
    const find = rootDir => getRootDirs({cwd: root, settings: {next: {rootDir}}}).sort();
    const expected = ['customer', 'staff'].map(name => join(root, name)).sort();
    assert.deepEqual(find(`${root}/*`), expected);
    assert.deepEqual(find(`${root}/{customer,staff}`), expected);
    assert.deepEqual(find([`${root}/customer`, `${root}/staff`, `${root}/missing`]), expected);
    assert.deepEqual(find(`${root}/*`.replaceAll('/', '\\')), expected);
    assert.deepEqual(find(`${root}/missing*`), []);
});

test('Next lint still rejects internal HTML links in a glob-discovered Next project', async t => {
    const root = fixture(t);
    const eslint = new ESLint({overrideConfig: [{settings: {next: {rootDir: `${root}/customer*`}}, rules: {'@next/next/no-html-link-for-pages': 'error'}}]});
    const [result] = await eslint.lintText('export default function Page() { return <a href="/orders">Orders</a>; }',
        {filePath: join(process.cwd(), 'components', 'LintRootDiscoveryFixture.tsx')});
    assert.equal(result.messages.filter(message => message.ruleId === '@next/next/no-html-link-for-pages').length, 1);
});
