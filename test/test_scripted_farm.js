// The scripted farm the journey tests stand on: it answers in Agent Farm's real shapes (examples.json, captured
// from the farm's bridge), pages a 2,000-message conversation the way sessions.read does, keeps a 500-session
// history, moves turns over time with the contract's events, delays every frame by its network profile and
// logs what was asked, finding any call that asked past a page.
'use strict';
const { suite } = require('./lib');
const { createHost } = require('../tools/fakehost/host');
const K = require('../tools/fakehost/contract');
const P = require('./fakehost_client');
const real = require('../tools/fakehost/examples.json');

const t = suite();
const c = K.load();

const kind = v => (v === null ? 'null' : Array.isArray(v) ? 'array' : typeof v);

/** The same keys as the farm's answer, and the same JSON type wherever both sides hold a value. */
function shape(farm, ours, path, out) {
    const want = Object.keys(farm).sort();
    const got = Object.keys(ours).sort();
    const missing = want.filter(k => !got.includes(k));
    const extra = got.filter(k => !want.includes(k));
    if (missing.length) { out.push(`${path}: missing ${missing.join(', ')}`); }
    if (extra.length) { out.push(`${path}: not in the farm's answer ${extra.join(', ')}`); }
    for (const k of want) {
        if (!(k in ours) || farm[k] === null || ours[k] === null) { continue; }
        if (kind(farm[k]) !== kind(ours[k])) { out.push(`${path}.${k}: ${kind(ours[k])} where the farm sends ${kind(farm[k])}`); }
    }
    return out;
}

