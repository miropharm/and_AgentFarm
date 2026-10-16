// The live channel the way Agent Farm keeps it (vsc src/core/remoteLiveHub.ts and remoteLive.ts): a watch is one
// phone screen on one Console card (full: its rows too) or on every card ("*", summary). Its first frame comes at
// once and carries no rows (what is there is read with sessions.read); then at most one frame per beat - full
// 250 ms, summary 1 s - and none when nothing the screen draws changed. A row is put in sessions.read's words.
'use strict';

const BEAT = { full: 250, summary: 1000 };
const MAX_CHARS = 4000;

function createLive(h) {
    const log = { frames: 0, rows: 0, bytes: 0 };
    const open = new Set();

    const stepsOf = row => h.world.steps(h.world.bySession(row.sessionId));
    const said = s => `${(s.text || '').length}|${(s.input || '').length}|${s.isError ? 1 : 0}`;
    const memoOf = row => ({ world: h.world, seen: new Map(stepsOf(row).map(s => [s, said(s)])) });

    /** The call in flight: a working card's last row when it is a call (vsc liveStep). */
    function step(row) {
        if (row.status !== 'working') { return null; }
        const ss = stepsOf(row);
        const m = ss[ss.length - 1];
        return m && m.role === 'tool' && m.tool ? { tool: m.tool, detail: m.input || '', since: m.ts } : null;
    }

    function turnSince(row) {
        const t = h.turns.running.get(row.id);
        if (t) { return t.startedAt; }
        const user = stepsOf(row).filter(s => s.role === 'user').pop();
        return user ? user.ts : null;
    }

    function cut(s) {
        if (typeof s !== 'string' || !s) { return { full: 0 }; }
        return s.length <= MAX_CHARS ? { v: s, full: 0 } : { v: s.slice(0, MAX_CHARS), full: s.length };
    }

    function liveRow(s, k, turn) {
        const out = { key: s.uuid || `c:${k + 1}`, turn, role: s.role, ts: s.ts };
        const t = cut(s.text);
        const inp = cut(s.input);
        if (t.v !== undefined) { out.text = t.v; }
        if (s.tool) { out.tool = s.tool; }
        if (inp.v !== undefined) { out.input = inp.v; }
        if (s.isError === true) { out.isError = true; }
        const full = Math.max(t.full, inp.full);
        if (full) { out.clipped = full; }
        return out;
    }

    function data(w, row) {
        const out = {
            agent: row.agent, status: row.status, turnSince: row.status === 'working' ? turnSince(row) : null,
            cost: row.cost, steerable: row.steerable, step: step(row), asking: row.asking,
        };
        if (w.depth !== 'full') { return out; }
        let memo = w.memo.get(row.id);
        // a new scenario is a new world: what is on its cards now is read, never told as new
        if (!memo || memo.world !== h.world) { memo = memoOf(row); w.memo.set(row.id, memo); }
        const rows = [];
        stepsOf(row).forEach((s, k) => {
            const now = said(s);
            if (memo.seen.get(s) === now) { return; }
            memo.seen.set(s, now);
            rows.push(liveRow(s, k, row.turns));
        });
        out.messages = rows;
        return out;
    }

    function tick(w) {
        if (w.session !== '*' && !h.world.card(w.session)) { return end(w, 'ended'); }
        const cards = w.session === '*' ? h.world.rows : [h.world.card(w.session)];
        for (const row of cards) {
            const d = data(w, row);
            const rows = Array.isArray(d.messages) ? d.messages.length : 0;
            const sig = JSON.stringify({ ...d, messages: undefined });
            if (!rows && w.sent.get(row.id) === sig) { continue; }
            w.sent.set(row.id, sig);
            const frame = { kind: 'live', watch: w.id, session: row.id, data: d };
            log.frames++;
            log.rows += rows;
            log.bytes += Buffer.byteLength(JSON.stringify(frame));
            h.send(w.conn, frame);
        }
    }

    function end(w, reason) {
        stop(w);
        h.send(w.conn, { kind: 'unwatch', watch: w.id, reason });
    }

    function stop(w) {
        clearInterval(w.timer);
        w.conn.watches.delete(w.id);
        open.delete(w);
    }

    /** A watch the farm cannot keep is answered unwatch unavailable (vsc RemoteSession.watch). */
    function watch(conn, f) {
        if (!conn.watches) { conn.watches = new Map(); }
        unwatch(conn, f.watch);
        const ok = (f.depth === 'full' || f.depth === 'summary') && (f.session === '*' ? f.depth === 'summary' : !!h.world.card(f.session));
        if (!ok) { return h.send(conn, { kind: 'unwatch', watch: f.watch, reason: 'unavailable' }); }
        const w = { id: f.watch, conn, session: f.session, depth: f.depth, memo: new Map(), sent: new Map() };
        for (const row of (f.session === '*' ? h.world.rows : [h.world.card(f.session)])) { w.memo.set(row.id, memoOf(row)); }
        w.timer = setInterval(() => tick(w), BEAT[f.depth]);
        if (w.timer.unref) { w.timer.unref(); }
        conn.watches.set(w.id, w);
        open.add(w);
        tick(w);
    }

    function unwatch(conn, id) {
        const w = conn.watches && conn.watches.get(id);
        if (w) { stop(w); }
    }

    /** The socket closed: its watches end with it. */
    const drop = conn => { if (conn.watches) { [...conn.watches.values()].forEach(stop); } };
    const clear = () => { log.frames = 0; log.rows = 0; log.bytes = 0; };
    const stats = () => ({ watches: open.size, ...log });

    return { watch, unwatch, drop, clear, stats };
}

module.exports = { createLive, BEAT };
