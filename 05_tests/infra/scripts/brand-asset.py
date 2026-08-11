#!/usr/bin/env python3
"""
Brand asset tooling for the helmet emblem (Incident 11).

Two modes:

  build   Regenerate the multi-density emblem from the source artwork. The
          emblem is a raster crop of a supplied illustration, so without this
          script the shipped WebP buckets are unreproducible — which is the
          same "no construction geometry" gap that caused Incident 11 in the
          first place.

  check   Measure the committed PNG master against the brand geometry rules.
          This mirrors app/src/test/java/com/drivingcoach/brand/
          BrandAssetGeometryTest.kt, which is the authoritative gate and runs
          in L1. Use this mode when iterating on artwork outside Gradle.

Requires pillow and numpy, neither of which the app build depends on:

    python3 -m venv .venv && .venv/bin/pip install pillow numpy
    .venv/bin/python 05_tests/infra/scripts/brand-asset.py check

Usage:
    brand-asset.py build <source.png> [--out DIR]
    brand-asset.py check [master.png]
"""

import argparse
import math
import os
import sys
from collections import deque

# Geometry of the supplied source illustration. These are measured constants,
# not guesses: the artwork is a helmet on a navy disc, and the helmet is
# separated from the disc by keying out the disc colour inside a circular mask.
DISC_CENTRE = (584, 455)
DISC_RADIUS = 401
# 0.92R excludes a green/yellow glow ring living at 0.88-0.97R. Verified safe:
# masking at 0.86R and 0.92R yields an identical helmet bounding box.
MASK_FRACTION = 0.92
HELMET_BBOX = (329, 176, 852, 709)

DENSITIES = [(132, "mdpi"), (198, "hdpi"), (264, "xhdpi"), (396, "xxhdpi"), (528, "xxxhdpi")]
MARGIN_FRACTION = 0.02
SUPERSAMPLE = 3


def _require_deps():
    try:
        from PIL import Image, ImageFilter  # noqa: F401
        import numpy  # noqa: F401
    except ImportError as exc:
        sys.exit(f"missing dependency: {exc}. See the module docstring for setup.")


def _is_disc_navy(r, g, b):
    """True for the navy backing disc, false for every colour in the helmet."""
    return 85 < b < 150 and r < 60 and g < 95 and (b - r) > 45


