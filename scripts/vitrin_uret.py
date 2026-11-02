#!/usr/bin/env python3
"""Katalogdaki uygulamalar icin 1024x500 vitrin afisi uretir (Pillow gerekir).
Kullanim: python3 scripts/vitrin_uret.py katalog
Her <paket>.png ikonu ve bilgiler.json'daki ad/kisa aciklama kullanilir;
cikti: katalog/<paket>.banner.png (varsa uzerine yazilmaz, --zorla ile yazilir).
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1024, 500
FONTS = ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
         "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]
PALETTES = {
    "Eğitim": ((37, 99, 235), (30, 58, 138)),
    "Araçlar": ((124, 58, 237), (37, 99, 235)),
    "Verimlilik": ((5, 150, 105), (13, 148, 136)),
    "Oyun": ((234, 88, 12), (219, 39, 119)),
}
DEFAULT = ((51, 65, 85), (15, 23, 42))


def font(bold, size):
    path = FONTS[0] if bold else FONTS[1]
    return ImageFont.truetype(path, size) if os.path.exists(path) else ImageFont.load_default()


def wrap(draw, text, f, width):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if draw.textlength(t, font=f) <= width:
            cur = t
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def banner(icon_path, name, subtitle, category):
    c1, c2 = PALETTES.get(category, DEFAULT)
    img = Image.new("RGB", (W, H))
    px = img.load()
    for y in range(H):
        for x in range(W):
            t = (x / W) * 0.7 + (y / H) * 0.3
            px[x, y] = tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))
    # yumusak dekoratif daireler
    deco = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(deco)
    d.ellipse([W - 360, -160, W + 120, 320], fill=(255, 255, 255, 28))
    d.ellipse([W - 220, 260, W + 60, 540], fill=(255, 255, 255, 20))
    d.ellipse([-120, 330, 200, 650], fill=(255, 255, 255, 16))
    img = Image.alpha_composite(img.convert("RGBA"), deco.filter(ImageFilter.GaussianBlur(2)))

    icon = Image.open(icon_path).convert("RGBA").resize((220, 220), Image.LANCZOS)
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle([72, 152, 72 + 220, 152 + 220], 48, fill=(0, 0, 0, 90))
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(18)))
    img.alpha_composite(icon, (64, 140))

    draw = ImageDraw.Draw(img)
    x = 340
    f_name = font(True, 64)
    f_sub = font(False, 30)
    lines = wrap(draw, subtitle, f_sub, W - x - 60)[:3]
    total = 76 + len(lines) * 42
    y = (H - total) // 2
    draw.text((x, y), name, font=f_name, fill=(255, 255, 255))
    y += 84
    for line in lines:
        draw.text((x, y), line, font=f_sub, fill=(226, 232, 240))
        y += 42
    return img.convert("RGB")


def main():
    folder = sys.argv[1] if len(sys.argv) > 1 else "katalog"
    force = "--zorla" in sys.argv
    with open(os.path.join(folder, "bilgiler.json"), encoding="utf-8") as f:
        meta = json.load(f)
    for pkg, info in meta.items():
        icon = os.path.join(folder, pkg + ".png")
        out = os.path.join(folder, pkg + ".banner.png")
        if not os.path.exists(icon):
            print("ikon yok, atlandi:", pkg)
            continue
        if os.path.exists(out) and not force:
            print("zaten var:", out)
            continue
        banner(icon, info.get("name", pkg), info.get("shortDescription", ""),
               info.get("category", "")).save(out, optimize=True)
        print("uretildi:", out)


if __name__ == "__main__":
    main()
