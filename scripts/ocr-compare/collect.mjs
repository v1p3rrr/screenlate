// Takes a screenshot on the connected emulator and writes its ground truth: the text on screen with boxes in screen
// pixels, for score.py.
//
//   node scripts/ocr-compare/collect.mjs <out dir> <language>-<name> [--web]
//
// Without --web the regions are the text views of the Android interface (uiautomator). With --web the regions are the
// lines of the page open in Chrome, from the page itself through the DevTools protocol, with a box per word; run
// `adb forward tcp:9222 localabstract:chrome_devtools_remote` first. Text Chrome draws in its own toolbar is left out.
// Japanese and Chinese (names starting with ja, javert, zh, zht) have a box per character instead, and javert's lines
// are columns, right to left. Thai, Lao and Khmer have a box per word as the browser splits it (they have no spaces
// between words), and Arabic, Persian and Hebrew lines read right to left.
// ADB names the adb binary (default: adb on the PATH).

import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const adb = process.env.ADB || 'adb';
const [outDir, name, ...flags] = process.argv.slice(2);
if (!outDir || !name) {
  console.error('usage: collect.mjs <out dir> <language>-<name> [--web]');
  process.exit(2);
}
const web = flags.includes('--web');
const language = name.split('-')[0];
const perCharacter = ['ja', 'javert', 'zh', 'zht'].includes(language);
const vertical = language === 'javert';
const segmented = ['th', 'lo', 'km'].includes(language);
const rightToLeft = ['ar', 'fa', 'he'].includes(language);

function run(...args) {
  return execFileSync(adb, args, { maxBuffer: 64 << 20 });
}

function decodeEntities(text) {
  return text.replace(/&(#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos);/gi, (_, e) => {
    if (e[0] === '#') return String.fromCodePoint(e[1] === 'x' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10));
    return { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" }[e.toLowerCase()];
  });
}

/** The nodes of the current screen: their attributes, bounds as [left, top, right, bottom]. */
function dumpNodes() {
  run('shell', 'uiautomator', 'dump', '/sdcard/window_dump.xml');
  const xml = run('exec-out', 'cat', '/sdcard/window_dump.xml').toString('utf8');
  const nodes = [];
  for (const tag of xml.match(/<node\b[^>]*>/g) || []) {
    const node = {};
    for (const [, key, value] of tag.matchAll(/([\w-]+)="([^"]*)"/g)) node[key] = decodeEntities(value);
    const b = /\[(\d+),(\d+)\]\[(\d+),(\d+)\]/.exec(node.bounds || '');
    if (!b) continue;
    node.box = b.slice(1).map(Number);
    nodes.push(node);
  }
  return nodes;
}

/** Runs [expression] in the page Chrome shows and returns its value. */
async function evaluate(expression) {
  const targets = await (await fetch('http://localhost:9222/json')).json();
  const page = targets.find((t) => t.type === 'page');
  if (!page) throw new Error('No Chrome page; is the DevTools port forwarded?');
  const socket = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {
    socket.onopen = resolve;
    socket.onerror = reject;
  });
  const reply = new Promise((resolve) => {
    socket.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id === 1) resolve(message);
    };
  });
  socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression, returnByValue: true } }));
  const message = await reply;
  socket.close();
  if (message.result.exceptionDetails) throw new Error(JSON.stringify(message.result.exceptionDetails));
  return message.result.result.value;
}

