// A fake Agent Farm Remote Host, driven by the contract copy, so the shell can be tested with no PC:
// pairing, the signed handshake, numbered events with resume and gap, exactly-once calls, pages, live watches (live.js).
// /_test/* lets a test (or the emulator script) push events, drop sockets and revoke devices; as the scripted
// farm it also sets the network profile, runs turns over time and hands over the log of what was asked.
'use strict';
const crypto = require('crypto');
const http = require('http');
const https = require('https');
const ws = require('./ws');
const K = require('./contract');
const pages = require('./pages');
const { createFarm } = require('./farm');
const { createTurns } = require('./turns');
const { audit, rowsOf } = require('./audit');
const { createLive } = require('./live');
const { summaryOf } = require('./summary');

/** One-way delay on everything the farm sends, so a round trip costs what it costs on that network. */
const PROFILES = { none: 0, lan: 50, mobile: 300 };

const OPS = {
    'farm.ping': () => ({ pong: true }),
    'console.answer': () => ({ answered: true }),
    // The farm's remote gate (vsc core/remoteSay.ts): a command that cannot be undone waits for the yes, in the farm's words.
    'console.send': a => /\brm\s+-[a-zA-Z]*[rf]/.test(String(a.text || '')) && a.confirm !== 'risk'
        ? { sent: false, confirm: 'risk', note: '⚠️ This asks for rm -rf on developer, which cannot be undone.\nRun it?\nNot sent yet — confirm it, or send it from the PC.' }
        : { note: '→ developer' },
    // A shared picture goes up first (vsc console.stage): the farm says where it put it.
    'console.stage': a => ({ path: 'C:\\Temp\\agentfarm-attachments\\' + String(a.name || 'shared.png'), bytes: Buffer.from(String(a.data || ''), 'base64').length }),
    'needs.act': () => ({ done: true }),
    'console.interrupt': a => ({ id: a.id, interrupted: true, engine: 'claude', how: 'interrupted over the control channel — the process stays' }),
    'workflows.answer': a => ({ answered: a.id, runId: 'r_fake', answer: a.answer || `option ${a.option}` }),
};

/** The farm's network card as welcome.wake carries it (a locally administered MAC, the emulator's subnet). */
const WAKE = [{ mac: '02:00:5e:10:00:01', broadcast: '10.0.2.255' }];

