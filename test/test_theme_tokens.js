// Agent Farm's look lives in ONE file, core/theme/Tokens.kt: the Compose theme reads it, and the farm's web
// pages get the same values. The only copy is the window background in res/values{,-night}/colors.xml (what
// shows before the first frame); it must be the token's, or the app flashes another colour as it opens.
'use strict';
const { read, suite } = require('./lib');

const t = suite();
const tokens = read('app/src/main/java/com/muvusoft/agentfarm/core/theme/Tokens.kt');

/** The palette named `name` (LIGHT/DARK): every `key = 0xAARRGGBB` inside its Palette(...). */
function palette(name) {
    const m = new RegExp(`val ${name} = Palette\\(([\\s\\S]*?)\\n    \\)`).exec(tokens);
    if (!m) { return null; }
    return new Map([...m[1].matchAll(/(\w+) = 0x([0-9A-F]{8})/g)].map(x => [x[1], x[2]]));
}
const fields = [.../class Palette\(([\s\S]*?)\n\)/.exec(tokens)[1].matchAll(/val (\w+): Long/g)].map(m => m[1]);
t.ok(fields.length > 20, 'Palette declares its colour slots');

for (const [name, xml] of [['LIGHT', 'app/src/main/res/values/colors.xml'], ['DARK', 'app/src/main/res/values-night/colors.xml']]) {
    const p = palette(name);
    t.ok(!!p, `Tokens.${name} is found`);
    if (!p) { continue; }
    for (const f of fields) { t.ok(p.has(f), `Tokens.${name} gives ${f} a value`); }
    const bg = /<color name="shell_background">#([0-9A-Fa-f]{8})<\/color>/.exec(read(xml));
    t.ok(!!bg && bg[1].toUpperCase() === p.get('background'), `${xml} shell_background is Tokens.${name}.background (#${p.get('background')})`);
}

// The theme takes the palette whole: a slot the scheme never reads is a colour nobody sees.
const theme = read('app/src/main/java/com/muvusoft/agentfarm/ui/theme/AppTheme.kt');
for (const f of fields) { t.ok(new RegExp(`p\\.${f}\\b`).test(theme), `ui/theme/AppTheme.kt reads Palette.${f}`); }

t.done();
