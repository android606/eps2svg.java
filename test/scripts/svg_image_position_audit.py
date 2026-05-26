#!/usr/bin/env python3
"""Compare effective <image> positions in our SVG vs Illustrator reference.

Usage:
  python3 test/scripts/svg_image_position_audit.py \\
    test/output/all-the-dita/v16415663-illustrator.svg \\
    test/output/all-the-dita/v16415663.svg
"""

from __future__ import annotations

import math
import re
import sys
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class ImageBox:
    label: str
    x: float
    y: float
    width: float
    height: float

    @property
    def mid_x(self) -> float:
        return self.x + self.width / 2.0

    @property
    def mid_y(self) -> float:
        return self.y + self.height / 2.0


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__.strip(), file=sys.stderr)
        return 2

    reference = parse_images(Path(sys.argv[1]).read_text(encoding="utf-8"), reference=True)
    candidate = parse_images(Path(sys.argv[2]).read_text(encoding="utf-8"), reference=False)
    if not reference:
        print("No reference images found.", file=sys.stderr)
        return 1
    if not candidate:
        print("No candidate images found.", file=sys.stderr)
        return 1

    tolerance = 0.01
    mismatches = 0
    matched = 0
    used: set[str] = set()
    for ref in reference:
        score, best = best_match(ref, candidate, used)
        if best is None:
            print(f"NO_MATCH {ref.label} ref=({ref.x:.3f},{ref.y:.3f}) {ref.width:.2f}x{ref.height:.2f}")
            mismatches += 1
            continue
        used.add(best.label)
        dx = abs(ref.x - best.x)
        dy = abs(ref.y - best.y)
        ref_scale = max(ref.width, ref.height, 1.0)
        bad_x = dx > tolerance * ref_scale
        bad_y = dy > tolerance * ref_scale
        matched += 1
        if bad_x or bad_y:
            mismatches += 1
            print(
                f"MISMATCH {ref.label} -> {best.label} score={score:.3f} "
                f"ref=({ref.x:.3f},{ref.y:.3f}) ours=({best.x:.3f},{best.y:.3f}) "
                f"delta=({dx:.3f},{dy:.3f}) size={ref.width:.2f}x{ref.height:.2f}"
            )

    print(
        f"\nSummary: reference={len(reference)} candidate={len(candidate)} "
        f"matched={matched} mismatches={mismatches} tolerance={tolerance * 100:.1f}%"
    )
    return 1 if mismatches else 0


def parse_images(svg: str, reference: bool) -> list[ImageBox]:
    body_start = svg.find("</defs>")
    body = svg[body_start:] if body_start >= 0 else svg
    boxes: list[ImageBox] = []
    stack: list[list[str]] = []
    group_ids: list[str | None] = []

    for match in re.finditer(r"<(g|image)\s+([^>]*?)(?:/>|>)|</g>", body, flags=re.DOTALL):
        if match.group(0) == "</g>":
            if stack:
                stack.pop()
                group_ids.pop()
            continue

        tag = match.group(1)
        attrs = match.group(2) or ""
        if tag == "g":
            transform = str_attr(attrs, "transform")
            stack.append(parse_transforms(transform) if transform else [])
            group_ids.append(str_attr(attrs, "id"))
            continue

        width = attr(attrs, "width")
        height = attr(attrs, "height")
        if width is None or height is None:
            continue

        chain: list[str] = []
        for frame in stack:
            chain.extend(frame)
        local = str_attr(attrs, "transform")
        if local:
            chain.extend(parse_transforms(local))

        matrix = concat_transforms(chain)
        x = attr(attrs, "x") or 0.0
        y = attr(attrs, "y") or 0.0
        bx, by, bw, bh = box_from_image(matrix, x, y, width, height)

        if reference:
            label = group_ids[-1] if group_ids else None
            if not label or label.startswith("clippath") or label.startswith("Unnamed_Pattern"):
                label = str_attr(attrs, "id") or "image"
        else:
            label = f'id="{str_attr(attrs, "id") or "?"}"'
        boxes.append(ImageBox(label, bx, by, bw, bh))
    return boxes


def best_match(
    reference: ImageBox,
    candidates: list[ImageBox],
    used: set[str],
) -> tuple[float, ImageBox | None]:
    available = [candidate for candidate in candidates if candidate.label not in used]
    if not available:
        return 0.0, None
    ranked = sorted(
        ((match_score(reference, candidate), candidate) for candidate in available),
        key=lambda item: item[0],
    )
    return ranked[0]


def match_score(reference: ImageBox, candidate: ImageBox) -> float:
    center_distance = math.hypot(reference.mid_x - candidate.mid_x, reference.mid_y - candidate.mid_y)
    size_delta = abs(reference.width - candidate.width) + abs(reference.height - candidate.height)
    return center_distance + size_delta * 0.5


def parse_transforms(transform: str) -> list[str]:
    return re.findall(r"(?:translate|scale|matrix)\([^)]*\)", transform)


def concat_transforms(chain: list[str]) -> tuple[float, float, float, float, float, float]:
    a, b, c, d, e, f = 1.0, 0.0, 0.0, 1.0, 0.0, 0.0
    for token in chain:
        inner = token[token.find("(") + 1 : token.rfind(")")]
        if token.startswith("translate"):
            values = [float(value) for value in re.split(r"[ ,]+", inner.strip()) if value]
            tx = values[0]
            ty = values[1] if len(values) > 1 else 0.0
            e += a * tx + c * ty
            f += b * tx + d * ty
        elif token.startswith("scale"):
            values = [float(value) for value in re.split(r"[ ,]+", inner.strip()) if value]
            sx = values[0]
            sy = values[1] if len(values) > 1 else sx
            a *= sx
            c *= sy
            b *= sx
            d *= sy
        elif token.startswith("matrix"):
            values = [float(value) for value in re.split(r"[ ,]+", inner.strip()) if value]
            ma, mb, mc, md, me, mf = values
            na = a * ma + c * mb
            nb = b * ma + d * mb
            nc = a * mc + c * md
            nd = b * mc + d * md
            ne = a * me + c * mf + e
            nf = b * me + d * mf + f
            a, b, c, d, e, f = na, nb, nc, nd, ne, nf
    return a, b, c, d, e, f


def box_from_image(
    matrix: tuple[float, float, float, float, float, float],
    x: float,
    y: float,
    width: float,
    height: float,
) -> tuple[float, float, float, float]:
    a, b, c, d, e, f = matrix
    xs: list[float] = []
    ys: list[float] = []
    for px, py in ((x, y), (x + width, y), (x, y + height), (x + width, y + height)):
        xs.append(a * px + c * py + e)
        ys.append(b * px + d * py + f)
    return min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys)


def attr(attrs: str, name: str) -> float | None:
    value = str_attr(attrs, name)
    return float(value) if value is not None else None


def str_attr(attrs: str, name: str) -> str | None:
    match = re.search(name + r'="([^"]+)"', attrs)
    return match.group(1) if match else None


if __name__ == "__main__":
    raise SystemExit(main())
