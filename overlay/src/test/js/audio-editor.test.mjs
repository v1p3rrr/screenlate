import assert from 'node:assert/strict';
import { beforeEach, test } from 'node:test';
import { popupPage } from '../../../../scripts/page-tests/harness.mjs';

let page;
let AudioEditor;

beforeEach(() => {
    page = popupPage();
    AudioEditor = page.global('AudioEditor');
});

// Gesture assertions use semantic callbacks; the wire-level tests inspect envelopes directly.
function bridge(actions) {
    page.window.ScreenlateBridge = { onAudioEditorCommand: raw => {
        const c = JSON.parse(raw);
        const routes = {
            play: () => actions.onAudioPlay?.(c.cursor, c.mode),
            pause: () => actions.onAudioPause?.(),
            seek: () => actions.onAudioSeek?.(c.cursor, c.mode),
            save: () => actions.onAudioAdd?.(JSON.stringify({ selection: c.selection, exclusions: c.exclusions })),
            without: () => actions.onAudioWithout?.(),
            cancel: () => actions.onAudioCancel?.(AudioEditor.state.dirty),
            silence: () => actions.onAudioFindSilence?.(...c.selection, c.request),
            silenceSettings: () => actions.onAudioSilenceSettingsChanged?.(c.minPauseSeconds, c.edgeMarginMillis),
            refresh: () => actions.onAudioRefresh?.(),
            expand: () => actions.onAudioExpand?.(c.expanded),
            collapse: () => actions.onAudioCollapse?.(c.collapsed),
            drag: () => actions.onDragWindow?.(c.dx, c.dy),
            settings: () => actions.onAudioChangeSettings?.(),
        };
        routes[c.action]?.();
    } };
}

const tap = el => {
    if (!el) throw new Error('Cannot tap null element');
    el.dispatchEvent(new page.window.MouseEvent('click', { bubbles: true }));
};

test('opening waits for matching Ready acknowledgement before enabling audio actions', async () => {
    const commands = [];
    page.window.ScreenlateBridge = { onAudioEditorCommand: raw => commands.push(JSON.parse(raw)) };
    AudioEditor.open({ operation: 'ready-a', phase: 'opening', sourceRevision: 0,
        totalFrames: 480000, selection: [100000, 300000] });
    const save = page.document.getElementById('sl-action-add');
    assert.equal(save.disabled, true);
    tap(save);
    assert.equal(commands.some(c => c.action === 'save'), false);
    await new Promise(resolve => page.window.requestAnimationFrame(resolve));
    assert.equal(commands.filter(c => c.action === 'ready').length, 1);
    AudioEditor.receive({ operation: 'old', phase: 'editing' });
    assert.equal(save.disabled, true);
    AudioEditor.receive({ operation: 'ready-a', phase: 'editing' });
    assert.equal(save.disabled, false);
});

test('resize redraws the waveform without replacing the draft, viewport or Ready identity', () => {
    const commands = [];
    page.window.ScreenlateBridge = { onAudioEditorCommand: raw => commands.push(JSON.parse(raw)) };
    const wave = page.document.getElementById('sl-wave');
    let pixels = 340;
    Object.defineProperty(wave, 'clientWidth', { get: () => pixels });
    AudioEditor.open({ operation: 'rotation', totalFrames: 480000, selection: [100000, 300000], cuts: [[180000, 200000]] });
    AudioEditor.state.width = 200000;
    AudioEditor.state.view = 50000;
    AudioEditor.state.cursor = 120000;
    const before = [AudioEditor.state.a, AudioEditor.state.b, AudioEditor.state.view, AudioEditor.state.width, AudioEditor.state.cursor];
    pixels = 600;
    page.window.dispatchEvent(new page.window.Event('resize'));
    assert.equal(wave.getAttribute('viewBox'), '0 0 600 112');
    assert.deepEqual([AudioEditor.state.a, AudioEditor.state.b, AudioEditor.state.view, AudioEditor.state.width, AudioEditor.state.cursor], before);
    assert.equal(AudioEditor.state.operation, 'rotation');
    assert.equal(commands.some(c => c.action === 'ready'), false);
});

