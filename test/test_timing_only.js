// The emulator run fails on anything but a journey step past its time target (scripts/ci/timing-only.js): read
// against the instrumentation output's real shape, every other failure keeps the run red.
'use strict';
const { classify } = require('../scripts/ci/timing-only');
const { read, suite } = require('./lib');

const t = suite();
const at = '\tat com.muvusoft.agentfarm.JourneyNowTest.journeys(JourneyNowTest.kt:43)';
const run = (blocks, n = blocks.length) => [
    'com.muvusoft.agentfarm.AppLanguageE2ETest:.', '', `There ${n === 1 ? 'was' : 'were'} ${n} failure${n === 1 ? '' : 's'}:`,
    ...blocks.flatMap((b, i) => [`${i + 1}) ${b[0]}(com.muvusoft.agentfarm.JourneyNowTest)`, ...b.slice(1), at]),
    '', 'FAILURES!!!', `Tests run: 62,  Failures: ${n}`, '',
].join('\r\n');

const late = classify(run([
    ['conversationOnTheLan', 'java.lang.AssertionError: send: your words at once: 359 ms past 200 ms', 'the next instruction from the same screen: 201 ms past 200 ms'],
    ['nowOnMobileData', 'java.lang.AssertionError: reopen: the copy at once: 1089 ms past 500 ms'],
]));
t.ok(late.timingOnly && late.steps.length === 3, 'steps past their time target only: the run is not held back, each late step is listed');
t.ok(late.timingOnly && late.steps[0] === 'JourneyNowTest.conversationOnTheLan: send: your words at once: 359 ms past 200 ms', 'a late step is named with its test');

const look = classify(run([['nowOnTheLan', 'java.lang.AssertionError: reopen: the copy at once: 300 ms past 200 ms', 'open: java.lang.AssertionError: no node with text "Running"']]));
t.ok(!look.timingOnly && /Running/.test(look.why), 'a failed look beside a late step keeps the run red');
t.ok(!classify(run([['nowOnTheLan', 'java.lang.AssertionError: economy: sessions.recent past a page']])).timingOnly, 'an economy finding keeps it red');
t.ok(!classify(run([['pairs', 'java.lang.IllegalStateException: boom']])).timingOnly, 'another kind of failure keeps it red');
t.ok(!classify(run([['nowOnTheLan', 'java.lang.AssertionError: reopen: 300 ms past 200 ms']], 2)).timingOnly, 'a failure that cannot be read keeps it red');
t.ok(!classify('INSTRUMENTATION_RESULT: shortMsg=Process crashed.\r\nINSTRUMENTATION_CODE: 0\r\n').timingOnly, 'a crashed run keeps it red');
t.ok(!classify('OK (62 tests)\n').timingOnly, 'a run with nothing counted as failed is not called late');

const sh = read('scripts/ci/emulator-run.sh');
t.ok(/node scripts\/ci\/timing-only\.js "\$OUT\/instrumentation\.txt"/.test(sh), 'emulator-run.sh asks the classifier before it calls a failed run red');
t.ok(/FATAL EXCEPTION[\s\S]*status=1/.test(sh), 'a crash in logcat still fails the run');

t.done();
