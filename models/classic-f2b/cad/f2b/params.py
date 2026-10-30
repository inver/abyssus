"""Named, bounded parameters of the parametric Classic F2B.

Every shape constant that is not a datum is a Gene: a name, a group (the part it
shapes), a unit, a default (the hand-tuned Blender value), bounds and a description.
Datum dimensions that define the registered photo cameras and the blueprint's
explicit dimensions are constants in `geometry.py`, not genes.
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np


@dataclass(frozen=True)
class Gene:
    name: str
    group: str
    unit: str
    default: float
    low: float
    high: float
    description: str


# Control stations along the fuselage for the three smooth scale/offset profiles.
FUSELAGE_CONTROL_X = (0.0, 200.0, 400.0, 600.0, 800.0, 1065.0)


def _fuselage_genes() -> list[Gene]:
    genes: list[Gene] = []
    for x in FUSELAGE_CONTROL_X:
        label = f"{int(x)}"
        genes.append(Gene(f"fuselage_width_scale_{label}", "fuselage", "ratio", 1.0, .85, 1.20,
                          f"Half-width scale at x={label} mm, smoothly interpolated along the body"))
    for x in FUSELAGE_CONTROL_X:
        label = f"{int(x)}"
        genes.append(Gene(f"fuselage_depth_scale_{label}", "fuselage", "ratio", 1.0, .85, 1.20,
                          f"Half-depth scale at x={label} mm"))
    for x in FUSELAGE_CONTROL_X:
        label = f"{int(x)}"
        genes.append(Gene(f"fuselage_dz_{label}", "fuselage", "mm", 0.0, -12.0, 12.0,
                          f"Vertical offset of the cross-section centre at x={label} mm"))
    return genes


GENES: tuple[Gene, ...] = tuple(
    _fuselage_genes() + [
        Gene("fin_le_dx", "fin", "mm", 0.0, -15.0, 20.0, "Shift of the swept leading edge's base along x"),
        Gene("fin_crown_dx", "fin", "mm", 0.0, -25.0, 25.0, "Shift of the crown (front corner and top) along x"),
        Gene("fin_aft_dx", "fin", "mm", 0.0, -15.0, 15.0, "Shift of the aft top corner along x"),
        Gene("fin_te_dx", "fin", "mm", 0.0, -12.0, 12.0, "Shift of the trailing edge's lower end along x"),
        Gene("fin_thickness", "fin", "mm", 7.0, 5.0, 10.0, "Fin thickness"),
        Gene("canopy_rise", "canopy", "mm", 65.0, 55.0, 80.0, "Canopy crest height above the 53 mm fuselage deck"),
        Gene("canopy_crest", "canopy", "ratio", .42, .33, .55, "Crest position along the canopy, 0 at the nose end"),
        Gene("canopy_half_width", "canopy", "mm", 32.0, 26.0, 40.0, "Maximum half-width of the glazing"),
        Gene("canopy_sill_dip", "canopy", "mm", 16.0, 6.0, 24.0, "How far the sill dips below the deck at mid length"),
        Gene("canopy_width_exponent", "canopy", "ratio", .65, .45, .95, "Plan-view fullness of the glazing ends"),
        Gene("canopy_fullness", "canopy", "ratio", 1.0, .85, 1.15, "Scale of the canopy's mid-height profile"),
        Gene("spat_x_nose", "gear", "mm", 170.0, 150.0, 200.0, "Spat nose station"),
        Gene("spat_length", "gear", "mm", 160.0, 130.0, 190.0, "Spat length"),
        Gene("spat_half_width", "gear", "mm", 19.0, 14.0, 25.0, "Spat half-width"),
        Gene("spat_half_height", "gear", "mm", 24.0, 18.0, 30.0, "Spat half-height"),
        Gene("gear_axle_x", "gear", "mm", 240.0, 215.0, 265.0, "Wheel axle station"),
        Gene("gear_axle_y", "gear", "mm", 217.0, 190.0, 245.0, "Wheel axle distance from the centreline (half track)"),
        Gene("gear_axle_z", "gear", "mm", -150.0, -165.0, -135.0, "Wheel axle height"),
        Gene("gear_leg_top_x", "gear", "mm", 260.0, 240.0, 290.0, "Leg exit station on the wing underside"),
        Gene("gear_leg_top_y", "gear", "mm", 168.0, 150.0, 190.0, "Leg exit distance from the centreline"),
        Gene("wing_z", "wing", "mm", 0.0, -10.0, 10.0, "Vertical position of the wing section centre"),
        Gene("wing_tip_sweep_94", "wing", "mm", 33.0, 20.0, 45.0, "Leading-edge setback at 94% of the rounded tip"),
        Gene("wing_tip_sweep_100", "wing", "mm", 65.0, 50.0, 80.0, "Leading-edge setback at the very tip"),
        Gene("wing_tip_te_94", "wing", "mm", 218.0, 195.0, 250.0, "Trailing-edge station at 94% of the tip"),
        Gene("wing_tip_te_100", "wing", "mm", 118.0, 90.0, 160.0, "Trailing-edge station at the very tip"),
        Gene("wing_tip_thickness_94", "wing", "mm", 25.0, 16.0, 36.0, "Thickness at 94% of the tip"),
        Gene("wing_tip_thickness_100", "wing", "mm", 8.0, 4.0, 14.0, "Thickness at the very tip"),
        Gene("stab_z", "stabilizer", "mm", 5.0, -6.0, 14.0, "Vertical position of the stabilizer"),
        Gene("stab_root_thickness", "stabilizer", "mm", 18.0, 14.0, 22.0, "Stabilizer root thickness"),
        Gene("stab_tip_thickness", "stabilizer", "mm", 12.0, 8.0, 16.0, "Stabilizer thickness before tip rounding"),
        Gene("stab_tip_inset", "stabilizer", "mm", 10.0, 4.0, 18.0, "Corner rounding of the stabilizer tip"),
        Gene("engine_cylinder_x", "engine", "mm", 76.0, 60.0, 95.0, "Cylinder station"),
        Gene("engine_cylinder_reach", "engine", "mm", 76.0, 62.0, 95.0, "Distance from the centreline to the cylinder base end"),
        Gene("silencer_y", "engine", "mm", 53.0, 40.0, 66.0, "Silencer distance from the centreline"),
        Gene("silencer_z", "engine", "mm", -9.0, -22.0, 2.0, "Silencer height"),
        Gene("silencer_x_front", "engine", "mm", 114.0, 98.0, 132.0, "Silencer front station"),
        Gene("silencer_x_rear", "engine", "mm", 238.0, 210.0, 265.0, "Silencer rear station"),
    ])

NAMES = tuple(g.name for g in GENES)
_BY_NAME = {g.name: g for g in GENES}
GROUPS = tuple(dict.fromkeys(g.group for g in GENES))


class ParameterError(ValueError):
    """A parameter name or value is not part of the bounded set."""


class Params:
    """An immutable-by-convention mapping of every gene name to a validated value."""

    def __init__(self, values: dict[str, float] | None = None):
        values = dict(values or {})
        unknown = sorted(set(values) - set(_BY_NAME))
        if unknown:
            raise ParameterError(f"unknown parameter {unknown[0]!r}")
        merged = {g.name: float(values.get(g.name, g.default)) for g in GENES}
        for g in GENES:
            v = merged[g.name]
            if not (g.low <= v <= g.high) or v != v:
                raise ParameterError(f"parameter {g.name!r} = {v} is outside [{g.low}, {g.high}]")
        self._values = merged

    def __getitem__(self, name: str) -> float:
        return self._values[name]

    def as_dict(self) -> dict[str, float]:
        return dict(self._values)

    def __eq__(self, other: object) -> bool:
        return isinstance(other, Params) and self._values == other._values

    def __hash__(self) -> int:
        return hash(tuple(self._values.values()))

    # Normalised vector in [0, 1]^n, in GENES order, used by the genetic algorithm.
    def to_vector(self) -> np.ndarray:
        return np.array([(self._values[g.name] - g.low) / (g.high - g.low) for g in GENES])

    @classmethod
    def from_vector(cls, vector) -> "Params":
        vector = np.asarray(vector, dtype=float)
        if vector.shape != (len(GENES),):
            raise ParameterError(f"expected {len(GENES)} genes, got shape {vector.shape}")
        clipped = np.clip(vector, 0.0, 1.0)
        return cls({g.name: g.low + float(t) * (g.high - g.low) for g, t in zip(GENES, clipped)})

    def save(self, path: str | Path) -> None:
        Path(path).write_text(json.dumps(self._values, indent=2) + "\n")

    @classmethod
    def load(cls, path: str | Path) -> "Params":
        return cls(json.loads(Path(path).read_text()))


def defaults() -> Params:
    return Params()
