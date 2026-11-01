#!/usr/bin/env python3
"""
Fast Store katalog olusturucu
==========================
Bir klasordeki APK dosyalarini okuyup Fast Store'un kullandigi katalog.json'u uretir.
Ek bir program gerektirmez (sadece Python 3).

Kullanim:
    python3 katalog_olustur.py <apk_klasoru> [--ad "Fast Store"]

Klasor yapisi (ornek):
    magaza/
      notlar.apk
      com.ornek.notlar.png        <- (istege bagli) ikon, adi paket adiyla ayni
      bilgiler.json               <- (istege bagli) aciklamalar, kategori vb.

bilgiler.json ornegi:
    {
      "com.ornek.notlar": {
        "name": "Notlar",
        "author": "Ben",
        "category": "Verimlilik",
        "description": "Basit not uygulamasi.",
        "changelog": "Karanlik tema eklendi.",
        "screenshots": ["ekran1.png", "ekran2.png"]
      }
    }

Paket adi, surum ve SHA-256 APK'dan otomatik okunur; elle yazmana gerek yok.
Cikti: <apk_klasoru>/katalog.json  (klasoru oldugu gibi bir web sunucusuna /
GitHub'a yukle, Fast Store'da katalog.json'un adresini gir.)
"""
import hashlib
import json
import os
import struct
import sys
import zipfile


def parse_manifest(data):
    """Ikili AndroidManifest.xml'den manifest ve application ozniteliklerini okur."""
    pos = 8
    strings = []
    result = {}
    while pos < len(data):
        ctype, hsize, csize = struct.unpack_from("<HHI", data, pos)
        if ctype == 0x0001:  # string pool
            count, _, flags, sstart, _ = struct.unpack_from("<IIIII", data, pos + 8)
            utf8 = flags & 0x100
            offs = struct.unpack_from("<%dI" % count, data, pos + hsize)
            for o in offs:
                p = pos + sstart + o
                if utf8:
                    n = data[p]; p += 2 if n & 0x80 else 1
                    n = data[p]
                    if n & 0x80:
                        n = ((n & 0x7F) << 8) | data[p + 1]; p += 2
                    else:
                        p += 1
                    strings.append(data[p:p + n].decode("utf-8", "replace"))
                else:
                    n = struct.unpack_from("<H", data, p)[0]; p += 2
                    if n & 0x8000:
                        n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0]; p += 2
                    strings.append(data[p:p + 2 * n].decode("utf-16-le", "replace"))
        elif ctype == 0x0102:  # start element
            name_i = struct.unpack_from("<I", data, pos + 20)[0]
            astart, asize, acount = struct.unpack_from("<HHH", data, pos + 24)
            tag = strings[name_i]
            if tag in ("manifest", "application", "uses-sdk"):
                for i in range(acount):
                    a = pos + 16 + astart + i * asize
                    _, an, raw, _, _, dtype, dval = struct.unpack_from("<IIIHBBI", data, a)
                    key = tag + ":" + strings[an]
                    if dtype == 0x03:
                        result[key] = strings[dval]
                    elif raw != 0xFFFFFFFF:
                        result[key] = strings[raw]
                    elif dtype in (0x10, 0x11):
                        result[key] = dval
        pos += csize
    return result


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    args = sys.argv[1:]
    if not args or args[0] in ("-h", "--help"):
        print(__doc__)
        sys.exit(0)
    folder = args[0]
    store_name = "Fast Store"
    if "--ad" in args:
        store_name = args[args.index("--ad") + 1]

    meta = {}
    meta_path = os.path.join(folder, "bilgiler.json")
    if os.path.exists(meta_path):
        with open(meta_path, encoding="utf-8") as f:
            meta = json.load(f)

    apps = {}
    for fn in sorted(os.listdir(folder)):
        if not fn.lower().endswith(".apk"):
            continue
        path = os.path.join(folder, fn)
        try:
            with zipfile.ZipFile(path) as z:
                m = parse_manifest(z.read("AndroidManifest.xml"))
        except Exception as ex:
            print("ATLANDI %s: okunamadi (%s)" % (fn, ex))
            continue
        pkg = m.get("manifest:package")
        vcode = m.get("manifest:versionCode", 0)
        if isinstance(vcode, str):
            vcode = int(vcode) if vcode.isdigit() else 0
        label = m.get("application:label")
        if not isinstance(label, str):
            label = None
        info = meta.get(pkg, {})
        entry = {
            "packageName": pkg,
            "name": info.get("name") or label or pkg,
            "author": info.get("author", ""),
            "category": info.get("category", "Diğer"),
            "description": info.get("description", ""),
            "changelog": info.get("changelog", ""),
            "versionCode": vcode,
            "versionName": str(m.get("manifest:versionName", vcode)),
            "size": os.path.getsize(path),
            "sha256": sha256(path),
            "apkUrl": fn,
        }
        if "iconUrl" in info:
            entry["iconUrl"] = info["iconUrl"]
        else:
            for ext in (".png", ".jpg", ".webp"):
                if os.path.exists(os.path.join(folder, pkg + ext)):
                    entry["iconUrl"] = pkg + ext
                    break
        if "screenshots" in info:
            entry["screenshots"] = info["screenshots"]
        prev = apps.get(pkg)
        if prev and prev["versionCode"] >= vcode:
            print("ATLANDI %s: %s icin daha yeni surum zaten var" % (fn, pkg))
            continue
        apps[pkg] = entry
        print("EKLENDI %-40s %s (%s)" % (pkg, entry["versionName"], fn))

    out = {"name": store_name, "apps": sorted(apps.values(), key=lambda e: e["name"].lower())}
    out_path = os.path.join(folder, "katalog.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print("\n%d uygulama -> %s" % (len(apps), out_path))


if __name__ == "__main__":
    main()