def build(source, out_dir):
    _require_deps()
    from PIL import Image, ImageFilter
    import numpy as np

    cx, cy = DISC_CENTRE
    x0, y0, x1, y1 = HELMET_BBOX
    src = Image.open(source).convert("RGBA")
    px = src.load()

    w, h = x1 - x0 + 1, y1 - y0 + 1
    colour = np.zeros((h, w, 3), np.uint8)
    alpha = np.zeros((h, w), np.uint8)
    for y in range(h):
        for x in range(w):
            sx, sy = x0 + x, y0 + y
            if math.hypot(sx - cx, sy - cy) > DISC_RADIUS * MASK_FRACTION:
                continue
            r, g, b, _ = px[sx, sy]
            if not _is_disc_navy(r, g, b):
                colour[y, x] = (r, g, b)
                alpha[y, x] = 255

    alpha, colour, speckles = _keep_largest_component(alpha, colour)
    holes = _fill_interior_holes(alpha, colour, src, x0, y0, x1, y1)
    print(f"removed {speckles} speckle px, filled {holes} interior hole px")

    # Feather the hard keyed edge: upsample, blur, then clamp the levels so the
    # transition is a few pixels wide rather than a 1px staircase.
    big_c = Image.fromarray(colour).resize((w * SUPERSAMPLE, h * SUPERSAMPLE), Image.LANCZOS)
    big_a = (
        Image.fromarray(alpha)
        .resize((w * SUPERSAMPLE, h * SUPERSAMPLE), Image.LANCZOS)
        .filter(ImageFilter.GaussianBlur(1.6))
        .point(lambda v: 0 if v < 40 else (255 if v > 215 else int((v - 40) * 255 / 175)))
    )

    side = max(w, h)
    margin = int(side * MARGIN_FRACTION)
    canvas = side + 2 * margin
    sheet_c = Image.new("RGB", (canvas * SUPERSAMPLE,) * 2, (0, 0, 0))
    sheet_a = Image.new("L", (canvas * SUPERSAMPLE,) * 2, 0)
    off = (((canvas - w) // 2) * SUPERSAMPLE, ((canvas - h) // 2) * SUPERSAMPLE)
    sheet_c.paste(big_c, off)
    sheet_a.paste(big_a, off)

    # Downsample in premultiplied space. PIL's RGBA resize is not premultiplied
    # and would pull the black backing colour into every edge pixel, producing a
    # dark fringe against the dark app background.
    a = np.asarray(sheet_a).astype(np.float32) / 255.0
    pre = Image.fromarray(
        (np.asarray(sheet_c).astype(np.float32) * a[..., None]).clip(0, 255).astype(np.uint8)
    )

    os.makedirs(out_dir, exist_ok=True)
    for size, bucket in DENSITIES:
        img = _emit(pre, sheet_a, size)
        path = os.path.join(out_dir, f"{bucket}.webp")
        img.save(path, lossless=True)
        print(f"{bucket:<8} {size}x{size}  {os.path.getsize(path):>7} bytes")

    master = os.path.join(out_dir, "ic_helmet_emblem_master.png")
    _emit(pre, sheet_a, 528).save(master)
    print(f"master   -> {master}")
    print("\nInstall with:")
    print(f"  for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do "
          f"cp {out_dir}/$d.webp app/src/main/res/drawable-$d/ic_helmet_emblem.webp; done")
    print(f"  cp {master} app/src/test/resources/brand/")


def _emit(pre, sheet_a, size):
    from PIL import Image
    import numpy as np

    p = np.asarray(pre.resize((size, size), Image.LANCZOS)).astype(np.float32)
    a = np.asarray(sheet_a.resize((size, size), Image.LANCZOS)).astype(np.float32) / 255.0
    rgb = np.where(a[..., None] > 0.004, p / np.maximum(a[..., None], 1e-6), 0).clip(0, 255)
    return Image.fromarray(
        np.dstack([rgb.astype(np.uint8), (a * 255).astype(np.uint8)]), "RGBA"
    )


def _keep_largest_component(alpha, colour):
    """Drop keyed-out fragments; only the helmet itself should survive."""
    import numpy as np

    h, w = alpha.shape
    label = np.zeros((h, w), np.int32)
    sizes = {}
    current = 0
    for sy in range(h):
        for sx in range(w):
            if alpha[sy, sx] == 0 or label[sy, sx]:
                continue
            current += 1
            n = 0
            q = deque([(sy, sx)])
            label[sy, sx] = current
            while q:
                y, x = q.popleft()
                n += 1
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < h and 0 <= nx < w and alpha[ny, nx] and not label[ny, nx]:
                        label[ny, nx] = current
                        q.append((ny, nx))
            sizes[current] = n
    main = max(sizes, key=sizes.get)
    removed = sum(v for k, v in sizes.items() if k != main)
    alpha[label != main] = 0
    colour[label != main] = 0
    return alpha, colour, removed


def _fill_interior_holes(alpha, colour, src, x0, y0, x1, y1):
    """Restore helmet detail that happened to match the disc navy."""
    import numpy as np

    h, w = alpha.shape
    outside = np.zeros((h, w), bool)
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            if alpha[y, x] == 0 and not outside[y, x]:
                outside[y, x] = True
                q.append((y, x))
    for y in range(h):
        for x in (0, w - 1):
            if alpha[y, x] == 0 and not outside[y, x]:
                outside[y, x] = True
                q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and alpha[ny, nx] == 0 and not outside[ny, nx]:
                outside[ny, nx] = True
                q.append((ny, nx))
    interior = (alpha == 0) & (~outside)
    original = np.asarray(src.crop((x0, y0, x1 + 1, y1 + 1)).convert("RGB")).astype(np.uint8)
    for c in range(3):
        colour[..., c][interior] = original[..., c][interior]
    alpha[interior] = 255
    return int(interior.sum())


def check(master):
    _require_deps()
    from PIL import Image
    import numpy as np

    img = Image.open(master).convert("RGBA")
    a = np.asarray(img)[..., 3]
    rows = np.where(a.any(1))[0]
    cols = np.where(a.any(0))[0]
    if len(rows) == 0:
        sys.exit("FAIL: asset has no visible pixels")
    w = cols[-1] - cols[0] + 1
    h = rows[-1] - rows[0] + 1
    aspect = h / w
    off_x = abs((cols[0] + w / 2) - img.width / 2) / img.width
    off_y = abs((rows[0] + h / 2) - img.height / 2) / img.height
    border = max(a[0].max(), a[-1].max(), a[:, 0].max(), a[:, -1].max())

    checks = [
        ("square canvas", img.width == img.height, f"{img.width}x{img.height}"),
        ("content aspect 1.00 +/- 0.05", 0.95 <= aspect <= 1.05, f"{w}x{h} -> {aspect:.3f}"),
        ("centred horizontally <= 3%", off_x <= 0.03, f"{off_x * 100:.1f}%"),
        ("centred vertically <= 3%", off_y <= 0.03, f"{off_y * 100:.1f}%"),
        ("transparent border", border == 0, f"max edge alpha {border}"),
        ("antialiased edge", int(((a > 0) & (a < 255)).sum()) > 0,
         f"{int(((a > 0) & (a < 255)).sum())} soft px"),
    ]
    failed = 0
    for name, ok, detail in checks:
        print(f"[{'PASS' if ok else 'FAIL'}] {name:<32} {detail}")
        failed += 0 if ok else 1
    if failed:
        sys.exit(f"\n{failed} check(s) failed")
    print("\nall brand geometry checks passed")


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="mode", required=True)
    b = sub.add_parser("build", help="regenerate density buckets from source artwork")
    b.add_argument("source")
    b.add_argument("--out", default="build/brand-asset")
    c = sub.add_parser("check", help="verify a PNG master against brand geometry rules")
    c.add_argument("master", nargs="?",
                   default="app/src/test/resources/brand/ic_helmet_emblem_master.png")
    args = ap.parse_args()
    build(args.source, args.out) if args.mode == "build" else check(args.master)


if __name__ == "__main__":
    main()
