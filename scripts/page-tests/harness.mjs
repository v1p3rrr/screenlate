// Loads the popup page scripts into a simulated DOM for node:test.
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const RENDER = path.join(root, 'dictionary/render-yomitan/src/main/assets/yomitan-render');
const POPUP = path.join(root, 'overlay/src/main/assets/popup');

export const SCRIPTS = {
    render: path.join(RENDER, 'render.js'),
    anki: path.join(RENDER, 'anki.js'),
    note: path.join(POPUP, 'note.js'),
    definition: path.join(POPUP, 'definition.js'),
    definitionJa: path.join(POPUP, 'definition-ja.js'),
    popup: path.join(POPUP, 'popup.js'),
};

/**
 * A document with the given scripts run in order as classic scripts, so their top-level constants (NoteData, Popup)
 * are globals as in the WebView. `calls` records every ScreenlateBridge call as [name, ...args]. Nothing is fetched.
 */
export function loadPage({ html = '<!DOCTYPE html><html><head></head><body></body></html>', scripts }) {
    const dom = new JSDOM(html, { runScripts: 'dangerously', pretendToBeVisual: true });
    const { window } = dom;
    const calls = [];
    window.ScreenlateBridge = new Proxy({}, {
        get: (_, name) => (...args) => {
            calls.push([name, ...args]);
        },
    });
    for (const file of scripts) {
        const script = window.document.createElement('script');
        script.textContent = readFileSync(file, 'utf8');
        window.document.body.append(script);
    }
    return { window, document: window.document, calls, global: name => window.eval(name) };
}

/** render.js and anki.js on an empty page. */
export function renderPage() {
    return loadPage({ scripts: [SCRIPTS.render, SCRIPTS.anki] });
}

/** The note builder with the renderer, on an empty page. */
export function notePage() {
    return loadPage({ scripts: [SCRIPTS.render, SCRIPTS.anki, SCRIPTS.note] });
}

/** The definition copier with the renderer and the Japanese rules, on an empty page. */
export function definitionPage() {
    return loadPage({ scripts: [SCRIPTS.render, SCRIPTS.anki, SCRIPTS.definition, SCRIPTS.definitionJa] });
}

/** popup.html with all its scripts; the page's own script tags are dropped. */
export function popupPage() {
    const html = readFileSync(path.join(POPUP, 'popup.html'), 'utf8').replace(/<script[^>]*><\/script>\s*/g, '');
    return loadPage({ html, scripts: [SCRIPTS.render, SCRIPTS.anki, SCRIPTS.note, SCRIPTS.definition, SCRIPTS.definitionJa, SCRIPTS.popup] });
}
