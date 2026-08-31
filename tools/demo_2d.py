#!/usr/bin/env python3
"""2D animation demo — DevBrowser neon browser mockup.

Uses PySide6's offscreen raster backend (no GPU / X required) and encodes
the frames with imageio-ffmpeg. Output: docs/media/banner.png (still) and
docs/media/banner.mp4 (animated).
"""
import math
import os
import subprocess

import imageio_ffmpeg

FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PySide6.QtGui import QColor, QFont, QLinearGradient, QPainter, QPen, QPixmap
from PySide6.QtCore import Qt, QPointF
from PySide6.QtWidgets import QApplication

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUT = os.path.join(ROOT, "docs", "media")
os.makedirs(OUT, exist_ok=True)

W, H, FRAMES = 1200, 630, 60
PINK, CYAN, PURPLE = QColor("#ff3ea5"), QColor("#00e7ff"), QColor("#8a5cff")


def draw_scene(app, frame):
    t = frame / FRAMES
    glow = 0.5 + 0.5 * math.sin(2 * math.pi * t)
    pm = QPixmap(W, H)
    pm.fill(QColor("#0d0d13"))

    p = QPainter(pm)
    p.setRenderHint(QPainter.Antialiasing)

    # background glows
    for center, color, r in [((W * 0.18, H * 0.10), PURPLE, 420),
                             ((W * 0.88, H * 0.35), QColor("#0f2a3d"), 380),
                             ((W * 0.5, H * 1.05), PINK, 460)]:
        g = QLinearGradient(0, 0, 0, r)
        g.setColorAt(0, QColor(color.red(), color.green(), color.blue(), 60))
        g.setColorAt(1, QColor(color.red(), color.green(), color.blue(), 0))
        p.setBrush(g)
        p.setPen(Qt.NoPen)
        p.drawEllipse(QPointF(center[0], center[1]), r, r)

    # browser window
    x0, y0, ww, wh = int(W * 0.10), int(H * 0.14), int(W * 0.80), int(H * 0.72)
    wx, wy = x0 + 6 * glow, y0 + 6 * glow
    p.setPen(QPen(QColor("#2e2e40"), 2))
    p.setBrush(QColor("#17171f"))
    p.drawRoundedRect(wx, wy, ww, wh, 18, 18)

    # window top bar
    p.setBrush(QColor("#1e1e2a"))
    p.setPen(Qt.NoPen)
    p.drawRoundedRect(wx, wy, ww, 46, 18, 18)
    p.drawRect(wx, wy + 28, ww, 18)
    p.setBrush(QColor(0, 0, 0, 0))
    p.setPen(QPen(QColor("#2e2e40"), 1))
    p.drawLine(wx, wy + 46, wx + ww, wy + 46)

    # traffic dots
    for i, c in enumerate([PINK, QColor("#ffb300"), QColor("#4cd97b")]):
        p.setBrush(c)
        p.setPen(Qt.NoPen)
        p.drawEllipse(int(wx + 22 + i * 22), wy + 18, 10, 10)

    # address pill
    p.setBrush(QColor("#232333"))
    p.setPen(QPen(QColor("#35354a"), 1))
    p.drawRoundedRect(wx + 110, wy + 8, ww - 260, 30, 12, 12)
    p.setPen(QColor("#9a9ab0"))
    p.setFont(QFont("Sans", 10))
    p.drawText(QPointF(wx + 126, wy + 28), "duckduckgo.com/?q=developer+browser")

    # go button (animated neon)
    gox0, goy0 = wx + ww - 140, wy + 8
    g2 = QLinearGradient(gox0, goy0, gox0 + 120, goy0 + 30)
    g2.setColorAt(0, PINK)
    g2.setColorAt(1, PURPLE)
    p.setBrush(g2)
    p.setPen(Qt.NoPen)
    p.drawRoundedRect(gox0, goy0, 120, 30, 12, 12)
    p.setPen(QColor("#ffffff"))
    p.setFont(QFont("Sans", 11, QFont.Bold))
    p.drawText(QPointF(gox0 + 48, goy0 + 21), "Go")

    # tab chips
    chips = [("DevBrowser", True), ("MDN", False), ("GitHub", False)]
    cx0 = wx + 16
    for i, (label, active) in enumerate(chips):
        x = cx0 + i * 130
        if active:
            g3 = QLinearGradient(x, wy + 54, x + 116, wy + 88)
            g3.setColorAt(0, PINK)
            g3.setColorAt(1, PURPLE)
            p.setBrush(g3)
            p.setPen(Qt.NoPen)
        else:
            p.setBrush(QColor("#20202e"))
            p.setPen(QPen(QColor("#2e2e40"), 1))
        p.drawRoundedRect(x, wy + 56, 116, 32, 10, 10)
        p.setPen(QColor("#ffffff") if active else QColor("#9a9ab0"))
        p.drawText(QPointF(x + 14, wy + 76), label)

    # page content: neon hero
    py0 = wy + 110
    p.setPen(QColor("#ffffff"))
    p.setFont(QFont("Sans", 34, QFont.Bold))
    p.drawText(QPointF(wx + 56, py0 + 40), "Dev")
    p.setPen(PINK)
    p.drawText(QPointF(wx + 160, py0 + 40), "Browser")
    p.setPen(QColor("#9a9ab0"))
    p.setFont(QFont("Sans", 15))
    p.drawText(QPointF(wx + 56, py0 + 74), "Tor & proxy ready · dev console · install apps")

    # cards
    cards = ["View source", "Console", "Screenshot", "UA switch"]
    for i, label in enumerate(cards):
        x = wx + 56 + i * (int(ww * 0.20))
        p.setBrush(QColor("#1e1e2a"))
        p.setPen(QPen(QColor("#2e2e40"), 1))
        p.drawRoundedRect(x, py0 + 100, int(ww * 0.18), 96, 14, 14)
        p.setPen(QColor("#00e7ff") if i % 2 == 0 else QColor("#ff3ea5"))
        p.setFont(QFont("Sans", 13, QFont.Bold))
        p.drawText(QPointF(x + 16, py0 + 136), label)
        p.setPen(QColor("#9a9ab0"))
        p.setFont(QFont("Sans", 10))
        p.drawText(QPointF(x + 16, py0 + 158), "developer workspace")

    # console strip at bottom with animated caret
    p.setBrush(QColor("#10101a"))
    p.setPen(Qt.NoPen)
    p.drawRect(wx, wy + wh - 64, ww, 64)
    p.setPen(QColor("#00e7ff"))
    p.setFont(QFont("Monospace", 11))
    p.drawText(QPointF(wx + 20, wy + wh - 34), "> document.title  =>  'DevBrowser'")
    caret_x = wx + 40 + int(0.6 * (ww - 80)) * glow
    p.setPen(CYAN)
    p.drawLine(int(caret_x), wy + wh - 44, int(caret_x), wy + wh - 26)

    # footer
    p.setPen(QColor("#55556a"))
    p.setFont(QFont("Sans", 11))
    p.drawText(QPointF(wx + 20, wy + wh - 18), "window.DevBrowser → title · url · host · version")

    p.end()
    return pm


def main():
    app = QApplication([])
    os.makedirs(os.path.join(OUT, ".frames"), exist_ok=True)
    for f in range(FRAMES):
        pm = draw_scene(app, f)
        pm.save(os.path.join(OUT, ".frames", f"f{f:03d}.png"))
    app.processEvents()

    # still + animation
    pm = draw_scene(app, 0)
    pm.save(os.path.join(OUT, "banner.png"))
    subprocess.run([
        FFMPEG, "-y",
        "-framerate", "30", "-i", os.path.join(OUT, ".frames", "f%03d.png"),
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "26",
        os.path.join(OUT, "banner.mp4"),
    ], check=True, capture_output=True)
    print("wrote docs/media/banner.png, docs/media/banner.mp4")


if __name__ == "__main__":
    main()
