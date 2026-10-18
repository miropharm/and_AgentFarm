// now.summary as the scripted farm says it (vsc core/nowSpoken.ts): one sentence of what waits and what runs, in
// the language asked, its two counts, and the Console questions among the waits as their ask.opened carried them.
'use strict';

const WORDS = {
    en: (w, r) => `${w} ${w === 1 ? 'thing waits' : 'things wait'} for you. ${r} ${r === 1 ? 'session is' : 'sessions are'} running.`,
    tr: (w, r) => `Seni bekleyen ${w} şey var. ${r} oturum çalışıyor.`,
};

function summaryOf(world, turns, args) {
    const tag = String((args && args.lang) || 'en').toLowerCase().split(/[-_]/)[0];
    const lang = WORDS[tag] ? tag : 'en';
    const asks = [...turns.running.values()].filter(t => t.opened && t.row.asking).map(t => ({ ...t.opened }));
    const waiting = world.ops['needs.list']().items.length + asks.length;
    const running = world.rows.filter(r => r.status === 'working' && !r.asking).length;
    return { spoken: { text: WORDS[lang](waiting, running), lang }, waiting, running, asks };
}

module.exports = { summaryOf };
