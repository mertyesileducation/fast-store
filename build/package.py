#!/usr/bin/env python3
"""Imzasiz APK paketleyici: sikistirilmamis girdileri 4 bayta hizalar
(Android 11+ resources.arsc'nin sikistirilmamis ve hizali olmasini ister).
Kullanim: package.py <cikis.apk> <derlenmis_kaynak_klasoru> <classes.dex>
"""
import os
import sys
import zipfile

out, res_dir, dex = sys.argv[1], sys.argv[2], sys.argv[3]

entries = [
    ("AndroidManifest.xml", os.path.join(res_dir, "AndroidManifest.xml"), zipfile.ZIP_DEFLATED),
    ("classes.dex", dex, zipfile.ZIP_DEFLATED),
    ("resources.arsc", os.path.join(res_dir, "resources.arsc"), zipfile.ZIP_STORED),
]
draw = os.path.join(res_dir, "res", "drawable")
for fn in sorted(os.listdir(draw)):
    entries.append(("res/drawable/" + fn, os.path.join(draw, fn), zipfile.ZIP_STORED))

with zipfile.ZipFile(out, "w") as z:
    for name, path, method in entries:
        with open(path, "rb") as f:
            data = f.read()
        info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = method
        info.create_system = 0
        if method == zipfile.ZIP_STORED:
            # yerel baslik 30 bayt + ad + extra; veri baslangicini 4'e hizala
            header_end = z.fp.tell() + 30 + len(name.encode())
            pad = (4 - header_end % 4) % 4
            info.extra = b"\x00" * pad
        z.writestr(info, data)
print("Paketlendi ->", out)
