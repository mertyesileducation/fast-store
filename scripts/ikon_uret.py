#!/usr/bin/env python3
"""Fast Store ikonlarini res/drawable altina uretir (Pillow gerekir)."""
import os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "res", "drawable")
S = 4
C1, C2 = (124, 58, 237), (37, 99, 235)
WHITE = (255, 255, 255, 255)
BOLT = (250, 204, 21, 255)


def gradient(n):
    img = Image.new("RGBA", (n, n))
    px = img.load()
    for y in range(n):
        for x in range(n):
            t = (x + y) / (2 * (n - 1))
            px[x, y] = tuple(int(C1[i] + (C2[i] - C1[i]) * t) for i in range(3)) + (255,)
    return img


def bag(d, n, fill, bolt_fill, cut=None):
    """Alisveris cantasi + simsek. n: tuval boyutu."""
    bx0, by0, bx1, by1 = n * 0.24, n * 0.36, n * 0.76, n * 0.80
    d.rounded_rectangle([bx0, by0, bx1, by1], int(n * 0.07), fill=fill)
    w = int(n * 0.055)
    d.arc([n * 0.36, n * 0.18, n * 0.64, n * 0.50], 180, 360, fill=fill, width=w)
    d.line([(n * 0.36, n * 0.34), (n * 0.36, n * 0.40)], fill=fill, width=w)
    d.line([(n * 0.64, n * 0.34), (n * 0.64, n * 0.40)], fill=fill, width=w)
    bolt = [(0.545, 0.42), (0.415, 0.61), (0.495, 0.61), (0.455, 0.75), (0.595, 0.55), (0.51, 0.55), (0.55, 0.42)]
    d.polygon([(x * n, y * n) for x, y in bolt], fill=bolt_fill)


def launcher():
    n = 192 * S
    img = gradient(n)
    bag(ImageDraw.Draw(img), n, WHITE, BOLT)
    m = Image.new("L", (n, n), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, n - 1, n - 1], int(n * 0.22), fill=255)
    img.putalpha(m)
    return img.resize((192, 192), Image.LANCZOS)


def stat():
    n = 96 * S
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    bag(ImageDraw.Draw(img), n, WHITE, (0, 0, 0, 0))
    return img.resize((96, 96), Image.LANCZOS)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    launcher().save(os.path.join(OUT, "ic_launcher.png"), optimize=True)
    stat().save(os.path.join(OUT, "ic_stat_store.png"), optimize=True)
    print("ikonlar uretildi")