// Words (or characters) fully inside the viewport, drawn by their own element (not covered), in CSS pixels.
const WORDS = `(() => {
  const PER_CHARACTER = ${perCharacter};
  const SEGMENTER = ${segmented} ? new Intl.Segmenter('${language}', { granularity: 'word' }) : null;
  const words = [];
  const width = innerWidth, height = innerHeight;
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    const node = walker.currentNode;
    const element = node.parentElement;
    if (!element) continue;
    const style = getComputedStyle(element);
    if (style.visibility !== 'visible' || parseFloat(style.opacity) === 0) continue;
    if (/rgba\\(.*, 0\\)$/.test(style.color)) continue;
    if (style.textTransform === 'capitalize') continue;
    const tokens = [];
    if (SEGMENTER) {
      for (const { segment, index } of SEGMENTER.segment(node.data)) {
        if (segment.trim()) tokens.push([index, segment]);
      }
    } else if (PER_CHARACTER) {
      let index = 0;
      for (const character of node.data) {
        if (character.trim()) tokens.push([index, character]);
        index += character.length;
      }
    } else {
      const pattern = /\\S+/g;
      let match;
      while ((match = pattern.exec(node.data))) tokens.push([match.index, match[0]]);
    }
    for (const [index, token] of tokens) {
      const range = document.createRange();
      range.setStart(node, index);
      range.setEnd(node, index + token.length);
      const rects = [...range.getClientRects()].filter((r) => r.width > 0 && r.height > 0);
      if (rects.length !== 1) continue;
      const r = rects[0];
      if (r.left < 0 || r.top < 0 || r.right > width || r.bottom > height) continue;
      const hit = document.elementFromPoint((r.left + r.right) / 2, (r.top + r.bottom) / 2);
      if (!hit || !(hit === element || element.contains(hit) || hit.contains(element))) continue;
      let text = token;
      if (style.textTransform === 'uppercase') text = text.toUpperCase();
      if (style.textTransform === 'lowercase') text = text.toLowerCase();
      words.push({ text, box: [r.left, r.top, r.right, r.bottom] });
    }
  }
  return { dpr: devicePixelRatio, words };
})()`;

/**
 * Words grouped into lines: overlapping vertically and close horizontally, left to right. Columns when [vertical]:
 * the boxes are turned a quarter clockwise, grouped as lines and turned back, so columns read top to bottom, right to
 * left.
 */
function lines(words, vertical) {
  if (vertical) {
    const turn = ([l, t, r, b]) => [t, -r, b, -l];
    const back = ([l, t, r, b]) => [-b, l, -t, r];
    return lines(words.map((w) => ({ ...w, box: turn(w.box) })), false).map((line) => ({
      ...line,
      box: back(line.box),
      words: line.words.map((w) => ({ ...w, box: back(w.box) })),
    }));
  }
  const sorted = [...words].sort((a, b) => a.box[1] - b.box[1] || a.box[0] - b.box[0]);
  const result = [];
  for (const word of sorted) {
    const [l, t, r, b] = word.box;
    const h = b - t;
    const line = result.find((candidate) => {
      const [cl, ct, cr, cb] = candidate.box;
      const overlap = Math.min(b, cb) - Math.max(t, ct);
      const gap = Math.max(cl - r, l - cr);
      return overlap > 0.5 * Math.min(h, cb - ct) && gap < 1.5 * h;
    });
    if (line) {
      line.words.push(word);
      line.box = [Math.min(line.box[0], l), Math.min(line.box[1], t), Math.max(line.box[2], r), Math.max(line.box[3], b)];
    } else {
      result.push({ box: [l, t, r, b], words: [word] });
    }
  }
  for (const line of result) {
    line.words.sort((a, b) => a.box[0] - b.box[0]);
    if (rightToLeft) line.words.reverse();
    line.text = line.words.map((w) => w.text).join(perCharacter || segmented ? '' : ' ');
  }
  return result;
}

const nodes = dumpNodes();
let regions;
if (web) {
  const view = nodes.find((n) => n.class === 'android.webkit.WebView');
  if (!view) throw new Error('No WebView on screen');
  const { dpr, words } = await evaluate(WORDS);
  const [left, top] = view.box;
  const toScreen = ([l, t, r, b]) => [left + l * dpr, top + t * dpr, left + r * dpr, top + b * dpr].map((v) => Math.round(v));
  regions = lines(words.map((w) => ({ text: w.text, box: toScreen(w.box) })), vertical);
} else {
  // A text view cut off at the edge of a scrolling container shows only part of its text.
  const scrollers = nodes.filter((n) => n.scrollable === 'true');
  const cut = (n) => scrollers.some((s) => n.box[0] >= s.box[0] && n.box[2] <= s.box[2] && n.box[1] >= s.box[1] &&
    n.box[3] <= s.box[3] && (n.box[1] <= s.box[1] + 1 || n.box[3] >= s.box[3] - 1));
  regions = nodes
    .filter((n) => (n.text || '').trim() && n.box[2] > n.box[0] && n.box[3] > n.box[1] && !cut(n))
    .map((n) => ({ box: n.box, text: n.text.trim() }));
}

mkdirSync(outDir, { recursive: true });
writeFileSync(join(outDir, `${name}.png`), run('exec-out', 'screencap', '-p'));
writeFileSync(join(outDir, `${name}.json`), JSON.stringify({ image: `${name}.png`, source: web ? 'web' : 'ui', regions }, null, 1));
console.log(`${name}: ${regions.length} regions`);
