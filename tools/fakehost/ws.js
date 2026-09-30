// A minimal WebSocket (RFC 6455) for the fake host and its test: text frames, ping/pong, close.
// Zero dependencies, because the repo gate runs on a bare node. Not a general library: frames up to
// 2^31 bytes, no extensions, no fragmentation on send.
'use strict';
const crypto = require('crypto');
const net = require('net');
const tls = require('tls');
const { EventEmitter } = require('events');

const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';
const acceptKey = key => crypto.createHash('sha1').update(key + GUID).digest('base64');

function encodeFrame(opcode, payload, mask) {
    const len = payload.length;
    const head = [0x80 | opcode];
    const m = mask ? 0x80 : 0;
    if (len < 126) { head.push(m | len); } else if (len < 65536) { head.push(m | 126, len >> 8, len & 255); } else {
        head.push(m | 127, 0, 0, 0, 0, (len >>> 24) & 255, (len >> 16) & 255, (len >> 8) & 255, len & 255);
    }
    if (!mask) { return Buffer.concat([Buffer.from(head), payload]); }
    const key = crypto.randomBytes(4);
    const body = Buffer.from(payload);
    for (let i = 0; i < body.length; i++) { body[i] ^= key[i & 3]; }
    return Buffer.concat([Buffer.from(head), key, body]);
}

/** One open socket. Emits 'message' (string), 'close'. */
class Conn extends EventEmitter {
    constructor(socket, { client }) {
        super();
        this.socket = socket;
        this.client = client;
        this.buf = Buffer.alloc(0);
        this.open = true;
        socket.on('data', d => { this.buf = Buffer.concat([this.buf, d]); this.drain(); });
        socket.on('close', () => this.closed());
        socket.on('error', () => this.closed());
    }

    drain() {
        for (;;) {
            if (this.buf.length < 2) { return; }
            const op = this.buf[0] & 15;
            const masked = (this.buf[1] & 0x80) !== 0;
            let len = this.buf[1] & 127;
            let off = 2;
            if (len === 126) { if (this.buf.length < 4) { return; } len = this.buf.readUInt16BE(2); off = 4; }
            else if (len === 127) { if (this.buf.length < 10) { return; } len = this.buf.readUInt32BE(6); off = 10; }
            const need = off + (masked ? 4 : 0) + len;
            if (this.buf.length < need) { return; }
            let body = this.buf.subarray(off + (masked ? 4 : 0), need);
            if (masked) {
                const key = this.buf.subarray(off, off + 4);
                body = Buffer.from(body);
                for (let i = 0; i < body.length; i++) { body[i] ^= key[i & 3]; }
            }
            this.buf = this.buf.subarray(need);
            if (op === 1) { this.emit('message', body.toString('utf8')); }
            else if (op === 8) { this.close(); return; }
            else if (op === 9) { this.write(10, body); }
        }
    }

    write(op, payload) {
        if (this.open) { this.socket.write(encodeFrame(op, payload, this.client)); }
    }

    send(text) { this.write(1, Buffer.from(text, 'utf8')); }

    close() {
        if (!this.open) { return; }
        this.write(8, Buffer.alloc(0));
        this.socket.end();
        this.closed();
    }

    closed() {
        if (!this.open) { return; }
        this.open = false;
        this.emit('close');
    }
}

/** Upgrades an http(s) 'upgrade' request to a Conn. */
function accept(req, socket) {
    const key = req.headers['sec-websocket-key'];
    if (!key) { socket.destroy(); return null; }
    socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n' +
        `Sec-WebSocket-Accept: ${acceptKey(key)}\r\n\r\n`);
    return new Conn(socket, { client: false });
}

/** Opens a client Conn to ws(s)://host:port/path. Resolves once the upgrade is answered. */
function connect({ host, port, path = '/ws', secure = false, rejectUnauthorized = true }) {
    return new Promise((resolve, reject) => {
        const socket = secure ? tls.connect({ host, port, rejectUnauthorized }) : net.connect({ host, port });
        const key = crypto.randomBytes(16).toString('base64');
        socket.once('error', reject);
        socket.once(secure ? 'secureConnect' : 'connect', () => {
            socket.write(`GET ${path} HTTP/1.1\r\nHost: ${host}:${port}\r\nUpgrade: websocket\r\n` +
                `Connection: Upgrade\r\nSec-WebSocket-Key: ${key}\r\nSec-WebSocket-Version: 13\r\n\r\n`);
        });
        let head = Buffer.alloc(0);
        const onData = d => {
            head = Buffer.concat([head, d]);
            const end = head.indexOf('\r\n\r\n');
            if (end < 0) { return; }
            socket.removeListener('data', onData);
            const text = head.subarray(0, end).toString();
            if (!/^HTTP\/1\.1 101/.test(text) || !text.includes(acceptKey(key))) { reject(new Error('upgrade refused: ' + text.split('\r\n')[0])); return; }
            const conn = new Conn(socket, { client: true });
            // Frames that rode in with the 101 wait until the caller has attached its listeners.
            conn.buf = head.subarray(end + 4);
            resolve(conn);
            setImmediate(() => conn.drain());
        };
        socket.on('data', onData);
    });
}

module.exports = { accept, connect, encodeFrame };
