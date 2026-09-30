// A backlog code (M-1, U-3, X-12.4) never reaches the user (vsc_AgentFarm CONVENTIONS, "A backlog code
// never reaches the user"). Codes belong in comments, commit bodies and the CHANGELOG's source notes,
// never in a string the app shows. Swept over strings.xml values and every Kotlin string literal.
'use strict';
const { read, walk, suite } = require('./lib');

const CODE = /\b[A-Z]{1,2}-\d+(?:\.\d+)*\b/;
const ALLOW = new Set([]); // the user's own data only, never a label

const t = suite();

// The detector bites, and does not fire on ordinary tokens.
t.ok(CODE.test('Bitti (' + 'U-' + '3)'), 'a backlog code is detected');
t.ok(!CODE.test('UTF-8 metin'), 'UTF-8 is not a code');
t.ok(!CODE.test('Sürüm 0.1.0-b7'), 'a version label is not a code');

function stripComments(src) {
    return src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:"])\/\/.*$/gm, '$1');
}

const kotlin = walk('app/src/main', ['.kt']);
t.ok(kotlin.length > 0, 'Kotlin sources are found under app/src/main');
for (const f of kotlin) {
    for (const m of stripComments(read(f)).matchAll(/"((?:[^"\\\n]|\\.)*)"/g)) {
        const s = m[1];
        t.ok(ALLOW.has(s) || !CODE.test(s), `${f}: string "${s}" carries a backlog code`);
    }
}

for (const f of walk('app/src/main/res', ['.xml']).filter(p => /\/values[^/]*\//.test(p))) {
    for (const m of read(f).matchAll(/<string[^>]*>([\s\S]*?)<\/string>/g)) {
        t.ok(!CODE.test(m[1]), `${f}: string "${m[1]}" carries a backlog code`);
    }
}

t.done();
