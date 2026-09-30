// Shared helpers for the repo gate (node test/run-all.js). Zero dependencies.
'use strict';
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const SKIP_DIRS = new Set(['.git', 'build', '.gradle', '.kotlin', '.idea', 'node_modules', 'Keys', 'Screenshots']);

/** Reads a repo file with line endings normalised: a rule is about shape, never about CRLF. */
function read(rel) {
    return fs.readFileSync(path.join(ROOT, rel), 'utf8').replace(/\r\n/g, '\n');
}

function exists(rel) {
    return fs.existsSync(path.join(ROOT, rel));
}

/** Repo-relative paths (forward slashes) under relDir whose extension is in exts. */
function walk(relDir, exts, out = []) {
    const abs = path.join(ROOT, relDir);
    if (!fs.existsSync(abs)) { return out; }
    for (const e of fs.readdirSync(abs, { withFileTypes: true })) {
        const rel = relDir ? relDir + '/' + e.name : e.name;
        if (e.isDirectory()) {
            if (!SKIP_DIRS.has(e.name)) { walk(rel, exts, out); }
        } else if (exts.includes(path.extname(e.name))) {
            out.push(rel);
        }
    }
    return out;
}

/** A tiny assertion counter printing the "N passed, M failed" line run-all.js reads. */
function suite() {
    let pass = 0;
    let fail = 0;
    return {
        ok(cond, msg) {
            if (cond) { pass++; } else { fail++; console.log('  FAIL ' + msg); }
        },
        done() {
            console.log(`${pass} passed, ${fail} failed`);
            process.exit(fail ? 1 : 0);
        },
    };
}

module.exports = { ROOT, read, exists, walk, suite };
