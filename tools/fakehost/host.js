// A fake Agent Farm Remote Host, driven by the contract copy, so the shell can be tested with no PC:
// pairing, the signed handshake, numbered events with resume and gap, exactly-once calls, pages.
// /_test/* lets a test (or the emulator script) push events, drop sockets and revoke devices.
'use strict';
const crypto = require('crypto');
const http = require('http');
const https = require('https');
const ws = require('./ws');
const K = require('./contract');
const pages = require('./pages');

const OPS = {
    'farm.ping': () => ({ pong: true }),
    'console.answer': () => ({ answered: true }),
    'console.send': () => ({ sent: true }),
    'console.sessions': () => [
        { id: 'c_dev', agent: 'developer', status: 'running', steerable: true, asking: null },
        { id: 'c_psy', agent: 'psikolog', status: 'idle', steerable: true, asking: null },
        { id: 'c_old', agent: 'yazar', status: 'ended', steerable: false, asking: null },
    ],
    'needs.act': () => ({ done: true }),
};

function createHost(opts = {}) {
    const c = opts.contract || K.load();
    const farm = opts.farm || { id: 'farm_fake', name: 'Sahte Çiftlik' };
    const keep = opts.keep || 100;
    const h = { c, farm, codes: new Set([opts.code || 'FAKE-CODE']), devices: new Map(), events: [], seq: 0, results: new Map(), calls: [], conns: new Set(), sessions: new Map() };

    const reply = (res, status, body) => { res.writeHead(status, { 'content-type': 'application/json' }); res.end(JSON.stringify(body)); };
    const body = req => new Promise(r => { let s = ''; req.on('data', d => { s += d; }); req.on('end', () => { try { r(JSON.parse(s || '{}')); } catch { r(null); } }); });

    h.newCode = () => { const code = crypto.randomBytes(4).toString('hex').toUpperCase(); h.codes.add(code); return code; };

    h.emit = (type, data) => {
        const ev = { kind: 'event', seq: ++h.seq, type, ts: Date.now(), data: data || (c.events[type] && c.events[type].example) || {} };
        h.events.push(ev);
        if (h.events.length > keep) { h.events.shift(); }
        for (const conn of h.conns) { if (conn.welcomed) { conn.send(JSON.stringify(ev)); } }
        return ev.seq;
    };

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
        if (req.method === 'POST' && url === '/_test/revoke') { const b = await body(req); const d = h.devices.get(b.device); if (d) { d.revoked = true; } return reply(res, 200, { revoked: !!d }); }
        if (req.method === 'GET' && url === '/_test/state') { return reply(res, 200, { lastSeq: h.seq, devices: [...h.devices.keys()], calls: h.calls, code: [...h.codes][0] || h.newCode() }); }
        reply(res, 404, { error: 'not found' });
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
        conn.send(JSON.stringify({ kind: 'welcome', farm, device: f.device, scope: d.scope, features, lastSeq: h.seq, session: conn.session }));
        if (typeof f.resumeAfter === 'number') {
            const oldest = h.events.length ? h.events[0].seq : h.seq + 1;
            if (oldest > f.resumeAfter + 1) { conn.send(JSON.stringify({ kind: 'gap', from: f.resumeAfter + 1, to: oldest - 1 })); }
            for (const ev of h.events) { if (ev.seq > f.resumeAfter) { conn.send(JSON.stringify(ev)); } }
        }
    }

    function call(conn, f) {
        const done = h.results.get(f.id);
        if (done) { return conn.send(JSON.stringify({ ...done, duplicate: true })); }
        h.calls.push({ id: f.id, op: f.op, args: f.args || null, device: conn.device });
        const run = OPS[f.op];
        const r = run ? { kind: 'result', id: f.id, ok: true, result: run(f.args || {}) } : { kind: 'result', id: f.id, ok: false, error: `unknown op ${f.op}` };
        h.results.set(f.id, r);
        conn.send(JSON.stringify(r));
    }

    function onFrame(conn, text) {
        let f = null;
        try { f = JSON.parse(text); } catch { return; }
        const bad = K.checkFrame(c, f);
        if (!conn.welcomed) { return bad || f.kind !== 'hello' ? refuse(conn, 'not-allowed', { detail: bad || 'hello first' }) : hello(conn, f); }
        if (bad) { return f && f.kind === 'call' && f.id ? conn.send(JSON.stringify({ kind: 'result', id: String(f.id), ok: false, error: bad })) : undefined; }
        if (f.kind === 'call') { return call(conn, f); }
        if (f.kind === 'ping') { return conn.send(JSON.stringify({ kind: 'pong', ts: f.ts })); }
        if (f.kind === 'view.open') { return conn.send(JSON.stringify({ kind: 'view.post', view: f.view, message: { type: 'state', page: f.page } })); }
        if (f.kind === 'view.msg') { return conn.send(JSON.stringify({ kind: 'view.post', view: f.view, message: { type: 'echo', message: f.message } })); }
    }

    const server = opts.tls ? https.createServer(opts.tls, onRequest) : http.createServer(onRequest);
    server.on('upgrade', (req, socket) => {
        if (req.url.split('?')[0] !== '/ws') { socket.destroy(); return; }
        const conn = ws.accept(req, socket);
        if (!conn) { return; }
        conn.nonce = 'b64:' + crypto.randomBytes(16).toString('base64');
        h.conns.add(conn);
        conn.on('close', () => { h.conns.delete(conn); if (conn.session) { h.sessions.delete(conn.session); } });
        conn.on('message', t => onFrame(conn, t));
        conn.send(JSON.stringify({ kind: 'challenge', nonce: conn.nonce, farm, contract: c.version }));
    });

    h.server = server;
    h.listen = (port = 0, host = '127.0.0.1') => new Promise(r => server.listen(port, host, () => r(server.address().port)));
    h.close = () => new Promise(r => { for (const conn of h.conns) { conn.close(); } server.close(() => r()); });
    return h;
}

module.exports = { createHost, OPS };
