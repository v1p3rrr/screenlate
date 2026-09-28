// Checks the generated icon drawables in Chromium: each VectorDrawable, converted back to SVG, must match the
// reference SVG drawn from the design shapes; the launcher foreground must stay inside the 66-unit safe zone.
// Writes build/drawables.png, the drawables in launcher masks and sizes, for a visual check.
//
//   python3 scripts/icon/icon.py && node scripts/icon/check.mjs
//
// Needs Playwright (npm i -g playwright, or set PLAYWRIGHT to its module path).
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT ?? 'playwright');
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const read = (rel) => fs.readFileSync(path.join(root, rel), 'utf8');
const build = (name) => fs.readFileSync(path.join(here, 'build', name), 'utf8');

const pairs = [
  ['release-foreground.svg', 'app/src/main/res/drawable/ic_launcher_foreground.xml', false],
  ['release-monochrome.svg', 'app/src/main/res/drawable/ic_launcher_monochrome.xml', true],
  ['debug-foreground.svg', 'app/src/debug/res/drawable/ic_launcher_foreground.xml', false],
  ['debug-monochrome.svg', 'app/src/debug/res/drawable/ic_launcher_monochrome.xml', true],
  ['small.svg', 'overlay/src/main/res/drawable/ic_tile_bubble.xml', false],
  ['small.svg', 'dictionary/api/src/main/res/drawable/ic_dictionary_import.xml', false],
];
const SIZE = 432;

const browser = await chromium.launch();
const page = await browser.newPage();
await page.setContent('<canvas></canvas>');

// Runs in the page: VectorDrawable XML to SVG (the subset the generator writes, plus groups and strokes).
await page.evaluate(() => {
  const NS = 'http://schemas.android.com/apk/res/android';
  const color = (c, alpha = 1) => {
    if (!c) return null;
    let a = 1, hex = c.slice(1);
    if (hex.length === 8) { a = parseInt(hex.slice(0, 2), 16) / 255; hex = hex.slice(2); }
    const [r, g, b] = [0, 2, 4].map((i) => parseInt(hex.slice(i, i + 2), 16));
    return `rgba(${r},${g},${b},${a * alpha})`;
  };
  window.vdToSvg = (xml) => {
    const doc = new DOMParser().parseFromString(xml, 'application/xml');
    if (doc.querySelector('parsererror')) throw new Error('XML does not parse');
    const v = doc.documentElement;
    if (v.localName !== 'vector') throw new Error('Root is not <vector>');
    const at = (el, name) => el.getAttributeNS(NS, name);
    const w = at(v, 'viewportWidth'), h = at(v, 'viewportHeight');
    const walk = (el) => [...el.children].map((c) => {
      if (c.localName === 'path') {
        const fill = color(at(c, 'fillColor'), +(at(c, 'fillAlpha') ?? 1)) ?? 'none';
        const stroke = color(at(c, 'strokeColor'), +(at(c, 'strokeAlpha') ?? 1));
        const rule = at(c, 'fillType') === 'evenOdd' ? 'evenodd' : 'nonzero';
        const s = stroke ? ` stroke="${stroke}" stroke-width="${at(c, 'strokeWidth') ?? 0}"` : '';
        return `<path d="${at(c, 'pathData')}" fill="${fill}" fill-rule="${rule}"${s}/>`;
      }
      if (c.localName === 'group') {
        const t = `translate(${at(c, 'translateX') ?? 0} ${at(c, 'translateY') ?? 0}) rotate(${at(c, 'rotation') ?? 0} ${at(c, 'pivotX') ?? 0} ${at(c, 'pivotY') ?? 0}) scale(${at(c, 'scaleX') ?? 1} ${at(c, 'scaleY') ?? 1})`;
        return `<g transform="${t}">${walk(c)}</g>`;
      }
      throw new Error(`Unsupported element <${c.localName}>`);
    }).join('');
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}">${walk(v)}</svg>`;
  };
  window.pixels = async (svg, size) => {
    const img = new Image();
    img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
    await img.decode();
    const c = document.createElement('canvas');
    c.width = c.height = size;
    const g = c.getContext('2d');
    g.drawImage(img, 0, 0, size, size);
    return g.getImageData(0, 0, size, size).data;
  };
  window.compare = async (refSvg, xml, size, alphaOnly) => {
    const a = await pixels(refSvg, size), b = await pixels(vdToSvg(xml), size);
    let max = 0, off = 0, edge = 0;
    for (let i = 0; i < a.length; i += 4) {
      const channels = alphaOnly ? [3] : [0, 1, 2, 3];
      // Compare premultiplied values so fully transparent pixels of any color count as equal.
      const d = Math.max(...channels.map((k) => (k === 3 ? Math.abs(a[i + 3] - b[i + 3])
        : Math.abs(a[i + k] * a[i + 3] - b[i + k] * b[i + 3]) / 255)));
      max = Math.max(max, d);
      if (d > 24) edge++;
      if (d > 128) off++;
    }
    return { max: Math.round(max), off, edge };
  };
  window.outsideSafeZone = async (xml, size) => {
    const px = await pixels(vdToSvg(xml), size);
    const c = size / 2, r = (33 / 108) * size + 1.5;
    let n = 0;
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
      if (px[(y * size + x) * 4 + 3] > 8 && Math.hypot(x + 0.5 - c, y + 0.5 - c) > r) n++;
    }
    return n;
  };
});

