// The scripted farm the journey tests stand on: it answers in the shapes the contract declares for the ops the
// phone reads (its "ops", with Agent Farm's real answers as examples), pages the history 20 light rows at a time
// after a cursor, pages a 2,000-message conversation the way sessions.read does, moves turns over time with the
// contract's events, delays every frame by its network profile and logs what was asked, finding any call that
// asked past a page.
'use strict';
const { suite } = require('./lib');
const { createHost } = require('../tools/fakehost/host');
const K = require('../tools/fakehost/contract');
const P = require('./fakehost_client');

const t = suite();
const c = K.load();
const ex = op => c.ops[op].example;

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

    // ---- the contract's shapes
    const rows = (await call('console.sessions', { messages: 4 })).result;
    const dev = rows.find(r => r.id === 'c_dev');
    const old = rows.find(r => r.id === 'c_old');
    const [xDev] = ex('console.sessions');
    t.ok(K.checkOp(c, 'console.sessions', rows) === null, 'console.sessions rows carry the contract\'s fields: ' + K.checkOp(c, 'console.sessions', rows));
    t.ok(shape(xDev.messages[0], dev.messages[0], 'message', []).length === 0, 'a console.sessions message carries the farm\'s fields: ' + shape(xDev.messages[0], dev.messages[0], 'message', []).join('; '));
    const bare = (await call('console.sessions')).result;
    t.ok(bare.every(r => !('messages' in r)), 'console.sessions without messages carries none (the farm\'s default is 0)');
    const summary = (await call('console.sessions', { view: 'summary' })).result;
    t.ok(K.checkOp(c, 'console.sessions', summary) === null && summary.every(r => Object.keys(r).every(k => k in c.ops['console.sessions'].row)),
        'console.sessions view summary is cut to the contract\'s row');
    const { agent: _gone, ...noAgent } = summary[0];
    t.ok(/row 0: missing agent/.test(K.checkOp(c, 'console.sessions', [noAgent]) || '') && /rows is not/.test(K.checkOp(c, 'sessions.recent', { rows: {}, next: null }) || ''),
        'the contract check refuses a row missing a declared field and a list that is not one');
    t.ok(JSON.stringify(summary).length * 2 < JSON.stringify(bare).length, `the summary is less than half the full rows (${JSON.stringify(summary).length} of ${JSON.stringify(bare).length} bytes)`);
    t.ok(rows.map(r => r.status).join() === 'working,idle,ended' && dev.steerable === true && old.steerable === false,
        'the three cards keep the states the shell tests rely on: working, idle, ended');
    t.ok(['user', 'agent', 'tool'].includes(dev.messages[3].role) && dev.messages.every(m => m.role !== 'assistant'),
        'console.sessions speaks the Console\'s words (agent, tool), never sessions.read\'s');

    // ---- the history, 20 light rows at a time after a cursor
    const first = (await call('sessions.recent', { view: 'page' })).result;
    t.ok(K.checkOp(c, 'sessions.recent', first) === null && first.rows.length === 20 && typeof first.next === 'string',
        'the page view gives 20 light rows and the next page\'s cursor: ' + K.checkOp(c, 'sessions.recent', first));
    t.ok(first.rows.every(r => Object.keys(r).every(k => k in c.ops['sessions.recent'].row)), 'a light row carries only the contract\'s fields');
    const seen = [...first.rows];
    let next = first.next;
    let pages = 1;
    while (next) { const p = (await call('sessions.recent', { view: 'page', cursor: next })).result; seen.push(...p.rows); next = p.next; pages++; }
    t.ok(seen.length === 500 && new Set(seen.map(r => r.id)).size === 500 && pages === 25, `pages joined are the 500 sessions once and the last says next null (${pages} pages)`);
    t.ok(seen.every((r, i) => i === 0 || seen[i - 1].lastTs >= r.lastTs), 'newest first across the pages');
    const devRow = seen.find(r => r.id === dev.sessionId);
    t.ok(devRow.status === 'working' && !!devRow.lastLine && seen.filter(r => r.status).length === 3 && seen.filter(r => r.waiting).length === 1,
        'a card\'s session says its status, a session says its last answer\'s line, the one with a need waits');
    const talkEnd = r => host.world.steps(host.world.bySession(r.id)).filter(x => x.role === 'user' || x.role === 'assistant').pop();
    const answered = seen.filter(r => talkEnd(r).role === 'assistant');
    t.ok(answered.length > 0 && answered.length < seen.length && seen.every(r => !!r.lastLine === (talkEnd(r).role === 'assistant')),
        `a last line is the answer of the turn; a prompt not answered yet has none, as vsc lastSaid (${answered.length} of ${seen.length} answered)`);
    t.ok((await call('sessions.recent')).result.length === 10, 'the plain sessions.recent keeps the farm\'s default 10');
    const all = (await call('sessions.recent', { limit: 600 })).result;
    t.ok(all.length === 500, 'the history holds 500 sessions');

    const needs = (await call('needs.list')).result;
    t.ok(K.checkOp(c, 'needs.list', needs) === null, 'needs.list and its items carry the contract\'s fields: ' + K.checkOp(c, 'needs.list', needs));
    t.ok(needs.items[0].key === `permission|${needs.items[0].ref.sessionId}`, 'a permission need is keyed the way vsc needKey keys it');
    const menu = (await call('pages.list')).result;
    t.ok(K.checkOp(c, 'pages.list', menu) === null && menu.pages.length > 0, 'pages.list and its rows carry the contract\'s fields: ' + K.checkOp(c, 'pages.list', menu));

    // ---- a 2,000-message conversation, paged like sessions.read
    const sid = dev.sessionId;
    const last = (await call('sessions.read', { session: sid, roles: 'all', from: -30, limit: 30 })).result;
    t.ok(K.checkOp(c, 'sessions.read', last) === null, 'sessions.read carries the contract\'s page and row fields: ' + K.checkOp(c, 'sessions.read', last));
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
    const pageCalls = log.calls.filter(x => x.op === 'sessions.recent' && x.args && x.args.view === 'page');
    t.ok(pageCalls.length === 25 && pageCalls.every(x => x.rows <= 20) && !log.findings.some(f => pageCalls.some(x => f.startsWith(x.id + ' '))),
        'the page view asked without a limit is one page, never a finding');

    // ---- turns over time: a question parks the turn until it is answered
    await P.request(port, 'POST', '/_test/scenario', {});
    t.ok((await P.request(port, 'GET', '/_test/log')).body.calls.length === 0, 'a new scenario empties the log');
    const started = (await P.request(port, 'POST', '/_test/turn', { id: 'c_psy', stepMs: 20, ask: { question: 'Notları özetleyeyim mi?', options: ['Evet', 'Hayır'] }, answer: 'Özet hazır.' })).body;
    t.ok(started.started === true, 'a scripted turn starts on an idle card');
    const asked = await s.waitFor(f => f.kind === 'event' && f.type === 'ask.opened' && f.data.session === 'c_psy', 3000);
    t.ok(asked && asked.data.options.length === 2, 'the turn parks on its question and the phone hears ask.opened');
    const psy = (await call('console.sessions', { id: 'c_psy', messages: 3 })).result[0];
    t.ok(psy.status === 'working' && psy.asking && psy.asking.kind === 'question', 'while it waits the card says working and asking');
    t.ok(shape(xDev.asking, psy.asking, 'asking', []).length === 0 && shape(xDev.asking.questions[0], psy.asking.questions[0], 'question', []).length === 0,
        'asking carries the fields of the farm\'s own (the contract\'s example): ' + shape(xDev.asking, psy.asking, 'asking', []).join('; '));
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

    // ---- words into an idle card start its turn with them (vsc remoteSay -> ConsoleHost.send); the gate's question starts nothing
    const psyOf = async () => (await call('console.sessions', { view: 'summary' })).result.find(r => r.id === 'c_psy');
    const gated = (await call('console.send', { id: 'c_psy', text: 'rm -rf build' })).result;
    t.ok(gated.sent === false && (await psyOf()).status === 'idle', 'a send the farm\'s gate stops starts no turn');
    const took = (await call('console.send', { id: 'c_psy', text: 'Sürüm notunu yaz' })).result;
    const sent = await psyOf();
    const heard = (await call('sessions.read', { session: sent.sessionId, from: -1, roles: 'all' })).result.messages;
    t.ok(typeof took.note === 'string' && sent.status === 'working' && heard[0].role === 'user' && heard[0].text === 'Sürüm notunu yaz',
        'words sent into an idle card start its turn, their user step last in the record');
    await P.request(port, 'POST', '/_test/scenario', {});

    // ---- a card's permission is a Needs row of its session (vsc needs.ts), needs.act answers it; the PC answering closes a question
    const pause = ms => new Promise(r => setTimeout(r, ms));
    await P.request(port, 'POST', '/_test/turn', { id: 'c_psy', stepMs: 20, permission: { tool: 'Bash', summary: 'rm -rf build' } });
    await pause(160);
    const parkedSid = (await psyOf()).sessionId;
    const listedNeeds = (await call('needs.list')).result;
    const permRow = listedNeeds.items.find(i => i.key === `permission|${parkedSid}`);
    t.ok(permRow && permRow.kind === 'permission' && permRow.agentName === 'psikolog' && permRow.detail === 'rm -rf build' && K.checkOp(c, 'needs.list', listedNeeds) === null,
        'a card parked on a permission is a permission row of its session in needs.list');
    const permKey = permRow ? permRow.key : 'none';
    await call('needs.act', { key: permKey, action: 'permission-allow' });
    await pause(80);
    const allowed = await psyOf();
    t.ok(allowed.status === 'idle' && !allowed.asking && !(await call('needs.list')).result.items.some(i => i.key === permKey), 'needs.act on that row lets the turn go on, and the row goes');
    await P.request(port, 'POST', '/_test/turn', { id: 'c_psy', stepMs: 20, ask: { question: 'Hangi dal?', options: ['master', 'yeni dal'] } });
    await pause(160);
    const asking = (await psyOf()).asking;
    const settled = await P.request(port, 'POST', '/_test/answer', { id: 'c_psy', answer: 'master' });
    await pause(80);
    t.ok(asking && asking.kind === 'question' && settled.body.settled === true && !(await psyOf()).asking, 'the PC answering a question closes it on the card');
    await P.request(port, 'POST', '/_test/scenario', {});

    // ---- a new session the phone's way (vsc phoneNew + openNew): the card it opens is in the Console list at once
    const listed = (await call('console.new')).result;
    t.ok(Array.isArray(listed.agents) && listed.agents.includes('developer'), 'console.new called bare lists the agents a session opens with');
    const opened = (await call('console.new', { agent: 'developer', prompt: 'Testleri çalıştır' })).result;
    const card = (await call('console.sessions', { view: 'summary' })).result.find(r => r.id === opened.id);
    t.ok(card && card.status === 'working' && card.agent === 'developer', 'with an agent and words it opens a Console card whose first turn runs');
    t.ok(opened.note === '▶ developer — a Console session is open; its first turn is running.', 'its note is openNew\'s own sentence: ' + opened.note);
    const waits = (await call('console.new', { agent: 'psikolog' })).result;
    t.ok(/; it waits for its first message\.$/.test(waits.note), 'with no first message the card waits for one: ' + waits.note);
    const said = (await call('sessions.read', { session: card.sessionId, roles: 'all' })).result;
    t.ok(said.messages.length === 1 && said.messages[0].role === 'user' && said.messages[0].text === 'Testleri çalıştır', 'the new card\'s session is named and holds its first message');
    const lastCard = (await call('console.sessions', { view: 'summary' })).result.find(r => r.id === waits.id);
    const top = (await call('sessions.recent', { view: 'page' })).result.rows[0];
    t.ok(top.id === lastCard.sessionId, 'the session opened last is the newest in the history');
    // The live channel ticks every card; one opened this way must never stop it (a card with no steps is quiet, not a crash).
    const watched = await (async () => { s.send({ kind: 'watch', watch: 'wn', session: '*', depth: 'summary' }); return s.waitFor(f => f.kind === 'live' && f.watch === 'wn' && f.session === opened.id, 3000); })();
    t.ok(watched.data.status === 'working', 'the live channel tells the new card');
    s.send({ kind: 'unwatch', watch: 'wn' });
    await P.request(port, 'POST', '/_test/scenario', {});
    t.ok(!(await call('console.sessions')).result.some(r => r.id === opened.id), 'a new scenario takes the opened cards away');

    // quiet: the farm an idle link rests on (the phone's core/power/LinkRest) - no turn runs, nothing waits.
    await P.request(port, 'POST', '/_test/scenario', { quiet: true });
    const calm = (await call('console.sessions', { view: 'summary' })).result;
    t.ok(calm.length > 0 && !calm.some(r => r.status === 'working'), 'a quiet farm runs no turn: ' + calm.map(r => r.status).join(','));
    t.ok((await call('needs.list')).result.items.length === 0, 'a quiet farm has nothing waiting');
    t.ok((await P.request(port, 'GET', '/_test/state')).body.sockets === 1, 'the farm counts the welcomed sockets (a resting phone has none)');
    await P.request(port, 'POST', '/_test/scenario', {});
    t.ok((await call('console.sessions', { view: 'summary' })).result.some(r => r.status === 'working'), 'the default scenario runs a turn again');

    t.ok(s.bad.length === 0, 'every frame the scripted farm sent passes the contract checker: ' + s.bad.join('; '));
    await host.close();
}

main().then(() => t.done(), e => { t.ok(false, 'threw: ' + (e && e.stack)); t.done(); });
