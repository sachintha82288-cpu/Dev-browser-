#!/usr/bin/env python3
"""3D modeling + animation demo — CPU only (trimesh + matplotlib).

Builds a parametric "DevBrowser knot" from a torus-knot curve, renders an
orbiting turntable with matplotlib's 3D raster engine (no GPU/GL needed),
encodes with ffmpeg. Output: docs/media/torus_knot.png + .mp4
"""
import math
import os
import subprocess

import imageio_ffmpeg

FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import trimesh

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUT = os.path.join(ROOT, "docs", "media")
os.makedirs(OUT, exist_ok=True)

FRAMES = 48


def knot_pipe(p=4, q=3, R=1.0, r=0.28, tube=0.09, n=1400, m=28):
    """Torus knot swept tube (CPU, itertools-free)."""
    t = np.linspace(0, 2 * np.pi, n)
    # centerline on (R + r*cos(q t)) ...
    x = (R + r * np.cos(q * t)) * np.cos(p * t)
    y = (R + r * np.cos(q * t)) * np.sin(p * t)
    z = r * np.sin(q * t) * 1.0
    center = np.stack([x, y, z], axis=1)
    # Frenet-ish frame via tangent + up
    tangent = np.gradient(center, axis=0)
    tangent /= np.linalg.norm(tangent, axis=1, keepdims=True) + 1e-12
    up = np.array([0.0, 0.0, 1.0])
    side = np.cross(tangent, up)
    side /= np.linalg.norm(side, axis=1, keepdims=True) + 1e-12
    binormal = np.cross(tangent, side)

    verts = []
    faces = []
    for i in range(n):
        c = center[i]
        for j in range(m):
            a = 2 * math.pi * j / m
            v = c + tube * (math.cos(a) * side[i] + math.sin(a) * binormal[i])
            verts.append(v)
    verts = np.array(verts)
    for i in range(n):
        for j in range(m):
            a = i * m + j
            b = i * m + (j + 1) % m
            c2 = ((i + 1) % n) * m + (j + 1) % m
            d = ((i + 1) % n) * m + j
            faces.append((a, b, c2))
            faces.append((a, c2, d))
    return trimesh.Trimesh(vertices=verts, faces=np.array(faces), process=True)


def render_mesh(a, mesh, elev=18, azim=45):
    a.clear()
    a.set_facecolor("#0d0d13")
    for axis in (a.xaxis, a.yaxis, a.zaxis):
        axis.set_ticks([])
        axis.pane.set_facecolor((0.05, 0.05, 0.08, 0.9))
        axis.pane.set_edgecolor((0.1, 0.1, 0.15, 0.9))
    a.set_axis_off()
    a.view_init(elev=elev, azim=azim)
    # clip to mesh bbox
    b = mesh.bounds
    a.set_xlim(b[0][0], b[1][0])
    a.set_ylim(b[0][1], b[1][1])
    a.set_zlim(b[0][2], b[1][2])
    # face colors: neon gradient by point y
    tri = mesh.triangles
    ys = tri[:, :, 0].mean(axis=1)  # use x for gradient
    norm = (ys - ys.min()) / (ys.max() - ys.min() + 1e-12)
    from matplotlib.colors import LinearSegmentedColormap
    cmap = LinearSegmentedColormap.from_list("gx",
            ["#ff3ea5", "#8a5cff", "#00e7ff"])
    cols = cmap(norm)
    from mpl_toolkits.mplot3d.art3d import Poly3DCollection
    pc = Poly3DCollection(mesh.triangles, facecolors=cols,
                          edgecolors=(0, 0, 0, 0.08), linewidths=0.12)
    a.add_collection3d(pc)
    return a


def main():
    mesh = knot_pipe()
    print("mesh:", len(mesh.vertices), "verts,", len(mesh.faces), "faces, watertight:",
          mesh.is_watertight)
    fig = plt.figure(figsize=(6.4, 4.8), facecolor="#0d0d13")
    a = fig.add_subplot(projection="3d")

    frames_dir = os.path.join(OUT, ".frames3d")
    os.makedirs(frames_dir, exist_ok=True)
    for i in range(FRAMES):
        render_mesh(a, mesh, elev=16 + 4 * math.sin(2 * math.pi * i / FRAMES),
                    azim=20 + 340 * i / FRAMES)
        fig.savefig(os.path.join(frames_dir, f"f{i:03d}.png"), facecolor="#0d0d13")
    fig.savefig(os.path.join(OUT, "torus_knot.png"), facecolor="#0d0d13")

    subprocess.run([
        FFMPEG, "-y",
        "-framerate", "25", "-i", os.path.join(frames_dir, "f%03d.png"),
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "26",
        os.path.join(OUT, "torus_knot.mp4"),
    ], check=True, capture_output=True)
    print("wrote docs/media/torus_knot.png, docs/media/torus_knot.mp4")


if __name__ == "__main__":
    main()