test('identified save keeps the editor open, prevents double submission and permits retry after failure', () => {
    const commands = [];
    page.window.ScreenlateBridge = { onAudioEditorCommand: json => commands.push(JSON.parse(json)) };
    AudioEditor.open({ operation: 'a', sourceRevision: 0, totalFrames: 480000, selection: [100000, 300000] });
    const save = page.document.getElementById('sl-action-add');
    tap(save);
    tap(save);
    assert.equal(page.document.getElementById('audio-editor').hidden, false);
    assert.equal(commands.filter(c => c.action === 'save').length, 1);
    const cutsBefore = AudioEditor.state.cuts.length;
    tap(page.document.getElementById('sl-action-cut'));
    assert.equal(AudioEditor.state.cuts.length, cutsBefore);
    AudioEditor.receive({ operation: 'a', phase: 'failed', error: 'Export failed' });
    assert.equal(save.disabled, false);
    assert.equal(page.document.querySelector('.sl-toast').textContent, 'Export failed');
    tap(save);
    assert.equal(commands.filter(c => c.action === 'save').length, 2);
    AudioEditor.receive({ operation: 'a', closed: true });
    assert.equal(page.document.getElementById('audio-editor').hidden, true);
});

test('commands include current cuts for playback and delayed native results cannot mutate another editor', () => {
    const commands = [];
    page.window.ScreenlateBridge = { onAudioEditorCommand: json => commands.push(JSON.parse(json)) };
    AudioEditor.open({ operation: 'b', sourceRevision: 3, totalFrames: 480000, selection: [100000, 300000] });
    tap(page.document.getElementById('sl-action-cut'));
    tap(page.document.getElementById('sl-action-play'));
    const play = commands.find(c => c.action === 'play');
    assert.equal(play.operation, 'b');
    assert.equal(play.sourceRevision, 3);
    assert.ok(play.exclusions.length > 0);
    AudioEditor.receive({ operation: 'a', closed: true, cursor: 123, phase: 'failed' });
    assert.equal(AudioEditor.state.isOpen, true);
    assert.notEqual(AudioEditor.state.cursor, 123);
});

test('refresh appends available source while keeping selection, cuts and viewport fixed', () => {
    AudioEditor.open({ operation: 'c', sourceRevision: 0, totalFrames: 480000,
        sourceStart: 10000, sourceEnd: 480000, selection: [100000, 300000], cuts: [[150000, 180000]] });
    const view = AudioEditor.state.view;
    AudioEditor.receive({ operation: 'c', timeline: { sourceRevision: 1, sourceStart: 10000,
        sourceEnd: 600000, totalFrames: 600000 } });
    assert.equal(AudioEditor.state.sourceEnd, 600000);
    assert.equal(AudioEditor.state.sourceRevision, 1);
    assert.equal(AudioEditor.state.b, 300000);
    assert.deepEqual(Array.from(AudioEditor.state.cuts[0]), [150000, 180000]);
    assert.equal(AudioEditor.state.view, view);
    AudioEditor.receive({ operation: 'c', timeline: { sourceRevision: 0, sourceEnd: 480000 } });
    assert.equal(AudioEditor.state.sourceEnd, 600000);
});

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
    bridge({
        onAudioPlay: (cursor, mode) => { playCall = { cursor, mode }; },
        onAudioPause: () => { pauseCalled = true; },
        onAudioSeek: (cursor, mode) => {},
    });

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
    bridge({
        onAudioFindSilence: (a, b, req) => { silenceReq = { a, b, req }; },
    });

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
    bridge({
        onAudioCancel: dirty => { cancelCall = dirty; },
    });

    // Clean cancel
    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 200000],
    });

    tap(page.document.getElementById('sl-action-cancel'));
    assert.equal(cancelCall, false);
    assert.equal(page.document.getElementById('audio-editor').hidden, false);
    AudioEditor.receive({ operation: null, closed: true });
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
    assert.equal(page.document.getElementById('audio-editor').hidden, false);
});

