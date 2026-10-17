// Whether an instrumentation run failed ONLY because journey steps took longer than their time targets
// (androidTest Journey.kt writes such a step as "<step>: N ms past T ms"). On the CI emulator that is as much the
// emulator's own frame floor as the app (docs/test-plan.md), so it is reported, not a reason to hold the phone's
// build back; any other failure - a look, a check, the economy, another test, a crash - is.
// Usage: node scripts/ci/timing-only.js <instrumentation.txt>  -> exit 0 and the late steps, else exit 1 and why.
'use strict';
const fs = require('fs');

const PREFIX = 'java.lang.AssertionError: ';
const TIMING = /^.+: \d+ ms past \d+ ms$/;

function classify(text) {
    const lines = text.replace(/\r/g, '').split('\n');
    const run = /^Tests run: (\d+),\s+Failures: (\d+)/m.exec(text.replace(/\r/g, ''));
    if (!run) return { timingOnly: false, why: 'no test summary: the run did not finish' };
    const failures = Number(run[2]);
    if (failures === 0) return { timingOnly: false, why: 'no failure was counted' };
    const steps = [];
    let blocks = 0;
    for (let i = 0; i < lines.length; i++) {
        const head = /^\d+\) (\S+)\((\S+)\)$/.exec(lines[i]);
        if (!head) continue;
        blocks++;
        const test = head[2].split('.').pop() + '.' + head[1];
        const first = lines[i + 1] || '';
        if (!first.startsWith(PREFIX)) return { timingOnly: false, why: `${lines[i]}: ${first}` };
        const message = [first.slice(PREFIX.length)];
        for (let j = i + 2; j < lines.length && lines[j] !== '' && !/^\s+at /.test(lines[j]); j++) message.push(lines[j]);
        for (const m of message) {
            if (!TIMING.test(m)) return { timingOnly: false, why: `${test}: ${m}` };
            steps.push(`${test}: ${m}`);
        }
    }
    if (blocks !== failures) return { timingOnly: false, why: `${failures} failures counted, ${blocks} read` };
    return { timingOnly: true, steps };
}

module.exports = { classify };

if (require.main === module) {
    const r = classify(fs.readFileSync(process.argv[2], 'utf8'));
    console.log(r.timingOnly ? r.steps.join('\n') : r.why);
    process.exit(r.timingOnly ? 0 : 1);
}
