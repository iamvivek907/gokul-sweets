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

// Execute the checked-in github-script bodies against the API surface supplied by
// actions/github-script v7. Pure numbering tests cannot detect SDK namespace errors.
const fs = require('node:fs');
const path = require('node:path');
const workflow = fs.readFileSync(path.join(__dirname, '../workflows/production-release.yml'), 'utf8');
const scripts = [...workflow.matchAll(/^          script: \|\n((?:            .*\n?)*)/gm)]
  .map(match => match[1].replace(/^            /gm, ''));
assert.equal(scripts.length, 2);
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
async function executeScript(index, options = {}) {
  const calls = [];
  const outputs = {};
  const method = (name, data) => async args => {
    calls.push({ name, args });
    return { data };
  };
  const github = {
    rest: {
      repos: {
        getBranch: method('getBranch', { commit: { sha: options.mainSha || 'current' } }),
        listTags: async () => options.tags || [],
        getReleaseByTag: async () => {
          if (options.releaseExists) return { data: {} };
          throw Object.assign(new Error('Not found'), { status: options.releaseStatus || 404 });
        },
        generateReleaseNotes: method('generateReleaseNotes', { body: 'Release notes' }),
        createRelease: method('createRelease', {})
      },
      actions: { listWorkflowRunsForRepo: async () => options.runs || [run('Build and verify')] },
      git: {
        createTag: method('createTag', { sha: 'annotated-tag' }),
        createRef: method('createRef', {})
      }
    },
    paginate: async (endpoint, args) => endpoint(args)
  };
  const core = { setOutput: (key, value) => { outputs[key] = value; }, info() {}, notice() {} };
  const context = { repo: { owner: 'owner', repo: 'repo' },
    payload: { workflow_run: { head_sha: 'current', html_url: 'https://example.test/checks' } } };
  await new AsyncFunction('github', 'core', 'context', 'require', scripts[index])(
    github, core, context, () => ({ releasePlan, checksReady }));
  return { calls, outputs };
}
test('candidate script uses github-script v7 REST namespace and checks current main', async () => {
  assert.equal((await executeScript(0)).outputs.current, 'true');
  assert.equal((await executeScript(0, { mainSha: 'newer' })).outputs.current, 'false');
});
test('release script creates an annotated tag and source release for verified main', async () => {
  const { calls } = await executeScript(1);
  assert.deepEqual(calls.map(call => call.name),
    ['getBranch', 'createTag', 'createRef', 'generateReleaseNotes', 'createRelease']);
  assert.equal(calls.find(call => call.name === 'createTag').args.object, 'current');
  assert.equal(calls.find(call => call.name === 'createRef').args.sha, 'annotated-tag');
  const release = calls.find(call => call.name === 'createRelease').args;
  assert.equal(release.tag_name, 'v1.0.0');
  assert.equal(release.target_commitish, 'current');
});
test('release script waits for checks and skips superseded main without writing', async () => {
  assert.deepEqual((await executeScript(1, { runs: [] })).calls, []);
  assert.deepEqual((await executeScript(1, { mainSha: 'newer' })).calls.map(call => call.name), ['getBranch']);
});
test('release rerun reuses existing tag and release; recovers missing release', async () => {
  const tags = [tag('v1.0.0', 'current')];
  assert.deepEqual((await executeScript(1, { tags, releaseExists: true })).calls.map(call => call.name), ['getBranch']);
  assert.deepEqual((await executeScript(1, { tags })).calls.map(call => call.name),
    ['getBranch', 'generateReleaseNotes', 'createRelease']);
});
test('release script propagates API errors other than missing release', async () => {
  await assert.rejects(executeScript(1, { tags: [tag('v1.0.0', 'current')], releaseStatus: 403 }),
    { status: 403 });
});
