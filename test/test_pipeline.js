// The cloud pipeline's load-bearing shape. Each clause below was a real defect or a real decision:
// a later push cancelled a [testlab]/[deliver] run; delivery must never skip the emulator gate;
// the repo gate must run in the cloud too, not only in a local hook someone can skip.
'use strict';
const { read, exists, suite } = require('./lib');

const t = suite();
const yml = read('.github/workflows/android.yml');

function job(name) {
    const start = yml.search(new RegExp('^  ' + name + ':$', 'm'));
    if (start < 0) { return ''; }
    const rest = yml.slice(start + 1);
    const next = rest.search(/^  [a-z][\w-]*:$/m);
    return next < 0 ? yml.slice(start) : yml.slice(start, start + 1 + next);
}

t.ok(/cancel-in-progress:\s*false/.test(yml), 'a running pipeline is never cancelled by a newer push');
t.ok(/paths-ignore:.*docs\/\*\*/.test(yml), 'docs-only changes do not trigger a build');

const build = job('build');
t.ok(/node test\/run-all\.js/.test(build), 'the build job runs the repo gate (node test/run-all.js)');
const gate = build.indexOf('node test/run-all.js');
const gradle = build.indexOf('gradle --no-daemon');
t.ok(gate >= 0 && gradle >= 0 && gate < gradle, 'the repo gate runs before the Gradle build');

const deliver = job('deliver');
t.ok(/needs:\s*\[\s*build\s*,\s*emulator\s*\]/.test(deliver), 'deliver waits for the emulator gate');
t.ok(/contains\(github\.event\.head_commit\.message, '\[deliver\]'\)/.test(deliver), 'deliver runs only on [deliver]');
t.ok(/contains\(github\.event\.head_commit\.message, '\[testlab\]'\)/.test(job('testlab')), 'testlab runs only on [testlab]');

for (const m of yml.matchAll(/bash (scripts\/ci\/[\w-]+\.sh)/g)) {
    t.ok(exists(m[1]), `${m[1]} referenced by the workflow exists`);
}
for (const m of yml.matchAll(/\$\{\{\s*secrets\.(\w+)\s*\}\}/g)) {
    t.ok(m[1] === 'GCP_SA_KEY', `secret ${m[1]} is the only declared secret (GCP_SA_KEY)`);
}

t.done();
