"""STEP and STL export of the parametric model."""
from __future__ import annotations

from pathlib import Path

import cadquery as cq
import numpy as np

from .geometry import Part, build
from .params import Params


def assembly(parts: list[Part], name: str = "Classic_F2B") -> cq.Assembly:
    """One named, coloured child per part."""
    root = cq.Assembly(name=name)
    for part in parts:
        root.add(part.shape, name=part.name, color=cq.Color(*part.color))
    return root


def export(parts: list[Part], out: str | Path, name: str = "Classic_F2B") -> dict[str, Path]:
    """Write `<name>.step` (named assembly) and `stl/<part>.stl` (millimetres) into `out`."""
    out = Path(out)
    (out / "stl").mkdir(parents=True, exist_ok=True)
    step = out / f"{name}.step"
    assembly(parts, name).export(str(step), "STEP")
    written = {"step": step}
    for part in parts:
        path = out / "stl" / f"{part.name}.stl"
        cq.exporters.export(cq.Workplane("XY").add(part.shape), str(path), tolerance=0.2, angularTolerance=0.2)
        written[part.name] = path
    return written


def shape_bounds(shape: cq.Shape, tolerance: float = 0.05) -> tuple[float, float, float, float, float, float]:
    """Tight bounds from the tessellation (OCC's own box is loose for splines and ellipsoids)."""
    vertices, _ = shape.tessellate(tolerance, 0.1)
    points = np.array([(v.x, v.y, v.z) for v in vertices])
    low, high = points.min(axis=0), points.max(axis=0)
    return low[0], high[0], low[1], high[1], low[2], high[2]


def assembly_bounds(parts: list[Part]) -> tuple[float, float, float, float, float, float]:
    boxes = np.array([shape_bounds(part.shape) for part in parts])
    return (boxes[:, 0].min(), boxes[:, 1].max(), boxes[:, 2].min(), boxes[:, 3].max(),
            boxes[:, 4].min(), boxes[:, 5].max())


def build_main(argv: list[str] | None = None) -> None:
    import argparse
    parser = argparse.ArgumentParser(description="Build the parametric Classic F2B and export STEP + STL")
    parser.add_argument("--params", help="parameter JSON (default: the defaults)")
    parser.add_argument("--out", default="out/default", help="output folder")
    parser.add_argument("--no-detail", action="store_true", help="skip cockpit, engine fins and small hardware")
    args = parser.parse_args(argv)
    params = Params.load(args.params) if args.params else Params()
    parts = build(params, detail=not args.no_detail)
    written = export(parts, args.out)
    print(f"wrote {written['step']} and {len(parts)} STL files")
