// ESLint's older plugins call the module as a function. Use the patched engine,
// retaining that interface and its named helpers without vendoring the old parser.
// eslint-disable-next-line @typescript-eslint/no-require-imports -- Legacy lint plugins load this CommonJS adapter.
const core = require('minimatch-core');
const api = core.minimatch;
function minimatch(path, pattern, options) { return api(path, pattern, options); }
Object.assign(minimatch, api, core);
minimatch.defaults = options => api.defaults(options);
module.exports = minimatch;
module.exports.minimatch = api;
module.exports.Minimatch = core.Minimatch;
module.exports.filter = core.filter ?? api.filter;
module.exports.match = core.match ?? api.match;
// The import plugin only calls .test(). Keep that shape without recreating
// the regular-expression compiler removed by the patched engine.
module.exports.makeRe = (pattern, options) => {
  const matcher = new core.Minimatch(pattern, options);
  return {test: path => matcher.match(path)};
};
module.exports.braceExpand = core.braceExpand ?? api.braceExpand;
module.exports.GLOBSTAR = core.GLOBSTAR ?? api.GLOBSTAR;
module.exports.escape = core.escape;
module.exports.unescape = core.unescape;
