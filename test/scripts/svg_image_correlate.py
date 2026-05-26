#!/usr/bin/env python3
"""Correlate Illustrator SVG image layers with images in our SVG output.

This is a development aid only. It does not affect conversion output.

Usage:
  python3 test/scripts/svg_image_correlate.py \
    test/output/all-the-dita/v16415663-illustrator.svg \
    test/output/all-the-dita/v16415663.svg \
    logo_Image
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
    line: int

    @property
    def mid_x(self) -> float:
        return self.x + self.width / 2.0

    @property
    def mid_y(self) -> float:
        return self.y + self.height / 2.0

    @property
    def max_x(self) -> float:
        return self.x + self.width

    @property
    def max_y(self) -> float:
        return self.y + self.height


def main() -> int:
    if len(sys.argv) not in (3, 4):
        print(__doc__.strip(), file=sys.stderr)
        return 2

    reference_svg = Path(sys.argv[1]).read_text(encoding="utf-8")
    candidate_svg_path = Path(sys.argv[2])
    candidate_svg = candidate_svg_path.read_text(encoding="utf-8")
    target_id = sys.argv[3] if len(sys.argv) == 4 else None

    reference_images = parse_reference_images(reference_svg)
    candidate_images = parse_output_images(candidate_svg)

    if not candidate_images:
        print("No <image> elements found in candidate SVG.", file=sys.stderr)
        return 1

    targets = [box for box in reference_images if box.label == target_id] if target_id else reference_images
    if target_id and not targets:
        print(f'No Illustrator image layer found for id "{target_id}".', file=sys.stderr)
        return 1

    for reference in targets:
        print_reference(reference)
        for rank, (score, candidate) in enumerate(best_matches(reference, candidate_images), start=1):
            print_match(rank, score, candidate, candidate_svg_path)
        print()
    return 0


def parse_reference_images(svg: str) -> list[ImageBox]:
    boxes: list[ImageBox] = []
    for match in re.finditer(r'<g\s+id="([^"]+)"[^>]*>', svg):
        group_id = match.group(1)
        if group_id.startswith("clippath") or group_id.startswith("Unnamed_Pattern"):
            continue
        chunk = group_chunk(svg, match.start())
        if chunk is None:
            continue
        image = re.search(r"<image\s+([^>]+)/?>", chunk, flags=re.DOTALL)
        if image is None:
            continue
        attrs = image.group(1)
        width = attr(attrs, "width")
        height = attr(attrs, "height")
        transform = str_attr(attrs, "transform")
        if width is None or height is None or transform is None:
            continue
        tx, ty, sx, sy = parse_transform(transform)
        boxes.append(ImageBox(
            group_id,
            tx,
            ty,
            abs(width * sx),
            abs(height * sy),
            svg[:match.start()].count("\n") + 1,
        ))
    return boxes


def parse_output_images(svg: str) -> list[ImageBox]:
    boxes: list[ImageBox] = []
    for index, match in enumerate(re.finditer(r"<image\s+([^>]+)/?>", svg, flags=re.DOTALL), start=1):
        attrs = match.group(1)
        x = attr(attrs, "x")
        y = attr(attrs, "y")
        width = attr(attrs, "width")
        height = attr(attrs, "height")
        if x is None or y is None or width is None or height is None:
            continue
        element_id = str_attr(attrs, "id")
        label = f'id="{element_id}"' if element_id is not None else f"image#{index:03d}"
        boxes.append(ImageBox(
            label,
            x,
            y,
            width,
            height,
            svg[:match.start()].count("\n") + 1,
        ))
    return boxes


def best_matches(reference: ImageBox, candidates: list[ImageBox], limit: int = 10) -> list[tuple[float, ImageBox]]:
    ranked = [(match_score(reference, candidate), candidate) for candidate in candidates]
    return sorted(ranked, key=lambda item: item[0])[:limit]


def match_score(reference: ImageBox, candidate: ImageBox) -> float:
    center_distance = math.hypot(reference.mid_x - candidate.mid_x, reference.mid_y - candidate.mid_y)
    size_delta = abs(reference.width - candidate.width) + abs(reference.height - candidate.height)
    return center_distance + size_delta * 0.5


def print_reference(reference: ImageBox) -> None:
    print(
        f"{reference.label}: "
        f"x={reference.x:.4f} y={reference.y:.4f} "
        f"w={reference.width:.4f} h={reference.height:.4f} "
        f"mid=({reference.mid_x:.4f},{reference.mid_y:.4f}) "
        f"max=({reference.max_x:.4f},{reference.max_y:.4f})"
    )


def print_match(rank: int, score: float, candidate: ImageBox, svg_path: Path) -> None:
    print(
        f"  {rank:2d}. {candidate.label} score={score:.3f} "
        f"{svg_path}:{candidate.line} "
        f"x={candidate.x:.4f} y={candidate.y:.4f} "
        f"w={candidate.width:.4f} h={candidate.height:.4f} "
        f"mid=({candidate.mid_x:.4f},{candidate.mid_y:.4f}) "
        f"max=({candidate.max_x:.4f},{candidate.max_y:.4f})"
    )


def group_chunk(svg: str, open_index: int) -> str | None:
    depth = 0
    i = open_index
    while i < len(svg) - 3:
        if svg.startswith("<g", i) and (i + 2 >= len(svg) or not svg[i + 2].isalnum()):
            depth += 1
            i += 2
            continue
        if svg.startswith("</g>", i):
            depth -= 1
            i += 4
            if depth == 0:
                return svg[open_index:i]
            continue
        i += 1
    return None


def parse_transform(transform: str) -> tuple[float, float, float, float]:
    tx = ty = 0.0
    sx = sy = 1.0
    for token in re.finditer(r"(translate|scale)\(([^)]*)\)", transform):
        values = [float(value) for value in re.split(r"[ ,]+", token.group(2).strip()) if value]
        if not values:
            continue
        if token.group(1) == "translate":
            tx = values[0]
            ty = values[1] if len(values) > 1 else 0.0
        else:
            sx = values[0]
            sy = values[1] if len(values) > 1 else sx
    return tx, ty, sx, sy


def attr(attrs: str, name: str) -> float | None:
    value = str_attr(attrs, name)
    return float(value) if value is not None else None


def str_attr(attrs: str, name: str) -> str | None:
    match = re.search(name + r'="([^"]+)"', attrs)
    return match.group(1) if match else None


if __name__ == "__main__":
    raise SystemExit(main())
