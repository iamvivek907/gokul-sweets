'use strict';

function version(name) {
  const match = /^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.exec(name);
  if (!match) return null;
  const parts = match.slice(1).map(Number);
  if (!parts.every(Number.isSafeInteger)) throw new Error('Release version exceeds safe integer range');
  return parts;
}

function compare(a, b) {
  for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] < b[i] ? -1 : 1;
  return 0;
}

function releasePlan(tags, sha) {
  const stable = tags.map(tag => ({ ...tag, version: version(tag.name) }))
    .filter(tag => tag.version).sort((a, b) => compare(b.version, a.version));
  const existing = stable.find(tag => tag.commit.sha === sha);
  if (existing) return { tag: existing.name, existing: true };
  if (!stable.length) return { tag: 'v1.0.0', existing: false };
  const [major, minor, patch] = stable[0].version;
  if (patch === Number.MAX_SAFE_INTEGER) throw new Error('Release patch version exhausted');
  return { tag: `v${major}.${minor}.${patch + 1}`, existing: false };
}

function checksReady(runs, sha) {
  const latest = new Map();
  for (const run of runs) {
    if (run.head_sha !== sha || run.head_branch !== 'main' || run.event !== 'push') continue;
    if (!latest.has(run.name) || latest.get(run.name).id < run.id) latest.set(run.name, run);
  }
  const build = latest.get('Build and verify');
  if (!build || build.status !== 'completed' || build.conclusion !== 'success') return false;
  const agent = latest.get('Verify local print agent');
  return !agent || agent.status === 'completed' && agent.conclusion === 'success';
}

module.exports = { releasePlan, checksReady };
