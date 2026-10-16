// The scripted farm's world: the Console's live sessions, a long history, conversations up to 2,000 messages
// and the Needs queue, answered in Agent Farm's real shapes (held to the contract's "ops"). Seeded, so the
// same farm is drawn on every run and a journey's numbers compare run to run.
'use strict';
const { readPage } = require('./read');
const { recentPage, summaryRow } = require('./views');

// vsc src/core/consoleEngine.ts: the engine table the Console rows carry.
const ENGINES = {
    claude: { label: 'Claude Code', shape: 'persistent', steerable: true, reportsCost: true, costSource: 'cli', effort: true },
    codex: { label: 'Codex', shape: 'perTurn', steerable: false, reportsCost: false, costSource: 'tokens', effort: true },
    gemini: { label: 'Gemini', shape: 'perTurn', steerable: false, reportsCost: false, costSource: 'tokens', effort: false },
};
const AGENTS = ['developer', 'psikolog', 'yazar', 'asistan', 'muhasebe'];
const TITLES = ['Telefon ana ekranı', 'Bildirim kanalları', 'Haftalık değerlendirme', 'Bülten taslağı', 'Fatura kontrolü',
    'Test kapısı', 'Gece mesaisi', 'Pil politikası', 'Sözleşme eşleşmesi', 'Sesli okuma', 'Kitap bölüm 4', 'CI düzeltmesi'];
const ASKS = ['Testleri çalıştır ve sonucu söyle.', 'Şu hatanın kökünü bul.', 'Bunu kısaca özetle.', 'Bir de koyu temayı kontrol et.', 'Değişikliği commit et.'];
const TOOLS = [['Read', 'app/src/main/java/com/muvusoft/agentfarm/ui/ShellApp.kt'], ['Bash', 'node test/run-all.js'],
    ['Grep', 'console.sessions'], ['Edit', 'core/now/NowState.kt'], ['Bash', './gradlew testDebugUnitTest']];
const SAYS = ['Bakıyorum; önce ilgili dosyayı okuyorum.', 'Bütün testler geçti.', 'Kök neden bulundu: durum adı "working", "running" değil.',
    'Değişiklik yerinde; CI koşusu bekleniyor.', 'Özet: üç iş kapandı, biri açık.'];
const MODEL = { claude: 'claude-opus-4-1', codex: 'gpt-5-codex', gemini: 'gemini-2.5-pro' };
const MIN = 60000;

function rng(seed) {
    let s = seed >>> 0;
    return () => { s = (Math.imul(s, 1664525) + 1013904223) >>> 0; return s / 4294967296; };
}
const pick = (r, list) => list[Math.floor(r() * list.length)];
const hex = (r, n) => Array.from({ length: n }, () => Math.floor(r() * 16).toString(16)).join('');
const uuid = r => `${hex(r, 8)}-${hex(r, 4)}-4${hex(r, 3)}-a${hex(r, 3)}-${hex(r, 12)}`;

/** A long markdown answer (past sessions.read's 4,000-character cut), the kind an agent really writes. */
function longAnswer(n) {
    const part = `## Bölüm ${n}\n\nİki tema hazır: **açık** ve **koyu**. Gövde yazısı en az 14 sp.\n\n- \`core/theme/Tokens.kt\` — renkler tek yerde\n- \`ui/theme/AppTheme.kt\` — Compose teması\n\n| Belirteç | Açık | Koyu |\n|---|---|---|\n| primary | #2E7D46 | #6FD58A |\n\n`;
    return part.repeat(20);
}

