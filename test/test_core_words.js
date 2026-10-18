// The core's sentences (pure Kotlin, no Android resources) are translated through core/lang/Words: the English
// sentence is the key. Every Words.t key the core uses is in every catalog, a catalog holds nothing else, a key and
// its translation have the same {n} blanks, a key is a plain literal (a $ template would never be found), and the
// catalogs' languages are exactly the res/values-<lang> translations, so a screen's words and its verdicts agree.
'use strict';
const { read, walk, suite } = require('./lib');

const t = suite();
const CORE = 'app/src/main/java/com/muvusoft/agentfarm/core/';
const LIT = '"((?:[^"\\\\\\n]|\\\\.)*)"';

// Words.t in the app's language, Words.tIn(lang, ...) in a named one (the read-aloud voice's).
const usedIn = src => [...src.matchAll(new RegExp('\\bWords\\.t(?:\\(|In\\(\\s*[\\w.]+\\s*,)\\s*' + LIT, 'g'))].map(m => m[1]);
const pairsIn = src => [...src.matchAll(new RegExp(LIT + '\\s+to\\s*\\n?\\s*' + LIT, 'g'))].map(m => [m[1], m[2]]);
const blanks = s => (s.match(/\{\d\}/g) || []).sort().join(',');

// The readers bite.
t.ok(JSON.stringify(usedIn('Words.t("A {0}", x) + Words.t(\n "B") + Words.tIn(lang, "C")')) === '["A {0}","B","C"]', 'the key reader finds keys, also on the next line and in a named language');
t.ok(JSON.stringify(pairsIn('"a {0}" to "b {0}",\n    "c" to\n        "d",')) === '[["a {0}","b {0}"],["c","d"]]', 'the catalog reader finds pairs, also wrapped');

const used = new Map();
for (const f of walk(CORE, ['.kt']).filter(f => !/core[\\/]lang[\\/]Words/.test(f))) {
    for (const k of usedIn(read(f))) { used.set(k, f); }
}
t.ok(used.size > 20, 'the core\'s Words.t keys are found');
for (const [k, f] of used) {
    t.ok(!/\$/.test(k), `${f}: a Words.t key is a plain literal, never a template: "${k}"`);
}

const catalogs = walk(CORE + 'lang', ['.kt']).filter(f => /Words[A-Z][a-z]\.kt$/.test(f));
const words = read(CORE + 'lang/Words.kt');
t.ok(catalogs.length >= 1, 'a catalog is found (WordsTr.kt)');
const langs = [];
for (const f of catalogs) {
    const lang = /Words([A-Z][a-z])\.kt$/.exec(f)[1].toLowerCase();
    langs.push(lang);
    const map = new Map(pairsIn(read(f)));
    t.ok(new RegExp(`"${lang}" to ${/val (\w+)/.exec(read(f))[1]}`).test(words), `Words.kt serves the ${lang} catalog`);
    for (const k of used.keys()) { t.ok(map.has(k), `${f}: "${k}" is not translated`); }
    for (const [k, v] of map) {
        t.ok(used.has(k), `${f}: "${k}" is used by no Words.t in core/`);
        t.ok(blanks(k) === blanks(v), `${f}: "${k}" and its translation have the same blanks`);
    }
}
const resLangs = walk('app/src/main/res', ['.xml']).map(f => /res[\\/]values-([a-z]{2,3})[\\/]strings\.xml$/.exec(f)).filter(Boolean).map(m => m[1]);
t.ok(JSON.stringify([...langs].sort()) === JSON.stringify([...resLangs].sort()), `the core's catalogs (${langs}) are the strings.xml translations (${resLangs})`);

t.done();