function createHost(opts = {}) {
    const c = opts.contract || K.load();
    const farm = opts.farm || { id: 'farm_fake', name: 'Fake Farm' };
    const keep = opts.keep || 100;
    const h = { c, farm, codes: new Set([opts.code || 'FAKE-CODE']), devices: new Map(), events: [], seq: 0, results: new Map(), calls: [], conns: new Set(), sessions: new Map(), profile: 'none', lang: opts.lang === undefined ? 'en' : opts.lang };

    const reply = (res, status, body) => { res.writeHead(status, { 'content-type': 'application/json' }); res.end(JSON.stringify(body)); };
    const body = req => new Promise(r => { let s = ''; req.on('data', d => { s += d; }); req.on('end', () => { try { r(JSON.parse(s || '{}')); } catch { r(null); } }); });
    const send = (conn, frame) => {
        const text = JSON.stringify(frame);
        const ms = PROFILES[h.profile] || 0;
        if (ms) { setTimeout(() => conn.send(text), ms); } else { conn.send(text); }
    };

    h.send = send;
    const live = createLive(h);

    h.newCode = () => { const code = crypto.randomBytes(4).toString('hex').toUpperCase(); h.codes.add(code); return code; };

    h.emit = (type, data) => {
        const ev = { kind: 'event', seq: ++h.seq, type, ts: Date.now(), data: data || (c.events[type] && c.events[type].example) || {} };
        h.events.push(ev);
        if (h.events.length > keep) { h.events.shift(); }
        for (const conn of h.conns) { if (conn.welcomed) { send(conn, ev); } }
        return ev.seq;
    };

    /** A fresh world: the farm redrawn, its turns stopped, the log emptied, the network profile set. */
    h.scenario = (o = {}) => {
        if (h.turns) { h.turns.clear(); }
        h.world = createFarm({ history: o.history, longest: o.longest, seed: o.seed, quiet: o.quiet === true, contract: c });
        h.turns = createTurns(h.world, h.emit);
        h.profile = PROFILES[o.profile] !== undefined ? o.profile : 'none';
        h.calls = [];
        h.slow = { headMs: 0, pageMs: 0 };
        live.clear();
        return { profile: h.profile, sessions: h.world.sessions.length, cards: h.world.rows.map(x => x.id) };
    };
    h.scenario();

    async function onRequest(req, res) {
        const url = req.url.split('?')[0];
        if (req.method === 'GET' && /^\/(view|res)\//.test(url)) {
            const m = /^AF (\S+)$/.exec(req.headers.authorization || '');
            if (!m || !h.sessions.has(m[1])) { return reply(res, 401, { error: 'session' }); }
            const page = pages.serve(req.url);
            res.writeHead(page.status, { 'content-type': page.type });
            return res.end(page.body);
        }
        if (req.method === 'GET' && url === '/health') { return reply(res, 200, { ok: true, farm, contract: c.version }); }
        if (req.method === 'POST' && url === '/pair') {
            const b = await body(req);
            const bad = K.checkFields(b, c.pairing.request.fields, 'pair.request');
            if (bad) { return reply(res, 400, { error: bad }); }
            if (!h.codes.delete(b.code)) { return reply(res, 403, { error: 'code' }); }
            const device = 'dev_' + crypto.randomBytes(4).toString('hex');
            h.devices.set(device, { name: b.name, publicKey: b.publicKey, scope: 'manage', revoked: false });
            return reply(res, 200, { device, farm, scope: 'manage' });
        }
        if (req.method === 'POST' && url === '/_test/event') { const b = await body(req); return reply(res, 200, { seq: h.emit(b.type, b.data) }); }
        if (req.method === 'POST' && url === '/_test/drop') { for (const conn of [...h.conns]) { conn.close(); } return reply(res, 200, { dropped: true }); }
        if (req.method === 'POST' && url === '/_test/away') { const b = await body(req); h.away = b.away === true; for (const conn of [...h.conns]) { conn.close(); } return reply(res, 200, { away: h.away }); }
        if (req.method === 'POST' && url === '/_test/sleep') { const b = await body(req); h.asleep = b.asleep === true; if (h.asleep) { for (const conn of [...h.conns]) { conn.close(); } } return reply(res, 200, { asleep: h.asleep }); }
        // The PC's language setting changed: a live farm.language event, and every later welcome tells it (lang null = an older farm that says none).
        if (req.method === 'POST' && url === '/_test/lang') { const b = await body(req) || {}; h.lang = typeof b.lang === 'string' && b.lang ? b.lang : null; return reply(res, 200, { lang: h.lang, seq: h.lang ? h.emit('farm.language', { lang: h.lang }) : null }); }
        if (req.method === 'POST' && url === '/_test/revoke') { const b = await body(req); const d = h.devices.get(b.device); if (d) { d.revoked = true; } return reply(res, 200, { revoked: !!d }); }
        if (req.method === 'GET' && url === '/_test/state') { return reply(res, 200, { lastSeq: h.seq, devices: [...h.devices.keys()], calls: h.calls, code: [...h.codes][0] || h.newCode(), sockets: [...h.conns].filter(x => x.welcomed).length, socketsOf: socketsOf() }); }
        // The history's answers held back (ms): the first page (the fresh head) and a page after a cursor, apart.
        if (req.method === 'POST' && url === '/_test/slow') { const b = await body(req) || {}; h.slow = { headMs: Number(b.headMs) || 0, pageMs: Number(b.pageMs) || 0 }; return reply(res, 200, h.slow); }
        if (req.method === 'POST' && url === '/_test/scenario') { return reply(res, 200, h.scenario(await body(req) || {})); }
        // The PC answers a parked turn itself (the Console, another phone): the card's question or permission closes.
        if (req.method === 'POST' && url === '/_test/answer') { const b = await body(req) || {}; return reply(res, 200, { settled: h.turns.settle(String(b.id || ''), b.answer) }); }
        if (req.method === 'POST' && url === '/_test/turn') { const b = await body(req) || {}; return reply(res, 200, h.turns.start(String(b.id || ''), b)); }
        if (req.method === 'GET' && url === '/_test/log') { return reply(res, 200, { profile: h.profile, calls: h.calls, findings: audit(h.calls), live: live.stats() }); }
        reply(res, 404, { error: 'not found' });
    }

    /** The welcomed sockets per device: a test's own link apart from the app's link service on the same farm. */
    function socketsOf() {
        const n = {};
        for (const x of h.conns) { if (x.welcomed) { n[x.device] = (n[x.device] || 0) + 1; } }
        return n;
    }

    function refuse(conn, reason, extra = {}) { conn.send(JSON.stringify({ kind: 'refuse', reason, ...extra })); conn.close(); }

    function verify(device, nonce, signature) {
        try {
            const key = crypto.createPublicKey({ key: Buffer.from(device.publicKey, 'base64'), format: 'der', type: 'spki' });
            return crypto.verify('sha256', Buffer.from(nonce, 'utf8'), key, Buffer.from(signature, 'base64'));
        } catch { return false; }
    }

    function hello(conn, f) {
        if (f.contract !== c.version) { return refuse(conn, 'contract', { detail: `this farm speaks contract ${c.version}`, minContract: c.version }); }
        const d = h.devices.get(f.device);
        if (!d) { return refuse(conn, 'unknown-device'); }
        if (d.revoked) { return refuse(conn, 'revoked'); }
        if (!verify(d, K.signedText(c, farm.id, f.device, conn.nonce), f.signature)) { return refuse(conn, 'bad-signature'); }
        conn.welcomed = true;
        conn.device = f.device;
        conn.session = 's_' + crypto.randomBytes(12).toString('base64url');
        h.sessions.set(conn.session, f.device);
        const features = Object.fromEntries(c.features.map(x => [x, { allowed: true }]));
        send(conn, { kind: 'welcome', farm, device: f.device, scope: d.scope, features, lastSeq: h.seq, session: conn.session, wake: WAKE, ...(h.lang ? { lang: h.lang } : {}) });
        if (typeof f.resumeAfter === 'number') {
            const oldest = h.events.length ? h.events[0].seq : h.seq + 1;
            if (oldest > f.resumeAfter + 1) { send(conn, { kind: 'gap', from: f.resumeAfter + 1, to: oldest - 1 }); }
            for (const ev of h.events) { if (ev.seq > f.resumeAfter) { send(conn, ev); } }
        }
    }

    // While h.away, the socket is the farm's headless service (vsc src/service/serviceRemote.ts): VS Code is
    // closed, three ops answer and every other one is refused with the same sentence.
    function awayAnswer(op) {
        const sentence = `Agent Farm is not running: VS Code is closed on ${farm.name}.`;
        if (op === 'farm.ping') { return { ok: true, result: { pong: true, away: true } }; }
        if (op === 'farm.away') {
            const ts = Date.now() - 12 * 60000;
            return { ok: true, result: { away: true, farm: farm.name, sentence, since: ts, snapshot: {
                v: 2, ts, writer: 'service', host: farm.name,
                totals: { agents: 3, activeAgents: 1, dormantAgents: 2, costToday: 1.5, cost30: 40, tokensToday: 1000, needs: 2, unreadNotices: 4, runningTasks: 1 },
                needs: [{ kind: 'ask', agent: 'developer', title: 'Testleri şimdi çalıştırayım mı?', detail: '', ts }, { kind: 'permission', agent: 'psikolog', title: 'Bash: git status', detail: '', ts }],
            }, age: { seconds: 720, stale: true, note: 'VS Code is closed.' } } };
        }
        if (op === 'farm.open') {
            // VS Code starts, the service lets go of the socket, the shell reconnects to the full farm.
            setTimeout(() => { h.away = false; for (const conn of [...h.conns]) { conn.close(); } }, 1500);
            return { ok: true, result: { opening: true } };
        }
        return { ok: false, error: sentence };
    }

    function answer(op, args) {
        if (h.away) { return awayAnswer(op); }
        if (op === 'farm.away') { return { ok: true, result: { away: false, farm: farm.name } }; }
        if (op === 'farm.open') { return { ok: true, result: { opening: false, already: true, farm: farm.name } }; }
        if (op === 'console.answer' && args.id) { h.turns.settle(String(args.id), args.answer ?? args.response); }
        if (op === 'console.interrupt' && args.id) { h.turns.stop(String(args.id)); }
        const asks = op === 'needs.act' && /^permission\|/.test(String(args.key || '')) ? h.world.rows.find(r => `permission|${r.sessionId}` === args.key) : null;
        if (asks) { h.turns.settle(asks.id, args.action === 'permission-allow' ? 'allowed' : 'denied'); }
        if (op === 'now.summary') { return { ok: true, result: summaryOf(h.world, h.turns, args) }; }
        const run = h.world.ops[op] || OPS[op];
        if (!run) { return { ok: false, error: `unknown op ${op}` }; }
        try {
            const result = run(args);
            // Words the farm took into an idle card start its turn, the user's words first (vsc remoteSay -> ConsoleHost.send).
            const card = op === 'console.send' && result.sent !== false ? h.world.card(String(args.id || '')) : null;
            if (card && card.status === 'idle') { const text = String(args.text || ''); h.turns.start(card.id, { text, stepMs: 1000, answer: `Yaptım: ${text}` }); }
            return { ok: true, result };
        } catch (e) { return { ok: false, error: e.message }; }
    }

    function call(conn, f) {
        const done = h.results.get(f.id);
        if (done) { return send(conn, { ...done, duplicate: true }); }
        const entry = { id: f.id, op: f.op, args: f.args || null, device: conn.device, ts: Date.now(), profile: h.profile };
        h.calls.push(entry);
        const r = { kind: 'result', id: f.id, ...answer(f.op, f.args || {}) };
        entry.rows = rowsOf(r.result);
        entry.bytes = Buffer.byteLength(JSON.stringify(r));
        h.results.set(f.id, r);
        const late = f.op === 'sessions.recent' ? (f.args && f.args.cursor ? h.slow.pageMs : h.slow.headMs) : 0;
        if (late) { setTimeout(() => send(conn, r), late); } else { send(conn, r); }
    }

    function onFrame(conn, text) {
        let f = null;
        try { f = JSON.parse(text); } catch { return; }
        const bad = K.checkFrame(c, f);
        if (!conn.welcomed) { return bad || f.kind !== 'hello' ? refuse(conn, 'not-allowed', { detail: bad || 'hello first' }) : hello(conn, f); }
        if (bad) { return f && f.kind === 'call' && f.id ? send(conn, { kind: 'result', id: String(f.id), ok: false, error: bad }) : undefined; }
        if (f.kind === 'call') { return call(conn, f); }
        if (f.kind === 'ping') { return send(conn, { kind: 'pong', ts: f.ts }); }
        if (f.kind === 'watch') { return live.watch(conn, f); }
        if (f.kind === 'unwatch') { return live.unwatch(conn, f.watch); }
        if (f.kind === 'view.open') { return send(conn, { kind: 'view.post', view: f.view, message: { type: 'state', page: f.page, ...(f.args ? { args: f.args } : {}) } }); }
        if (f.kind === 'view.msg') { return send(conn, { kind: 'view.post', view: f.view, message: { type: 'echo', message: f.message } }); }
    }

    const server = opts.tls ? https.createServer(opts.tls, onRequest) : http.createServer(onRequest);
    server.on('upgrade', (req, socket) => {
        // An asleep PC answers nothing (B-4: the phone offers to wake it).
        if (req.url.split('?')[0] !== '/ws' || h.asleep) { socket.destroy(); return; }
        const conn = ws.accept(req, socket);
        if (!conn) { return; }
        conn.nonce = 'b64:' + crypto.randomBytes(16).toString('base64');
        h.conns.add(conn);
        conn.on('close', () => { h.conns.delete(conn); live.drop(conn); if (conn.session) { h.sessions.delete(conn.session); } });
        conn.on('message', t => onFrame(conn, t));
        conn.send(JSON.stringify({ kind: 'challenge', nonce: conn.nonce, farm, contract: c.version }));
    });

    h.server = server;
    h.listen = (port = 0, host = '127.0.0.1') => new Promise(r => server.listen(port, host, () => r(server.address().port)));
    h.close = () => new Promise(r => { for (const conn of h.conns) { conn.close(); } server.close(() => r()); });
    return h;
}

module.exports = { createHost, OPS };
