// Every frame kind, event type and pairing body in contract/ has a Kotlin model in core/contract, with
// every declared field under the same name - optional in the contract (`type?`) exactly when nullable in
// Kotlin. Kinds, types and fields are read from the JSON; the models are read from the source. The JVM
// test (ContractExamplesTest) decodes the examples; this one fails in seconds, before any build.
'use strict';
const { read, walk, suite } = require('./lib');

const t = suite();
const DIR = 'app/src/main/java/com/muvusoft/agentfarm/core/contract';
const src = walk(DIR, ['.kt']).map(read).join('\n');
const c = JSON.parse(read('contract/remote-contract.v1.json'));

/** Constructor parameters of `data class Name(...)`: { name: { type, nullable } } or null. */
function params(className) {
    const m = new RegExp(`data class ${className}\\(([\\s\\S]*?)\\)\\s*(?::|\\n)`).exec(src);
    if (!m) { return null; }
    const out = {};
    const re = /\bval\s+(\w+)\s*:\s*([\w<>, ?.]+?)(?=\s*=|\s*,\s*(?:val\b|$)|\s*,?\s*$)/gm;
    let p;
    while ((p = re.exec(m[1]))) { out[p[1]] = { type: p[2].trim(), nullable: p[2].trim().endsWith('?') }; }
    return out;
}

function classForSerialName(name) {
    const m = new RegExp(`@SerialName\\("${name.replace(/\./g, '\\.')}"\\)\\s*data class (\\w+)`).exec(src);
    return m ? m[1] : null;
}

function classForEvent(type) {
    const m = new RegExp(`"${type.replace(/\./g, '\\.')}"\\s+to\\s+(\\w+)\\.serializer\\(\\)`).exec(src);
    return m ? m[1] : null;
}

function checkFields(what, className, fields) {
    t.ok(!!className, `${what} has a Kotlin model`);
    if (!className) { return; }
    const ps = params(className);
    t.ok(!!ps, `${what}: data class ${className} found`);
    if (!ps) { return; }
    for (const [name, spec] of Object.entries(fields)) {
        const optional = spec.endsWith('?');
        t.ok(!!ps[name], `${what}: ${className} carries ${name}`);
        if (ps[name]) { t.ok(ps[name].nullable === optional, `${what}: ${className}.${name} is ${optional ? '' : 'not '}nullable`); }
    }
    for (const name of Object.keys(ps)) {
        t.ok(name in fields, `${what}: ${className}.${name} is declared by the contract`);
    }
}

// The reader bites.
t.ok(JSON.stringify(Object.keys(params('Welcome') || {})) === JSON.stringify(['farm', 'device', 'scope', 'features', 'lastSeq', 'session']), 'the parser reads a multi-line constructor with a generic type');
t.ok(classForSerialName('no.such.kind') === null, 'an unknown kind finds no model');

for (const [kind, spec] of Object.entries(c.frames)) {
    checkFields(`frame ${kind}`, classForSerialName(kind), spec.fields);
}
for (const [type, spec] of Object.entries(c.events)) {
    checkFields(`event ${type}`, classForEvent(type), spec.fields);
}
checkFields('pair.request', 'PairRequest', c.pairing.request.fields);
checkFields('pair.response', 'PairResponse', c.pairing.response.fields);

const version = /const val CONTRACT_VERSION = (\d+)L/.exec(src);
t.ok(version && Number(version[1]) === c.version, `CONTRACT_VERSION in Kotlin is the copy's version (${c.version})`);

t.done();