test('add sends selection and exclusions and awaits native completion', () => {
    let addPayload = null;
    bridge({
        onAudioAdd: json => { addPayload = JSON.parse(json); },
    });

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
    assert.equal(page.document.getElementById('audio-editor').hidden, false);
});

test('without action awaits native completion', () => {
    let withoutCalled = false;
    bridge({
        onAudioWithout: () => { withoutCalled = true; },
    });

    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 300000],
    });

    tap(page.document.getElementById('sl-action-without'));
    assert.equal(withoutCalled, true);
    assert.equal(page.document.getElementById('audio-editor').hidden, false);
});

test('expand and collapse actions notify bridge and toggle presentation', () => {
    let expandCall = null;
    let collapseCall = null;
    bridge({
        onAudioExpand: exp => { expandCall = exp; },
        onAudioCollapse: col => { collapseCall = col; },
    });

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

test('editor applies localized labels to DOM elements', () => {
    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 300000],
        labels: {
            audio_editor_title: 'Аудио предложения',
            audio_editor_start: 'Начало',
            audio_editor_end: 'Конец',
            audio_editor_to_card: 'В карточку',
            audio_editor_cut: 'Вырезать',
            audio_editor_find_silence: 'Найти тишину',
            audio_editor_refresh: 'Обновить',
            audio_editor_cancel: 'Отмена',
            audio_editor_without: 'Без аудио',
            audio_editor_add: 'Добавить',
        },
    });

    assert.equal(page.document.getElementById('sl-editor-title').textContent, 'Аудио предложения');
    assert.equal(page.document.getElementById('sl-summary-start').textContent, 'Начало');
    assert.equal(page.document.getElementById('sl-summary-end').textContent, 'Конец');
    assert.equal(page.document.getElementById('sl-summary-to-card').textContent, 'В карточку');
    assert.equal(page.document.getElementById('sl-cut-label').textContent, 'Вырезать');
    assert.equal(page.document.getElementById('sl-silence-label').textContent, 'Найти тишину');
    assert.equal(page.document.getElementById('sl-refresh-label').textContent, 'Обновить');
    assert.equal(page.document.getElementById('sl-action-cancel').textContent, 'Отмена');
    assert.equal(page.document.getElementById('sl-action-without').textContent, 'Без аудио');
    assert.equal(page.document.getElementById('sl-action-add').textContent, 'Добавить');
});

test('add action prevents submission and shows toast when output duration is zero', () => {
    let addCall = null;
    bridge({
        onAudioAdd: json => { addCall = json; },
    });

    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 200000],
        cuts: [[100000, 200000]], // Fully cut
        labels: {
            audio_editor_empty_output: 'Выделенный фрагмент пуст.',
        },
    });

    const addBtn = page.document.getElementById('sl-action-add');
    tap(addBtn);

    assert.equal(addCall, null);
    const toast = page.document.querySelector('.sl-toast');
    assert.equal(toast.hidden, false);
    assert.equal(toast.textContent, 'Выделенный фрагмент пуст.');
});

test('dragging editor header dispatches onDragWindow to bridge', () => {
    const dragDeltas = [];
    bridge({
        onDragWindow: (dx, dy) => { dragDeltas.push({ dx, dy }); },
    });

    AudioEditor.open({
        sampleRate: 48000,
        totalFrames: 480000,
        selection: [100000, 200000],
    });

    const head = page.document.getElementById('sl-editor-head');
    assert.ok(head, 'Header element must exist');

    head.dispatchEvent(new page.window.MouseEvent('pointerdown', { bubbles: true, screenX: 100, screenY: 150 }));
    head.dispatchEvent(new page.window.MouseEvent('pointermove', { bubbles: true, screenX: 120, screenY: 180 }));
    head.dispatchEvent(new page.window.MouseEvent('pointermove', { bubbles: true, screenX: 115, screenY: 190 }));
    head.dispatchEvent(new page.window.MouseEvent('pointerup', { bubbles: true, screenX: 115, screenY: 190 }));

    assert.deepEqual(dragDeltas, [
        { dx: 20, dy: 30 },
        { dx: -5, dy: 10 },
    ]);
});
