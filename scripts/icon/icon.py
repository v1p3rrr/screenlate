#!/usr/bin/env python3
"""Generates the app icon resources from one design.

The icon: a word lifted out of a line of text, its place highlighted, and the bubble's aim dot on that place, in the
overlay's colors. Shapes are drawn in the 108-unit adaptive icon grid; holes are cut with boolean path operations so
the drawables need nothing beyond plain filled paths.

Writes the launcher foreground, background and monochrome layers (release and debug), the Quick Settings tile and
import notification icons, docs/images/icon.svg, and reference SVGs for scripts/icon/check.mjs.

    pip install fonttools skia-pathops
    python3 scripts/icon/icon.py
"""
import math
import re
from pathlib import Path

import pathops
from fontTools.pens.recordingPen import RecordingPen
from fontTools.pens.transformPen import TransformPen
from fontTools.svgLib.path import parse_path

ROOT = Path(__file__).resolve().parents[2]
BUILD = Path(__file__).resolve().parent / "build"

BACKGROUND = "#F8F6FD"
MARK = "#2E2848"  # grey text rows (drawn translucent)
ACCENT = "#7C5CFF"  # the overlay's bubble, aim and word highlight color
BADGE = "#2E2848"
BUG = "#FFC83D"  # the overlay's all-lines highlight color
WHITE = "#FFFFFF"


def num(v, digits=2):
    s = f"{v:.{digits}f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def rr(x, y, w, h, r, deg=0.0, cx=None, cy=None):
    """Rounded rectangle path, optionally rotated by [deg] degrees around ([cx], [cy])."""
    cx = x + w / 2 if cx is None else cx
    cy = y + h / 2 if cy is None else cy
    r = min(r, w / 2, h / 2)
    a = math.radians(deg)

    def p(px, py):
        dx, dy = px - cx, py - cy
        return f"{cx + dx * math.cos(a) - dy * math.sin(a):.4f},{cy + dx * math.sin(a) + dy * math.cos(a):.4f}"

    def arc(px, py):
        return f"A{r},{r} 0 0 1 {p(px, py)}"

    return (f"M{p(x + r, y)}L{p(x + w - r, y)}{arc(x + w, y + r)}L{p(x + w, y + h - r)}{arc(x + w - r, y + h)}"
            f"L{p(x + r, y + h)}{arc(x, y + h - r)}L{p(x, y + r)}{arc(x + r, y)}Z")


def circle(cx, cy, r):
    return f"M{cx - r},{cy}A{r},{r} 0 1 1 {cx + r},{cy}A{r},{r} 0 1 1 {cx - r},{cy}Z"


def paint(d, fill, opacity=1.0, mono_opacity=None, evenodd=False, mono_hole=False):
    """A filled shape; [mono_opacity] is its alpha in the monochrome layer, [mono_hole] makes it a cut there."""
    return dict(d=d, fill=fill, opacity=opacity, mono_opacity=opacity if mono_opacity is None else mono_opacity,
                evenodd=evenodd, mono_hole=mono_hole)


def hole(d):
    """Cuts every shape drawn before it."""
    return dict(d=d, hole=True)


def material_bug(cx, cy, scale):
    """Material Icons' bug_report glyph (Apache 2.0, already in the app), centered at ([cx], [cy])."""
    xml = (ROOT / "app/src/main/res/drawable/ic_bug_report.xml").read_text()
    d = re.search(r'android:pathData="([^"]+)"', xml).group(1)
    rec = RecordingPen()
    parse_path(d, TransformPen(rec, (scale, 0, 0, scale, cx - 12 * scale, cy - 12 * scale)))
    return recording_to_d(rec)


