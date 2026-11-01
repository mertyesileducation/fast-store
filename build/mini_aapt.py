#!/usr/bin/env python3
"""Mini kaynak derleyici: aapt2 olmadan APK icin ikili AndroidManifest.xml,
resources.arsc ve uygulama ikonunu uretir.

Kullanim: mini_aapt.py <AndroidManifest.xml> <cikti_klasoru>
Cikti: <klasor>/AndroidManifest.xml, <klasor>/resources.arsc,
       <klasor>/res/drawable/ic_launcher.png
"""
import os
import struct
import sys
import zlib
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"

# android.R.attr degerleri (android-34 android.jar'dan javap ile alindi)
ATTR_IDS = {
    "theme": 0x01010000, "label": 0x01010001, "icon": 0x01010002,
    "name": 0x01010003, "permission": 0x01010006, "enabled": 0x0101000E,
    "exported": 0x01010010, "launchMode": 0x0101001D,
    "configChanges": 0x0101001F, "minSdkVersion": 0x0101020C,
    "versionCode": 0x0101021B, "versionName": 0x0101021C,
    "windowSoftInputMode": 0x0101022B, "targetSdkVersion": 0x01010270,
    "allowBackup": 0x01010280, "parentActivityName": 0x010103A7,
    "supportsRtl": 0x010103AF, "usesCleartextTraffic": 0x010104EC,
}
FRAMEWORK_REFS = {
    "@android:style/Theme.Material.Light.DarkActionBar": 0x01030238,
    "@android:style/Theme.DeviceDefault.Light.DarkActionBar": 0x0103013F,
}
APP_REFS = {"@drawable/ic_launcher": 0x7F010000}
INT_ATTRS = {"versionCode", "minSdkVersion", "targetSdkVersion"}
ENUM_ATTRS = {
    "launchMode": {"standard": 0, "singleTop": 1, "singleTask": 2, "singleInstance": 3},
    "windowSoftInputMode": {"adjustResize": 0x10, "adjustPan": 0x20,
                            "stateHidden|adjustResize": 0x12},
}

T_REFERENCE, T_STRING, T_INT_DEC, T_INT_HEX, T_BOOLEAN = 0x01, 0x03, 0x10, 0x11, 0x12
NO_INDEX = 0xFFFFFFFF


def _utf8_len(n):
    return bytes([n]) if n < 0x80 else bytes([0x80 | (n >> 8), n & 0xFF])


def string_pool(strings):
    """UTF-8 ResStringPool parcasi."""
    data = bytearray()
    offsets = []
    for s in strings:
        offsets.append(len(data))
        b = s.encode("utf-8")
        data += _utf8_len(len(s)) + _utf8_len(len(b)) + b + b"\x00"
    while len(data) % 4:
        data += b"\x00"
    header_size = 28
    strings_start = header_size + 4 * len(strings)
    size = strings_start + len(data)
    hdr = struct.pack("<HHIIIIII", 0x0001, header_size, size, len(strings), 0,
                      0x100, strings_start, 0)
    return hdr + b"".join(struct.pack("<I", o) for o in offsets) + bytes(data)


def compile_manifest(path):
    tree = ET.parse(path)
    root = tree.getroot()

    # Once kaynak kimligi olan (android:) oznitelik adlari, sonra diger dizgiler
    attr_names = []
    others = []

    def add_other(s):
        if s not in others:
            others.append(s)

    def walk(el):
        for k, v in el.attrib.items():
            if k.startswith("{" + ANDROID_NS + "}"):
                n = k.split("}")[1]
                if n not in ATTR_IDS:
                    raise SystemExit("Bilinmeyen android ozniteligi: " + n)
                if n not in attr_names:
                    attr_names.append(n)
            else:
                add_other(k)
            add_other(v)
        add_other(el.tag)
        for c in el:
            walk(c)

    walk(root)
    attr_names.sort(key=lambda n: ATTR_IDS[n])
    add_other("android")
    add_other(ANDROID_NS)
    strings = attr_names + [s for s in others if s not in attr_names]
    idx = {s: i for i, s in enumerate(strings)}
    # oznitelik adiyla ayni olan deger dizgileri ilk indeksi kullanir
    ns_i = idx[ANDROID_NS]

    chunks = bytearray()
    chunks += string_pool(strings)
    resmap = b"".join(struct.pack("<I", ATTR_IDS[n]) for n in attr_names)
    chunks += struct.pack("<HHI", 0x0180, 8, 8 + len(resmap)) + resmap
    chunks += struct.pack("<HHIIIII", 0x0100, 16, 24, 1, NO_INDEX,
                          idx["android"], ns_i)

    def encode_value(name, value, is_android):
        if value.startswith("@"):
            ref = FRAMEWORK_REFS.get(value) or APP_REFS.get(value)
            if ref is None:
                raise SystemExit("Bilinmeyen kaynak referansi: " + value)
            return NO_INDEX, T_REFERENCE, ref
        if is_android and value in ("true", "false"):
            return NO_INDEX, T_BOOLEAN, 0xFFFFFFFF if value == "true" else 0
        if is_android and name in INT_ATTRS:
            return NO_INDEX, T_INT_DEC, int(value)
        if is_android and name in ENUM_ATTRS:
            return NO_INDEX, T_INT_HEX if name == "windowSoftInputMode" else T_INT_DEC, ENUM_ATTRS[name][value]
        return idx[value], T_STRING, idx[value]

    def emit(el, line=[1]):
        attrs = []
        for k, v in el.attrib.items():
            if k.startswith("{" + ANDROID_NS + "}"):
                n = k.split("}")[1]
                raw, t, d = encode_value(n, v, True)
                attrs.append((0, ATTR_IDS[n], ns_i, idx[n], raw, t, d))
            else:
                raw, t, d = encode_value(k, v, False)
                attrs.append((1, 0, NO_INDEX, idx[k], raw, t, d))
        attrs.sort(key=lambda a: (a[0], a[1]))  # kaynak kimligine gore sirali
        body = struct.pack("<IIHHHHHH", NO_INDEX, idx[el.tag], 20, 20,
                           len(attrs), 0, 0, 0)
        for _, _, ns, nm, raw, t, d in attrs:
            body += struct.pack("<IIIHBBI", ns, nm, raw, 8, 0, t, d)
        chunks.extend(struct.pack("<HHIII", 0x0102, 16, 16 + len(body), line[0], NO_INDEX) + body)
        line[0] += 1
        for c in el:
            emit(c)
        chunks.extend(struct.pack("<HHIIIII", 0x0103, 16, 24, line[0], NO_INDEX,
                                  NO_INDEX, idx[el.tag]))
        line[0] += 1

    emit(root)
    chunks += struct.pack("<HHIIIII", 0x0101, 16, 24, 1, NO_INDEX,
                          idx["android"], ns_i)
    return struct.pack("<HHI", 0x0003, 8, 8 + len(chunks)) + bytes(chunks)


