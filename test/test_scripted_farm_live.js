// The scripted farm's live channel, held to the contract and to Agent Farm's hub (vsc remoteLiveHub.ts): a watch's
// first frame comes at once with no rows, then one frame per beat at most and none when nothing drawn changed;
// a turn's rows arrive in sessions.read's words and are the rows sessions.read gives afterwards; a summary watch
// of "*" tells every card without rows; a watch it cannot keep is answered unwatch unavailable; a closed socket
// or an unwatch ends it. The data's fields and a row's words are read from the contract copy.
'use strict';
const { suite } = require('./lib');
const { createHost } = require('../tools/fakehost/host');
const { BEAT } = require('../tools/fakehost/live');
const K = require('../tools/fakehost/contract');
const P = require('./fakehost_client');

const t = suite();
const c = K.load();
const DATA = Object.keys(c.frames.live.example.data).sort();
const rule = c.rules.find(r => /^watch asks/.test(r)) || '';
const WORDS = ((/in sessions\.read's words \(([^)]+)\)/.exec(rule) || [])[1] || '').split(',').map(w => w.trim().replace(/\?$/, '')).filter(Boolean);

const lives = (s, watch) => s.frames.map((f, k) => ({ f, at: s.at[k] })).filter(x => x.f.kind === 'live' && x.f.watch === watch);
const minGap = xs => xs.slice(1).reduce((m, x, k) => Math.min(m, x.at - xs[k].at), Infinity);

async function main() {
    t.ok(DATA.includes('messages') && WORDS.includes('key') && WORDS.includes('clipped'), `the contract names the data's fields (${DATA}) and a row's words (${WORDS})`);
    const host = createHost({ code: 'CODE-L' });
    const port = await host.listen(0);
    const p = await P.pair(port, 'CODE-L');
    const s = await P.handshake(port, c, { device: p.body.device, key: p.key });
    let n = 0;
    const call = async (op, args) => {
        const id = 'l' + (++n);
        s.send({ kind: 'call', id, op, ...(args ? { args } : {}) });
        return s.waitFor(f => f.kind === 'result' && f.id === id, 4000);
    };
    const stats = async () => (await P.request(port, 'GET', '/_test/log')).body.live;

    // ---- a full watch of a turn, from its first frame to its answer
    s.send({ kind: 'watch', watch: 'w1', session: 'c_psy', depth: 'full' });
    const first = await s.waitFor(f => f.kind === 'live' && f.watch === 'w1', 1000);
    t.ok(first && first.session === 'c_psy' && JSON.stringify(Object.keys(first.data).sort()) === JSON.stringify(DATA),
        'the first frame comes at once and carries the contract\'s fields: ' + (first && Object.keys(first.data)));
    t.ok(first && first.data.status === 'idle' && first.data.turnSince === null && first.data.step === null && first.data.messages.length === 0,
        'an idle card says so, and the first frame tells no rows (what is there is read with sessions.read)');
    await P.tick(BEAT.full * 3);
    t.ok(lives(s, 'w1').length === 1, 'a card where nothing changes is not told again');

    await P.request(port, 'POST', '/_test/turn', { id: 'c_psy', stepMs: BEAT.full + 50, answer: 'Notlar hazır.' });
    const done = await s.waitFor(f => f.kind === 'live' && f.watch === 'w1' && f.data.status === 'idle' && f.data.messages.length > 0, 5000);
    const beats = lives(s, 'w1');
    const rows = beats.flatMap(x => x.f.data.messages);
    t.ok(!!done && rows.map(r => r.role).join() === 'user,tool,toolResult,tool,toolResult,assistant',
        'the turn arrives row by row in sessions.read\'s roles: ' + rows.map(r => r.role).join());
    t.ok(rows.every(r => Object.keys(r).every(k => WORDS.includes(k)) && ['key', 'turn', 'role', 'ts'].every(k => k in r)),
        'every row speaks only the contract\'s words and carries key, turn, role, ts');
    t.ok(new Set(rows.map(r => r.key)).size === rows.length, 'no row is told twice');
    t.ok(beats.some(x => x.f.data.status === 'working' && x.f.data.step && x.f.data.step.tool === 'Bash' && typeof x.f.data.turnSince === 'number'),
        'while a call runs the frame names it as the step, with the turn\'s start');
    t.ok(done && done.data.turnSince === null && done.data.step === null && rows[rows.length - 1].text === 'Notlar hazır.',
        'the frame that ends the turn carries the answer and no step');
    t.ok(minGap(beats.slice(1)) >= BEAT.full - 30, `full frames are a beat apart at least (closest ${minGap(beats.slice(1))} ms)`);

    const psy = (await call('console.sessions', { id: 'c_psy' })).result[0];
    const read = (await call('sessions.read', { session: psy.sessionId, roles: 'all', from: -rows.length })).result.messages;
    t.ok(read.map(m => `${m.role}|${m.text || ''}|${m.tool || ''}|${m.ts}`).join() === rows.map(m => `${m.role}|${m.text || ''}|${m.tool || ''}|${m.ts}`).join(),
        'the rows told live are the rows sessions.read gives afterwards');

    // ---- unwatch, and watches the farm cannot keep
    t.ok((await stats()).watches === 1, 'the log counts the open watch');
    s.send({ kind: 'unwatch', watch: 'w1' });
    await P.tick(50);
    const told = lives(s, 'w1').length;
    await P.request(port, 'POST', '/_test/turn', { id: 'c_psy', stepMs: 40 });
    await P.tick(600);
    t.ok(lives(s, 'w1').length === told && (await stats()).watches === 0, 'after unwatch nothing more is told');
    for (const [watch, session, depth] of [['w2', 'c_nope', 'full'], ['w3', '*', 'full'], ['w4', 'c_dev', 'everything']]) {
        s.send({ kind: 'watch', watch, session, depth });
        const no = await s.waitFor(f => f.kind === 'unwatch' && f.watch === watch, 1000);
        t.ok(no && no.reason === 'unavailable', `a watch of ${session} at ${depth} is answered unwatch unavailable`);
    }

    // ---- a summary of every card
    s.send({ kind: 'watch', watch: 'w5', session: '*', depth: 'summary' });
    await P.tick(100);
    const all = lives(s, 'w5');
    t.ok(all.map(x => x.f.session).join() === 'c_dev,c_psy,c_old' && all.every(x => !('messages' in x.f.data)),
        'a watch of every card tells each card once, without rows');
    await P.request(port, 'POST', '/_test/turn', { id: 'c_dev', stepMs: 150 });
    await P.tick(2300);
    const dev = lives(s, 'w5').filter(x => x.f.session === 'c_dev');
    t.ok(dev.length >= 2 && minGap(dev) >= BEAT.summary - 30, `a summary tells a card at most once a second (${dev.length} frames, closest ${minGap(dev)} ms)`);
    t.ok(lives(s, 'w5').filter(x => x.f.session === 'c_old').length === 1, 'a card that does not change is told once');
    const log = await stats();
    t.ok(log.frames >= lives(s, 'w5').length && log.rows >= rows.length && log.bytes > 0, 'the log counts the frames, rows and bytes told');

    // ---- now.summary (vsc core/nowSpoken.ts): the sentence, its counts, and an open question as its ask.opened said it
    await P.request(port, 'POST', '/_test/turn', { id: 'c_psy', stepMs: 20, ask: { question: 'Hangi dal?', options: ['master', 'yeni dal'] } });
    const opened = await s.waitFor(f => f.kind === 'event' && f.type === 'ask.opened' && f.data.session === 'c_psy', 2000);
    const sum = (await call('now.summary', { lang: 'tr-TR' })).result;
    t.ok(K.checkOp(c, 'now.summary', sum) === null, 'now.summary carries the contract\'s fields: ' + K.checkOp(c, 'now.summary', sum));
    t.ok(sum.spoken.lang === 'tr' && sum.waiting === 2 && sum.asks.length === 1 && opened && JSON.stringify(sum.asks[0]) === JSON.stringify(opened.data),
        'in the language asked, the open question counted beside the permission and given as its ask.opened data');
    await call('console.answer', { id: 'c_psy', answer: 'master' });
    const after = (await call('now.summary')).result;
    t.ok(after.spoken.lang === 'en' && after.asks.length === 0 && after.waiting === 1, 'an answered question is no longer among the asks');

    // ---- a closed socket ends its watches
    s.conn.close();
    await P.tick(100);
    t.ok((await stats()).watches === 0, 'a closed socket\'s watches end with it');

    t.ok(s.bad.length === 0, 'every frame the scripted farm sent passes the contract checker: ' + s.bad.join('; '));
    await host.close();
}

main().then(() => t.done(), e => { t.ok(false, 'threw: ' + (e && e.stack)); t.done(); });
