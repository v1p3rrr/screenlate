import assert from 'node:assert/strict';
import { beforeEach, test } from 'node:test';
import { popupPage } from '../../../../scripts/page-tests/harness.mjs';

let page;
let AudioEditor;

beforeEach(() => {
    page = popupPage();
    AudioEditor = page.global('AudioEditor');
});

const tap = el => {
    if (!el) throw new Error('Cannot tap null element');
    el.dispatchEvent(new page.window.MouseEvent('click', { bubbles: true }));
};

test('editor opens with initial range, displays subtitle, and hides dictionary content', () => {
    const content = page.document.getElementById('content');
    const editor = page.document.getElementById('audio-editor');
    const pill = page.document.getElementById('sl-editor-pill');
    const subtitle = page.document.getElementById('sl-editor-subtitle');

    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        scanFrame: 240000,
        selection: [192000, 288000],
        term: '食べる',
        reading: 'たべる',
    });

    assert.equal(content.hidden, true);
    assert.equal(editor.hidden, false);
    assert.equal(pill.hidden, true);
    assert.equal(subtitle.textContent, '食べる · たべる');
    assert.equal(AudioEditor.state.a, 192000);
    assert.equal(AudioEditor.state.b, 288000);
    assert.equal(AudioEditor.state.dirty, false);
});

test('playhead updates cursor, and play/pause invokes bridge callbacks', () => {
    let playCall = null;
    let pauseCalled = false;
    page.window.ScreenlateBridge = {
        onAudioPlay: (cursor, mode) => { playCall = { cursor, mode }; },
        onAudioPause: () => { pauseCalled = true; },
        onAudioSeek: (cursor, mode) => {},
    };

    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 200000],
    });

    const playBtn = page.document.getElementById('sl-action-play');
    tap(playBtn);
    assert.deepEqual(playCall, { cursor: 100000, mode: 'result' });
    assert.equal(AudioEditor.state.playing, true);

    tap(playBtn);
    assert.equal(pauseCalled, true);
    assert.equal(AudioEditor.state.playing, false);

    AudioEditor.updateCursor(150000);
    assert.equal(AudioEditor.state.cursor, 150000);
});

test('mode toggling switches between result and original', () => {
    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 200000],
    });

    tap(page.document.getElementById('sl-mode-original'));
    assert.equal(AudioEditor.state.mode, 'original');

    tap(page.document.getElementById('sl-mode-result'));
    assert.equal(AudioEditor.state.mode, 'result');
});

test('cutting creates new exclusion interval and undo restores previous state', () => {
    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 200000],
    });

    assert.equal(AudioEditor.state.cuts.length, 0);
    assert.equal(AudioEditor.state.dirty, false);

    tap(page.document.getElementById('sl-action-cut'));
    assert.equal(AudioEditor.state.cuts.length, 1);
    assert.equal(AudioEditor.state.dirty, true);

    tap(page.document.getElementById('sl-action-undo'));
    assert.equal(AudioEditor.state.cuts.length, 0);
    assert.equal(AudioEditor.state.dirty, false);
});

test('removing cut chip removes that cut and marks dirty', () => {
    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 200000],
    });

    tap(page.document.getElementById('sl-action-cut'));
    assert.equal(AudioEditor.state.cuts.length, 1);

    const removeBtn = page.document.querySelector('[data-remove-cut="0"]');
    assert.notEqual(removeBtn, null);
    tap(removeBtn);

    assert.equal(AudioEditor.state.cuts.length, 0);
});

test('silence proposals accept and reject', () => {
    let silenceReq = null;
    page.window.ScreenlateBridge = {
        onAudioFindSilence: (a, b, req) => { silenceReq = { a, b, req }; },
    };

    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 200000],
    });

    tap(page.document.getElementById('sl-action-silence'));
    assert.notEqual(silenceReq, null);
    assert.equal(silenceReq.a, 100000);
    assert.equal(silenceReq.b, 200000);

    // Provide silence proposals
    AudioEditor.setSilenceProposals(silenceReq.req, [[120000, 150000]]);
    const suggestionsBox = page.document.getElementById('sl-suggestions');
    assert.equal(suggestionsBox.hidden, false);
    assert.equal(AudioEditor.state.suggestions.length, 1);

    // Reject proposals
    tap(page.document.getElementById('sl-action-reject-silence'));
    assert.equal(AudioEditor.state.suggestions.length, 0);

    // Deliver again and accept
    AudioEditor.setSilenceProposals(silenceReq.req, [[120000, 150000]]);
    tap(page.document.getElementById('sl-action-accept-silence'));
    assert.equal(AudioEditor.state.suggestions.length, 0);
    assert.equal(AudioEditor.state.cuts.length, 1);
    assert.equal(AudioEditor.state.cuts[0][0], 120000);
    assert.equal(AudioEditor.state.cuts[0][1], 150000);
});

