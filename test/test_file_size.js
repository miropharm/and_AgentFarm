// Anti-monolith: a source file stays focused. Target under 300 lines, hard limit 500.
// Over 300 is reported so the next change extracts before it grows; over 500 fails.
'use strict';
const { read, walk, suite } = require('./lib');

const TARGET = 300;
const LIMIT = 500;
const t = suite();

const files = [
    ...walk('app/src', ['.kt', '.kts']),
    ...walk('scripts', ['.sh']),
    ...walk('test', ['.js']),
    ...walk('tools', ['.js']),
];
t.ok(files.length > 0, 'source files are found');
for (const f of files) {
    const lines = read(f).split('\n').length;
    if (lines > TARGET && lines <= LIMIT) { console.log(`  note ${f}: ${lines} lines (target ${TARGET}) - extract before adding`); }
    t.ok(lines <= LIMIT, `${f}: ${lines} lines, limit ${LIMIT}`);
}

t.done();
