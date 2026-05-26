#!/usr/bin/env python3
"""Correlate Inkscape plain SVG images with our converter output.

Inkscape exports one <image> per AGM tile (like our pipeline), unlike Illustrator's
merged layer images. Coordinates are normalized from Inkscape viewBox to candidate
viewBox before matching.

Usage:
  python3 test/scripts/svg_image_inkscape_correlate.py \\
    test/references/v16415663-inkscape_plain.eps.svg \\
    test/output/all-the-dita/v16415663.svg

  python3 test/scripts/svg_image_inkscape_correlate.py REF CAND --tolerance 0.02
  python3 test/scripts/svg_image_inkscape_correlate.py REF CAND --show-good 20
"""

from __future__ import annotations

import argparse
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
    pixel_w: float
    pixel_h: float
    line: int

    @property
    def mid_x(self) -> float:
        return self.x + self.width / 2.0

    @property
    def mid_y(self) -> float:
        return self.y + self.height / 2.0

    @property
    def area(self) -> float:
        return self.width * self.height


@dataclass(frozen=True)
class ViewBox:
    width: float
    height: float

    @staticmethod
    def from_svg(svg: str) -> ViewBox:
        match = re.search(r'viewBox="([^"]+)"', svg)
        if not match:
            return ViewBox(195.0, 191.0)
        parts = [float(value) for value in match.group(1).split()]
        return ViewBox(parts[2], parts[3])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference_svg", type=Path, help="Inkscape plain SVG export")
    parser.add_argument("candidate_svg", type=Path, help="Our SVG output")
    parser.add_argument(
        "--tolerance",
        type=float,
        default=0.02,
        help="Relative position/size tolerance (default 2%%)",
    )
    parser.add_argument(
        "--show-good",
        type=int,
        default=0,
        help="Print this many best matches within tolerance",
    )
    parser.add_argument(
        "--show-bad",
        type=int,
        default=50,
        help="Max mismatch lines to print (default 50)",
    )
    parser.add_argument(
        "--reciprocal-only",
        action="store_true",
        help="Only pair tiles that are each other's best match",
    )
    args = parser.parse_args()

    reference_svg = args.reference_svg.read_text(encoding="utf-8")
    candidate_svg = args.candidate_svg.read_text(encoding="utf-8")
    ref_vb = ViewBox.from_svg(reference_svg)
    cand_vb = ViewBox.from_svg(candidate_svg)
    scale_x = cand_vb.width / ref_vb.width
    scale_y = cand_vb.height / ref_vb.height

    reference = parse_images(reference_svg, "ink#")
    candidate = parse_images(candidate_svg, 'id="')
    if not reference:
        print("No reference images found.", file=sys.stderr)
        return 1
    if not candidate:
        print("No candidate images found.", file=sys.stderr)
        return 1

    reference = normalize_boxes(reference, scale_x, scale_y)
    print(
        f"viewBox ref={ref_vb.width:.4f}x{ref_vb.height:.4f} "
        f"cand={cand_vb.width:.4f}x{cand_vb.height:.4f} "
        f"scale=({scale_x:.4f},{scale_y:.4f})"
    )
    print(f"images ref={len(reference)} cand={len(candidate)}")

    used: set[str] = set()
    good: list[tuple[float, ImageBox, ImageBox]] = []
    bad: list[tuple[float, ImageBox, ImageBox, float, float]] = []
    orphan_ref: list[ImageBox] = []
    wrong_pair: list[tuple[float, ImageBox, ImageBox]] = []

    for ref in reference:
        score, best = best_match(ref, candidate, used)
        if best is None:
            orphan_ref.append(ref)
            continue
        reciprocal = False
        if args.reciprocal_only:
            back_score, back = best_match(best, reference, set())
            reciprocal = back is not None and back.label == ref.label
            if not reciprocal:
                wrong_pair.append((score, ref, best))
                continue
        used.add(best.label)
        dx = abs(ref.x - best.x)
        dy = abs(ref.y - best.y)
        dw = abs(ref.width - best.width)
        dh = abs(ref.height - best.height)
        ref_scale = max(ref.width, ref.height, 1.0)
        ok = (
            dx <= args.tolerance * ref_scale
            and dy <= args.tolerance * ref_scale
            and dw <= args.tolerance * ref_scale
            and dh <= args.tolerance * ref_scale
        )
        if ok:
            good.append((score, ref, best))
        else:
            bad.append((score, ref, best, dx, dy))

    orphan_cand = [box for box in candidate if box.label not in used]

    if args.show_good:
        print(f"\nGood matches (showing up to {args.show_good}):")
        for score, ref, cand in sorted(good, key=lambda item: item[0])[: args.show_good]:
            print_match("OK", score, ref, cand, args.candidate_svg)

    if bad:
        print(f"\nMismatches (showing up to {args.show_bad}):")
        for score, ref, cand, dx, dy in sorted(bad, key=lambda item: item[0])[: args.show_bad]:
            print(
                f"MISMATCH {ref.label} -> {cand.label} score={score:.3f} "
                f"ref=({ref.x:.3f},{ref.y:.3f}) {ref.width:.2f}x{ref.height:.2f} "
                f"ours=({cand.x:.3f},{cand.y:.3f}) {cand.width:.2f}x{cand.height:.2f} "
                f"delta=({dx:.3f},{dy:.3f}) {args.candidate_svg}:{cand.line}"
            )

    if orphan_ref:
        print(f"\nInkscape tiles with no candidate match ({len(orphan_ref)}):")
        for ref in sorted(orphan_ref, key=lambda box: -box.area)[:15]:
            print(
                f"  {ref.label} ({ref.x:.3f},{ref.y:.3f}) {ref.width:.2f}x{ref.height:.2f} "
                f"px={ref.pixel_w:.0f}x{ref.pixel_h:.0f}"
            )

    if orphan_cand:
        print(f"\nOur tiles with no Inkscape match ({len(orphan_cand)}):")
        for cand in sorted(orphan_cand, key=lambda box: -box.area)[:15]:
            print(
                f"  {cand.label} ({cand.x:.3f},{cand.y:.3f}) {cand.width:.2f}x{cand.height:.2f} "
                f"px={cand.pixel_w:.0f}x{cand.pixel_h:.0f} line={cand.line}"
            )

    if wrong_pair and args.reciprocal_only:
        print(f"\nAmbiguous pairings (not reciprocal best, showing up to 15):")
        for score, ref, cand in sorted(wrong_pair, key=lambda item: item[0])[:15]:
            print(
                f"  {ref.label} ~> {cand.label} score={score:.3f} "
                f"ref=({ref.x:.1f},{ref.y:.1f}) {ref.width:.1f}x{ref.height:.1f}"
            )

    print(
        f"\nSummary: good={len(good)} bad={len(bad)} "
        f"orphan_ref={len(orphan_ref)} orphan_cand={len(orphan_cand)} "
        f"wrong_pair={len(wrong_pair)} tolerance={args.tolerance * 100:.1f}%"
    )
    return 1 if bad or orphan_ref else 0