async function main() {
    const host = createHost({ code: 'CODE-S' });
    const port = await host.listen(0);
    const p = await P.pair(port, 'CODE-S');
    const s = await P.handshake(port, c, { device: p.body.device, key: p.key });
    let n = 0;
    const call = async (op, args) => {
        const id = 'k' + (++n);
        s.send({ kind: 'call', id, op, ...(args ? { args } : {}) });
        return s.waitFor(f => f.kind === 'result' && f.id === id, 4000);
    };

    // ---- the real shapes
    const rows = (await call('console.sessions', { messages: 4 })).result;
    const dev = rows.find(r => r.id === 'c_dev');
    const old = rows.find(r => r.id === 'c_old');
    const [rDev, rOld] = real['console.sessions'];
    const problems = [];
    shape(rDev, dev, 'console.sessions row', problems);
    shape(rOld.lastTurn, old.lastTurn, 'console.sessions lastTurn', problems);
    shape(rDev.messages[0], dev.messages[0], 'console.sessions message', problems);
    t.ok(problems.length === 0, 'console.sessions rows carry the farm\'s fields and types: ' + problems.join('; '));
    const bare = (await call('console.sessions')).result;
    t.ok(bare.every(r => !('messages' in r)), 'console.sessions without messages carries none (the farm\'s default is 0)');
    t.ok(rows.map(r => r.status).join() === 'working,idle,ended' && dev.steerable === true && old.steerable === false,
        'the three cards keep the states the shell tests rely on: working, idle, ended');
    t.ok(['user', 'agent', 'tool'].includes(dev.messages[3].role) && dev.messages.every(m => m.role !== 'assistant'),
        'console.sessions speaks the Console\'s words (agent, tool), never sessions.read\'s');

    const recent = (await call('sessions.recent', { limit: 20 })).result;
    t.ok(shape(real['sessions.recent'][0], recent[0], 'sessions.recent row', []).length === 0, 'sessions.recent rows carry the farm\'s fields: ' + shape(real['sessions.recent'][0], recent[0], 'row', []).join('; '));
    t.ok(recent.length === 20 && recent.every((r, i) => i === 0 || recent[i - 1].lastTs >= r.lastTs), 'sessions.recent gives the page asked for, newest first');
    t.ok((await call('sessions.recent')).result.length === 10, 'sessions.recent without a limit gives the farm\'s default 10');
    const all = (await call('sessions.recent', { limit: 600 })).result;
    t.ok(all.length === 500, 'the history holds 500 sessions');

    const needs = (await call('needs.list')).result;
    t.ok(shape(real['needs.list'], needs, 'needs.list', []).length === 0 && shape(real['needs.list'].items[0], needs.items[0], 'needs item', []).length === 0,
        'needs.list and its items carry the farm\'s fields');
    t.ok(needs.items[0].key === `permission|${needs.items[0].ref.sessionId}`, 'a permission need is keyed the way vsc needKey keys it');

    // ---- a 2,000-message conversation, paged like sessions.read
    const sid = dev.sessionId;
    const last = (await call('sessions.read', { session: sid, roles: 'all', from: -30, limit: 30 })).result;
    t.ok(shape(real['sessions.read'], last, 'sessions.read', []).length === 0, 'sessions.read carries the farm\'s page fields: ' + shape(real['sessions.read'], last, 'page', []).join('; '));
    t.ok(last.total === 2000 && last.returned === 30 && last.from === 1971 && last.next === null && last.messages[29].i === 2000,
        'from -30 is the last thirty of 2,000 and the end has no next');
    const before = (await call('sessions.read', { session: sid.slice(0, 8), roles: 'all', from: last.from - 30, limit: 30 })).result;
    t.ok(before.from === 1941 && before.next === 1971 && before.messages.length === 30, 'the page before it ends where the last began; an 8-character prefix finds the session');
    const answers = (await call('sessions.read', { session: sid, roles: 'assistant', limit: 500 })).result;
    t.ok(answers.clipped > 0 && answers.messages.some(m => m.clipped > 4000 && m.text.length === 4000), 'a long answer arrives cut at 4,000 characters with its full length');
    t.ok(answers.messages.every(m => ['i', 'key', 'turn', 'role', 'ts'].every(k => k in m)), 'every step carries i, key, turn, role, ts');
    const unknown = await call('sessions.read', { session: 'nope-nope' });
    t.ok(unknown.ok === false && /No session/.test(unknown.error), 'an unknown session is refused, as the farm refuses it');

    // ---- the log and its findings
    const log = (await P.request(port, 'GET', '/_test/log')).body;
    const big = log.calls.find(x => x.op === 'sessions.recent' && x.args && x.args.limit === 600);
    t.ok(big && big.rows === 500 && big.bytes > 100000, 'the log records how many rows and bytes each answer carried');
    t.ok(log.findings.some(f => /sessions\.recent asked 600 sessions/.test(f)) && log.findings.some(f => /sessions\.read asked 500 messages/.test(f)),
        'a call past a page is a finding: ' + log.findings.join('; '));
    t.ok(!log.findings.some(f => /asked 20 sessions|asked 30 messages/.test(f)), 'a page-sized call is not');

    // ---- turns over time: a question parks the turn until it is answered
    await P.request(port, 'POST', '/_test/scenario', {});
    t.ok((await P.request(port, 'GET', '/_test/log')).body.calls.length === 0, 'a new scenario empties the log');
    const started = (await P.request(port, 'POST', '/_test/turn', { id: 'c_psy', stepMs: 20, ask: { question: 'Notları özetleyeyim mi?', options: ['Evet', 'Hayır'] }, answer: 'Özet hazır.' })).body;
    t.ok(started.started === true, 'a scripted turn starts on an idle card');
    const asked = await s.waitFor(f => f.kind === 'event' && f.type === 'ask.opened' && f.data.session === 'c_psy', 3000);
    t.ok(asked && asked.data.options.length === 2, 'the turn parks on its question and the phone hears ask.opened');
    const psy = (await call('console.sessions', { id: 'c_psy', messages: 3 })).result[0];
    t.ok(psy.status === 'working' && psy.asking && psy.asking.kind === 'question', 'while it waits the card says working and asking');
    t.ok(shape(rDev.asking, psy.asking, 'asking', []).length === 0 && shape(rDev.asking.questions[0], psy.asking.questions[0], 'question', []).length === 0,
        'asking carries the farm\'s fields: ' + shape(rDev.asking, psy.asking, 'asking', []).join('; '));
    await call('console.answer', { id: 'c_psy', answer: 'Evet' });
    const closed = await s.waitFor(f => f.kind === 'event' && f.type === 'ask.closed' && f.data.session === 'c_psy', 2000);
    const finished = await s.waitFor(f => f.kind === 'event' && f.type === 'turn.finished' && f.data.session === 'c_psy', 3000);
    t.ok(closed && finished && finished.data.summary === 'Özet hazır.', 'the answer closes the question and the turn finishes with its answer');
    const after = (await call('console.sessions', { id: 'c_psy' })).result[0];
    t.ok(after.status === 'idle' && after.asking === null && after.lastTurn.outcome === 'done', 'the card is idle again with the turn it just finished');
    const tail = (await call('sessions.read', { session: after.sessionId, from: -1 })).result;
    t.ok(tail.messages[0].role === 'assistant' && tail.messages[0].text === 'Özet hazır.', 'the answer is the conversation\'s last message');

    // ---- a permission, then a stop
    await P.request(port, 'POST', '/_test/turn', { id: 'c_dev', stepMs: 20, permission: { tool: 'Bash', summary: 'git push origin master' } });
    const perm = await s.waitFor(f => f.kind === 'event' && f.type === 'permission.opened' && f.data.session === 'c_dev', 3000);
    t.ok(perm && perm.data.summary === 'git push origin master', 'a permission parks the turn and the phone hears permission.opened');
    await call('console.interrupt', { id: 'c_dev' });
    const stopped = (await call('console.sessions', { id: 'c_dev' })).result[0];
    t.ok(stopped.status === 'idle' && stopped.lastTurn.outcome === 'interrupted', 'console.interrupt stops the turn where it stands');

    // ---- the network profiles
    const rtt = async () => { const t0 = Date.now(); await call('farm.ping'); return Date.now() - t0; };
    const none = await rtt();
    await P.request(port, 'POST', '/_test/scenario', { profile: 'lan' });
    const lan = await rtt();
    await P.request(port, 'POST', '/_test/scenario', { profile: 'mobile' });
    const mobile = await rtt();
    t.ok(none < 50 && lan >= 50 && lan < 300 && mobile >= 300, `a profile delays every answer by its network (none ${none} ms, LAN ${lan} ms, mobile ${mobile} ms)`);
    const late = host.emit('turn.finished');
    const t0 = Date.now();
    await s.waitFor(f => f.kind === 'event' && f.seq === late, 2000);
    t.ok(Date.now() - t0 >= 280, 'events travel with the same delay');
    await P.request(port, 'POST', '/_test/scenario', {});

    t.ok(s.bad.length === 0, 'every frame the scripted farm sent passes the contract checker: ' + s.bad.join('; '));
    await host.close();
}

main().then(() => t.done(), e => { t.ok(false, 'threw: ' + (e && e.stack)); t.done(); });
