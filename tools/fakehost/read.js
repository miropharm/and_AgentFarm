// sessions.read's page, as Agent Farm cuts it (vsc src/core/sessionRead.ts readTranscriptPage, ported line
// for line): the scripted farm pages a 2,000-message conversation exactly the way the real farm does.
'use strict';

const READ_ROLES = ['user', 'assistant', 'tool', 'toolResult', 'system'];
const DEFAULT_READ_ROLES = ['user', 'assistant'];
const ROLE_ALIASES = {
    user: 'user', human: 'user', prompt: 'user',
    assistant: 'assistant', agent: 'assistant', model: 'assistant',
    tool: 'tool', tool_use: 'tool', call: 'tool',
    toolresult: 'toolResult', tool_result: 'toolResult', result: 'toolResult',
    system: 'system',
};
const READ_LIMIT_DEFAULT = 50;
const READ_LIMIT_MAX = 500;
const READ_MAX_CHARS_DEFAULT = 4000;

function readRoles(raw) {
    const words = (Array.isArray(raw) ? raw : typeof raw === 'string' ? raw.split(',') : [])
        .map(w => String(w ?? '').trim().toLowerCase()).filter(Boolean);
    if (!words.length) { return { roles: DEFAULT_READ_ROLES.slice() }; }
    if (words.some(w => w === 'all' || w === '*')) { return { roles: READ_ROLES.slice() }; }
    const out = [];
    for (const w of words) {
        const r = ROLE_ALIASES[w];
        if (!r) { return { error: `Unknown role "${w}". Roles: ${READ_ROLES.join(', ')} (or "all").` }; }
        if (!out.includes(r)) { out.push(r); }
    }
    return { roles: out };
}

function clip(s, max) {
    if (typeof s !== 'string' || !s) { return { full: 0 }; }
    if (max <= 0 || s.length <= max) { return { v: s, full: 0 }; }
    return { v: s.slice(0, max), full: s.length };
}

const stepKey = (step, index) => (step.uuid ? step.uuid : `#${index + 1}`);

/** A page of a transcript's steps; a bad `roles` comes back as `error`. */
function readPage(steps, opts = {}) {
    const picked = readRoles(opts.roles);
    if (picked.error) { return picked; }
    const roles = picked.roles;
    const all = Array.isArray(steps) ? steps : [];
    const turns = all.reduce((m, s) => Math.max(m, typeof s.turn === 'number' ? s.turn : 0), 0);
    const turn = Math.floor(Number(opts.turn) || 0);
    const keyOf = new Map();
    const wanted = all.filter((s, at) => {
        const keep = roles.includes(s.role) && (opts.sidechain === true || s.sidechain !== true) && (turn <= 0 || s.turn === turn);
        if (keep) { keyOf.set(s, stepKey(s, at)); }
        return keep;
    });
    const total = wanted.length;
    const limit = Math.min(READ_LIMIT_MAX, Math.max(1, Math.floor(Number(opts.limit) || READ_LIMIT_DEFAULT)));
    const rawFrom = Math.floor(Number(opts.from) || 1);
    const from = rawFrom < 0 ? Math.max(1, total + rawFrom + 1) : Math.max(1, rawFrom);
    const maxChars = opts.maxChars === undefined || !isFinite(Number(opts.maxChars))
        ? READ_MAX_CHARS_DEFAULT : Math.max(0, Math.floor(Number(opts.maxChars)));
    let clipped = 0;
    const messages = wanted.slice(from - 1, from - 1 + limit).map((s, k) => {
        const t = clip(s.text, maxChars);
        const inp = clip(s.input, maxChars);
        const out = { i: from + k, key: keyOf.get(s), turn: typeof s.turn === 'number' ? s.turn : null, role: s.role, ts: s.ts };
        if (t.v !== undefined) { out.text = t.v; }
        if (s.tool) { out.tool = s.tool; }
        if (inp.v !== undefined) { out.input = inp.v; }
        if (s.isError === true) { out.isError = true; }
        if (s.model) { out.model = s.model; }
        const full = Math.max(t.full, inp.full);
        if (full) { out.clipped = full; clipped++; }
        return out;
    });
    const end = from - 1 + messages.length;
    return { roles, turns, total, from, returned: messages.length, next: end < total ? end + 1 : null, clipped, messages };
}

module.exports = { readPage, READ_LIMIT_DEFAULT, READ_LIMIT_MAX };
