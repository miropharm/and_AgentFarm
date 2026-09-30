// The repo is public: nothing credential-shaped may be tracked, and the local key folder stays out.
// The staged-content half runs in .githooks/pre-commit (tools/scan_staged.js); this half sweeps
// every TRACKED file, so a key that got in before the hook existed is still found.
'use strict';
const { execFileSync } = require('child_process');
const { read, exists, suite } = require('./lib');
const { TEXT_FILE, credentialFindings } = require('../tools/credentialShapes');

const t = suite();

// The detector bites: a private-key header is found, a planted CANARY is not.
const header = '-----BEGIN ' + 'PRIVATE KEY-----';
t.ok(credentialFindings('x ' + header + ' y').length === 1, 'a private key header is detected');
t.ok(credentialFindings('gh' + 'p_' + 'B'.repeat(36)).length === 1, 'a token-shaped value is detected');
t.ok(credentialFindings('gh' + 'p_' + 'CANARY' + 'A'.repeat(30)).length === 0, 'a CANARY inside the match is ignored');
t.ok(credentialFindings('see AIza... in the docs').length === 0, 'a doc placeholder is not a finding');

// Every tracked text file is clean.
let tracked = [];
try {
    tracked = execFileSync('git', ['ls-files', '-z'], { cwd: require('./lib').ROOT, encoding: 'utf8' })
        .split('\0').filter(Boolean);
} catch { /* not a repository: nothing is tracked */ }
t.ok(tracked.length > 0, 'git ls-files lists the tracked files');
for (const f of tracked.filter(n => TEXT_FILE.test(n))) {
    if (!exists(f)) { continue; }
    const kinds = credentialFindings(read(f));
    t.ok(kinds.length === 0, `${f} holds ${kinds.join(', ')}`);
}

// The local key folder and the shared screenshots never reach the public repo.
const ignore = read('.gitignore');
t.ok(/^docs\/Keys\/$/m.test(ignore), '.gitignore keeps docs/Keys/ out');
t.ok(/^docs\/Screenshots\/$/m.test(ignore), '.gitignore keeps docs/Screenshots/ out');

// The commit hook runs the staged scan.
t.ok(/node tools\/scan_staged\.js/.test(read('.githooks/pre-commit')), 'pre-commit runs tools/scan_staged.js');

t.done();