/** A conversation as the transcript page builds its steps: user, then tool calls and results, then the answer. */
function conversation(r, count, end, engine) {
    const steps = [];
    let turn = 0;
    while (steps.length < count) {
        turn++;
        steps.push({ role: 'user', text: pick(r, ASKS), turn });
        const calls = 1 + Math.floor(r() * 4);
        for (let k = 0; k < calls && steps.length < count - 1; k++) {
            const [tool, input] = pick(r, TOOLS);
            steps.push({ role: 'tool', tool, input, turn });
            steps.push({ role: 'toolResult', text: 'ok', turn, ...(r() < 0.1 ? { isError: true } : {}) });
        }
        if (steps.length < count) { steps.push({ role: 'assistant', text: turn % 25 === 0 ? longAnswer(turn) : pick(r, SAYS), turn, model: MODEL[engine] }); }
    }
    steps.length = count;
    const gap = 40000;
    steps.forEach((s, k) => { s.ts = end - (count - 1 - k) * gap; });
    return steps;
}

/** One live Console card in console.sessions' row shape (vsc src/extension.ts, op console.sessions). */
function consoleRow(o) {
    const e = ENGINES[o.engine];
    return {
        id: o.id, agent: o.agent, sessionId: o.sessionId, engine: o.engine, engineLabel: e.label, turnShape: e.shape,
        steerable: e.steerable, reportsCost: e.reportsCost, costSource: e.costSource, takesEffort: e.effort, route: null,
        status: o.status, model: null, effort: null, applied: { model: MODEL[o.engine], effort: e.effort ? 'high' : null },
        permissionMode: '', enforcedPermissionMode: 'default', permissionWhy: 'settings', bypassAutoApproved: 0,
        cost: o.cost, turns: o.turns, held: 0, tokens: o.turns * 9000, turnTokens: 0, turnApiCalls: 0,
        turnModel: o.status === 'working' ? MODEL[o.engine] : null, turnEffort: null,
        lastTurn: o.status === 'working' ? null : { endedAt: o.end, ms: 52000, tokens: 30000, wTokens: 9000, cost: 0.31, apiCalls: 4, steps: 9, thinking: 1, outcome: 'done', model: MODEL[o.engine], effort: null },
        backgroundTasks: [], contextTokens: null, contextWindowTokens: 200000, contextWindowSource: 'model', contextPct: null,
        contextSource: null, contextMeasuredAt: null, contextStale: null,
        contextSignal: null, contextSignalView: null, bridgeGuardView: null, batonView: null,
        pendingPermission: null, asking: null, exitReason: o.status === 'ended' ? 'closed' : null,
    };
}

/** console.sessions' message vocabulary: the model's reply is "agent", a call and its result are both "tool". */
function consoleMessage(s) {
    const role = s.role === 'assistant' ? 'agent' : s.role === 'user' ? 'user' : 'tool';
    return { role, text: s.role === 'tool' ? null : (s.text ?? null), tool: s.role === 'tool' ? `${s.tool} ${s.input || ''}`.trim() : null, isError: s.isError === true, ts: s.ts };
}

