const { test } = require('node:test');
const assert = require('node:assert/strict');
const { releasePlan, checksReady } = require('./release-version.cjs');
const tag = (name, sha = 'older') => ({ name, commit: { sha } });

test('first production release starts at v1.0.0', () => {
  assert.deepEqual(releasePlan([], 'current'), { tag: 'v1.0.0', existing: false });
});
test('versions sort numerically and ignore unrelated or prerelease tags', () => {
  assert.deepEqual(releasePlan([tag('v1.0.9'), tag('v1.0.10'), tag('qa'), tag('v2.0.0-rc.1')], 'current'),
    { tag: 'v1.0.11', existing: false });
  assert.equal(releasePlan([tag('v2.3.0'), tag('v1.9.99')], 'current').tag, 'v2.3.1');
});
test('rerun reuses the tag for this exact commit', () => {
  assert.deepEqual(releasePlan([tag('v1.0.0', 'current'), tag('v1.0.1')], 'current'),
    { tag: 'v1.0.0', existing: true });
});
test('unsafe version cannot silently produce duplicate numbering', () => {
  assert.throws(() => releasePlan([tag('v1.0.9007199254740992')], 'current'));
});
const run = (name, options = {}) => ({ name, id: 1, head_sha: 'current', head_branch: 'main',
  event: 'push', status: 'completed', conclusion: 'success', ...options });
test('release requires successful push verification for the exact main commit', () => {
  assert.equal(checksReady([], 'current'), false);
  assert.equal(checksReady([run('Build and verify')], 'current'), true);
  for (const options of [{ head_sha: 'other' }, { head_branch: 'dev' }, { event: 'pull_request' },
    { conclusion: 'failure' }, { status: 'in_progress' }]) {
    assert.equal(checksReady([run('Build and verify', options)], 'current'), false);
  }
});
test('waits for applicable printer workflow and respects latest failed verification', () => {
  assert.equal(checksReady([run('Build and verify'), run('Verify local print agent', { status: 'in_progress' })], 'current'), false);
  assert.equal(checksReady([run('Build and verify'), run('Verify local print agent')], 'current'), true);
  assert.equal(checksReady([run('Build and verify'), run('Build and verify', { id: 2, conclusion: 'failure' })], 'current'), false);
});
