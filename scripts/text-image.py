"""Renders lines of text into a phone-sized PNG for overlay and OCR checks.

Usage: python scripts/text-image.py [--vertical] OUT.png "first line" ["second line" ...]
With --vertical each argument is a column, read top to bottom, columns from right to left.
Needs Pillow and a Japanese font (Noto Sans JP or Yu Gothic on Windows; set FONT to override, e.g. to
C:/Windows/Fonts/msjh.ttc for Traditional Chinese).
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
        y = 600
        for line in lines:
            draw.text((80, y), line, font=font, fill="black")
            y += 160
    image.save(out)


if __name__ == "__main__":
    main()
