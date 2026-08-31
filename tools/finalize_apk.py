#!/usr/bin/env python3
"""Insert classes.dex into an aapt2-built APK and 4-byte-align stored entries.

Implementation notes
--------------------
* We re-emit every entry with Python's zipfile (valid, canonical headers).
* Before writing, each STORED entry gets padding bytes added to its local
  "extra" field so that its data starts on a 4-byte boundary (zipalign
  equivalent) — classes.dex included.
* DEFLATED entries never need alignment (they are not mmap'd); their
  compressed sizes are pre-computed identically to zipfile's writer so the
  alignment forecast stays correct.

Usage: finalize_apk.py <unsigned.apk> <classes.dex> <output.apk>
"""
import sys
import zipfile
import zlib


def _comp_size(info, data):
    if info.compress_type == zipfile.ZIP_STORED:
        return len(data)
    c = zlib.compressobj(6, zlib.DEFLATED, -15)
    return len(c.compress(data) + c.flush())


def build(src, dex, dst):
    with zipfile.ZipFile(src) as zin:
        infos = zin.infolist()
        data = {i.filename: zin.read(i) for i in infos}

    dex_data = open(dex, "rb").read()

    # ---- pass 1: forecast offsets, assign extra-field padding -------------
    off = 0
    for info in infos:
        raw = info.filename.encode("utf-8")
        csize = _comp_size(info, data[info.filename])
        base = 30 + len(raw)
        pad = 0
        if info.compress_type == zipfile.ZIP_STORED:
            pad = (4 - ((off + base) % 4)) % 4
        info.extra = b"\x00" * pad
        off += base + pad + csize

    dex_name = "classes.dex"
    dbase = 30 + len(dex_name)
    dex_pad = (4 - ((off + dbase) % 4)) % 4

    # ---- pass 2: write ----------------------------------------------------
    with zipfile.ZipFile(dst, "w", allowZip64=False) as zout:
        for info in infos:
            zout.writestr(info, data[info.filename])
        dex_info = zipfile.ZipInfo(dex_name)
        dex_info.compress_type = zipfile.ZIP_STORED
        dex_info.extra = b"\x00" * dex_pad
        zout.writestr(dex_info, dex_data)

    # verify
    unaligned = []
    with zipfile.ZipFile(dst) as z:
        for info in z.infolist():
            if info.compress_type == zipfile.ZIP_STORED:
                o = info.header_offset + 30 + len(info.filename.encode()) + len(info.extra)
                if o % 4:
                    unaligned.append((info.filename, o))
    print(f"built {dst}: {len(infos) + 1} entries, dex {len(dex_data)} B (pad {dex_pad})")
    print(f"alignment: stored entries unaligned -> {unaligned or 'none'}")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print(__doc__)
        sys.exit(2)
    build(sys.argv[1], sys.argv[2], sys.argv[3])