test('clean cancel immediately cancels; dirty cancel prompts confirmation', () => {
    let cancelCall = null;
    page.window.ScreenlateBridge = {
        onAudioCancel: dirty => { cancelCall = dirty; },
    };

    // Clean cancel
    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 200000],
    });

    tap(page.document.getElementById('sl-action-cancel'));
    assert.equal(cancelCall, false);
    assert.equal(page.document.getElementById('audio-editor').hidden, true);
    assert.equal(page.document.getElementById('content').hidden, false);

    // Dirty cancel
    cancelCall = null;
    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 200000],
    });
    tap(page.document.getElementById('sl-action-cut')); // Make dirty

    tap(page.document.getElementById('sl-action-cancel'));
    const dialogLayer = page.document.getElementById('sl-dialog-layer');
    assert.equal(dialogLayer.hidden, false);
    assert.equal(cancelCall, null); // Has not cancelled yet

    // Click 'Keep editing' (the first button in dialog actions)
    const dialogButtons = page.document.querySelectorAll('#sl-dialog-actions button');
    assert.equal(dialogButtons.length, 2);
    tap(dialogButtons[0]); // Keep editing
    assert.equal(dialogLayer.hidden, true);
    assert.equal(cancelCall, null);

    // Tap cancel again and choose discard
    tap(page.document.getElementById('sl-action-cancel'));
    const dialogButtons2 = page.document.querySelectorAll('#sl-dialog-actions button');
    tap(dialogButtons2[1]); // Cancel note
    assert.equal(cancelCall, true);
    assert.equal(page.document.getElementById('audio-editor').hidden, true);
});

test('add action passes serialized JSON with selection and exclusions to onAudioAdd', () => {
    let addPayload = null;
    page.window.ScreenlateBridge = {
        onAudioAdd: json => { addPayload = JSON.parse(json); },
    };

    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 300000],
        cuts: [[150000, 180000]],
    });

    tap(page.document.getElementById('sl-action-add'));
    assert.deepEqual(addPayload, {
        selection: [100000, 300000],
        exclusions: [[150000, 180000]],
    });
    assert.equal(page.document.getElementById('audio-editor').hidden, true);
});

test('without action calls onAudioWithout and closes editor', () => {
    let withoutCalled = false;
    page.window.ScreenlateBridge = {
        onAudioWithout: () => { withoutCalled = true; },
    };

    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 300000],
    });

    tap(page.document.getElementById('sl-action-without'));
    assert.equal(withoutCalled, true);
    assert.equal(page.document.getElementById('audio-editor').hidden, true);
});

test('expand and collapse actions notify bridge and toggle presentation', () => {
    let expandCall = null;
    let collapseCall = null;
    page.window.ScreenlateBridge = {
        onAudioExpand: exp => { expandCall = exp; },
        onAudioCollapse: col => { collapseCall = col; },
    };

    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 300000],
    });

    const editor = page.document.getElementById('audio-editor');
    const pill = page.document.getElementById('sl-editor-pill');

    tap(page.document.getElementById('sl-action-expand'));
    assert.equal(expandCall, true);
    assert.equal(editor.classList.contains('sl-expanded'), true);

    tap(page.document.getElementById('sl-action-collapse'));
    assert.equal(collapseCall, true);
    assert.equal(editor.hidden, true);
    assert.equal(pill.hidden, false);

    tap(pill);
    assert.equal(collapseCall, false);
    assert.equal(editor.hidden, false);
    assert.equal(pill.hidden, true);
});
