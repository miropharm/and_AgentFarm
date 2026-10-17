// Android compiles a Regex with ICU, which refuses a brace the JVM lets pass: an unescaped `}` (or a `{` that opens
// no {n}, {n,} or {n,m} count) outside a [...] set. The JVM unit tests then pass and the app dies at its first start
// (ExceptionInInitializerError on the device only). Every pattern literal in the app is read here, in seconds.
'use strict';
const { read, walk, suite } = require('./lib');

const t = suite();

/** Pattern literals: Regex("..."), Regex("""..."""), "...".toRegex(), Pattern.compile("..."), as the regex engine sees them. */
function patterns(src) {
    const out = [];
    const raw = /(?:\bRegex\(|Pattern\.compile\()\s*"""([\s\S]*?)"""/g;
    const plain = /(?:\bRegex\(|Pattern\.compile\()\s*"(?!"")((?:[^"\\\n]|\\.)*)"|(?<!")"((?:[^"\\\n]|\\.)*)"\.toRegex\(/g;
    for (const m of src.matchAll(raw)) { out.push(m[1]); }
    for (const m of src.matchAll(plain)) { out.push((m[1] ?? m[2]).replace(/\\(.)/g, '$1')); }
    return out;
}

/** The first brace ICU refuses, or null. */
function badBrace(p) {
    let inSet = false;
    for (let i = 0; i < p.length; i++) {
        const c = p[i];
        if (c === '\\') { i++; continue; }
        if (inSet) { if (c === ']') { inSet = false; } continue; }
        if (c === '[') { inSet = true; if (p[i + 1] === ']' || p.slice(i + 1, i + 3) === '^]') { i += p[i + 1] === '^' ? 2 : 1; } continue; }
        if (c === '{') {
            const q = /^\{\d+(,\d*)?\}/.exec(p.slice(i));
            if (!q || i === 0) { return `"{" at ${i}`; }
            i += q[0].length - 1;
            continue;
        }
        if (c === '}') { return `"}" at ${i}`; }
    }
    return null;
}

// The readers bite.
t.ok(JSON.stringify(patterns('Regex("\\\\{(\\\\d)}") + Regex("""a{2}""") + "x\\\\.y".toRegex()')) === '["a{2}","\\\\{(\\\\d)}","x\\\\.y"]', 'the literal reader finds plain, raw and toRegex patterns');
t.ok(badBrace('\\{(\\d)}') !== null, 'a lone "}" is refused');
t.ok(badBrace('a{x') !== null && badBrace('{2}') !== null, 'a "{" that opens no count is refused');
t.ok(badBrace('\\{(\\d)\\}') === null && badBrace('[0-9a-f]{2}([:-][0-9a-f]{2}){5}$') === null, 'escaped braces and counts pass');
t.ok(badBrace('\\s{0,3}#{1,6}[{}]x{2,}') === null, 'ranges, open counts and braces inside a set pass');

let seen = 0;
for (const f of walk('app/src', ['.kt'])) {
    for (const p of patterns(read(f))) {
        seen++;
        const bad = badBrace(p);
        t.ok(bad === null, `${f}: /${p}/ has ${bad} - Android's ICU refuses it; escape it as \\\\{ or \\\\}`);
    }
}
t.ok(seen > 10, `the app's patterns are read (${seen})`);

t.done();
