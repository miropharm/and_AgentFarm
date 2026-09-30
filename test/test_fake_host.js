// The fake Agent Farm host behaves the way the contract says the real one does: pairing with one-time
// codes, the signed handshake and its refusals, numbered events with resume and gap, exactly-once calls,
// and every frame it sends passes the contract checker. The shell's emulator tests stand on this.
'use strict';
const { suite } = require('./lib');
const { createHost } = require('../tools/fakehost/host');
const K = require('../tools/fakehost/contract');
const P = require('./fakehost_client');

const t = suite();
const c = K.load();

async function main() {
    const host = createHost({ keep: 3, code: 'CODE-1' });
    const port = await host.listen(0);
    const all = [];
    const hs = async o => { const s = await P.handshake(port, c, o); all.push(s); return s; };

    const health = await P.request(port, 'GET', '/health');
    t.ok(health.status === 200 && health.body.contract === c.version, 'health names the contract version');

    const p = await P.pair(port, 'CODE-1');
    t.ok(p.status === 200 && !K.checkFields(p.body, c.pairing.response.fields, 'pair.response'), 'pairing answers with the contract response');
    const again = await P.pair(port, 'CODE-1');
    t.ok(again.status === 403, 'a pairing code works once');
    const partial = await P.request(port, 'POST', '/pair', { code: 'X' });
    t.ok(partial.status === 400, 'a pairing request missing contract fields is refused');
    const me = { device: p.body.device, key: p.key };

    const s1 = await hs(me);
    t.ok(s1.answer && s1.answer.kind === 'welcome', 'a signed hello is welcomed');
    t.ok(s1.answer.device === me.device && s1.answer.lastSeq === 0, 'welcome names the device and the last seq');
    t.ok(c.features.every(f => s1.answer.features[f]), 'welcome carries every contract feature');

    const seq = host.emit('turn.finished');
    const ev = await s1.waitFor(f => f.kind === 'event' && f.seq === seq);
    t.ok(ev && ev.type === 'turn.finished', 'an emitted event reaches the connected phone with its seq');

    s1.send({ kind: 'call', id: 'c1', op: 'console.answer', args: { answer: 'A' } });
    s1.send({ kind: 'call', id: 'c1', op: 'console.answer', args: { answer: 'A' } });
    await s1.waitFor(f => f.kind === 'result' && f.duplicate === true);
    const results = s1.frames.filter(f => f.kind === 'result' && f.id === 'c1');
    t.ok(results.length === 2 && results[0].ok && !results[0].duplicate && results[1].duplicate === true, 'the same call id twice answers the second as a duplicate');
    t.ok(host.calls.filter(x => x.id === 'c1').length === 1, 'a repeated call id runs the op once');

    s1.send({ kind: 'call', id: 'c2', op: 'no.such.op' });
    const unk = await s1.waitFor(f => f.kind === 'result' && f.id === 'c2');
    t.ok(unk && unk.ok === false && /unknown op/.test(unk.error), 'an unknown op answers ok:false');
    s1.send({ kind: 'call', id: 'c3' });
    const shapeless = await s1.waitFor(f => f.kind === 'result' && f.id === 'c3');
    t.ok(shapeless && shapeless.ok === false, 'a call missing contract fields answers ok:false');

    s1.send({ kind: 'ping', ts: 7 });
    const pong = await s1.waitFor(f => f.kind === 'pong');
    t.ok(pong && pong.ts === 7, 'ping is answered with pong');
    s1.send({ kind: 'view.open', view: 'v1', page: 'needs' });
    const post = await s1.waitFor(f => f.kind === 'view.post' && f.view === 'v1');
    t.ok(post && post.message.page === 'needs', 'view.open answers with a view.post for that view');

    const drop = await P.request(port, 'POST', '/_test/drop');
    await P.tick();
    t.ok(drop.body.dropped && s1.closed, '/_test/drop closes the sockets');

    host.emit('notice.posted');
    host.emit('needs.changed');
    const s2 = await hs({ ...me, resumeAfter: seq });
    await s2.waitFor(f => f.kind === 'event' && f.seq === seq + 2);
    const replay = s2.frames.filter(f => f.kind === 'event').map(f => f.seq);
    t.ok(replay.join() === [seq + 1, seq + 2].join(), 'resumeAfter replays exactly the missed events in order');
    t.ok(!s2.frames.some(f => f.kind === 'gap'), 'no gap while the missed events are still kept');

    for (let i = 0; i < 4; i++) { host.emit('turn.finished'); }
    const s3 = await hs({ ...me, resumeAfter: seq });
    const gap = await s3.waitFor(f => f.kind === 'gap');
    t.ok(gap && gap.from === seq + 1 && gap.to === host.seq - 3, 'events past the keep limit arrive as a gap with its range');
    await s3.waitFor(f => f.kind === 'event' && f.seq === host.seq);
    t.ok(s3.count(f => f.kind === 'event') === 3, 'after the gap only the kept events are replayed');

    const other = P.newKey();
    const forged = await hs({ device: me.device, key: other });
    t.ok(forged.answer && forged.answer.reason === 'bad-signature', 'a signature from another key is refused');
    const wrongFarm = await hs({ ...me, farm: 'farm_other' });
    t.ok(wrongFarm.answer && wrongFarm.answer.reason === 'bad-signature', 'a signature over another farm id is refused');
    const garbage = await hs({ ...me, sign: () => 'bm90LWEtc2ln' });
    t.ok(garbage.answer && garbage.answer.reason === 'bad-signature', 'a malformed signature is refused');
    const stranger = await hs({ device: 'dev_nobody', key: other });
    t.ok(stranger.answer && stranger.answer.reason === 'unknown-device', 'an unpaired device is refused');
    const old = await hs({ ...me, contract: c.version + 1 });
    t.ok(old.answer && old.answer.reason === 'contract' && old.answer.minContract === c.version, 'another contract version is refused with the one this farm speaks');

    const early = await P.open(port, c);
    all.push(early);
    await early.waitFor(f => f.kind === 'challenge');
    early.send({ kind: 'ping', ts: 1 });
    const notHello = await early.waitFor(f => f.kind === 'refuse');
    t.ok(notHello && notHello.reason === 'not-allowed', 'anything before hello is refused');

    await P.request(port, 'POST', '/_test/revoke', { device: me.device });
    const revoked = await hs(me);
    t.ok(revoked.answer && revoked.answer.reason === 'revoked', 'a revoked device is refused');

    const state = await P.request(port, 'GET', '/_test/state');
    const code = state.body.code;
    t.ok(typeof code === 'string' && (await P.pair(port, code)).status === 200, '/_test/state hands out a fresh pairing code');

    const refusals = all.filter(s => s.answer && s.answer.kind === 'refuse');
    await P.tick();
    t.ok(refusals.length === 6 && refusals.every(s => s.closed), 'every refusal closes the socket');
    const bad = all.flatMap(s => s.bad);
    t.ok(bad.length === 0, 'every host frame passes the contract checker: ' + bad.join('; '));

    await host.close();
}

main().then(() => t.done(), e => { t.ok(false, 'threw: ' + (e && e.stack)); t.done(); });
