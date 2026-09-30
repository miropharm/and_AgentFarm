// Refreshes contract/ from its owner, vsc_AgentFarm/src/remote/contract, when that repo sits beside
// this one. The contract is Agent Farm's: this repo only carries a versioned copy of it.
//   node tools/sync_contract.js           copy every remote-contract.v*.json across
//   node tools/sync_contract.js --check   copy nothing; exit 1 when a copy differs from its owner
'use strict';
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const OWNER = path.join(ROOT, '..', 'vsc_AgentFarm', 'src', 'remote', 'contract');
const COPY = path.join(ROOT, 'contract');
const NAME = /^remote-contract\.v\d+\.json$/;

function sync({ check }) {
    if (!fs.existsSync(OWNER)) {
        return { owner: false, files: [], differ: [] };
    }
    const files = fs.readdirSync(OWNER).filter(f => NAME.test(f)).sort();
    const differ = [];
    for (const f of files) {
        const src = fs.readFileSync(path.join(OWNER, f));
        const dst = path.join(COPY, f);
        const same = fs.existsSync(dst) && Buffer.compare(src, fs.readFileSync(dst)) === 0;
        if (same) { continue; }
        differ.push(f);
        if (!check) {
            fs.mkdirSync(COPY, { recursive: true });
            fs.writeFileSync(dst, src);
        }
    }
    return { owner: true, files, differ };
}

module.exports = { sync, OWNER, COPY, NAME };

if (require.main === module) {
    const check = process.argv.includes('--check');
    const r = sync({ check });
    if (!r.owner) {
        console.log(`no owner at ${OWNER} - nothing to compare`);
        process.exit(0);
    }
    for (const f of r.differ) { console.log(`${check ? 'differs' : 'copied '}  ${f}`); }
    console.log(`${r.files.length} contract file(s), ${r.differ.length} ${check ? 'differ' : 'refreshed'}`);
    process.exit(check && r.differ.length ? 1 : 0);
}