def normalize_boxes(boxes: list[ImageBox], scale_x: float, scale_y: float) -> list[ImageBox]:
    normalized: list[ImageBox] = []
    for box in boxes:
        normalized.append(ImageBox(
            box.label,
            box.x * scale_x,
            box.y * scale_y,
            box.width * scale_x,
            box.height * scale_y,
            box.pixel_w,
            box.pixel_h,
            box.line,
        ))
    return normalized


def parse_images(svg: str, label_prefix: str) -> list[ImageBox]:
    boxes: list[ImageBox] = []
    stack: list[list[str]] = []
    index = 0
    for match in re.finditer(r"<(g|image)\s+([^>]*?)(?:/>|>)|</g>", svg, flags=re.DOTALL):
        if match.group(0) == "</g>":
            if stack:
                stack.pop()
            continue
        tag = match.group(1)
        attrs = match.group(2) or ""
        if tag == "g":
            transform = str_attr(attrs, "transform")
            stack.append(parse_transforms(transform) if transform else [])
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
        local_x = attr(attrs, "x") or 0.0
        local_y = attr(attrs, "y") or 0.0
        x, y, w, h = box_from_transform(chain, local_x, local_y, width, height)
        index += 1
        element_id = str_attr(attrs, "id")
        if label_prefix == 'id="':
            label = f'id="{element_id or index}"'
        else:
            label = f"{label_prefix}{index:03d}"
        boxes.append(ImageBox(
            label,
            x,
            y,
            w,
            h,
            width,
            height,
            svg[: match.start()].count("\n") + 1,
        ))
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
    size_delta = (
        abs(reference.width - candidate.width) / max(reference.width, 1.0)
        + abs(reference.height - candidate.height) / max(reference.height, 1.0)
    )
    pixel_delta = 0.0
    if reference.pixel_w > 1 and candidate.pixel_w > 1:
        pixel_delta += abs(reference.pixel_w - candidate.pixel_w) / reference.pixel_w
    if reference.pixel_h > 1 and candidate.pixel_h > 1:
        pixel_delta += abs(reference.pixel_h - candidate.pixel_h) / reference.pixel_h
    return center_distance + size_delta * 8.0 + pixel_delta * 20.0