def recording_to_d(rec, digits=2):
    out = []
    for op, args in rec.value:
        if op == "qCurveTo" and len(args) > 2:
            # TrueType-style splines: split into single quadratic segments at the implied on-curve points.
            offs, end = list(args[:-1]), args[-1]
            for i, o in enumerate(offs):
                nxt = ((o[0] + offs[i + 1][0]) / 2, (o[1] + offs[i + 1][1]) / 2) if i < len(offs) - 1 else end
                out.append("Q" + " ".join(f"{num(x, digits)},{num(y, digits)}" for x, y in (o, nxt)))
            continue
        letter = {"moveTo": "M", "lineTo": "L", "qCurveTo": "Q", "curveTo": "C"}.get(op, "Z")
        out.append(letter + " ".join(f"{num(x, digits)},{num(y, digits)}" for x, y in args))
    return "".join(out)


def to_path(d, evenodd=False):
    p = pathops.Path(fillType=pathops.FillType.EVEN_ODD if evenodd else pathops.FillType.WINDING)
    parse_path(d, p.getPen())
    # Simplifying resolves even-odd into winding contours, so the output needs only the default fill rule.
    return pathops.simplify(p, fix_winding=True, keep_starting_points=False)


def flatten(items, mono=False, mono_color="#000000", digits=2):
    """Paint order with holes applied: a list of (path data, color, alpha)."""
    entries = []
    for it in items:
        path = to_path(it["d"], it.get("evenodd", False))
        if it.get("hole") or (mono and it.get("mono_hole")):
            for e in entries:
                e[0] = pathops.op(e[0], path, pathops.PathOp.DIFFERENCE, fix_winding=True, keep_starting_points=False)
        else:
            entries.append([path, mono_color if mono else it["fill"], it["mono_opacity"] if mono else it["opacity"]])
    result = []
    for path, color, alpha in entries:
        rec = RecordingPen()
        path.draw(rec)
        d = recording_to_d(rec, digits)
        if d:
            result.append((d, color, alpha))
    return result


ROWS = (rr(27, 50, 12, 6, 3) + rr(69, 50, 12, 6, 3) + rr(26, 62, 31, 6, 3) + rr(61, 62, 21, 6, 3)
        + rr(34, 74, 22, 6, 3) + rr(60, 74, 12, 6, 3))

RELEASE = [
    paint(ROWS, MARK, 0.32, 0.5),
    paint(rr(42, 49.3, 24, 7.4, 3.7), ACCENT, 0.3, 0.45),  # where the word was
    paint(rr(36, 28, 36, 15, 6, -6), ACCENT),  # the lifted word
    # The aim dot and its white outline; the outline is a gap in the monochrome layer.
    paint(circle(54, 53, 5.4) + circle(54, 53, 4), WHITE, evenodd=True, mono_hole=True),
    paint(circle(54, 53, 4), ACCENT),
]

DEBUG = RELEASE + [
    hole(circle(69, 69, 13.6)),
    paint(circle(69, 69, 11.6), BADGE),
    paint(material_bug(69, 69, 0.78), BUG, mono_hole=True),
]

# 24-unit silhouette for the Quick Settings tile and the notification; the system tints it.
SMALL = [
    paint(rr(6, 2.6, 12, 5, 2.2, -6), WHITE),
    paint(rr(2, 10.7, 5, 2.6, 1.3) + rr(17, 10.7, 5, 2.6, 1.3) + rr(2, 15.4, 10, 2.6, 1.3) + rr(14, 15.4, 8, 2.6, 1.3)
          + rr(5, 19.4, 8, 2.6, 1.3) + rr(15, 19.4, 4, 2.6, 1.3), WHITE, 0.55),
    paint(circle(12, 12, 2.4), WHITE),
]


HEADER = "<!-- Generated by scripts/icon/icon.py; edit the design there. -->\n"


