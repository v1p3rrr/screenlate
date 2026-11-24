import assert from 'node:assert/strict';
import { beforeEach, test } from 'node:test';
import { popupPage } from '../../../../scripts/page-tests/harness.mjs';

let page, editor, wave;
beforeEach(() => {
    page = popupPage();
    editor = page.global('AudioEditor');
    wave = page.document.getElementById('sl-wave');
    wave.setPointerCapture = () => {};
    wave.getBoundingClientRect = () => ({ left: 0, top: 0, width: 300, height: 112 });
    Object.defineProperty(wave, 'clientWidth', { value: 300 });
});
const pointer = (el, type, x = 0, id = 1) => {
    const event = new page.window.Event(type, { bubbles: true });
    Object.assign(event, { clientX: x, clientY: 0, pointerId: id });
    el.dispatchEvent(event);
};
const key = (id, value) => page.document.getElementById(id).dispatchEvent(
    new page.window.KeyboardEvent('keydown', { key: value, bubbles: true }));

test('navigation and handles stay inside the retained source after a long recording session', () => {
    const start = 4_800_000, end = start + 480_000;
    editor.open({ operation: 'long', sampleRate: 48000, totalFrames: end, sourceStart: start, sourceEnd: end,
        scanFrame: start + 240000, selection: [start, end] });
    page.document.getElementById('sl-action-zoom-out').click();
    assert.ok(editor.state.width <= end - start);
    assert.ok(editor.state.view >= start);
    key('sl-start', 'ArrowLeft');
    assert.equal(editor.state.a, start);
    key('sl-end', 'ArrowRight');
    assert.equal(editor.state.b, end);
});

test('short retained audio can be trimmed without inventing frames outside it', () => {
    editor.open({ operation: 'short', sampleRate: 48000, totalFrames: 1100, sourceStart: 1000, sourceEnd: 1100,
        selection: [1000, 1100] });
    key('sl-end', 'ArrowLeft');
    assert.ok(editor.state.b > editor.state.a && editor.state.b <= 1100);
    key('sl-start', 'ArrowRight');
    assert.ok(editor.state.a >= 1000 && editor.state.a < editor.state.b);
    assert.ok(editor.state.width <= 100);
});

test('a handle drag from the previous editor cannot change the new draft', () => {
    editor.open({ operation: 'old', sampleRate: 1000, totalFrames: 10000, selection: [2000, 5000] });
    const handle = page.document.getElementById('sl-start');
    handle.setPointerCapture = () => {};
    pointer(handle, 'pointerdown', 60);
    editor.close();
    editor.open({ operation: 'new', sampleRate: 1000, totalFrames: 10000, selection: [6000, 9000] });
    pointer(handle, 'pointermove', 0);
    assert.equal(editor.state.a, 6000);
});

test('a zero-width waveform during relayout cannot corrupt cursor coordinates', () => {
    editor.open({ operation: 'size', sampleRate: 1000, totalFrames: 10000, selection: [2000, 5000] });
    wave.getBoundingClientRect = () => ({ left: 0, top: 0, width: 0, height: 0 });
    pointer(wave, 'pointerdown');
    pointer(wave, 'pointerup');
    assert.equal(editor.state.cursor, 2000);
    assert.ok(Number.isFinite(editor.state.cursor));
});

test('overview dragging uses the retained source span and does not jump when grabbed inside the viewport', () => {
    editor.open({ operation: 'overview', sampleRate: 1000, totalFrames: 1010000, sourceStart: 1000000,
        sourceEnd: 1010000, selection: [1004000, 1006000] });
    page.document.getElementById('sl-action-zoom-in').click();
    const overview = page.document.getElementById('sl-overview');
    overview.setPointerCapture = () => {};
    overview.getBoundingClientRect = () => ({ left: 0, width: 300 });
    const before = editor.state.view;
    pointer(overview, 'pointerdown', 60);
    assert.equal(editor.state.view, before);
    pointer(overview, 'pointermove', 90);
    assert.equal(editor.state.view, before + 1000);
    assert.ok(editor.state.view >= 1000000 && editor.state.view + editor.state.width <= 1010000);
});

test('switching preview mode while playing submits the current cursor and cuts', () => {
    const commands = [];
    page.window.ScreenlateBridge = { onAudioEditorCommand: raw => commands.push(JSON.parse(raw)) };
    editor.open({ operation: 'mode', totalFrames: 480000, selection: [100000, 300000], cuts: [[150000, 180000]] });
    page.document.getElementById('sl-action-play').click();
    editor.updateCursor(140000);
    page.document.getElementById('sl-mode-original').click();
    const seek = commands.find(c => c.action === 'seek');
    assert.ok(seek);
    assert.equal(seek.mode, 'original');
    assert.equal(seek.cursor, 140000);
    assert.deepEqual(seek.exclusions, [[150000, 180000]]);
});


test('cuts wholly outside the trimmed phrase have no draggable handles', () => {
    editor.open({ operation: 'outside-cut', sampleRate: 1000, totalFrames: 10000,
        selection: [2000, 8000], cuts: [[3000, 4000]] });
    page.document.querySelector('[data-select-cut="0"]').click();
    for (let i = 0; i < 21; i++) key('sl-start', 'ArrowRight');
    assert.equal(editor.state.a, 4100);
    const visible = Array.from(page.document.querySelectorAll('[data-cut-edge]')).filter(el => !el.hidden);
    assert.equal(visible.length, 0, 'A cut before the kept phrase must not expose draggable edges');
});


test('partially trimmed cuts expose only edges inside the phrase and retain original coordinates for undo', () => {
    editor.open({ operation: 'partial-cut', sampleRate: 1000, totalFrames: 10000,
        selection: [2000, 8000], cuts: [[3000, 4000]] });
    page.document.querySelector('[data-select-cut="0"]').click();
    for (let i = 0; i < 15; i++) key('sl-start', 'ArrowRight');
    const handles = page.document.querySelectorAll('[data-cut-edge]');
    assert.equal(handles[0].hidden, true);
    assert.equal(handles[1].hidden, false);
    assert.deepEqual(Array.from(editor.state.cuts[0]), [3000, 4000]);
    for (let i = 0; i < 15; i++) page.document.getElementById('sl-action-undo').click();
    assert.deepEqual(Array.from(editor.state.cuts[0]), [3000, 4000]);
    page.document.querySelector('[data-select-cut="0"]').click();
    assert.equal(page.document.querySelector('[data-cut-edge="0"]').hidden, false);
});
