import assert from 'node:assert/strict';
import { test } from 'node:test';
import { popupPage } from '../../../../scripts/page-tests/harness.mjs';

test('delayed initial refinement cannot replace an edit still travelling to native', { timeout: 5000 }, async () => {
    const page = popupPage();
    const editor = page.global('AudioEditor');
    let ready;
    const opened = new Promise(resolve => { ready = resolve; });
    page.window.ScreenlateBridge = { onAudioEditorCommand: json => {
        const command = JSON.parse(json);
        if (command.action === 'ready') ready(command);
    } };
    editor.open({ operation: 'initial-edit', phase: 'opening', totalFrames: 480000, selection: [0, 300000] });
    const opening = await opened;
    editor.receive({ operation: 'initial-edit', phase: 'editing', initialSelection: [0, 300000], initialCuts: [] });
    page.document.getElementById('sl-action-cut').click();
    const cuts = JSON.stringify(editor.state.cuts);
    editor.receive({ operation: 'initial-edit', initialSequence: opening.sequence,
        initialSelection: [100000, 300000], initialCuts: [] });
    assert.equal(editor.state.a, 0);
    assert.equal(JSON.stringify(editor.state.cuts), cuts);
});

test('presentation changes preserve an untouched delayed initial suggestion', { timeout: 5000 }, async () => {
    const page = popupPage();
    const editor = page.global('AudioEditor');
    let ready;
    const opened = new Promise(resolve => { ready = resolve; });
    page.window.ScreenlateBridge = { onAudioEditorCommand: json => {
        const command = JSON.parse(json);
        if (command.action === 'ready') ready(command);
    } };
    editor.open({ operation: 'initial-expand', phase: 'opening', totalFrames: 480000, selection: [0, 300000] });
    const opening = await opened;
    editor.receive({ operation: 'initial-expand', phase: 'editing', initialSelection: [0, 300000], initialCuts: [] });
    page.document.getElementById('sl-action-expand').click();
    editor.receive({ operation: 'initial-expand', initialSequence: opening.sequence,
        initialSelection: [100000, 300000], initialCuts: [] });
    assert.equal(editor.state.a, 100000);
    const handleLeft = parseFloat(page.document.getElementById('sl-start').style.left);
    assert.ok(handleLeft > 20 && handleLeft < 21);
    assert.equal(editor.state.expanded, true);
    assert.equal(editor.state.dirty, false);
});

test('grabbing a trim handle protects it before any movement command is sent', { timeout: 5000 }, async () => {
    const page = popupPage();
    const editor = page.global('AudioEditor');
    let ready;
    const opened = new Promise(resolve => { ready = resolve; });
    page.window.ScreenlateBridge = { onAudioEditorCommand: json => {
        const command = JSON.parse(json);
        if (command.action === 'ready') ready(command);
    } };
    editor.open({ operation: 'initial-grab', phase: 'opening', totalFrames: 480000, selection: [0, 300000] });
    const opening = await opened;
    editor.receive({ operation: 'initial-grab', phase: 'editing', initialSelection: [0, 300000], initialCuts: [] });
    const handle = page.document.getElementById('sl-start');
    handle.setPointerCapture = () => {};
    const grab = new page.window.Event('pointerdown', { bubbles: true });
    grab.pointerId = 1;
    handle.dispatchEvent(grab);
    editor.receive({ operation: 'initial-grab', initialSequence: opening.sequence,
        initialSelection: [100000, 300000], initialCuts: [] });
    assert.equal(editor.state.a, 0);
    assert.equal(editor.state.dirty, false);
});

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

test('Back dismisses modal dialogs without cancelling the note and only cancels duplicateKeep', () => {
    const page = popupPage();
    const editor = page.global('AudioEditor');
    const commands = [];
    page.window.ScreenlateBridge = { onAudioEditorCommand: json => commands.push(JSON.parse(json)) };
    editor.open({ operation: 'modal-back', totalFrames: 480000, selection: [100000, 300000] });

    page.document.getElementById('sl-action-info').click();
    assert.equal(page.document.getElementById('sl-dialog-layer').hidden, false);
    editor.requestCancel('modal-back');
    assert.equal(page.document.getElementById('sl-dialog-layer').hidden, true);
    assert.equal(commands.some(c => c.action === 'cancel'), false);

    page.document.getElementById('sl-action-silence-settings').click();
    assert.equal(page.document.getElementById('sl-dialog-layer').hidden, false);
    editor.requestCancel('modal-back');
    assert.equal(page.document.getElementById('sl-dialog-layer').hidden, true);
    assert.equal(commands.some(c => c.action === 'cancel'), false);

    page.document.getElementById('sl-action-cut').click();
    editor.requestCancel('modal-back');
    assert.equal(page.document.getElementById('sl-dialog-layer').hidden, false);
    editor.requestCancel('modal-back');
    assert.equal(page.document.getElementById('sl-dialog-layer').hidden, true);
    assert.equal(commands.some(c => c.action === 'cancel'), false);

    editor.open({ operation: 'dup-keep', totalFrames: 480000, selection: [100000, 300000], duplicateKeep: true });
    assert.equal(page.document.getElementById('sl-dialog-layer').hidden, false);
    editor.requestCancel('dup-keep');
    assert.equal(page.document.getElementById('sl-dialog-layer').hidden, true);
    assert.equal(commands.at(-1).action, 'cancel');
    assert.equal(commands.at(-1).operation, 'dup-keep');
});

test('editorCollapsed dataset flag tracks collapsed state and cleans up on close', () => {
    const page = popupPage();
    const editor = page.global('AudioEditor');
    page.window.ScreenlateBridge = { onAudioEditorCommand: () => {} };
    editor.open({ operation: 'pill-state', totalFrames: 480000, selection: [100000, 300000] });
    assert.equal(page.document.documentElement.dataset.editorCollapsed, 'false');
    page.document.getElementById('sl-action-collapse').click();
    assert.equal(page.document.documentElement.dataset.editorCollapsed, 'true');
    editor.close();
    assert.equal(page.document.documentElement.dataset.editorCollapsed, undefined);
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