def vector(entries, size, viewport):
    lines = ['<?xml version="1.0" encoding="utf-8"?>', HEADER.rstrip(),
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             f'    android:width="{size}dp"', f'    android:height="{size}dp"',
             f'    android:viewportWidth="{viewport}"', f'    android:viewportHeight="{viewport}">']
    for d, color, alpha in entries:
        lines.append("    <path")
        lines.append(f'        android:fillColor="{color}"')
        if alpha < 1:
            lines.append(f'        android:fillAlpha="{num(alpha)}"')
        lines.append(f'        android:pathData="{d}" />')
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def adaptive():
    return ('<?xml version="1.0" encoding="utf-8"?>\n' + HEADER +
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n')


def design_svg(items, background, mono=False, viewport=108):
    """Reference SVG straight from the design shapes, holes as SVG masks (independent of the path operations)."""
    body, n = "", 0
    for it in items:
        rule = ' fill-rule="evenodd"' if it.get("evenodd") else ""
        if it.get("hole") or (mono and it.get("mono_hole")):
            n += 1
            body = (f'<mask id="h{n}" maskUnits="userSpaceOnUse" x="0" y="0" width="{viewport}" height="{viewport}">'
                    f'<rect width="{viewport}" height="{viewport}" fill="#fff"/><path d="{it["d"]}" fill="#000"{rule}/>'
                    f'</mask><g mask="url(#h{n})">{body}</g>')
        else:
            alpha = it["mono_opacity"] if mono else it["opacity"]
            fill = "#000000" if mono else it["fill"]
            body += f'<path d="{it["d"]}" fill="{fill}" fill-opacity="{alpha}"{rule}/>'
    bg = f'<rect width="{viewport}" height="{viewport}" fill="{background}"/>' if background else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {viewport} {viewport}" '
            f'width="{viewport}" height="{viewport}">{bg}{body}</svg>\n')


def readme_svg(entries):
    """The launcher icon in a rounded square, for the README."""
    paths = "".join(f'<path d="{d}" fill="{c}"' + (f' fill-opacity="{num(a)}"' if a < 1 else "") + "/>"
                    for d, c, a in entries)
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="192" height="192">'
            '<clipPath id="m"><rect x="18" y="18" width="72" height="72" rx="16"/></clipPath>'
            f'<g clip-path="url(#m)"><rect width="108" height="108" fill="{BACKGROUND}"/>{paths}</g></svg>\n')


def write(rel, text):
    path = ROOT / rel if not str(rel).startswith("/") else Path(rel)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
    print("wrote", path.relative_to(ROOT))


def main():
    background = [(f"M0,0h108v108h-108z", BACKGROUND, 1.0)]
    small = flatten(SMALL, digits=3)
    outputs = {
        "app/src/main/res/drawable/ic_launcher_background.xml": vector(background, 108, 108),
        "app/src/main/res/drawable/ic_launcher_foreground.xml": vector(flatten(RELEASE), 108, 108),
        "app/src/main/res/drawable/ic_launcher_monochrome.xml": vector(flatten(RELEASE, mono=True), 108, 108),
        "app/src/debug/res/drawable/ic_launcher_foreground.xml": vector(flatten(DEBUG), 108, 108),
        "app/src/debug/res/drawable/ic_launcher_monochrome.xml": vector(flatten(DEBUG, mono=True), 108, 108),
        "app/src/main/res/mipmap-anydpi/ic_launcher.xml": adaptive(),
        "app/src/main/res/mipmap-anydpi/ic_launcher_round.xml": adaptive(),
        "overlay/src/main/res/drawable/ic_tile_bubble.xml": vector(small, 24, 24),
        "dictionary/api/src/main/res/drawable/ic_dictionary_import.xml": vector(small, 24, 24),
        "docs/images/icon.svg": readme_svg(flatten(RELEASE)),
    }
    for rel, text in outputs.items():
        write(rel, text)
    # References for check.mjs: each drawable next to the design it must match.
    refs = {
        "release-foreground": (RELEASE, None, False, 108),
        "release-monochrome": (RELEASE, None, True, 108),
        "debug-foreground": (DEBUG, None, False, 108),
        "debug-monochrome": (DEBUG, None, True, 108),
        "small": (SMALL, None, False, 24),
    }
    BUILD.mkdir(exist_ok=True)
    for name, (items, bg, mono, vp) in refs.items():
        (BUILD / f"{name}.svg").write_text(design_svg(items, bg, mono, vp))
    print("wrote reference SVGs to", BUILD.relative_to(ROOT))


if __name__ == "__main__":
    main()
