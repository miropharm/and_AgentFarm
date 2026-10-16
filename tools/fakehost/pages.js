// The fake host's pages and resources: enough HTML, CSS and a stand-in afRemote.js for the shell's
// WebView to load a page through its native interceptor, and for tests to see what arrived.
'use strict';

const RES = {
    'common.css': { type: 'text/css; charset=utf-8', body: 'body{font-family:sans-serif;margin:16px;background:#1e1e1e;color:#ccc}body.vscode-light{background:#fff;color:#333}h1{font-size:20px}' },
    'afRemote.js': {
        type: 'text/javascript; charset=utf-8',
        // Mirrors acquireVsCodeApi's shape and its device-theme follower (U-238); the real one lives in vsc_AgentFarm.
        body: 'window.acquireVsCodeApi=function(){return{postMessage:function(m){window.afShell&&afShell.post(JSON.stringify(m));},getState:function(){return null;},setState:function(){}};};' +
            'document.addEventListener("DOMContentLoaded",function(){var mq=matchMedia("(prefers-color-scheme: light)");' +
            'var apply=function(){document.body.classList.toggle("vscode-light",mq.matches===true);};apply();mq.addEventListener("change",apply);});',
    },
};

function html(page, view) {
    const safe = s => String(s).replace(/[^a-z0-9._-]/gi, '');
    return '<!doctype html><html><head><meta charset="utf-8">' +
        '<meta name="viewport" content="width=device-width,initial-scale=1">' +
        // A farm page names itself in its title (vsc src/panels/base.ts), which the phone's own bar shows.
        `<title>Fake page: ${safe(page)}</title>` +
        '<link rel="stylesheet" href="/res/common.css"><script src="/res/afRemote.js"></script></head>' +
        `<body data-page="${safe(page)}" data-view="${safe(view)}"><h1 id="title">Fake page: ${safe(page)}</h1>` +
        // Records what the host sent, the way a real page's message listener would receive it,
        // and the arguments the page was opened with (the host echoes view.open's args in its state).
        '<script>window.afSeen=[];window.addEventListener("message",function(e){afSeen.push(e.data&&e.data.type);' +
        'if(e.data&&e.data.args){document.body.setAttribute("data-args",JSON.stringify(e.data.args));}' +
        'document.body.setAttribute("data-seen",afSeen.join(","));});</script></body></html>';
}

/** { status, type, body } for /view/<page> and /res/<path>; null when the path is neither. */
function serve(url) {
    const [path, query = ''] = url.split('?');
    const view = new URLSearchParams(query).get('view') || '';
    let m = /^\/view\/([\w.-]+)$/.exec(path);
    if (m) { return { status: 200, type: 'text/html; charset=utf-8', body: html(m[1], view) }; }
    m = /^\/res\/(.+)$/.exec(path);
    if (m) {
        const r = RES[m[1]];
        return r ? { status: 200, type: r.type, body: r.body } : { status: 404, type: 'text/plain', body: 'not found' };
    }
    return null;
}

module.exports = { serve, RES };
