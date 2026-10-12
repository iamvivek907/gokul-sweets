const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const read = relative => fs.readFileSync(path.join(__dirname, '..', relative), 'utf8');

test('the customer shell owns one shared navigation and scopes the visual system', () => {
    const shell = read('components/layout/AppShell.tsx');
    const styles = read('components/layout/futuristic-storefront.css');
    assert.match(shell, /import Header\s+from\s+["']\.\/Header["']/);
    assert.match(shell, /import BottomNavigation\s+from\s+["']\.\/BottomNavigation["']/);
    assert.equal((shell.match(/<Header\s*\/>/g) || []).length, 1);
    assert.equal((shell.match(/<BottomNavigation\s*\/>/g) || []).length, 1);
    assert.match(shell, /import ["']\.\/futuristic-storefront\.css["']/);
    assert.match(shell, /features\?\.futuristicStorefrontV2 === true/);
    assert.match(shell, /features\?\.checkoutExperienceV2 === true/);
    assert.match(shell, /futuristic \? "future-storefront" : ""/);
    assert.match(styles, /\.future-storefront \.customer-mobile-drawer/);
    assert.match(styles, /\.future-storefront \.customer-bottom-navigation/);
    assert.match(styles, /prefers-reduced-motion/);
    assert.match(styles, /focus-visible/);
});
