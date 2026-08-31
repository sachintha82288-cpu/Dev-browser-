#!/usr/bin/env python3
"""Generate DevBrowser launcher icons (legacy PNGs + adaptive foreground).

Pure Pillow, no external deps: neon-pink→purple→cyan "D" on a dark rounded tile.
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "app", "res")

PINK = (255, 62, 165)
PURPLE = (138, 92, 255)
CYAN = (0, 231, 255)
DARK1 = (23, 23, 31)
DARK2 = (13, 13, 19)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rounded(draw, box, radius, fill):
    draw.rounded_rectangle(box, radius=radius, fill=fill)


def gradient_hexagon(img, cx, cy, r, inset_steps=120):
    """Concentric rounded-hexagons from pink (outer) to purple (inner)."""
    draw = ImageDraw.Draw(img)
    for i in range(inset_steps):
        t = i / (inset_steps - 1)
        r_i = int(r * (1 - 0.82 * t))
        color = lerp(PINK, PURPLE, t)
        pts = hex_points(cx, cy, r_i)
        draw.polygon(pts, fill=color)


def hex_points(cx, cy, r):
    pts = []
    for k in range(6):
        a = math.radians(60 * k - 30)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def d_glyph(draw, cx, cy, h, color=(255, 255, 255)):
    """Bold stylised D built from a rounded rectangle + notch."""
    w = h * 0.72
    draw.rounded_rectangle((cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2),
                           radius=int(h * 0.18), fill=color)
    # cut out the counter (inner hole) for a D with a transparent bow
    bw = w * 0.34
    bx = cx + w * 0.10
    draw.rounded_rectangle((bx, cy - h * 0.30, bx + bw, cy + h * 0.30),
                           radius=int(bw * 0.5), fill=(0, 0, 0, 0))
    # second pass: carve using a dark rect of the tile (simpler + reliable)
    return None


def d_glyph(canvas_size, cx, cy, h):
    """Return a clean white 'D' as an RGBA image (body minus counter)."""
    from PIL import ImageChops
    w = int(h * 0.72)
    body = Image.new("L", (canvas_size, canvas_size), 0)
    bd = ImageDraw.Draw(body)
    bd.rounded_rectangle(
        (cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2),
        radius=int(h * 0.20), fill=255)
    counter = Image.new("L", (canvas_size, canvas_size), 0)
    cd = ImageDraw.Draw(counter)
    # counter sits right-of-centre, but keeps a visible right stroke (bowl)
    cd.rounded_rectangle(
        (cx - w * 0.04, cy - h * 0.26, cx + w * 0.34, cy + h * 0.26),
        radius=int(h * 0.12), fill=255)
    alpha = ImageChops.subtract(body, counter)
    glyph = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    glyph.putalpha(alpha)
    white = Image.new("RGBA", (canvas_size, canvas_size), (255, 255, 255, 255))
    return Image.composite(white, glyph, alpha)


def make_legacy(size, out_path, round_icon=False):
    img = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size * 4
    pad = int(s * 0.04)
    radius = int(s * 0.24)
    # tile
    d.rounded_rectangle((pad, pad, s - pad, s - pad), radius=radius, fill=DARK1)
    # dark gradient overlay
    overlay = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    for i in range(s):
        t = i / s
        od.line([(0, i), (s, i)], fill=(*lerp(DARK2, DARK1, t), 255))
    img = Image.alpha_composite(img, overlay)
    d = ImageDraw.Draw(img)
    # keep rounded tile shape by re-masking
    mask = Image.new("L", (s, s), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle((pad, pad, s - pad, s - pad), radius=radius, fill=255)
    if round_icon:
        md.ellipse((pad, pad, s - pad, s - pad), fill=255)
    img.putalpha(mask)

    # neon hexagon
    gradient_hexagon(img, s / 2, s / 2, int(s * 0.34))
    # clean D glyph (body minus counter)
    g = d_glyph(s, s / 2, s / 2, int(s * 0.30))
    img = Image.alpha_composite(img, g)

    img = img.resize((size, size), Image.LANCZOS)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    img.convert("RGB").save(out_path, "PNG")
    print("wrote", out_path)


def make_foreground(path, canvas=432):
    img = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    gradient_hexagon(img, canvas / 2, canvas / 2, int(canvas * 0.34))
    g = d_glyph(canvas, canvas / 2, canvas / 2, int(canvas * 0.30))
    img = Image.alpha_composite(img, g)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG")
    print("wrote", path)


def main():
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dpi, size in sizes.items():
        make_legacy(size, os.path.join(RES, f"mipmap-{dpi}", "ic_launcher.png"))
        make_legacy(size, os.path.join(RES, f"mipmap-{dpi}", "ic_launcher_round.png"),
                    round_icon=True)
    make_foreground(os.path.join(RES, "drawable-nodpi", "ic_launcher_foreground.png"))
    print("done")


if __name__ == "__main__":
    main()