def compile_arsc(package_name):
    """Tek kaynak iceren tablo: drawable/ic_launcher -> res/drawable/ic_launcher.png"""
    global_pool = string_pool(["res/drawable/ic_launcher.png"])
    type_pool = string_pool(["drawable"])
    key_pool = string_pool(["ic_launcher"])

    type_spec = struct.pack("<HHIBBHI", 0x0202, 16, 16 + 4, 1, 0, 0, 1) + struct.pack("<I", 0)

    config = struct.pack("<I", 64) + b"\x00" * 60
    entry = struct.pack("<HHI", 8, 0, 0) + struct.pack("<HBBI", 8, 0, T_STRING, 0)
    type_header_size = 20 + len(config)
    entries_start = type_header_size + 4
    type_chunk = struct.pack("<HHIBBHII", 0x0201, type_header_size,
                             entries_start + len(entry), 1, 0, 0, 1, entries_start)
    type_chunk += config + struct.pack("<I", 0) + entry

    name16 = package_name.encode("utf-16-le")[:254]
    name16 += b"\x00" * (256 - len(name16))
    pkg_header_size = 288
    type_strings_off = pkg_header_size
    key_strings_off = type_strings_off + len(type_pool)
    pkg_body = type_pool + key_pool + type_spec + type_chunk
    pkg = struct.pack("<HHII", 0x0200, pkg_header_size, pkg_header_size + len(pkg_body), 0x7F)
    pkg += name16 + struct.pack("<IIIII", type_strings_off, 1, key_strings_off, 1, 0)
    assert len(pkg) == pkg_header_size
    pkg += pkg_body

    body = global_pool + pkg
    return struct.pack("<HHII", 0x0002, 12, 12 + len(body), 1) + body


def make_icon(size=192):
    """Mavi-mor gecisli zemin uzerinde beyaz alisveris cantasi."""
    import math
    rows = []
    r_corner = size * 0.22
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            # yuvarlatilmis kare maskesi
            cx = min(max(x + 0.5, r_corner), size - r_corner)
            cy = min(max(y + 0.5, r_corner), size - r_corner)
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            alpha = max(0.0, min(1.0, r_corner - d + 0.5))
            t = (x + y) / (2.0 * size)
            r = int(37 + (124 - 37) * t)
            g = int(99 + (58 - 99) * t)
            b = int(235 + (237 - 235) * t)
            u, v = x / size, y / size
            white = False
            # canta govdesi
            if 0.27 <= u <= 0.73 and 0.38 <= v <= 0.78:
                white = True
            # sap (yay)
            dh = math.hypot(u - 0.5, (v - 0.38) * 1.0)
            if v < 0.40 and 0.13 <= dh <= 0.19:
                white = True
            # govde icinde gulumseme / ok
            if 0.27 <= u <= 0.73 and 0.38 <= v <= 0.78:
                ds = math.hypot(u - 0.5, v - 0.52)
                if v > 0.52 and 0.10 <= ds <= 0.145:
                    white = False
            if white:
                r, g, b = 255, 255, 255
            row += bytes([r, g, b, int(alpha * 255)])
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    manifest, out = sys.argv[1], sys.argv[2]
    os.makedirs(os.path.join(out, "res", "drawable"), exist_ok=True)
    pkg = ET.parse(manifest).getroot().attrib["package"]
    with open(os.path.join(out, "AndroidManifest.xml"), "wb") as f:
        f.write(compile_manifest(manifest))
    with open(os.path.join(out, "resources.arsc"), "wb") as f:
        f.write(compile_arsc(pkg))
    with open(os.path.join(out, "res", "drawable", "ic_launcher.png"), "wb") as f:
        f.write(make_icon())
    print("Kaynaklar derlendi ->", out)


if __name__ == "__main__":
    main()
