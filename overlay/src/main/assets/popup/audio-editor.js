'use strict';

/**
 * Sentence Audio Editor for Screenlate overlay popup.
 *
 * Implements the approved mockup layout and gestures:
 * - Full-history overview bar with continuous viewport drag and no grab-jump.
 * - Zoomed waveform with pan, pinch/button zoom, tap-to-seek, start/end handles, and cut intervals.
 * - Draggable playhead caret that hides when offscreen.
 * - Non-destructive edits: outer phrase bounds + shaded exclusion cuts.
 * - Mode toggle: Result (skips cuts) vs Original.
 * - Silence proposal search and review.
 * - Append-only Refresh preserving edits.
 * - Cancel with dirty confirmation.
 */
var AudioEditor = (() => {
    const editor = document.getElementById('audio-editor');
    const pill = document.getElementById('sl-editor-pill');
    const dialogLayer = document.getElementById('sl-dialog-layer');
    const dialogTitle = document.getElementById('sl-dialog-title');
    const dialogBody = document.getElementById('sl-dialog-body');
    const dialogActions = document.getElementById('sl-dialog-actions');

    const state = {
        isOpen: false,
        operation: null,
        sequence: 0,
        previewSequence: 0,
        initialSequence: 0,
        sourceRevision: 0,
        phase: "editing",
        sampleRate: 48000,
        scanFrame: 0,
        totalFrames: 0,
        sourceStart: 0,
        sourceEnd: 0,
        a: 0,
        b: 0,
        cursor: 0,
        view: 0,
        width: 0,
        cuts: [],
        suggestions: [],
        mode: 'result',
        playing: false,
        expanded: false,
        collapsed: false,
        limit: false,
        reason: null,
        remainingMillis: 0,
        newFrames: 0,
        overviewSummary: [],
        detailSummary: null,
        dirty: false,
        activeCut: -1,
        minPauseSeconds: 1.5,
        edgeMarginMillis: 150,
        labels: {},
    };

    const undo = [];
    let initialBaseline = null;
    let restoreFocus = null;
    let silenceRequestCount = 0;
    let lastSentEdit = null;
    let gestureRevision = 0;
    let waveformRequest = 0;
    let waveformKey = '';
    let waveformFrame = null;
    let toastTimeout = null;

    function hideMessage() {
        if (toastTimeout !== null) clearTimeout(toastTimeout);
        toastTimeout = null;
        const toast = document.querySelector('.sl-toast');
        if (toast) toast.hidden = true;
    }

    function showMessage(message, transient = false) {
        hideMessage();
        const toast = document.querySelector('.sl-toast');
        if (!toast) return;
        toast.textContent = message;
        toast.hidden = false;
        if (transient) toastTimeout = setTimeout(hideMessage, 3000);
    }

    const clamp = (n, min, max) => Math.max(min, Math.min(max, n));
    const sourceSpan = () => Math.max(1, state.sourceEnd - state.sourceStart);
    const boundedWidth = width => clamp(width, Math.min(state.sampleRate, sourceSpan()), sourceSpan());
    const boundedView = view => clamp(view, state.sourceStart, Math.max(state.sourceStart, state.sourceEnd - state.width));
    const editable = () => state.isOpen && ['editing', 'failed'].includes(state.phase);
    function pointerFraction(event, element) {
        const rect = element.getBoundingClientRect();
        return rect.width > 0 && Number.isFinite(event.clientX) ? clamp((event.clientX - rect.left) / rect.width, 0, 1) : null;
    }
    function moveBoundary(key, frame) {
        if (key === 'a') state.a = clamp(frame, state.sourceStart, state.b - Math.min(1, state.b - state.sourceStart));
        else state.b = clamp(frame, state.a + Math.min(1, state.sourceEnd - state.a), state.sourceEnd);
    }


    function send(action, payload = {}) {
        if (!state.isOpen || (!window.ScreenlateBridge || !window.ScreenlateBridge.onAudioEditorCommand)) return false;
        if (['exporting', 'opening'].includes(state.phase) && !['ready', 'cancel', 'without', 'expand', 'collapse', 'drag'].includes(action)) return false;
        const needsEdit = ['edit', 'play', 'seek', 'save', 'silence', 'refresh', 'silenceSettings'].includes(action);
        const edit = needsEdit ? {
            selection: [Math.round(state.a), Math.round(state.b)],
            exclusions: normalizedCuts().map(c => c.map(Math.round)),
            cursor: Math.round(state.cursor), mode: state.mode,
        } : {};
        const packet = JSON.stringify({
            operation: state.operation, sequence: ++state.sequence, sourceRevision: state.sourceRevision,
            action, ...edit, ...payload,
        });
        if (packet.length > 65000) {
            showMessage(labelOf('audio_editor_export_failed', 'Could not export the recording. Adjust the selection or cuts and try again.'));
            return false;
        }
        if (['play', 'seek', 'pause', 'edit', 'save'].includes(action)) state.previewSequence = state.sequence;
        if (['ready', 'edit', 'play', 'seek', 'pause', 'save', 'silence', 'refresh', 'silenceSettings'].includes(action)) state.initialSequence = state.sequence;
        ScreenlateBridge.onAudioEditorCommand(packet);
        return true;
    }

    function receive(data) {
        if (!state.isOpen || data.operation !== state.operation) return;
        if (data.closed) { close(); return; }
        if (data.sequence !== undefined && data.sequence < state.sequence) return;
        if (data.initialSequence !== undefined && data.initialSequence !== state.initialSequence) return;
        if (data.phase !== undefined) {
            if (data.phase === 'exporting' && state.phase !== 'exporting') {
                hideMessage();
            }
            state.phase = data.phase;
        }
        if (data.initialSelection) {
            state.a = data.initialSelection[0]; state.b = data.initialSelection[1];
            state.cursor = state.a;
            state.cuts = data.initialCuts || [];
            initialBaseline = { a: state.a, b: state.b, cuts: state.cuts.map(c => c.slice()) };
            lastSentEdit = JSON.stringify([Math.round(state.a), Math.round(state.b), normalizedCuts()]);
        }
        if (data.timeline) {
            if (data.timeline.sourceRevision < state.sourceRevision) return;
            if (data.timeline.sourceRevision !== state.sourceRevision) {
                invalidateSilence();
                state.detailSummary = null;
                state.overviewSummary = [];
            }
            state.sourceRevision = data.timeline.sourceRevision;
            updateTimeline(data.timeline);
        }
        if (data.waveform && data.waveform.sourceRevision === state.sourceRevision &&
            data.waveform.request === waveformRequest) {
            state.detailSummary = data.waveform;
            if (data.waveform.overview) state.overviewSummary = data.waveform.overview;
            render();
        }
        if (data.status) {
            const status = data.status;
            if (status.newFrames !== undefined) state.newFrames = status.newFrames;
            if (status.limit !== undefined) state.limit = Boolean(status.limit);
            if (status.remainingMillis !== undefined) state.remainingMillis = status.remainingMillis;
            if (status.recording !== undefined) state.recording = Boolean(status.recording);
            if (status.reason !== undefined) state.reason = status.reason;
            renderStatus();
        }
        if (data.refresh) {
            const result = data.refresh;
            const parts = [];
            if (result.addedFrames > 0) parts.push(labelOf('audio_editor_refreshed_toast', 'Added %1$s. Selection and cuts preserved.')
                .replace('%1$s', formatTime(result.addedFrames / state.sampleRate)));
            if (result.sealed) parts.push(result.limit ?
                labelOf('audio_editor_limit_toast', 'Fragment limit reached. No further audio can be added.') :
                labelOf('audio_editor_recording_stopped', 'Recording stopped'));
            else if (!result.addedFrames) parts.push(labelOf('audio_editor_no_new_toast', 'No new audio yet.'));
            showMessage(parts.join(' '));
        }
        if (data.initialSelection && !data.timeline) render();
        if (data.previewSequence === undefined || data.previewSequence === state.previewSequence) {
            if (data.cursor !== undefined) updateCursor(data.cursor);
            if (data.playing !== undefined) setPlaying(data.playing);
        }
        if (data.proposals !== undefined) setSilenceProposals(data.request, data.proposals);
        if (data.error) {
            showMessage(data.error);
        }
        updateBusy();
        requestWaveform();
    }

    function updateBusy() {
        const busy = ['exporting', 'opening'].includes(state.phase);
        for (const id of ['sl-action-add', 'sl-action-play', 'sl-action-refresh', 'sl-action-cut',
            'sl-action-silence', 'sl-action-undo', 'sl-action-accept-silence']) {
            const el = document.getElementById(id);
            if (el) el.disabled = busy;
        }
        if (editor) editor.setAttribute('aria-busy', String(busy));
        const save = document.getElementById('sl-action-add');
        if (save) save.textContent = state.phase === 'failed' ?
            labelOf('audio_editor_retry', 'Retry') : labelOf('audio_editor_add', 'Add');
    }

    function formatTime(seconds) {
        const total = Math.max(0, Math.round(seconds));
        const m = Math.floor(total / 60);
        const s = total % 60;
        return m + ':' + (s < 10 ? '0' : '') + s;
    }

    function relativeTime(frame) {
        const diffSeconds = (frame - state.scanFrame) / state.sampleRate;
        const sign = diffSeconds < 0 ? '−' : '+';
        return sign + formatTime(Math.abs(diffSeconds));
    }

    function invalidateSilence() {
        silenceRequestCount++;
        state.suggestions = [];
    }

    function remember() {
        state.initialSequence = -1;
        invalidateSilence();
        undo.push({ a: state.a, b: state.b, cuts: state.cuts.map(c => c.slice()) });
        if (undo.length > 30) undo.shift();
        checkDirty();
    }

    function checkDirty() {
        if (!initialBaseline) {
            state.dirty = false;
            return;
        }
        const aChanged = Math.round(state.a) !== Math.round(initialBaseline.a);
        const bChanged = Math.round(state.b) !== Math.round(initialBaseline.b);
        const cutsChanged = state.cuts.length !== initialBaseline.cuts.length ||
            state.cuts.some((c, i) => {
                const b = initialBaseline.cuts[i];
                return !b || Math.round(c[0]) !== Math.round(b[0]) || Math.round(c[1]) !== Math.round(b[1]);
            });
        state.dirty = aChanged || bChanged || cutsChanged;
    }

    function normalizedCuts(a = state.a, b = state.b) {
        const rows = state.cuts
            .map(c => [Math.max(a, c[0]), Math.min(b, c[1])])
            .filter(c => c[1] > c[0])
            .sort((x, y) => x[0] - y[0]);
        const out = [];
        for (const c of rows) {
            const last = out.length ? out[out.length - 1] : null;
            if (last && c[0] <= last[1]) {
                last[1] = Math.max(last[1], c[1]);
            } else {
                out.push(c.slice());
            }
        }
        return out;
    }

    function outputLengthSeconds() {
        const totalSelected = Math.max(0, state.b - state.a);
        const cutsTotal = normalizedCuts().reduce((acc, c) => acc + (c[1] - c[0]), 0);
        return Math.max(0, (totalSelected - cutsTotal) / state.sampleRate);
    }

    function requestWaveform() {
        if (!editable()) return;
        const range = [Math.floor(state.view), Math.min(state.sourceEnd, Math.ceil(state.view + state.width))];
        if (range[1] <= range[0]) return;
        const buckets = 256;
        const key = JSON.stringify([state.sourceRevision, range, buckets]);
        if (key === waveformKey) return;
        waveformKey = key;
        const request = ++waveformRequest;
        const operation = state.operation;
        if (waveformFrame !== null) cancelAnimationFrame(waveformFrame);
        waveformFrame = requestAnimationFrame(() => {
            waveformFrame = null;
            if (state.isOpen && operation === state.operation && request === waveformRequest) {
                if (!send('waveform', { range, buckets, request })) waveformKey = '';
            }
        });
    }

    function getAmplitude(frame, overview) {
        const detail = state.detailSummary;
        if (!overview && detail && frame >= detail.range[0] && frame < detail.range[1] && detail.peaks.length) {
            const index = Math.floor((frame - detail.range[0]) / (detail.range[1] - detail.range[0]) * detail.peaks.length);
            return detail.peaks[clamp(index, 0, detail.peaks.length - 1)];
        }
        if (!state.overviewSummary || state.overviewSummary.length === 0) return null;
        const start = state.sourceStart !== undefined ? state.sourceStart : 0;
        const end = state.sourceEnd !== undefined ? state.sourceEnd : state.totalFrames;
        const span = Math.max(1, end - start);
        const rel = clamp(frame - start, 0, span);
        const index = Math.floor((rel / span) * state.overviewSummary.length);
        const clampedIndex = clamp(index, 0, state.overviewSummary.length - 1);
        return state.overviewSummary[clampedIndex];
    }

    function drawWave(svg, overview) {
        const w = svg.clientWidth || (overview ? 340 : 340);
        const h = overview ? 34 : 112;
        const start = state.sourceStart !== undefined ? state.sourceStart : 0;
        const end = state.sourceEnd !== undefined ? state.sourceEnd : state.totalFrames;
        const from = overview ? start : state.view;
        const span = overview ? Math.max(1, end - start) : Math.max(1, state.width);
        const x = f => ((f - from) / span) * w;

        svg.setAttribute('viewBox', `0 0 ${w} ${h}`);
        let html = '';

        if (!overview) {
            const selLeft = Math.max(0, x(state.a));
            const selRight = Math.min(w, x(state.b));
            if (selRight > selLeft) {
                html += `<rect x="${selLeft}" y="3" width="${selRight - selLeft}" height="88" fill="var(--chip-bg)" opacity="0.6"/>`;
            }
        }

        const step = 4;
        for (let i = 1; i < w; i += step) {
            const f = from + (i / w) * span;
            const amplitude = getAmplitude(f, overview);
            if (amplitude === null) continue;
            const amp = amplitude * (overview ? 11 : 33);
            const active = f >= state.a && f <= state.b;
            const fill = active ? 'var(--accent)' : 'var(--muted)';
            const opacity = active ? '0.9' : '0.35';
            const y = (overview ? 17 : 47) - amp;
            html += `<rect x="${i}" y="${y}" width="2" height="${Math.max(2, amp * 2)}" rx="1" fill="${fill}" opacity="${opacity}"/>`;
        }
        if (!state.overviewSummary.length && (overview || !state.detailSummary)) {
            const y = overview ? 17 : 47;
            html += `<line x1="0" x2="${w}" y1="${y}" y2="${y}" stroke="var(--muted)" stroke-dasharray="2 6" opacity="0.35"/>`;
        }

        if (overview) {
            const selLeft = x(state.a);
            const selRight = x(state.b);
            if (selRight > selLeft) {
                html += `<rect x="${selLeft}" y="1" width="${selRight - selLeft}" height="32" fill="var(--chip-bg)" opacity="0.4"/>`;
            }
            const viewLeft = x(state.view);
            const viewWidth = (state.width / Math.max(1, span)) * w;
            html += `<rect id="sl-overview-viewport" x="${viewLeft}" y="2" width="${Math.max(8, viewWidth)}" height="30" rx="3" fill="none" stroke="var(--accent)" stroke-width="2"/>`;
        }

        if (!overview) {
            for (const c of normalizedCuts()) {
                const cutLeft = Math.max(0, x(c[0]));
                const cutRight = Math.min(w, x(c[1]));
                if (cutRight > cutLeft) {
                    html += `<rect x="${cutLeft}" y="3" width="${cutRight - cutLeft}" height="88" fill="#ffe0e5" opacity="0.85"/>`;
                    html += `<line x1="${cutLeft}" x2="${cutRight}" y1="26" y2="70" stroke="#b33b4a" stroke-width="1.5"/>`;
                }
            }
            for (const s of state.suggestions) {
                const sLeft = Math.max(0, x(s[0]));
                const sRight = Math.min(w, x(s[1]));
                if (sRight > sLeft) {
                    html += `<rect x="${sLeft}" y="5" width="${sRight - sLeft}" height="84" fill="#ffe0e5" opacity="0.6" stroke="#b33b4a" stroke-dasharray="3 3"/>`;
                }
            }
        }

        if (state.scanFrame >= from && state.scanFrame <= from + span) {
            const markerX = x(state.scanFrame);
            html += `<line x1="${markerX}" x2="${markerX}" y1="0" y2="${overview ? h : 91}" stroke="#99630b" stroke-width="2"/>`;
        }

        if (!overview && state.cursor >= from && state.cursor <= from + span) {
            const curX = x(state.cursor);
            html += `<line x1="${curX}" x2="${curX}" y1="0" y2="106" stroke="var(--fg)" stroke-width="1.5"/>`;
        }

        svg.innerHTML = html;
    }

    function renderStatus() {
        const recLabel = document.getElementById('sl-record-label');
        if (recLabel) {
            recLabel.textContent = state.recording ?
                labelOf('audio_editor_recording_active', 'Recording continues') :
                labelOf('audio_editor_recording_stopped', 'Recording stopped');
        }

        const recDot = document.querySelector('.sl-inline-dot');
        if (recDot) recDot.style.opacity = state.recording ? '1' : '0.25';

        const remainingEl = document.getElementById('sl-remaining');
        if (remainingEl) {
            remainingEl.textContent = state.reason === 'capture_stopped' ?
                labelOf('audio_editor_recording_stopped', 'Recording stopped') : state.limit ?
                labelOf('audio_editor_limit_reached', 'Limit reached') :
                labelOf('audio_editor_remaining', 'Allowance: %1$s').replace('%1$s', formatTime(state.remainingMillis / 1000));
        }

        const newEl = document.getElementById('sl-new');
        if (newEl) {
            newEl.textContent = labelOf('audio_editor_new_sound', 'New sound: %1$s')
                .replace('%1$s', formatTime(state.newFrames / state.sampleRate));
        }

    }

    function render() {
        if (!editor || !state.isOpen) return;

        checkDirty();
        const editKey = JSON.stringify([Math.round(state.a), Math.round(state.b), normalizedCuts()]);
        if (editKey !== lastSentEdit && editable()) {
            lastSentEdit = editKey;
            send('edit');
        }
        state.width = boundedWidth(state.width);
        state.view = boundedView(state.view);
        requestWaveform();

        editor.classList.toggle('sl-expanded', state.expanded);
        editor.hidden = state.collapsed;
        if (pill) pill.hidden = !state.collapsed;

        const expandBtn = document.getElementById('sl-action-expand');
        if (expandBtn) {
            expandBtn.setAttribute('aria-label', state.expanded ?
                labelOf('audio_editor_compact', 'Collapse to popup width') :
                labelOf('audio_editor_expand', 'Expand editor'));
        }

        renderStatus();

        const totalWrap = document.getElementById('sl-total-wrap');
        const retainedFrames = Math.max(0, (state.sourceEnd !== undefined ? state.sourceEnd : state.totalFrames) - (state.sourceStart || 0));
        const formattedTotal = formatTime(retainedFrames / state.sampleRate);
        if (totalWrap) {
            totalWrap.innerHTML = labelOf('audio_editor_retained', 'Retained: %1$s')
                .replace('%1$s', `<span id="sl-total">${formattedTotal}</span>`);
        } else {
            const totalEl = document.getElementById('sl-total');
            if (totalEl) totalEl.textContent = formattedTotal;
        }

        const startEl = document.getElementById('sl-start-label');
        if (startEl) startEl.textContent = relativeTime(state.a);

        const endEl = document.getElementById('sl-end-label');
        if (endEl) endEl.textContent = relativeTime(state.b);

        const lengthEl = document.getElementById('sl-length-label');
        if (lengthEl) lengthEl.textContent = formatTime(outputLengthSeconds());

        const leftTimeEl = document.getElementById('sl-left-time');
        if (leftTimeEl) leftTimeEl.textContent = relativeTime(state.view);

        const rightTimeEl = document.getElementById('sl-right-time');
        if (rightTimeEl) rightTimeEl.textContent = relativeTime(state.view + state.width);

        const playLabel = document.getElementById('sl-play-label');
        if (playLabel) {
            playLabel.textContent = state.playing ?
                labelOf('audio_editor_pause', 'Pause') :
                labelOf('audio_editor_listen', 'Listen');
        }

        const modeResult = document.getElementById('sl-mode-result');
        if (modeResult) modeResult.setAttribute('aria-pressed', String(state.mode === 'result'));

        const modeOriginal = document.getElementById('sl-mode-original');
        if (modeOriginal) modeOriginal.setAttribute('aria-pressed', String(state.mode === 'original'));

        const undoBtn = document.getElementById('sl-action-undo');
        if (undoBtn) undoBtn.disabled = !editable() || undo.length === 0;

        // Boundaries
        const startHandle = document.getElementById('sl-start');
        if (startHandle) {
            const inView = state.a >= state.view && state.a <= state.view + state.width;
            startHandle.hidden = !inView;
            if (inView) {
                startHandle.style.left = clamp(((state.a - state.view) / state.width) * 100, 0, 100) + '%';
            }
        }

        const endHandle = document.getElementById('sl-end');
        if (endHandle) {
            const inView = state.b >= state.view && state.b <= state.view + state.width;
            endHandle.hidden = !inView;
            if (inView) {
                endHandle.style.left = clamp(((state.b - state.view) / state.width) * 100, 0, 100) + '%';
            }
        }

        // Playhead
        const playhead = document.getElementById('sl-playhead');
        if (playhead) {
            const inView = state.cursor >= state.view && state.cursor <= state.view + state.width;
            playhead.hidden = !inView;
            playhead.min = state.view;
            playhead.max = Math.min(state.sourceEnd, state.view + state.width);
            playhead.value = state.cursor;
        }

        // Cut handles
        const cutHandles = document.getElementById('sl-cut-handles');
        if (cutHandles) {
            const active = state.cuts[state.activeCut];
            if (!active || active[1] <= state.a || active[0] >= state.b) {
                cutHandles.innerHTML = '';
            } else {
                if (!cutHandles.childElementCount) {
                    cutHandles.innerHTML =
                        '<button type="button" class="sl-cut-edge" data-cut-edge="0" aria-label="Start of cut">❮</button>' +
                        '<button type="button" class="sl-cut-edge" data-cut-edge="1" aria-label="End of cut">❯</button>';
                }
                const leftBtn = cutHandles.children[0];
                const rightBtn = cutHandles.children[1];
                const leftInView = active[0] >= state.a && active[0] < state.b && active[0] >= state.view && active[0] <= state.view + state.width;
                const rightInView = active[1] > state.a && active[1] <= state.b && active[1] >= state.view && active[1] <= state.view + state.width;
                leftBtn.hidden = !leftInView;
                rightBtn.hidden = !rightInView;
                if (leftInView) leftBtn.style.left = clamp(((active[0] - state.view) / state.width) * 100, 0, 100) + '%';
                if (rightInView) rightBtn.style.left = clamp(((active[1] - state.view) / state.width) * 100, 0, 100) + '%';
            }
        }

        // Cut chips
        const cutsContainer = document.getElementById('sl-cuts');
        if (cutsContainer) {
            cutsContainer.innerHTML = state.cuts.map((c, i) =>
                `<span class="sl-cut-chip">` +
                `<button type="button" data-select-cut="${i}">${relativeTime(c[0])} — ${relativeTime(c[1])}</button>` +
                `<button type="button" data-remove-cut="${i}" aria-label="Restore">×</button>` +
                `</span>`
            ).join('');
        }

        // Suggestions
        const suggestionsBox = document.getElementById('sl-suggestions');
        const suggestionLabel = document.getElementById('sl-suggestion-label');
        if (suggestionsBox && suggestionLabel) {
            suggestionsBox.hidden = state.suggestions.length === 0;
            suggestionLabel.textContent = labelOf('audio_editor_found_pauses', 'Found %1$d pauses')
                .replace('%1$d', state.suggestions.length);
        }

        const overviewSvg = document.getElementById('sl-overview');
        if (overviewSvg) drawWave(overviewSvg, true);

        const waveSvg = document.getElementById('sl-wave');
        if (waveSvg) drawWave(waveSvg, false);
    }

    function labelOf(key, fallback) {
        return (state.labels && state.labels[key]) || fallback;
    }

    function focusCut(i) {
        state.activeCut = i;
        const c = state.cuts[i];
        if (!c) return;
        state.width = boundedWidth(state.sampleRate * 16);
        const center = (c[0] + c[1]) / 2;
        state.view = boundedView(center - state.width / 2);
    }

    function showDialog(title, bodyHtml, buttons) {
        restoreFocus = document.activeElement;
        dialogTitle.textContent = title;
        dialogBody.innerHTML = bodyHtml;
        dialogActions.innerHTML = '';
        buttons.forEach(btn => {
            const b = document.createElement('button');
            b.type = 'button';
            b.className = 'sl-button ' + (btn.primary ? 'sl-filled' : 'sl-text');
            b.textContent = btn.label;
            b.addEventListener('click', () => {
                closeDialog();
                if (btn.run) btn.run();
            });
            dialogActions.append(b);
        });
        dialogLayer.hidden = false;
        var firstBtn = dialogActions.querySelector('button');
        if (firstBtn) firstBtn.focus();
    }

    function closeDialog() {
        dialogLayer.hidden = true;
        if (restoreFocus && restoreFocus.isConnected) restoreFocus.focus();
    }

    function showSilenceSettings() {
        showDialog(
            labelOf('audio_editor_silence_title', 'Silence search'),
            `<label>${labelOf('audio_editor_min_pause', 'Minimum pause, s')}` +
            `<input id="sl-silence-min" type="number" min="0.5" max="5.0" step="0.1" value="${state.minPauseSeconds}"></label>` +
            `<label>${labelOf('audio_editor_edge_margin', 'Margin at each edge, ms')}` +
            `<input id="sl-edge-margin" type="number" min="0" max="500" step="50" value="${state.edgeMarginMillis}"></label>`,
            [
                { label: labelOf('audio_editor_cancel', 'Cancel') },
                {
                    label: labelOf('audio_editor_save', 'Save'),
                    primary: true,
                    run: () => {
                        const minEl = document.getElementById('sl-silence-min');
                        const edgeEl = document.getElementById('sl-edge-margin');
                        const minValue = minEl ? parseFloat(minEl.value) : NaN;
                        const edgeValue = edgeEl ? parseInt(edgeEl.value, 10) : NaN;
                        const minS = Number.isFinite(minValue) ? minValue : state.minPauseSeconds;
                        const edgeMs = Number.isFinite(edgeValue) ? edgeValue : state.edgeMarginMillis;
                        if (minS < 0.1 || minS > 30 || edgeMs < 0 || edgeMs > 2000) return;
                        if (send('silenceSettings', { minPauseSeconds: minS, edgeMarginMillis: edgeMs })) {
                            state.minPauseSeconds = minS; state.edgeMarginMillis = edgeMs;
                        }
                    }
                }
            ]
        );
    }

    function showInfoDialog() {
        showDialog(
            labelOf('audio_editor_info_title', 'Sentence recording'),
            `<p>${labelOf('audio_editor_info_body', 'Audio history is protected when scanning. Refresh appends retained continuation without altering edits. Recording gaps are never joined.')}</p>`,
            [{ label: labelOf('audio_editor_understood', 'Got it'), primary: true }]
        );
    }

    function confirmDiscard(onConfirm) {
        if (!state.dirty) {
            onConfirm();
            return;
        }
        showDialog(
            labelOf('audio_editor_discard_title', 'Cancel note?'),
            `<p>${labelOf('audio_editor_discard_message', 'Audio clip changes will be lost.')}</p>`,
            [
                { label: labelOf('audio_editor_keep_editing', 'Keep editing') },
                { label: labelOf('audio_editor_discard', 'Cancel note'), primary: true, run: onConfirm }
            ]
        );
    }

    function requestCancel(operation) {
        if (!state.isOpen || state.operation !== operation) return;
        checkDirty();
        confirmDiscard(() => send('cancel'));
    }

    // Public API
    function open(data) {
        const tools = document.getElementById('sl-tools');
        if (tools) tools.open = false;
        hideMessage();
        gestureRevision++;
        state.isOpen = true;
        state.operation = data.operation || null;
        lastSentEdit = JSON.stringify([Math.round(data.selection ? data.selection[0] : 0), Math.round(data.selection ? data.selection[1] : 0), data.cuts || []]);
        state.sequence = 0;
        state.previewSequence = 0;
        state.initialSequence = 0;
        state.sourceRevision = data.sourceRevision || 0;
        state.phase = data.phase || 'editing';
        silenceRequestCount = 0;
        state.sampleRate = data.sampleRate || 48000;
        state.scanFrame = data.scanFrame || 0;
        state.totalFrames = data.totalFrames || 0;
        state.sourceStart = data.sourceStart !== undefined ? data.sourceStart : 0;
        state.sourceEnd = data.sourceEnd !== undefined ? data.sourceEnd : state.totalFrames;
        state.a = clamp(data.selection ? data.selection[0] : state.sourceStart, state.sourceStart, state.sourceEnd);
        state.b = clamp(data.selection ? data.selection[1] : state.sourceEnd, state.a, state.sourceEnd);
        state.cuts = (data.cuts || []).map(c => [c[0], c[1]]);
        state.suggestions = [];
        state.cursor = state.a;
        const span = Math.max(1, state.sourceEnd - state.sourceStart);
        state.width = Math.min(span, Math.max(state.sampleRate * 10, (state.b - state.a) + state.sampleRate * 4));
        state.view = clamp(state.a - state.sampleRate * 2, state.sourceStart, Math.max(state.sourceStart, state.sourceEnd - state.width));
        state.mode = data.mode || 'result';
        state.playing = false;
        state.recording = Boolean(data.recording);
        state.expanded = Boolean(data.expanded);
        state.collapsed = false;
        state.limit = Boolean(data.limit);
        state.reason = data.reason || null;
        state.remainingMillis = data.remainingMillis || 0;
        state.newFrames = data.newFrames || 0;
        state.overviewSummary = data.overviewSummary || [];
        state.detailSummary = null;
        waveformKey = '';
        waveformRequest++;
        state.labels = data.labels || {};
        state.minPauseSeconds = Number.isFinite(data.minPauseSeconds) && data.minPauseSeconds > 0 ? data.minPauseSeconds : 1.5;
        state.edgeMarginMillis = Number.isFinite(data.edgeMarginMillis) && data.edgeMarginMillis >= 0 ? data.edgeMarginMillis : 150;
        state.activeCut = -1;

        initialBaseline = {
            a: state.a,
            b: state.b,
            cuts: state.cuts.map(c => c.slice())
        };
        undo.length = 0;
        state.dirty = false;

        const subtitle = document.getElementById('sl-editor-subtitle');
        if (subtitle) {
            subtitle.textContent = data.term ? `${data.term} · ${data.reading || ''}` : '';
        }

        // Hide dictionary card content
        const content = document.getElementById('content');
        const header = document.querySelector('.header');
        if (content) content.hidden = true;
        if (header) header.hidden = true;

        if (editor) editor.hidden = false;
        if (pill) pill.hidden = true;

        applyStaticLabels();
        updateBusy();
        render();
        if (state.phase === 'opening' && !data.duplicateKeep) {
            const operation = state.operation;
            requestAnimationFrame(() => {
                if (state.isOpen && state.operation === operation && state.phase === 'opening') send('ready');
            });
        }

        if (data.duplicateKeep) {
            showDialog(
                labelOf('audio_editor_duplicate_keep_title', 'Existing audio kept'),
                `<p>${labelOf('audio_editor_duplicate_keep_body', 'This note already has sentence audio, which will be kept according to your field update settings.')}</p>`,
                [
                    {
                        label: labelOf('audio_editor_duplicate_keep_change_settings', 'Change settings'),
                        run: () => {
                            send('settings');
                        }
                    },
                    {
                        label: labelOf('audio_editor_duplicate_keep_continue', 'Continue without audio'),
                        primary: true,
                        run: () => {
                            send('without');
                        }
                    }
                ]
            );
        }
    }

    function applyStaticLabels() {
        const setAttr = (id, attr, val) => {
            const el = document.getElementById(id);
            if (el) el.setAttribute(attr, val);
        };
        const setText = (id, val) => {
            const el = document.getElementById(id);
            if (el) el.textContent = val;
        };

        setText('sl-editor-title', labelOf('audio_editor_title', 'Sentence audio'));
        setText('sl-pill-title', labelOf('audio_editor_title', 'Sentence audio'));
        setAttr('sl-action-collapse', 'aria-label', labelOf('audio_editor_collapse', 'Collapse editor'));
        setAttr('sl-action-info', 'aria-label', labelOf('audio_editor_info_title', 'Sentence recording'));
        setText('sl-marker-legend', labelOf('audio_editor_marker_legend', 'Marker — scan'));
        setText('sl-from-scan', labelOf('audio_editor_from_scan', 'From scan'));
        setText('sl-summary-start', labelOf('audio_editor_start', 'Start'));
        setText('sl-summary-end', labelOf('audio_editor_end', 'End'));
        setText('sl-summary-to-card', labelOf('audio_editor_to_card', 'To card'));
        setText('sl-mode-result', labelOf('audio_editor_result', 'Result'));
        setText('sl-mode-original', labelOf('audio_editor_original', 'Original'));
        setAttr('sl-action-zoom-out', 'aria-label', labelOf('audio_editor_zoom_out', 'Zoom out'));
        setAttr('sl-action-zoom-in', 'aria-label', labelOf('audio_editor_zoom_in', 'Zoom in'));
        setText('sl-tools-summary', labelOf('audio_editor_cut', 'Cut') + ' · ' + labelOf('audio_editor_find_silence', 'Find silence'));
        setText('sl-cut-label', labelOf('audio_editor_cut', 'Cut'));
        setText('sl-silence-label', labelOf('audio_editor_find_silence', 'Find silence'));
        setAttr('sl-action-silence-settings', 'aria-label', labelOf('audio_editor_silence_title', 'Silence search'));
        setAttr('sl-action-undo', 'aria-label', labelOf('audio_editor_undo', 'Undo'));
        setText('sl-action-accept-silence', labelOf('audio_editor_apply', 'Apply'));
        setText('sl-action-reject-silence', labelOf('audio_editor_dismiss', 'Dismiss'));
        setText('sl-refresh-label', labelOf('audio_editor_refresh', 'Refresh'));
        setText('sl-action-cancel', labelOf('audio_editor_cancel', 'Cancel'));
        setText('sl-action-without', labelOf('audio_editor_without', 'Without audio'));
        setText('sl-action-add', labelOf('audio_editor_add', 'Add'));
        setAttr('sl-start', 'aria-label', labelOf('audio_editor_start_handle', 'Start of saved audio'));
        setAttr('sl-end', 'aria-label', labelOf('audio_editor_end_handle', 'End of saved audio'));
    }

    function updateTimeline(data) {
        state.totalFrames = data.totalFrames !== undefined ? data.totalFrames : state.totalFrames;
        state.sourceStart = data.sourceStart !== undefined ? data.sourceStart : state.sourceStart;
        state.sourceEnd = data.sourceEnd !== undefined ? data.sourceEnd : state.sourceEnd;
        state.newFrames = data.newFrames !== undefined ? data.newFrames : state.newFrames;
        if (data.limit !== undefined) state.limit = Boolean(data.limit);
        if (data.reason !== undefined) state.reason = data.reason;
        state.remainingMillis = data.remainingMillis !== undefined ? data.remainingMillis : state.remainingMillis;
        state.recording = data.recording !== undefined ? Boolean(data.recording) : state.recording;
        if (data.overviewSummary) {
            state.overviewSummary = data.overviewSummary;
        }
        render();
    }

    function updateCursor(frame) {
        state.cursor = frame;
        const playhead = document.getElementById('sl-playhead');
        if (playhead) {
            const inView = frame >= state.view && frame <= state.view + state.width;
            playhead.hidden = !inView;
            playhead.value = frame;
        }
        const waveSvg = document.getElementById('sl-wave');
        if (waveSvg) drawWave(waveSvg, false);
    }

    function setPlaying(isPlaying) {
        state.playing = isPlaying;
        const playLabel = document.getElementById('sl-play-label');
        if (playLabel) {
            playLabel.textContent = isPlaying ?
                labelOf('audio_editor_pause', 'Pause') :
                labelOf('audio_editor_listen', 'Listen');
        }
    }

    function setSilenceProposals(request, proposals) {
        if (request !== silenceRequestCount) return;
        state.suggestions = (proposals || []).map(p => [p[0], p[1]]);
        if (state.suggestions.length === 0) {
            showMessage(labelOf('audio_editor_no_silence', 'No confident pauses found in selection.'), true);
        }
        render();
    }

    function close() {
        hideMessage();
        if (waveformFrame !== null) cancelAnimationFrame(waveformFrame);
        waveformFrame = null;
        waveformKey = '';
        state.detailSummary = null;
        gestureRevision++;
        state.isOpen = false;
        if (editor) editor.hidden = true;
        if (pill) pill.hidden = true;
        dialogLayer.hidden = true;

        const content = document.getElementById('content');
        const header = document.querySelector('.header');
        if (content) content.hidden = false;
        if (header) header.hidden = false;
    }

    // Attach DOM event listeners once DOM is ready
    function initListeners() {
        // Window dragging by header
        const editorHead = document.getElementById('sl-editor-head');
        if (editorHead) {
            let dragging = false;
            let dragRevision = -1;
            let lastX = 0;
            let lastY = 0;

            editorHead.addEventListener('pointerdown', e => {
                if (e.target.closest('button') || e.target.closest('.sl-toolbar')) return;
                dragging = true;
                dragRevision = gestureRevision;
                lastX = typeof e.screenX === 'number' && e.screenX !== 0 ? e.screenX : (e.clientX || 0);
                lastY = typeof e.screenY === 'number' && e.screenY !== 0 ? e.screenY : (e.clientY || 0);
                if (typeof editorHead.setPointerCapture === 'function') {
                    try { editorHead.setPointerCapture(e.pointerId); } catch (_) {}
                }
            });

            editorHead.addEventListener('pointermove', e => {
                if (!dragging || dragRevision !== gestureRevision || !state.isOpen) return;
                const currentX = typeof e.screenX === 'number' && e.screenX !== 0 ? e.screenX : (e.clientX || 0);
                const currentY = typeof e.screenY === 'number' && e.screenY !== 0 ? e.screenY : (e.clientY || 0);
                const dx = currentX - lastX;
                const dy = currentY - lastY;
                lastX = currentX;
                lastY = currentY;
                if (dx !== 0 || dy !== 0) send('drag', { dx, dy });
            });

            const stopDrag = e => {
                if (!dragging || dragRevision !== gestureRevision || !state.isOpen) return;
                dragging = false;
                if (typeof editorHead.releasePointerCapture === 'function') {
                    try { editorHead.releasePointerCapture(e.pointerId); } catch (_) {}
                }
            };

            editorHead.addEventListener('pointerup', stopDrag);
            editorHead.addEventListener('pointercancel', stopDrag);
        }

        // Start / End boundary handle dragging
        for (const [id, key] of [['sl-start', 'a'], ['sl-end', 'b']]) {
            const el = document.getElementById(id);
            if (!el) continue;
            let active = false;
            let handleRevision = -1;
            el.addEventListener('pointerdown', e => {
                if (!editable()) return;
                e.stopPropagation();
                remember();
                active = true;
                handleRevision = gestureRevision;
                el.setPointerCapture(e.pointerId);
            });
            el.addEventListener('pointermove', e => {
                if (!active || handleRevision !== gestureRevision || !editable()) return;
                const waveSvg = document.getElementById('sl-wave');
                const frac = pointerFraction(e, waveSvg);
                if (frac === null) return;
                const frame = state.view + frac * state.width;
                moveBoundary(key, frame);
                render();
            });
            const finish = () => { active = false; };
            el.addEventListener('pointerup', finish);
            el.addEventListener('pointercancel', finish);
            el.addEventListener('keydown', e => {
                if (!editable()) return;
                if (!['ArrowLeft', 'ArrowRight'].includes(e.key)) return;
                e.preventDefault();
                remember();
                const delta = (e.key === 'ArrowLeft' ? -0.1 : 0.1) * state.sampleRate;
                moveBoundary(key, state[key] + delta);
                render();
            });
        }

        // Cut edge dragging
        let cutDrag = null;
        const cutHandles = document.getElementById('sl-cut-handles');
        if (cutHandles) {
            cutHandles.addEventListener('pointerdown', e => {
                if (!editable()) return;
                const edge = e.target.closest('[data-cut-edge]');
                if (!edge || !state.cuts[state.activeCut]) return;
                e.stopPropagation();
                remember();
                cutDrag = { index: state.activeCut, edge: parseInt(edge.dataset.cutEdge, 10), pointerId: e.pointerId, revision: gestureRevision };
                edge.setPointerCapture(e.pointerId);
            });
            cutHandles.addEventListener('pointermove', e => {
                if (!cutDrag || cutDrag.pointerId !== e.pointerId || cutDrag.revision !== gestureRevision || !editable() || !state.cuts[cutDrag.index]) return;
                const c = state.cuts[cutDrag.index];
                if (c[1] <= state.a || c[0] >= state.b) return;
                const waveSvg = document.getElementById('sl-wave');
                const frac = pointerFraction(e, waveSvg);
                if (frac === null) return;
                const frame = state.view + frac * state.width;
                if (cutDrag.edge === 0) {
                    c[0] = clamp(frame, state.a, Math.min(c[1], state.b) - 1);
                } else {
                    c[1] = clamp(frame, Math.max(c[0], state.a) + 1, state.b);
                }
                render();
            });
            const finishCutDrag = e => {
                if (!cutDrag || cutDrag.pointerId !== e.pointerId) return;
                if (cutDrag && cutDrag.revision === gestureRevision && state.cuts[cutDrag.index]) {
                    const anchor = state.cuts[cutDrag.index][0];
                    state.cuts = normalizedCuts(state.sourceStart, state.sourceEnd);
                    state.activeCut = state.cuts.findIndex(c => c[0] <= anchor && anchor < c[1]);
                    render();
                }
                cutDrag = null;
            };
            cutHandles.addEventListener('pointerup', finishCutDrag);
            cutHandles.addEventListener('pointercancel', finishCutDrag);
            cutHandles.addEventListener('lostpointercapture', finishCutDrag);
        }

        // Zoomed waveform gestures: pan, pinch, tap
        const waveSvg = document.getElementById('sl-wave');
        if (waveSvg) {
            const points = new Map();
            let gesture = null;

            waveSvg.addEventListener('pointerdown', e => {
                if (gesture && gesture.revision !== gestureRevision) { points.clear(); gesture = null; }
                waveSvg.setPointerCapture(e.pointerId);
                points.set(e.pointerId, { x: e.clientX, y: e.clientY });
                if (points.size === 1) {
                    gesture = { x: e.clientX, y: e.clientY, axis: null, view: state.view, width: state.width, moved: false, revision: gestureRevision };
                } else if (points.size === 2) {
                    const p = [...points.values()];
                    gesture = { pinch: Math.abs(p[0].x - p[1].x), width: state.width, center: state.view + state.width / 2, revision: gestureRevision };
                }
            });

            waveSvg.addEventListener('pointermove', e => {
                if (!points.has(e.pointerId) || !gesture || gesture.revision !== gestureRevision || !state.isOpen) return;
                points.set(e.pointerId, { x: e.clientX, y: e.clientY });
                if (points.size === 2 && gesture.pinch) {
                    const p = [...points.values()];
                    const dist = Math.max(10, Math.abs(p[0].x - p[1].x));
                    state.width = boundedWidth(gesture.width * gesture.pinch / dist);
                    state.view = boundedView(gesture.center - state.width / 2);
                    render();
                } else if (points.size === 1 && !gesture.pinch) {
                    const dx = e.clientX - gesture.x;
                    const dy = e.clientY - gesture.y;
                    if (!gesture.axis && Math.max(Math.abs(dx), Math.abs(dy)) > 6) {
                        gesture.axis = Math.abs(dy) > Math.abs(dx) ? 'vertical' : 'horizontal';
                        gesture.moved = true;
                    }
                    if (gesture.axis === 'horizontal') {
                        state.view = boundedView(gesture.view - (dx / Math.max(1, waveSvg.clientWidth)) * gesture.width);
                        render();
                    }
                }
            });

            const finishWaveGesture = e => {
                if (!points.has(e.pointerId)) return;
                if (gesture && gesture.revision === gestureRevision && state.isOpen && !gesture.moved && !gesture.pinch) {
                    // Tap seeks
                    const frac = pointerFraction(e, waveSvg);
                    if (frac !== null) {
                        state.cursor = clamp(state.view + frac * state.width, state.sourceStart, state.sourceEnd);
                        send('seek');
                    }
                }
                points.delete(e.pointerId);
                if (!points.size) gesture = null;
                render();
            };
            waveSvg.addEventListener('pointerup', finishWaveGesture);
            waveSvg.addEventListener('pointercancel', e => { points.delete(e.pointerId); gesture = null; });
        }

        // Overview bar: continuous viewport drag without jump, tap to seek
        const overviewSvg = document.getElementById('sl-overview');
        if (overviewSvg) {
            let overviewDrag = null;
            overviewSvg.addEventListener('pointerdown', e => {
                const r = overviewSvg.getBoundingClientRect();
                if (r.width <= 0) return;
                const x = e.clientX - r.left;
                const viewLeftPx = ((state.view - state.sourceStart) / sourceSpan()) * r.width;
                const viewRightPx = ((state.view + state.width - state.sourceStart) / sourceSpan()) * r.width;
                overviewSvg.setPointerCapture(e.pointerId);

                const anchor = x < viewLeftPx - 6 || x > viewRightPx + 6 ?
                    boundedView(state.sourceStart + (x / r.width) * sourceSpan() - state.width / 2) : state.view;
                overviewDrag = { pointerId: e.pointerId, x: e.clientX, y: e.clientY, axis: null,
                    view: anchor, total: sourceSpan(), widthPx: r.width, revision: gestureRevision };
            });

            overviewSvg.addEventListener('pointermove', e => {
                if (!overviewDrag || overviewDrag.revision !== gestureRevision || !state.isOpen || e.pointerId !== overviewDrag.pointerId) return;
                const dx = e.clientX - overviewDrag.x;
                const dy = e.clientY - overviewDrag.y;
                if (!overviewDrag.axis && Math.max(Math.abs(dx), Math.abs(dy)) > 6) {
                    overviewDrag.axis = Math.abs(dy) > Math.abs(dx) ? 'vertical' : 'horizontal';
                }
                if (overviewDrag.axis !== 'horizontal') return;
                state.view = boundedView(overviewDrag.view + (dx / overviewDrag.widthPx) * overviewDrag.total);
                render();
            });

            const finishOverviewDrag = () => { overviewDrag = null; };
            overviewSvg.addEventListener('pointerup', e => {
                if (overviewDrag && overviewDrag.revision === gestureRevision && state.isOpen &&
                    overviewDrag.pointerId === e.pointerId && !overviewDrag.axis) {
                    state.view = overviewDrag.view;
                    render();
                }
                finishOverviewDrag();
            });
            overviewSvg.addEventListener('pointercancel', finishOverviewDrag);
        }

        // Playhead range input: seeks playhead without panning
        const playhead = document.getElementById('sl-playhead');
        if (playhead) {
            playhead.addEventListener('input', e => {
                state.cursor = parseFloat(e.target.value);
                send('seek');
                render();
            });
        }

        function bind(id, event, handler) {
            const el = typeof id === 'string' ? document.getElementById(id) : id;
            const edits = ['sl-action-cut', 'sl-cuts', 'sl-action-undo', 'sl-action-accept-silence'];
            if (el) el.addEventListener(event, e => {
                if (!editable() && edits.includes(id)) return;
                handler(e);
            });
        }

        // Buttons & controls
        bind('sl-action-play', 'click', () => {
            state.playing = !state.playing;
            if (state.playing) {
                if (state.cursor < state.a || state.cursor >= state.b) state.cursor = state.a;
                send('play');
            } else {
                send('pause');
            }
            render();
        });

        bind('sl-mode-result', 'click', () => {
            state.mode = 'result';
            if (state.playing) send('seek');
            render();
        });

        bind('sl-mode-original', 'click', () => {
            state.mode = 'original';
            if (state.playing) send('seek');
            render();
        });

        bind('sl-action-zoom-in', 'click', () => {
            state.width = boundedWidth(state.width / 1.6);
            state.view = boundedView(state.cursor - state.width / 2);
            render();
        });

        bind('sl-action-zoom-out', 'click', () => {
            state.width = boundedWidth(state.width * 1.6);
            state.view = boundedView(state.cursor - state.width / 2);
            render();
        });

        bind('sl-action-cut', 'click', () => {
            remember();
            const cutStart = clamp(state.cursor - state.sampleRate * 0.5, state.a, state.b - Math.min(1, state.b - state.a));
            const cutEnd = Math.min(state.b, cutStart + state.sampleRate * 1.5);
            state.cuts.push([cutStart, cutEnd]);
            state.cursor = cutEnd;
            focusCut(state.cuts.length - 1);
            render();
        });

        bind('sl-action-silence', 'click', () => {
            invalidateSilence();
            send('silence', { request: silenceRequestCount });
        });

        bind('sl-action-silence-settings', 'click', showSilenceSettings);

        bind('sl-action-undo', 'click', () => {
            const old = undo.pop();
            if (old) {
                invalidateSilence();
                state.a = old.a;
                state.b = old.b;
                state.cuts = old.cuts;
                state.activeCut = -1;
                checkDirty();
                render();
            }
        });

        bind('sl-action-accept-silence', 'click', () => {
            if (state.suggestions.length) {
                const accepted = state.suggestions.map(c => c.slice());
                remember();
                const first = state.cuts.length;
                state.cuts.push(...accepted);
                focusCut(first);
                render();
            }
        });

        bind('sl-action-reject-silence', 'click', () => {
            invalidateSilence();
            render();
        });

        bind('sl-action-refresh', 'click', () => {
            send('refresh');
        });

        bind('sl-action-expand', 'click', () => {
            state.expanded = !state.expanded;
            state.collapsed = false;
            send('expand', { expanded: state.expanded });
            render();
        });

        bind('sl-action-collapse', 'click', () => {
            state.collapsed = true;
            send('collapse', { collapsed: true });
            render();
        });

        bind('sl-editor-pill', 'click', () => {
            state.collapsed = false;
            send('collapse', { collapsed: false });
            render();
        });

        bind('sl-action-info', 'click', showInfoDialog);

        bind('sl-action-cancel', 'click', () => {
            requestCancel(state.operation);
        });

        bind('sl-action-without', 'click', () => {
            send('without');
        });

        bind('sl-action-add', 'click', () => {
            if (['exporting', 'opening'].includes(state.phase)) return;
            if (outputLengthSeconds() <= 0) {
                showMessage(labelOf('audio_editor_empty_output', 'Selected range is empty. Adjust selection or cuts.'), true);
                return;
            }
            if (send('save')) {
                hideMessage();
                state.phase = 'exporting';
                updateBusy();
            }
        });

        // Delegate cut chip selection and removal
        bind('sl-cuts', 'click', e => {
            const selectBtn = e.target.closest('[data-select-cut]');
            if (selectBtn) {
                focusCut(parseInt(selectBtn.dataset.selectCut, 10));
                render();
                return;
            }
            const removeBtn = e.target.closest('[data-remove-cut]');
            if (removeBtn) {
                remember();
                const idx = parseInt(removeBtn.dataset.removeCut, 10);
                state.cuts.splice(idx, 1);
                state.activeCut = -1;
                render();
            }
        });
    }

    initListeners();
    window.addEventListener('resize', () => { gestureRevision++; if (state.isOpen) render(); });

    return {
        open,
        receive,
        updateTimeline,
        updateCursor,
        setPlaying,
        setSilenceProposals,
        close,
        confirmDiscard,
        requestCancel,
        state,
    };
})();

if (typeof window !== 'undefined') {
    window.AudioEditor = AudioEditor;
}
