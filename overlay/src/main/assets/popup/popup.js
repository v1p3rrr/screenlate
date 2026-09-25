'use strict';

// Rendering entry point called from Kotlin with a JSON state object.
const Popup = (() => {
    const title = document.getElementById('title');
    const spinner = document.getElementById('spinner');
    const content = document.getElementById('content');

    document.getElementById('close').addEventListener('click', () => ScreenlateBridge.onClose());

    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function renderOcr(state) {
        content.replaceChildren();
        if (state.engine) content.append(element('span', 'chip', state.engine));
        if (state.lookup) {
            content.append(element('div', 'label', state.labels.lookup));
            content.append(element('div', null, state.lookup));
        }
        if (state.line) {
            content.append(element('div', 'label', state.labels.line));
            content.append(element('div', null, state.line));
        }
    }

    function render(state) {
        document.documentElement.dataset.theme = state.theme;
        title.textContent = state.title || '';
        spinner.hidden = !state.pending;
        if (state.message) {
            content.replaceChildren(element('div', 'message', state.message));
        } else {
            renderOcr(state);
        }
        content.scrollTop = 0;
    }

    return { render };
})();

ScreenlateBridge.onReady();
