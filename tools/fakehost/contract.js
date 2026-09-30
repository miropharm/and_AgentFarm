// The contract copy as the fake host reads it: frames and events are checked against the declared
// fields of contract/remote-contract.v<N>.json, the same rule Agent Farm's own checker applies.
'use strict';
const fs = require('fs');
const path = require('path');

function load(version = 1) {
    const file = path.join(__dirname, '..', '..', 'contract', `remote-contract.v${version}.json`);
    return JSON.parse(fs.readFileSync(file, 'utf8'));
}

function typeOk(v, type) {
    if (type === 'any') { return true; }
    if (type === 'array') { return Array.isArray(v); }
    if (type === 'object') { return typeof v === 'object' && v !== null && !Array.isArray(v); }
    return typeof v === type;
}

/** null when `obj` carries every declared field with its type; otherwise the first problem. */
function checkFields(obj, fields, what) {
    if (typeof obj !== 'object' || obj === null || Array.isArray(obj)) { return `${what}: not an object`; }
    for (const [name, spec] of Object.entries(fields)) {
        const optional = spec.endsWith('?');
        const type = optional ? spec.slice(0, -1) : spec;
        const v = obj[name];
        if (v === undefined || v === null) {
            if (!optional) { return `${what}: missing ${name}`; }
            continue;
        }
        if (!typeOk(v, type)) { return `${what}: ${name} is not ${type}`; }
    }
    return null;
}

function checkFrame(c, frame) {
    const kind = frame && frame.kind;
    const shape = typeof kind === 'string' ? c.frames[kind] : undefined;
    if (!shape) { return `frame: unknown kind ${kind}`; }
    const base = checkFields(frame, shape.fields, kind);
    if (base || kind !== 'event') { return base; }
    const ev = c.events[frame.type];
    return ev ? checkFields(frame.data, ev.fields, `event ${frame.type}`) : null;
}

const signedText = (c, farm, device, nonce) => `${c.contract}/${c.version}|${farm}|${device}|${nonce}`;

module.exports = { load, checkFields, checkFrame, signedText };