let failed = false;
for (const [ref, xmlPath, mono] of pairs) {
  const r = await page.evaluate(([s, x, n, m]) => compare(s, x, n, m), [build(ref), read(xmlPath), SIZE, mono]);
  // Edges differ slightly because arcs become cubic curves; a missing, extra or shifted shape shows up as pixels that
  // differ by more than half.
  const ok = r.off === 0;
  failed ||= !ok;
  console.log(`${ok ? 'OK  ' : 'FAIL'} ${xmlPath} vs ${ref}: ${r.off} px differ by more than half, max diff ${r.max}/255, ${r.edge} antialiased edge px`);
}
for (const xmlPath of ['app/src/main/res/drawable/ic_launcher_foreground.xml', 'app/src/debug/res/drawable/ic_launcher_foreground.xml',
  'app/src/main/res/drawable/ic_launcher_monochrome.xml', 'app/src/debug/res/drawable/ic_launcher_monochrome.xml']) {
  const n = await page.evaluate(([x, s]) => outsideSafeZone(x, s), [read(xmlPath), 1080]);
  failed ||= n > 0;
  console.log(`${n === 0 ? 'OK  ' : 'FAIL'} ${xmlPath}: ${n} px outside the safe zone`);
}

// Contact sheet from the drawables themselves: launcher masks, sizes, wallpapers, themed icons, small icons.
const svgs = await page.evaluate((files) => Object.fromEntries(Object.entries(files).map(([k, v]) => [k, vdToSvg(v)])), {
  bg: read('app/src/main/res/drawable/ic_launcher_background.xml'),
  fg: read('app/src/main/res/drawable/ic_launcher_foreground.xml'),
  mono: read('app/src/main/res/drawable/ic_launcher_monochrome.xml'),
  dfg: read('app/src/debug/res/drawable/ic_launcher_foreground.xml'),
  dmono: read('app/src/debug/res/drawable/ic_launcher_monochrome.xml'),
  small: read('overlay/src/main/res/drawable/ic_tile_bubble.xml'),
});
const inner = (svg) => svg.replace(/^<svg[^>]*>|<\/svg>$/g, '');
const masks = {
  circle: '<circle cx="54" cy="54" r="36"/>',
  rounded: '<rect x="18" y="18" width="72" height="72" rx="13"/>',
  square: '<rect x="18" y="18" width="72" height="72"/>',
  squircle: `<path d="${Array.from({ length: 120 }, (_, i) => {
    const t = (i / 120) * 2 * Math.PI, c = Math.cos(t), s = Math.sin(t);
    return `${54 + 36 * Math.sign(c) * Math.abs(c) ** 0.4},${54 + 36 * Math.sign(s) * Math.abs(s) ** 0.4}`;
  }).join('L').replace(/^/, 'M')}Z"/>`,
};
let id = 0;
const launcher = (fg, mask, size, themed = null) => {
  const m = `m${id++}`;
  const layers = themed
    ? `<rect width="108" height="108" fill="${themed.back}"/><g style="filter:url(#${m}t)">${inner(fg)}</g>`
    : inner(svgs.bg) + inner(fg);
  const tint = themed ? `<filter id="${m}t"><feFlood flood-color="${themed.tint}"/><feComposite in2="SourceAlpha" operator="in"/></filter>` : '';
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="${size}" height="${size}"><defs><clipPath id="${m}">${masks[mask]}</clipPath>${tint}</defs><g clip-path="url(#${m})">${layers}</g></svg>`;
};
const light = { back: '#d7e8d0', tint: '#26402a' }, dark = { back: '#1f2b21', tint: '#b6d3aa' };
const row = (label, fg, mono) => `<div class="row"><span>${label}</span>
  <div>${Object.keys(masks).map((k) => launcher(fg, k, 96)).join('')}</div>
  <div class="l">${[48, 32, 24].map((s) => launcher(fg, 'circle', s)).join('')}${launcher(fg, 'rounded', 48)}</div>
  <div class="d">${[48, 32, 24].map((s) => launcher(fg, 'circle', s)).join('')}${launcher(fg, 'squircle', 48)}</div>
  <div class="l">${launcher(mono, 'circle', 48, light)}${launcher(mono, 'circle', 24, light)}${launcher(mono, 'circle', 48, dark)}${launcher(mono, 'circle', 24, dark)}</div></div>`;
const smallRow = `<div class="row"><span>small</span><div class="d">${[48, 24, 18].map((s) => svgs.small.replace(/width="24" height="24"/, `width="${s}" height="${s}"`)).join('')}</div></div>`;
await page.setViewportSize({ width: 1300, height: 400 });
await page.setContent(`<style>body{margin:0;background:#888;font:13px sans-serif}.row{display:flex;gap:12px;align-items:center;padding:8px}
  .row>div{display:flex;gap:6px;align-items:center;padding:8px}.l{background:#f1f1f1}.d{background:#111}span{width:60px;color:#fff}</style>
  ${row('release', svgs.fg, svgs.mono)}${row('debug', svgs.dfg, svgs.dmono)}${smallRow}`);
fs.mkdirSync(path.join(here, 'build'), { recursive: true });
await page.screenshot({ path: path.join(here, 'build/drawables.png'), fullPage: true });
console.log('wrote scripts/icon/build/drawables.png');
await browser.close();
process.exit(failed ? 1 : 0);
