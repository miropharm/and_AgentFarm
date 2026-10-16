// The two light views Agent Farm answers a phone with (vsc src/core/sessionPage.ts, the contract's "ops"):
// sessions.recent { view: "page", cursor?, limit? } -> { rows, next }, newest activity first after a keyset cursor,
// light rows only; console.sessions { view: "summary" } -> each card cut to the contract's declared row fields.
'use strict';

const PAGE_DEFAULT = 20;
const PAGE_MAX = 50;
const LINE_MAX = 160;

const activity = s => s.lastTs || s.mtime || 0;
const order = (a, b) => activity(b) - activity(a) || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);
const cursorOf = s => `${activity(s)}:${s.id}`;

function parseCursor(raw) {
    if (typeof raw !== 'string') { return null; }
    const at = raw.indexOf(':');
    const ts = Number(raw.slice(0, at));
    return at > 0 && Number.isFinite(ts) && at < raw.length - 1 ? { ts, id: raw.slice(at + 1) } : null;
}

function firstLine(text) {
    const line = String(text || '').split('\n').map(l => l.trim()).find(Boolean) || '';
    return line.length > LINE_MAX ? line.slice(0, LINE_MAX - 1) + '…' : line;
}

/** vsc core/live.ts lastSaid: the agent's last words in the current turn; a prompt after them means none yet. */
function lastSaid(steps) {
    for (let k = steps.length - 1; k >= 0; k--) {
        if (steps[k].role === 'user') { return null; }
        if (steps[k].role === 'assistant' && steps[k].text) { return steps[k]; }
    }
    return null;
}

/** One page of `selected` (the world's sessions, any order); facts only for the rows on it. */
function recentPage(f, selected, args) {
    const limit = Math.min(PAGE_MAX, typeof args.limit === 'number' && args.limit > 0 ? Math.floor(args.limit) : PAGE_DEFAULT);
    const at = parseCursor(args.cursor);
    const sorted = selected.slice().sort(order);
    const from = at ? sorted.findIndex(s => activity(s) < at.ts || (activity(s) === at.ts && s.id > at.id)) : 0;
    const start = from < 0 ? sorted.length : from;
    const slice = sorted.slice(start, start + limit);
    const rows = slice.map(s => {
        const card = f.rows.find(x => x.sessionId === s.id);
        const said = lastSaid(f.steps(s));
        const row = {
            id: s.id, agent: s.agent, title: s.title, engine: s.engine, lastTs: activity(s), origin: s.origin,
            status: card ? card.status : null,
            waiting: f.needs.some(n => n.ref && n.ref.sessionId === s.id) || !!(card && card.asking),
        };
        const line = firstLine(said && said.text);
        if (line) { row.lastLine = line; }
        return row;
    });
    const more = start + slice.length < sorted.length;
    return { rows, next: more && slice.length ? cursorOf(slice[slice.length - 1]) : null };
}

/** A card cut to the fields the contract declares for console.sessions (vsc opRow). */
function summaryRow(c, row) {
    const fields = c.ops['console.sessions'].row;
    return Object.fromEntries(Object.keys(fields).filter(k => k in row).map(k => [k, row[k]]));
}

module.exports = { recentPage, summaryRow, parseCursor, PAGE_DEFAULT, PAGE_MAX };
