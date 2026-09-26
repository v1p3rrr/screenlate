// Evaluates a JavaScript expression in the overlay popup of the debug build through DevTools.
//
// Usage: node scripts/popup-eval.mjs "<expression>"
// Env: ADB (path to adb), ANDROID_SERIAL, PACKAGE (default com.vpr.screenlate.debug), PORT (default 9229).
// Needs Node 22+ (built-in WebSocket). The popup must have been shown at least once.
import { execFileSync } from 'node:child_process';

const adb = process.env.ADB || 'adb';
const pkg = process.env.PACKAGE || 'com.vpr.screenlate.debug';
const port = process.env.PORT || '9229';
const expression = process.argv[2];
if (!expression) {
    console.error('Usage: node scripts/popup-eval.mjs "<expression>"');
    process.exit(2);
}

const pid = execFileSync(adb, ['shell', 'pidof', pkg]).toString().trim();
execFileSync(adb, ['forward', `tcp:${port}`, `localabstract:webview_devtools_remote_${pid}`]);
const pages = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
const page = pages.find(p => p.url.includes('popup.html'));
if (!page) {
    console.error('Popup page not found:', pages.map(p => p.url));
    process.exit(1);
}

const socket = new WebSocket(page.webSocketDebuggerUrl);
socket.onopen = () => socket.send(JSON.stringify({
    id: 1,
    method: 'Runtime.evaluate',
    params: { expression, returnByValue: true, awaitPromise: true },
}));
socket.onmessage = event => {
    const message = JSON.parse(event.data);
    if (message.id !== 1) return;
    const { result, exceptionDetails } = message.result;
    if (exceptionDetails) console.error(exceptionDetails.exception?.description || exceptionDetails.text);
    else console.log(typeof result.value === 'string' ? result.value : JSON.stringify(result.value, null, 2));
    socket.close();
};
