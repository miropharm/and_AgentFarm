// Generic Test Runner — runs every test_*.js in this folder sequentially
// and aggregates their "N passed, M failed" summaries. Exit 1 if anything fails.
// Zero external dependencies (pure Node.js).
const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const testDir = __dirname;
const files = fs.readdirSync(testDir).filter(f => /^test_.*\.js$/.test(f) && f !== path.basename(__filename)).sort();

if (files.length === 0) {
    console.log('No test files matching test_*.js found in ' + testDir);
    process.exit(0);
}

let totalPass = 0, totalFail = 0;
const failed = [];
const t0 = Date.now();

console.log(`Running ${files.length} test suite(s)...`);

for (const f of files) {
    const filePath = path.join(testDir, f);
    const r = spawnSync(process.execPath, [filePath], {
        encoding: 'utf8',
        timeout: 60000
    });
    const out = (r.stdout || '') + (r.stderr || '');
    const m = /(\d+)\s+passed,\s+(\d+)\s+failed/i.exec(out);
    const pass = m ? Number(m[1]) : 0;
    const fail = m ? Number(m[2]) : 0;
    totalPass += pass;
    totalFail += fail;

    if (r.status !== 0 || !m || fail > 0) {
        failed.push(f);
        console.log(`\x1b[31mFAIL\x1b[0m ${f}${r.error ? ' — ' + r.error.message : ''}`);
        console.log(out.trim().split('\n').map(l => '    ' + l).join('\n'));
    } else {
        console.log(`\x1b[32mok\x1b[0m   ${f} (${pass} passed)`);
    }
}

const elapsedMs = Date.now() - t0;
console.log('----------------------------------------------------');
if (failed.length > 0) {
    console.log(`\x1b[31mFAILED\x1b[0m: ${failed.length} suite(s) failed (${totalPass} passed, ${totalFail} failed) in ${elapsedMs}ms`);
    console.log(`Failed files: ${failed.join(', ')}`);
    process.exit(1);
} else {
    console.log(`\x1b[32mPASSED\x1b[0m: All ${files.length} suite(s) green (${totalPass} assertions passed, 0 failed) in ${elapsedMs}ms`);
    process.exit(0);
}
