// A minimal phone for test_fake_host.js: pairs over HTTP, signs the challenge with a P-256 key the
// way the shell's Keystore key will, and records every frame the host sends (each one contract-checked).
'use strict';
const crypto = require('crypto');
const http = require('http');
const ws = require('../tools/fakehost/ws');
const K = require('../tools/fakehost/contract');

function request(port, method, path, body) {
    return new Promise((resolve, reject) => {
        const data = body === undefined ? '' : JSON.stringify(body);
        const req = http.request({ host: '127.0.0.1', port, method, path, headers: { 'content-type': 'application/json', 'content-length': Buffer.byteLength(data) } }, res => {
            let s = '';
            res.on('data', d => { s += d; });
            res.on('end', () => resolve({ status: res.statusCode, body: JSON.parse(s || '{}') }));
        });
        req.on('error', reject);
        req.end(data);
    });
}

function newKey() {
    const { publicKey, privateKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
    return { privateKey, publicKey: publicKey.export({ type: 'spki', format: 'der' }).toString('base64') };
}

async function pair(port, code, key = newKey()) {
    const r = await request(port, 'POST', '/pair', { code, name: 'Test Telefon', publicKey: key.publicKey, platform: 'android 14', app: 'test' });
    return { ...r, key };
}

/** Connects to /ws and collects frames; `bad` gathers every host frame that fails the contract. */
async function open(port, c) {
    const conn = await ws.connect({ host: '127.0.0.1', port });
    const s = { conn, frames: [], bad: [], closed: false, waiters: [] };
    conn.on('message', t => {
        const f = JSON.parse(t);
        const problem = K.checkFrame(c, f);
        if (problem) { s.bad.push(problem); }
        s.frames.push(f);
        s.waiters = s.waiters.filter(w => !w());
    });
    conn.on('close', () => { s.closed = true; s.waiters = s.waiters.filter(w => !w()); });
    s.send = f => conn.send(JSON.stringify(f));
    s.waitFor = (pred, ms = 2000) => new Promise(resolve => {
        const check = () => { const f = s.frames.find(pred); if (f) { resolve(f); return true; } if (s.closed) { resolve(null); return true; } return false; };
        if (check()) { return; }
        s.waiters.push(check);
        setTimeout(() => resolve(s.frames.find(pred) || null), ms);
    });
    s.count = pred => s.frames.filter(pred).length;
    return s;
}

/** Opens, answers the challenge with a signed hello, and waits for welcome or refuse. */
async function handshake(port, c, { device, key, farm, contract, resumeAfter, sign } = {}) {
    const s = await open(port, c);
    const ch = await s.waitFor(f => f.kind === 'challenge');
    const text = K.signedText(c, farm || ch.farm.id, device, ch.nonce);
    const signature = sign ? sign(text) : crypto.sign('sha256', Buffer.from(text, 'utf8'), key.privateKey).toString('base64');
    const hello = { kind: 'hello', device, contract: contract === undefined ? c.version : contract, signature };
    if (resumeAfter !== undefined) { hello.resumeAfter = resumeAfter; }
    s.send(hello);
    s.answer = await s.waitFor(f => f.kind === 'welcome' || f.kind === 'refuse');
    return s;
}

const tick = (ms = 50) => new Promise(r => setTimeout(r, ms));

module.exports = { request, newKey, pair, open, handshake, tick };
