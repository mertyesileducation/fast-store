#!/usr/bin/env python3
"""
Fast Store katalog olusturucu
=============================
Bir klasordeki APK dosyalarini okuyup Fast Store'un kullandigi katalog.json'u uretir.
Ek bir program gerektirmez (sadece Python 3).

Kullanim:
    python3 katalog_olustur.py <apk_klasoru> [--ad "Fast Store"]

APK'dan otomatik okunanlar:
    paket adi, surum, minSdk/targetSdk, izinler, imza sertifikasi (SHA-256),
    dosya boyutu ve SHA-256 ozeti.
Onceki katalog.json'dan korunanlar:
    "added" (ilk eklenme tarihi). "updated" surum degisince bugunun tarihi olur.

Klasor yapisi:
    katalog/
      uygulama.apk
      com.ornek.uygulama.png          <- (istege bagli) ikon
      com.ornek.uygulama.banner.png   <- (istege bagli) vitrin gorseli (1024x500)
      bilgiler.json                   <- (istege bagli) aciklamalar vb.

bilgiler.json ornegi:
    {
      "com.ornek.notlar": {
        "name": "Notlar",
        "author": "Ben",
        "category": "Verimlilik",
        "shortDescription": "Hizli ve sade not uygulamasi",
        "description": "Uzun aciklama...",
        "changelog": "Karanlik tema eklendi.",
        "tags": ["not", "yazi"],
        "featured": true,
        "website": "https://ornek.com",
        "email": "iletisim@ornek.com",
        "privacy": "https://ornek.com/gizlilik",
        "screenshots": ["ekran1.png", "ekran2.png"]
      }
    }
"""
import datetime
import hashlib
import json
import os
import struct
import sys
import zipfile


def parse_manifest(data):
    """Ikili AndroidManifest.xml'den temel bilgileri ve izinleri okur."""
    pos = 8
    strings = []
    result = {"permissions": []}
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
            attrs = {}
            for i in range(acount):
                a = pos + 16 + astart + i * asize
                _, an, raw, _, _, dtype, dval = struct.unpack_from("<IIIHBBI", data, a)
                if dtype == 0x03:
                    attrs[strings[an]] = strings[dval]
                elif raw != 0xFFFFFFFF:
                    attrs[strings[an]] = strings[raw]
                elif dtype in (0x10, 0x11):
                    attrs[strings[an]] = dval
            if tag in ("manifest", "application", "uses-sdk"):
                for k, v in attrs.items():
                    result[tag + ":" + k] = v
            elif tag in ("uses-permission", "uses-permission-sdk-23") and "name" in attrs:
                result["permissions"].append(attrs["name"])
        pos += csize
    return result


def signer_sha256(path):
    """APK Signature Scheme v2/v3 blogundan ilk imza sertifikasinin SHA-256 ozeti."""
    with open(path, "rb") as f:
        data = f.read()
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        return None
    cd_offset = struct.unpack_from("<I", data, eocd + 16)[0]
    if data[cd_offset - 16:cd_offset] != b"APK Sig Block 42":
        return None
    block_size = struct.unpack_from("<Q", data, cd_offset - 24)[0]
    start = cd_offset - block_size - 8
    p = start + 8
    end = cd_offset - 24
    while p < end:
        length, pid = struct.unpack_from("<QI", data, p)
        value = data[p + 12:p + 8 + length]
        if pid in (0x7109871A, 0xF05368C0):  # v2, v3
            def lp(buf, off):
                n = struct.unpack_from("<I", buf, off)[0]
                return buf[off + 4:off + 4 + n], off + 4 + n
            signers, _ = lp(value, 0)
            signer, _ = lp(signers, 0)
            signed_data, _ = lp(signer, 0)
            _, off = lp(signed_data, 0)          # digests
            certs, _ = lp(signed_data, off)      # certificates
            cert, _ = lp(certs, 0)
            return hashlib.sha256(cert).hexdigest()
        p += 8 + length
    return None


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
    today = datetime.date.today().isoformat()

    meta = {}
    meta_path = os.path.join(folder, "bilgiler.json")
    if os.path.exists(meta_path):
        with open(meta_path, encoding="utf-8") as f:
            meta = json.load(f)

    previous = {}
    out_path = os.path.join(folder, "katalog.json")
    if os.path.exists(out_path):
        try:
            with open(out_path, encoding="utf-8") as f:
                for e in json.load(f).get("apps", []):
                    previous[e["packageName"]] = e
        except Exception:
            pass

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
        prev = previous.get(pkg, {})
        entry = {
            "packageName": pkg,
            "name": info.get("name") or label or pkg,
            "author": info.get("author", ""),
            "category": info.get("category", "Diğer"),
            "shortDescription": info.get("shortDescription", ""),
            "description": info.get("description", ""),
            "changelog": info.get("changelog", ""),
            "versionCode": vcode,
            "versionName": str(m.get("manifest:versionName", vcode)),
            "minSdk": int(m.get("uses-sdk:minSdkVersion", 1) or 1),
            "targetSdk": int(m.get("uses-sdk:targetSdkVersion", 0) or 0),
            "size": os.path.getsize(path),
            "sha256": sha256(path),
            "signer": signer_sha256(path) or "",
            "permissions": sorted(set(m["permissions"])),
            "added": prev.get("added", today),
            "updated": today if prev.get("versionCode") != vcode else prev.get("updated", today),
            "apkUrl": fn,
        }
        for key in ("tags", "featured", "website", "email", "privacy", "screenshots"):
            if key in info:
                entry[key] = info[key]
        for key, suffix in (("iconUrl", ""), ("bannerUrl", ".banner")):
            if key in info:
                entry[key] = info[key]
                continue
            for ext in (".png", ".jpg", ".webp"):
                if os.path.exists(os.path.join(folder, pkg + suffix + ext)):
                    entry[key] = pkg + suffix + ext
                    break
        old = apps.get(pkg)
        if old and old["versionCode"] >= vcode:
            print("ATLANDI %s: %s icin daha yeni surum zaten var" % (fn, pkg))
            continue
        apps[pkg] = entry
        print("EKLENDI %-34s %-6s minSdk=%-3s izin=%-2d imza=%s (%s)" % (
            pkg, entry["versionName"], entry["minSdk"], len(entry["permissions"]),
            (entry["signer"][:16] + "…") if entry["signer"] else "YOK", fn))

    out = {
        "name": store_name,
        "generated": today,
        "apps": sorted(apps.values(), key=lambda e: e["name"].lower()),
    }
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print("\n%d uygulama -> %s" % (len(apps), out_path))


if __name__ == "__main__":
    main()
