// What the phone asked of the farm, and whether it asked for too much. No list is asked for whole (B path
// design, section 5): history comes 20 rows at a time, a conversation 30 messages at a time. A call past a
// page is a finding; a journey whose log has one fails.
'use strict';

const PAGE_ROWS = 20;
const PAGE_MESSAGES = 30;

/** How many rows an answer carried: the list itself, a page's rows or messages, or a queue's items. */
function rowsOf(result) {
    if (Array.isArray(result)) { return result.length; }
    if (result && Array.isArray(result.rows)) { return result.rows.length; }
    if (result && Array.isArray(result.messages)) { return result.messages.length; }
    if (result && Array.isArray(result.items)) { return result.items.length; }
    return 0;
}

/** One line per call that asked past a page; empty when the log is economical. */
function audit(calls) {
    const out = [];
    for (const c of calls) {
        const a = c.args || {};
        if (c.op === 'sessions.recent') {
            // the page view's own default is a page; the plain list's is 10 (vsc sessionPage.ts, sessions.recent)
            const asked = typeof a.limit === 'number' && a.limit > 0 ? Math.floor(a.limit) : a.view === 'page' ? PAGE_ROWS : 10;
            if (asked > PAGE_ROWS) { out.push(`${c.id} sessions.recent asked ${asked} sessions (a page is ${PAGE_ROWS})`); }
        }
        if (c.op === 'sessions.read') {
            const asked = typeof a.limit === 'number' && a.limit > 0 ? Math.floor(a.limit) : 50;
            if (asked > PAGE_MESSAGES) { out.push(`${c.id} sessions.read asked ${asked} messages (a page is ${PAGE_MESSAGES})`); }
        }
        if (c.op === 'console.sessions' && typeof a.messages === 'number' && a.messages > PAGE_MESSAGES) {
            out.push(`${c.id} console.sessions asked ${a.messages} messages per session (a page is ${PAGE_MESSAGES})`);
        }
    }
    return out;
}

module.exports = { audit, rowsOf, PAGE_ROWS, PAGE_MESSAGES };