function createFarm(opts = {}) {
    const now = opts.now || Date.now();
    const r = rng(opts.seed || 7);
    const count = opts.history ?? 500;
    const sessions = [];
    for (let k = 0; k < count; k++) {
        const engine = k === 2 || k % 9 === 4 ? 'codex' : k % 13 === 6 ? 'gemini' : 'claude';
        const origin = k % 5 === 2 ? 'auto' : k % 7 === 3 ? 'baton' : 'user';
        sessions.push({
            id: uuid(r), agent: k < 3 ? ['developer', 'psikolog', 'yazar'][k] : pick(r, AGENTS), title: pick(r, TITLES),
            lastTs: now - 5000 - k * 47 * MIN - Math.floor(r() * 20 * MIN),
            providers: [], route: null, profile: null, engine, origin, headless: origin === 'auto',
            launchedBy: origin === 'user' ? null : { kind: origin === 'baton' ? 'task' : 'schedule', label: origin === 'baton' ? 'Baton — leg 3' : 'Sabah özeti', mode: origin === 'baton' ? 'interactive' : 'headless' },
            turns: 0, cost: 0, tokens: 0, wTokens: 0, producer: engine, storage: engine, provenance: 'native', canonicalId: null,
            importedFrom: null, importedAt: null, aliasOf: null, importAliases: [], scanLimit: null,
        });
    }
    const talks = new Map();
    const steps = s => {
        if (!talks.has(s.id)) {
            const k = sessions.indexOf(s);
            const n = k === 0 ? (opts.longest ?? 2000) : 6 + ((k * 7) % 40);
            talks.set(s.id, conversation(rng(1000 + k), n, s.lastTs, s.engine));
        }
        return talks.get(s.id);
    };
    for (const s of sessions) {
        s.canonicalId = s.id;
        s.turns = 1 + (sessions.indexOf(s) % 9);
        s.cost = Math.round(s.turns * 31) / 100;
        s.tokens = s.turns * 9000;
        s.wTokens = s.turns * 3000;
    }
    const live = [
        { id: 'c_dev', agent: 'developer', status: 'working', at: 0, cost: 4.12 },
        { id: 'c_psy', agent: 'psikolog', status: 'idle', at: 1, cost: 0.4 },
        { id: 'c_old', agent: 'yazar', status: 'ended', at: 2, cost: 0.84 },
    ];
    const rows = live.map(l => {
        const s = sessions[l.at];
        return consoleRow({ ...l, sessionId: s.id, engine: s.engine, turns: steps(s).filter(x => x.role === 'user').length, end: s.lastTs });
    });
    const needs = [{
        urgency: 0, kind: 'permission', agentName: 'asistan', title: 'Bash: git status', detail: 'git status',
        ts: now - 40000, ref: { sessionId: sessions[3].id },
        actions: [{ id: 'permission-allow', label: 'Allow' }, { id: 'permission-deny', label: 'Deny' }],
    }];

    const f = { now, sessions, rows, needs, steps };
    f.bySession = id => sessions.find(s => s.id === id || (id && id.length >= 8 && s.id.startsWith(id)));
    f.card = id => rows.find(x => x.id === id);

    const ops = {
        'console.sessions': a => {
            const take = typeof a.messages === 'number' ? Math.max(0, Math.floor(a.messages)) : 0;
            const out = rows.filter(x => !a.id || x.id === a.id).map(x => (take
                ? { ...x, messages: steps(f.bySession(x.sessionId)).slice(-take).map(consoleMessage) } : { ...x }));
            return a.view === 'summary' && opts.contract ? out.map(x => summaryRow(opts.contract, x)) : out;
        },
        'sessions.recent': a => {
            const limit = typeof a.limit === 'number' && a.limit > 0 ? Math.floor(a.limit) : 10;
            const origin = String(a.origin || '').toLowerCase();
            const selected = sessions.filter(s => !['user', 'auto', 'baton'].includes(origin) || s.origin === origin);
            if (a.view === 'page') { return recentPage(f, selected, a); }
            return selected.slice().sort((x, y) => y.lastTs - x.lastTs).slice(0, limit).map(s => ({ ...s }));
        },
        'sessions.read': a => {
            const ref = String(a.session || a.id || '');
            const s = f.bySession(ref);
            if (!s) { throw new Error(`No session matches "${ref}".`); }
            const page = readPage(steps(s), { roles: a.roles ?? a.role, turn: a.turn, from: a.from, limit: a.limit, ...(a.maxChars !== undefined ? { maxChars: a.maxChars } : {}), sidechain: a.sidechain });
            if (page.error) { throw new Error(page.error); }
            return { id: s.id, file: `C:\\Users\\me\\.claude\\projects\\farm\\${s.id}.jsonl`, engine: s.engine, title: s.title, agent: s.agent, ...page };
        },
        'needs.list': () => {
            const items = needs.map(i => ({ ...i, key: `${i.kind}|${i.ref.sessionId}`, clearable: false }));
            return { items, blocking: items.length, hidden: 0 };
        },
    };
    f.ops = ops;
    return f;
}

module.exports = { createFarm, consoleMessage, ENGINES };
