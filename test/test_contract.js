// The shell <-> host contract is Agent Farm's (vsc_AgentFarm/src/remote/contract); this repo carries a
// versioned copy in contract/. The copy must parse, name the version its file name promises, give every
// frame and event an example, and be described by docs/PROTOKOL-VE-MIMARI.md - every frame kind and
// event type read from the JSON, never from a list written here. When the owner repo sits beside this
// one (a developer machine), the copy must be byte-identical to it; CI has no sibling and says so.
'use strict';
const fs = require('fs');
const path = require('path');
const { ROOT, read, suite } = require('./lib');
const { sync, NAME } = require('../tools/sync_contract');

const t = suite();
const dir = path.join(ROOT, 'contract');
const files = fs.existsSync(dir) ? fs.readdirSync(dir).filter(f => NAME.test(f)).sort() : [];
t.ok(files.length > 0, 'contract/ carries at least one remote-contract.vN.json');

const doc = read('docs/PROTOKOL-VE-MIMARI.md');
const SENDERS = ['host', 'client', 'either'];

for (const f of files) {
    let c = null;
    try { c = JSON.parse(read('contract/' + f)); } catch (e) { t.ok(false, `${f} parses: ${e.message}`); continue; }
    const n = Number(/v(\d+)\.json$/.exec(f)[1]);
    t.ok(c.contract === 'agentfarm.remote', `${f} names the contract agentfarm.remote`);
    t.ok(c.version === n, `${f} declares version ${n} (found ${c.version})`);
    t.ok(Array.isArray(c.scopes) && c.scopes.length > 0, `${f} lists scopes`);
    t.ok(Array.isArray(c.features) && c.features.length > 0, `${f} lists features`);
    t.ok(String(c.transport && c.transport.signature).includes(`agentfarm.remote/${n}|`), `${f} spells the signed text for version ${n}`);

    for (const [kind, s] of Object.entries(c.frames || {})) {
        t.ok(SENDERS.includes(s.from), `${f} frame ${kind} names its sender`);
        t.ok(s.fields && typeof s.fields === 'object', `${f} frame ${kind} declares fields`);
        t.ok(s.example && s.example.kind === kind, `${f} frame ${kind} has an example of its own kind`);
        t.ok(doc.includes('`' + kind + '`'), `docs/PROTOKOL-VE-MIMARI.md describes frame ${kind}`);
    }
    for (const [type, s] of Object.entries(c.events || {})) {
        t.ok(s.fields && s.example, `${f} event ${type} declares fields and an example`);
        t.ok(doc.includes('`' + type + '`'), `docs/PROTOKOL-VE-MIMARI.md describes event ${type}`);
    }
    t.ok(doc.includes('contract/' + f), `docs/PROTOKOL-VE-MIMARI.md points to contract/${f}`);
}

const r = sync({ check: true });
if (r.owner) {
    t.ok(r.differ.length === 0, `contract/ matches its owner (differ: ${r.differ.join(', ') || 'none'}) - run node tools/sync_contract.js`);
    t.ok(r.files.every(f => files.includes(f)), 'every owner contract version has a copy here');
} else {
    console.log('  note: vsc_AgentFarm is not beside this repo - owner comparison skipped');
}

t.done();
