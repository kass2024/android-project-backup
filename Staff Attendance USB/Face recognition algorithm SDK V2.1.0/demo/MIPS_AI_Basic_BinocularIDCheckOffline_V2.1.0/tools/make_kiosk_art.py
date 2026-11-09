#!/usr/bin/env python3
"""Draw RFID tap art (no chip) and Wisdom launcher mipmaps."""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
LOGO = Path(r"C:\Users\user\Downloads\Wisdom Logo.png")
TAP_PATH = RES / "drawable-xxhdpi" / "img_rfid_tap.png"

BLUE = (10, 102, 183, 255)  # #0A66B7
CYAN = (56, 189, 248, 255)  # #38BDF8
WHITE = (255, 255, 255, 255)


def _arc_points(cx, cy, r, a0, a1, n=64):
    pts = []
    for i in range(n + 1):
        t = a0 + (a1 - a0) * i / n
        rad = math.radians(t)
        pts.append((cx + r * math.cos(rad), cy + r * math.sin(rad)))
    return pts


def draw_glow_arc(base: Image.Image, bbox, start, end, color, width, glow=18):
    glow_img = Image.new("RGBA", base.size, (0, 0, 0, 0))
    g = ImageDraw.Draw(glow_img)
    g.arc(bbox, start=start, end=end, fill=color, width=width + glow)
    glow_img = glow_img.filter(ImageFilter.GaussianBlur(radius=glow * 0.55))
    base.alpha_composite(glow_img)
    d = ImageDraw.Draw(base)
    d.arc(bbox, start=start, end=end, fill=color, width=width)


def make_tap() -> None:
    size = 1024
    img = Image.new("RGBA", (size, size), BLUE)
    d = ImageDraw.Draw(img)

    # White contactless card (landscape, rounded, NO chip, NO magstripe)
    card = [210, 330, 814, 694]
    d.rounded_rectangle(card, radius=48, fill=WHITE)
    # subtle inner edge
    d.rounded_rectangle([218, 338, 806, 686], radius=42, outline=(226, 239, 250, 255), width=3)

    # Small cyan NFC mark on the card (standard contactless: concentric arcs)
    nfc_cx, nfc_cy = 690, 430
    for i, r in enumerate((18, 34, 50)):
        w = 6 if i < 2 else 7
        d.arc([nfc_cx - r, nfc_cy - r, nfc_cx + r, nfc_cy + r], start=230, end=400, fill=CYAN, width=w)
    d.ellipse([nfc_cx - 6, nfc_cy - 6, nfc_cx + 6, nfc_cy + 6], fill=CYAN)

    # Large glowing cyan radio waves around the card (3 concentric arcs)
    cx, cy = 512, 512
    waves = [
        (70, 14, 22),
        (150, 18, 26),
        (240, 22, 32),
    ]
    for extra, width, glow in waves:
        bbox = [card[0] - extra, card[1] - extra, card[2] + extra, card[3] + extra]
        col = (56, 189, 248, 230)
        draw_glow_arc(img, bbox, 200, 330, col, width, glow)
        draw_glow_arc(img, bbox, 20, 150, col, width, glow)

    TAP_PATH.parent.mkdir(parents=True, exist_ok=True)
    rgb = Image.new("RGB", img.size, (10, 102, 183))
    rgb.paste(img, mask=img.split()[-1])
    rgb.save(TAP_PATH, "PNG", optimize=True)
    raw = TAP_PATH.read_bytes()[:8]
    if raw != b"\x89PNG\r\n\x1a\n":
        raise SystemExit("tap image is not a real PNG")
    print("wrote", TAP_PATH, TAP_PATH.stat().st_size)


def _contain(src: Image.Image, box: int, bg=(255, 255, 255, 255)) -> Image.Image:
    canvas = Image.new("RGBA", (box, box), bg)
    im = src.convert("RGBA")
    im.thumbnail((int(box * 0.86), int(box * 0.86)), Image.Resampling.LANCZOS)
    x = (box - im.width) // 2
    y = (box - im.height) // 2
    canvas.alpha_composite(im, (x, y))
    return canvas


def _circle(src: Image.Image, box: int) -> Image.Image:
    sq = _contain(src, box, (255, 255, 255, 255))
    mask = Image.new("L", (box, box), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, box - 1, box - 1), fill=255)
    out = Image.new("RGBA", (box, box), (0, 0, 0, 0))
    out.paste(sq, mask=mask)
    # launcher PNG without alpha on older densities: composite on white
    rgb = Image.new("RGB", (box, box), (255, 255, 255))
    rgb.paste(out, mask=out.split()[-1])
    return rgb


def make_launcher() -> None:
    if not LOGO.exists():
        raise SystemExit("missing logo: " + str(LOGO))
    src = Image.open(LOGO)
    dens = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, px in dens.items():
        d = RES / folder
        d.mkdir(parents=True, exist_ok=True)
        sq = _contain(src, px, (255, 255, 255, 255)).convert("RGB")
        sq.save(d / "ic_launcher.png", "PNG", optimize=True)
        rnd = _circle(src, px)
        rnd.save(d / "ic_launcher_round.png", "PNG", optimize=True)
        print("wrote", folder, px)

    fg_dir = RES / "drawable"
    fg_dir.mkdir(parents=True, exist_ok=True)
    fg = _contain(src, 432, (0, 0, 0, 0))
    fg.save(fg_dir / "ic_launcher_foreground.png", "PNG", optimize=True)
    print("wrote foreground 432")

    anydpi = RES / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"""
    (anydpi / "ic_launcher.xml").write_text(xml, encoding="utf-8")
    (anydpi / "ic_launcher_round.xml").write_text(xml, encoding="utf-8")
    (fg_dir / "ic_launcher_background.xml").write_text(
        """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#FFFFFF" />
</shape>
""",
        encoding="utf-8",
    )


if __name__ == "__main__":
    make_tap()
    make_launcher()
    print("ok")
