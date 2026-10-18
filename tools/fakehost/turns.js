// Turns that move over time. A scripted turn on a Console card writes the user's message, the tool steps and
// the answer into its conversation the way the real farm records them, moves the card working -> idle,
// parks on a question or a permission until it is answered, and sends the contract's events
// (ask.opened / permission.opened, ask.closed / permission.closed, turn.finished).
'use strict';

const SUMMARY_MAX = 400;

function createTurns(f, emit) {
    const running = new Map();

    function start(id, plan = {}) {
        const row = f.card(id);
        if (!row) { return { error: `no Console session ${id}` }; }
        if (running.has(id)) { return { error: `${id} is already running a turn` }; }
        const s = f.bySession(row.sessionId);
        const steps = f.steps(s);
        const stepMs = plan.stepMs ?? 400;
        const turn = steps.reduce((m, x) => Math.max(m, x.turn || 0), 0) + 1;
        const t = { timers: [], at: 0, turn, plan, row, s };
        running.set(id, t);
        const push = step => { const st = { ...step, turn, ts: Date.now() }; steps.push(st); s.lastTs = st.ts; return st; };
        t.push = push;
        const later = fn => { t.at += stepMs; t.timers.push(setTimeout(fn, t.at)); };
        t.later = later;

        Object.assign(row, { status: 'working', lastTurn: null, turnModel: row.applied.model, turnTokens: 0, turnApiCalls: 0, asking: null });
        row.turns++;
        t.startedAt = Date.now();
        push({ role: 'user', text: plan.text || 'Testleri çalıştır ve sonucu söyle.' });
        for (const [tool, input] of plan.tools || [['Bash', 'node test/run-all.js'], ['Read', 'docs/test-plan.md']]) {
            later(() => {
                push({ role: 'tool', tool, input });
                row.turnApiCalls++;
                row.turnTokens += 3000;
                row.tokens += 3000;
                row.cost = Math.round((row.cost + 0.02) * 100) / 100;
            });
            later(() => push({ role: 'toolResult', text: 'ok' }));
        }
        if (plan.ask || plan.permission) { later(() => park(id)); } else { later(() => finish(id, 'done')); }
        return { started: true, id, turn, ms: t.at };
    }

    function park(id) {
        const t = running.get(id);
        const { row, plan } = t;
        const waitingSince = Date.now();
        if (plan.ask) {
            const a = plan.ask;
            const options = (a.options || ['Evet', 'Hayır']).map(label => ({ label }));
            t.askId = 'q' + t.turn;
            row.asking = { kind: 'question', tool: 'AskUserQuestion', questions: [{ question: a.question, header: a.header || 'Soru', multiSelect: false, options }], detail: a.question, waitingSince, autoAnswer: null };
            // kept as said: now.summary's asks are this very data (vsc askOpenedData)
            t.opened = { session: id, agent: row.agent, askId: t.askId, question: a.question, options };
            emit('ask.opened', { ...t.opened });
        } else {
            const p = plan.permission;
            t.permId = 'p' + t.turn;
            row.asking = { kind: 'permission', tool: p.tool, questions: [], detail: p.summary, waitingSince, autoAnswer: null };
            row.pendingPermission = p.summary;
            emit('permission.opened', { session: id, agent: row.agent, permId: t.permId, tool: p.tool, summary: p.summary, key: `permission|${row.sessionId}` });
        }
    }

    /** The answer reached the farm (console.answer, sessions.answerPermission): the turn goes on. */
    function settle(id, answer) {
        const t = running.get(id);
        if (!t || !t.row.asking) { return false; }
        const { row } = t;
        if (t.askId) { emit('ask.closed', { session: id, askId: t.askId }); } else { emit('permission.closed', { session: id, permId: t.permId }); }
        row.asking = null;
        row.pendingPermission = null;
        t.push({ role: 'toolResult', text: String(answer ?? '') });
        t.at = 0;
        t.later(() => finish(id, 'done'));
        return true;
    }

    function finish(id, outcome) {
        const t = running.get(id);
        if (!t) { return; }
        running.delete(id);
        t.timers.forEach(clearTimeout);
        const { row, s, plan } = t;
        const answer = outcome === 'done' ? (plan.answer || 'Bütün testler geçti.') : '';
        if (answer) { t.push({ role: 'assistant', text: answer, model: row.applied.model }); }
        Object.assign(row, {
            status: 'idle', asking: null, pendingPermission: null, turnModel: null,
            lastTurn: { endedAt: Date.now(), ms: Date.now() - t.startedAt, tokens: row.turnTokens, wTokens: 0, cost: 0.02 * row.turnApiCalls, apiCalls: row.turnApiCalls, steps: row.turnApiCalls, thinking: 0, outcome, model: row.applied.model, effort: null },
        });
        if (answer) { emit('turn.finished', { session: id, agent: row.agent, title: s.title, summary: answer.length > SUMMARY_MAX ? '…' + answer.slice(answer.length - SUMMARY_MAX + 1) : answer }); }
    }

    /** console.interrupt: a scripted turn stops where it is. The world's own working card stays working, so
     *  the tests that run after a stop still find a turn to stop; a journey that needs it gone starts a turn. */
    const stop = id => { if (!running.has(id)) { return false; } finish(id, 'interrupted'); return true; };
    const clear = () => { for (const t of running.values()) { t.timers.forEach(clearTimeout); } running.clear(); };

    return { start, settle, stop, clear, running };
}

module.exports = { createTurns };
