#!/usr/bin/env node
// Runs the fake Agent Farm host for the emulator or a hand test, and prints the pairing URI a phone
// would scan. Usage:
//   node tools/fake_host.js [--port 8743] [--bind 0.0.0.0] [--cert c.pem --key k.pem] [--addr 10.0.2.2:8743] [--keep 100]
'use strict';
const crypto = require('crypto');
const fs = require('fs');
const { createHost } = require('./fakehost/host');

function args(argv) {
    const o = {};
    for (let i = 0; i < argv.length; i++) {
        const m = /^--(.+)$/.exec(argv[i]);
        if (m) { o[m[1]] = argv[i + 1]; i++; }
    }
    return o;
}

/** SHA-256 over the certificate's DER bytes, lower-case hex: the fp the shell pins. */
function fingerprint(pem) {
    return new crypto.X509Certificate(pem).fingerprint256.replace(/:/g, '').toLowerCase();
}

async function main() {
    const a = args(process.argv.slice(2));
    const certPem = a.cert ? fs.readFileSync(a.cert) : null;
    const tls = certPem ? { cert: certPem, key: fs.readFileSync(a.key) } : null;
    const host = createHost({ tls, keep: a.keep ? Number(a.keep) : undefined });
    const port = await host.listen(Number(a.port || 8743), a.bind || '0.0.0.0');
    const code = host.newCode();
    const q = new URLSearchParams({ v: String(host.c.version), farm: host.farm.id, name: host.farm.name, code, fp: certPem ? fingerprint(certPem) : '', a: a.addr || `127.0.0.1:${port}` });
    console.log(`fake host on ${tls ? 'https' : 'http'}://${a.bind || '0.0.0.0'}:${port} (contract ${host.c.version})`);
    console.log('PAIR agentfarm://pair?' + q.toString());
    const stop = () => host.close().then(() => process.exit(0));
    process.on('SIGINT', stop);
    process.on('SIGTERM', stop);
}

main().catch(e => { console.error(e && e.stack); process.exit(1); });
