// Kotlin block comments nest: a path like `/_test/*` inside KDoc opens a second comment and the rest
// of the file becomes comment (CI then fails with "Unclosed comment"). Caught here, in seconds.
'use strict';
const { read, walk, suite } = require('./lib');

const t = suite();

/** Offsets of a `/*` that opens inside an already open block comment (strings are not comments). */
function nested(src) {
    const found = [];
    let depth = 0;
    let inString = false;
    for (let i = 0; i < src.length; i++) {
        const two = src.slice(i, i + 2);
        if (depth === 0 && !inString && two === '//') { i = src.indexOf('\n', i); if (i < 0) { break; } continue; }
        if (depth === 0 && src[i] === '"' && src[i - 1] !== '\\') { inString = !inString; continue; }
        if (inString) { continue; }
        if (two === '/*') { if (depth > 0) { found.push(i); } depth++; i++; continue; }
        if (two === '*/' && depth > 0) { depth--; i++; }
    }
    return found;
}

t.ok(nested('/** a /_test/* b */ x */').length === 1, 'the reader finds a nested opener');
t.ok(nested('/** plain */ val s = "/*" // /* in a line comment').length === 0, 'strings and line comments are not openers');

for (const file of walk('app/src', ['.kt'])) {
    const hits = nested(read(file));
    t.ok(hits.length === 0, `${file}: "/*" inside a block comment (Kotlin nests comments) at offset ${hits.join(', ')}`);
}

t.done();
