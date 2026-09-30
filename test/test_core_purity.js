// core/ is pure Kotlin: no Android, no Compose, no other layer of this app. That is what lets it be
// tested on the JVM in seconds and read by any screen without dragging a platform in with it
// (the vsc_AgentFarm src/core rule). A platform import here fails the gate.
'use strict';
const { read, walk, suite } = require('./lib');

const CORE = 'app/src/main/java/com/muvusoft/agentfarm/core';
const FORBIDDEN = [
    [/^import\s+android\./m, 'an android.* import'],
    [/^import\s+androidx\./m, 'an androidx.* import'],
    [/^import\s+com\.google\.android\./m, 'a com.google.android.* import'],
    [/^import\s+com\.muvusoft\.agentfarm\.(?!core\b)/m, 'an import from another layer of this app'],
];

const t = suite();

// The detector bites.
t.ok(FORBIDDEN[1][0].test('package x\nimport androidx.compose.ui.Modifier\n'), 'an androidx import is detected');

const files = walk(CORE, ['.kt']);
t.ok(files.length > 0, `${CORE} holds at least one file`);
for (const f of files) {
    const src = read(f);
    t.ok(/^package com\.muvusoft\.agentfarm\.core\b/m.test(src), `${f}: package is com.muvusoft.agentfarm.core`);
    for (const [re, what] of FORBIDDEN) {
        t.ok(!re.test(src), `${f}: ${what}`);
    }
}

t.done();
