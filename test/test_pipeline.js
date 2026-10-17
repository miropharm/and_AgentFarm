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
t.ok(/needs:\s*\[\s*build\s*,\s*emulator\s*,\s*markers\s*\]/.test(deliver), 'deliver waits for the emulator gate');
// A body that named [deliver] while explaining the rule once sent a build to the phone (v1.141): only the title counts.
const markers = job('markers');
t.ok(/head -1/.test(markers) && /MESSAGE: \$\{\{ github\.event\.head_commit\.message \}\}/.test(markers), 'the markers are read from the commit title line only');
t.ok(!/contains\(github\.event\.head_commit\.message/.test(yml), 'no job reads a marker from the whole commit message');
t.ok(/if: needs\.markers\.outputs\.deliver == 'true'/.test(deliver), 'deliver runs only on a [deliver] title');
t.ok(/needs: \[build, markers\]/.test(job('testlab')) && /if: needs\.markers\.outputs\.testlab == 'true'/.test(job('testlab')), 'testlab runs only on a [testlab] title');

// The journeys' timing targets mean the phone the user holds: the tested APK is the delivered one, not debuggable.
const gradleFile = read('app/build.gradle.kts');
t.ok(/testBuildType\s*=\s*"release"/.test(gradleFile), 'instrumentation tests run against the release build');
const release = (gradleFile.match(/^\s*release\s*\{[\s\S]*?^\s*\}/m) || [''])[0];
t.ok(/signingConfig\s*=\s*signingConfigs\.getByName\("debug"\)/.test(release), 'the release build is signed with the fixed key the phone already accepts');
t.ok(release !== '' && !/isDebuggable\s*=\s*true/.test(release), 'the release build is not debuggable');
t.ok(/assembleRelease\b/.test(build) && /assembleReleaseAndroidTest\b/.test(build), 'the build job assembles the release APK and its instrumentation APK');
for (const s of ['emulator-run', 'testlab-run', 'deliver']) {
    const sh = read(`scripts/ci/${s}.sh`);
    t.ok(/app-release\.apk/.test(sh) && !/app-debug/.test(sh), `${s}.sh takes the release APK`);
}

for (const m of yml.matchAll(/bash (scripts\/ci\/[\w-]+\.sh)/g)) {
    t.ok(exists(m[1]), `${m[1]} referenced by the workflow exists`);
}
for (const m of yml.matchAll(/\$\{\{\s*secrets\.(\w+)\s*\}\}/g)) {
    t.ok(m[1] === 'GCP_SA_KEY', `secret ${m[1]} is the only declared secret (GCP_SA_KEY)`);
}

t.done();
