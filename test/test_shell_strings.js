// The shell's own words live in res/values/strings.xml, not in Kotlin: a screen, a TalkBack label or a
// notification channel names what it shows through R.string. Core verdict texts stay in core/ (pure
// Kotlin, JVM-tested, no Android resources). Every R.string a source names exists, every string is used,
// and an apostrophe is escaped (an unescaped one fails the Android resource build).
'use strict';
const { read, walk, suite } = require('./lib');

const t = suite();

const SHOWN = [
    /\bText\(\s*"/,
    /\bIcon\([^,()]+,\s*"/,
    /\b(?:contentDescription|label|onClickLabel|onLongClickLabel|title|detail|verb|message)\s*=\s*"/,
];
const TURKISH = /[çğıöşüÇĞİÖŞÜ]/;

function stripComments(src) {
    return src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:"])\/\/.*$/gm, '$1');
}

/** Words a line shows the user: a literal in a showing position, or any literal with a Turkish letter. */
function offenders(src) {
    const out = [];
    for (const line of stripComments(src).split('\n')) {
        if (/\bLog\.[a-z]\(/.test(line)) { continue; }
        if (SHOWN.some(re => re.test(line))) { out.push(line.trim()); continue; }
        for (const m of line.matchAll(/"((?:[^"\\\n]|\\.)*)"/g)) {
            if (TURKISH.test(m[1])) { out.push(line.trim()); break; }
        }
    }
    return out;
}

// The detector bites, and passes the shapes the shell legitimately writes.
t.ok(offenders('Text("Unut")').length === 1, 'a literal handed to Text is caught');
t.ok(offenders('Icon(Icons.Filled.Clear, "Temizle")').length === 1, 'an icon\'s literal TalkBack label is caught');
t.ok(offenders('title = "${farm.name} unutulsun mu?",').length === 1, 'a literal dialog title is caught');
t.ok(offenders('return text(503, "Çiftlik bağlı değil.")').length === 1, 'any literal with Turkish words is caught');
t.ok(offenders('Text(stringResource(R.string.forget))').length === 0, 'a resource is not an offender');
t.ok(offenders('Modifier.testTag("forget-${farm.id}")').length === 0, 'a test tag is not shown words');
t.ok(offenders('Log.i(TAG, "blocked ${request.url}")').length === 0, 'a log line is not shown words');

const BASE = 'app/src/main/java/com/muvusoft/agentfarm/';
const shell = walk(BASE + 'ui', ['.kt']).concat([BASE + 'net/AlertPoster.kt']);
t.ok(shell.length > 5, 'the shell\'s screens are found');
for (const f of shell) {
    for (const line of offenders(read(f))) {
        t.ok(false, `${f}: shown words written in Kotlin, not strings.xml: ${line}`);
    }
}

const xml = read('app/src/main/res/values/strings.xml');
const defined = new Map([...xml.matchAll(/<string name="([a-z0-9_]+)"[^>]*>([\s\S]*?)<\/string>/g)].map(m => [m[1], m[2]]));
t.ok(defined.size > 20, 'strings.xml holds the shell\'s words');
const sources = walk('app/src/main', ['.kt', '.xml']).filter(f => !/res\/values\/strings\.xml$/.test(f)).map(read).join('\n');
const used = new Set([...sources.matchAll(/\bR\.string\.([a-z0-9_]+)|@string\/([a-z0-9_]+)/g)].map(m => m[1] || m[2]));
for (const name of used) { t.ok(defined.has(name), `R.string.${name} is named in code but missing from strings.xml`); }
for (const name of defined.keys()) { t.ok(used.has(name), `strings.xml "${name}" is used nowhere`); }
for (const [name, value] of defined) {
    t.ok(!/(^|[^\\])'/.test(value), `strings.xml "${name}": an apostrophe must be escaped as \\'`);
}

// The app speaks Agent Farm's own language, English: a Turkish letter in anything it shows is a
// translation that never happened. The voice and dictation language is the user's, not a shown word.
for (const [name, value] of defined) {
    t.ok(!TURKISH.test(value), `strings.xml "${name}" is English, like Agent Farm: ${value}`);
}
const literals = src => [...stripComments(src).matchAll(/"((?:[^"\\\n]|\\.)*)"/g)].map(m => m[1]);
t.ok(literals('val a = "Bağlı"; // "yorum"').length === 1 && TURKISH.test(literals('x("Bağlı")')[0]), 'the literal reader finds a Turkish literal and skips comments');
for (const f of walk('app/src/main/java', ['.kt'])) {
    for (const s of literals(read(f))) {
        t.ok(!TURKISH.test(s), `${f}: shown words must be English, like Agent Farm: "${s}"`);
    }
}

t.done();
