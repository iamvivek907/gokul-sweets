const {globSync: find} = require('tinyglobby');
const {isAbsolute} = require('node:path');

// Next's sole fast-glob call discovers roots with globSync(string, {onlyDirectories: true}).
// Preserve fast-glob's non-recursive directory matches and absolute-pattern output.
exports.globSync = (pattern, options) => find(pattern, {
    ...options, expandDirectories: false, absolute: options?.absolute ?? isAbsolute(pattern)
}).map(match => match.length > 1 ? match.replace(/\/$/, '') : match);