def print_match(status: str, score: float, ref: ImageBox, cand: ImageBox, svg_path: Path) -> None:
    print(
        f"{status} {ref.label} -> {cand.label} score={score:.3f} "
        f"ref=({ref.x:.3f},{ref.y:.3f}) {ref.width:.2f}x{ref.height:.2f} "
        f"ours=({cand.x:.3f},{cand.y:.3f}) {cand.width:.2f}x{cand.height:.2f} "
        f"{svg_path}:{cand.line}"
    )


def parse_transforms(transform: str) -> list[str]:
    return re.findall(r"(?:translate|scale|matrix)\([^)]*\)", transform)


def parse_matrix_vals(token: str) -> list[float]:
    inner = token[token.find("(") + 1 : token.rfind(")")]
    return [float(value) for value in re.split(r"[\s,]+", inner.strip()) if value]


def concat_transforms(chain: list[str]) -> tuple[float, float, float, float, float, float]:
    a, b, c, d, e, f = 1.0, 0.0, 0.0, 1.0, 0.0, 0.0
    for token in chain:
        if token.startswith("matrix"):
            ma, mb, mc, md, me, mf = parse_matrix_vals(token)
            na = a * ma + c * mb
            nb = b * ma + d * mb
            nc = a * mc + c * md
            nd = b * mc + d * md
            ne = a * me + c * mf + e
            nf = b * me + d * mf + f
            a, b, c, d, e, f = na, nb, nc, nd, ne, nf
        elif token.startswith("translate"):
            inner = token[token.find("(") + 1 : token.rfind(")")]
            values = [float(value) for value in re.split(r"[\s,]+", inner.strip()) if value]
            tx = values[0]
            ty = values[1] if len(values) > 1 else 0.0
            e += a * tx + c * ty
            f += b * tx + d * ty
        elif token.startswith("scale"):
            inner = token[token.find("(") + 1 : token.rfind(")")]
            values = [float(value) for value in re.split(r"[\s,]+", inner.strip()) if value]
            sx = values[0]
            sy = values[1] if len(values) > 1 else sx
            a *= sx
            c *= sy
            b *= sx
            d *= sy
    return a, b, c, d, e, f


def box_from_transform(
    chain: list[str],
    x: float,
    y: float,
    width: float,
    height: float,
) -> tuple[float, float, float, float]:
    a, b, c, d, e, f = concat_transforms(chain)
    corners = (
        (x, y),
        (x + width, y),
        (x, y + height),
        (x + width, y + height),
    )
    xs: list[float] = []
    ys: list[float] = []
    for px, py in corners:
        xs.append(a * px + c * py + e)
        ys.append(b * px + d * py + f)
    min_x = min(xs)
    max_x = max(xs)
    min_y = min(ys)
    max_y = max(ys)
    return min_x, min_y, max_x - min_x, max_y - min_y


def attr(attrs: str, name: str) -> float | None:
    value = str_attr(attrs, name)
    return float(value) if value is not None else None


def str_attr(attrs: str, name: str) -> str | None:
    match = re.search(name + r'="([^"]+)"', attrs)
    return match.group(1) if match else None


if __name__ == "__main__":
    raise SystemExit(main())
