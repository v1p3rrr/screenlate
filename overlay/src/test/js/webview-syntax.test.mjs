import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';

const require = createRequire(new URL('../../../../scripts/page-tests/package.json', import.meta.url));
const { parse } = require('acorn');
const popup = new URL('../../main/assets/popup/', import.meta.url);
const renderer = new URL('../../../../dictionary/render-yomitan/src/main/assets/yomitan-render/', import.meta.url);
const html = readFileSync(new URL('popup.html', popup), 'utf8');

for (const [, src] of html.matchAll(/<script src="([^"]+)"/g)) {
    const script = src.startsWith('../yomitan-render/')
        ? new URL(src.split('/').pop(), renderer)
        : new URL(src, popup);
    test(src + ' parses on the Android 7 WebView syntax baseline', () => {
        // Chromium 69 implements the ES2018 syntax used by the popup.
        assert.doesNotThrow(() => parse(readFileSync(script, 'utf8'), { ecmaVersion: 2018 }));
    });
}
