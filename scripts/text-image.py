"""Renders lines of text into a phone-sized PNG for overlay and OCR checks.

Usage: python scripts/text-image.py [--vertical] OUT.png "first line" ["second line" ...]
With --vertical each argument is a column, read top to bottom, columns from right to left.
Needs Pillow and a Japanese font (Noto Sans JP or Yu Gothic on Windows; set FONT to override, e.g. to
C:/Windows/Fonts/msjh.ttc for Traditional Chinese). FALLBACK lists fonts, separated by commas, for the characters
FONT lacks in horizontal lines: script-only fonts such as Noto Sans Georgian have no digits or Latin punctuation.
PITCH sets the distance between horizontal lines in pixels (default 160). At 160 the recognizers take each line as a
paragraph of its own; for one paragraph (a word hyphenated across lines) use about 1.3 times the 72 px font, 96.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 1280, 2856
FONT_CANDIDATES = [
    os.environ.get("FONT", ""),
    "C:/Windows/Fonts/NotoSansJP-VF.ttf",
    "C:/Windows/Fonts/YuGothM.ttc",
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
]
VERTICAL_PUNCTUATION = "。、，"
LINE_PITCH = int(os.environ.get("PITCH", "160"))


def main() -> None:
    args = sys.argv[1:]
    vertical = bool(args) and args[0] == "--vertical"
    if vertical:
        args = args[1:]
    if len(args) < 2:
        sys.exit(__doc__)
    out, lines = args[0], args[1:]
    font_path = next(p for p in FONT_CANDIDATES if p and os.path.exists(p))
    image = Image.new("RGB", (WIDTH, HEIGHT), "white")
    draw = ImageDraw.Draw(image)
    if vertical:
        font = ImageFont.truetype(font_path, 80)
        x = WIDTH - 200
        for column in lines:
            y = 500
            for ch in column:
                if ch in VERTICAL_PUNCTUATION:
                    # Vertical punctuation sits in the upper right of its cell.
                    draw.text((x + 40, y - 40), ch, font=font, fill="black")
                else:
                    draw.text((x, y), ch, font=font, fill="black")
                y += 100
            x -= 160
    else:
        font = ImageFont.truetype(font_path, 72)
        fallbacks = [ImageFont.truetype(p, 72) for p in os.environ.get("FALLBACK", "").split(",") if p]
        y = 600
        for line in lines:
            if fallbacks:
                draw_with_fallbacks(draw, (80, y), line, [font, *fallbacks])
            else:
                draw.text((80, y), line, font=font, fill="black")
            y += LINE_PITCH
    image.save(out)


def draw_with_fallbacks(draw: ImageDraw.ImageDraw, at: tuple[int, int], line: str, fonts: list) -> None:
    """Draws each character in the first of [fonts] that has it, on one baseline."""

    def mask(font, ch):
        m = font.getmask(ch)
        return m.size, bytes(m)

    # A missing character renders as the font's .notdef box, as a private-use one does.
    missing = [mask(f, "\ue000") for f in fonts]
    x, baseline = at[0], at[1] + 80
    for ch in line:
        font = next((f for f, nd in zip(fonts, missing) if ch == " " or mask(f, ch) != nd), fonts[-1])
        draw.text((x, baseline), ch, font=font, fill="black", anchor="ls")
        x += draw.textlength(ch, font=font)


if __name__ == "__main__":
    main()
