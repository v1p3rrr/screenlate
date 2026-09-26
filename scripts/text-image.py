"""Renders lines of text into a phone-sized PNG for overlay and OCR checks.

Usage: python scripts/text-image.py OUT.png "first line" ["second line" ...]
Needs Pillow and a Japanese font (Noto Sans JP or Yu Gothic on Windows; set FONT to override).
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


def main() -> None:
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    out, lines = sys.argv[1], sys.argv[2:]
    font_path = next(p for p in FONT_CANDIDATES if p and os.path.exists(p))
    font = ImageFont.truetype(font_path, 72)
    image = Image.new("RGB", (WIDTH, HEIGHT), "white")
    draw = ImageDraw.Draw(image)
    y = 600
    for line in lines:
        draw.text((80, y), line, font=font, fill="black")
        y += 160
    image.save(out)


if __name__ == "__main__":
    main()
