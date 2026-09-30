// The release version lives in ONE place (appVersion in app/build.gradle.kts) and every release has a
// CHANGELOG.md entry of the same number, newest first. The phone's release notes are read from that
// entry (scripts/ci/deliver.sh), so a version without an entry would ship a blank note.
'use strict';
const { read, suite } = require('./lib');

const t = suite();
const gradle = read('app/build.gradle.kts');
const log = read('CHANGELOG.md');

const v = /val appVersion = "(\d+\.\d+\.\d+)"/.exec(gradle);
t.ok(!!v, 'app/build.gradle.kts declares val appVersion = "x.y.z"');
t.ok(/versionName = "\$appVersion-b\$buildNumber"/.test(gradle), 'versionName is built from appVersion and the build number');

const heads = [...log.matchAll(/^## (\S+) - (.+) \((\d{4}-\d{2}-\d{2})\)$/gm)].map(m => ({ ver: m[1], title: m[2], date: m[3] }));
const loose = [...log.matchAll(/^## .*$/gm)].length;
t.ok(heads.length > 0, 'CHANGELOG.md has at least one "## x.y.z - title (YYYY-MM-DD)" entry');
t.ok(heads.length === loose, `every "## " heading has the form "## x.y.z - title (YYYY-MM-DD)" (${heads.length}/${loose})`);
if (v && heads.length) {
    t.ok(heads[0].ver === v[1], `the newest CHANGELOG entry (${heads[0].ver}) is appVersion (${v[1]})`);
}

const num = s => s.split('.').map(Number);
const newer = (a, b) => { const x = num(a), y = num(b); for (let i = 0; i < 3; i++) { if (x[i] !== y[i]) { return x[i] > y[i]; } } return false; };
for (let i = 1; i < heads.length; i++) {
    t.ok(newer(heads[i - 1].ver, heads[i].ver), `${heads[i - 1].ver} is newer than ${heads[i].ver}`);
    t.ok(heads[i - 1].date >= heads[i].date, `${heads[i - 1].ver} is not dated before ${heads[i].ver}`);
}

t.ok(/CHANGELOG\.md/.test(read('scripts/ci/deliver.sh')), 'deliver.sh reads the release notes from CHANGELOG.md');

t.done();
