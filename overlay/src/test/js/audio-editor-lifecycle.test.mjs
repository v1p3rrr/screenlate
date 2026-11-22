import assert from 'node:assert/strict';
import { test } from 'node:test';
import { popupPage } from '../../../../scripts/page-tests/harness.mjs';

test('queued preview callbacks cannot move the cursor or stop a newer seek', () => {
    const page = popupPage();
    const editor = page.global('AudioEditor');
    const commands = [];
    page.window.ScreenlateBridge = { onAudioEditorCommand: json => commands.push(JSON.parse(json)) };
    editor.open({ operation: 'preview', totalFrames: 480000, selection: [100000, 300000] });
    page.document.getElementById('sl-action-play').click();
    const first = commands.at(-1).sequence;
    editor.state.cursor = 200000;
    page.document.getElementById('sl-mode-original').click();
    const latest = commands.at(-1).sequence;
    editor.receive({ operation: 'preview', previewSequence: first, cursor: 110000, playing: false });
    assert.equal(editor.state.cursor, 200000);
    assert.equal(editor.state.playing, true);
    editor.receive({ operation: 'preview', previewSequence: latest, cursor: 210000, playing: true });
    assert.equal(editor.state.cursor, 210000);
    // Presentation commands do not invalidate playback progress.
    page.document.getElementById('sl-action-expand').click();
    editor.receive({ operation: 'preview', previewSequence: latest, cursor: 220000, playing: false });
    assert.equal(editor.state.cursor, 220000);
    assert.equal(editor.state.playing, false);
});

test('native Back ignores stale editors and confirms only actual edits in the current editor', () => {
    const page = popupPage();
    const editor = page.global('AudioEditor');
    const commands = [];
    page.window.ScreenlateBridge = { onAudioEditorCommand: json => commands.push(JSON.parse(json)) };
    editor.open({ operation: 'current', totalFrames: 480000, selection: [100000, 300000] });
    editor.requestCancel('old');
    assert.equal(commands.length, 0);
    editor.requestCancel('current');
    assert.equal(commands.at(-1).action, 'cancel');
    assert.equal(page.document.getElementById('sl-dialog-layer').hidden, true);
    editor.open({ operation: 'edited', totalFrames: 480000, selection: [100000, 300000] });
    page.document.getElementById('sl-action-cut').click();
    const before = commands.length;
    editor.requestCancel('edited');
    assert.equal(commands.length, before);
    assert.equal(page.document.getElementById('sl-dialog-layer').hidden, false);
    page.document.querySelectorAll('#sl-dialog-actions button')[1].click();
    assert.equal(commands.at(-1).action, 'cancel');
    assert.equal(commands.at(-1).operation, 'edited');
});

test('oversized edit metadata stays editable instead of waiting for a dropped native command', () => {
    const page = popupPage();
    const editor = page.global('AudioEditor');
    const commands = [];
    page.window.ScreenlateBridge = { onAudioEditorCommand: json => commands.push(JSON.parse(json)) };
    editor.open({ operation: 'large', totalFrames: 480000, selection: [100000, 300000] });
    editor.state.cuts = Array.from({ length: 9000 }, (_, i) => [100000 + i * 2, 100001 + i * 2]);
    page.document.getElementById('sl-action-add').click();
    assert.equal(editor.state.phase, 'editing');
    assert.equal(commands.some(c => c.action === 'save'), false);
    assert.equal(page.document.querySelector('.sl-toast').hidden, false);
    editor.requestCancel('large');
    page.document.querySelectorAll('#sl-dialog-actions button')[1]?.click();
    assert.equal(commands.at(-1).action, 'cancel');
});
