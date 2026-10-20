"""Parametric CadQuery model of the Classic F2B.

Coordinates are millimetres in the Blender model's final frame: x aft from the nose,
y positive on the engine side (the 740 mm semispan), z up. `build(params)` is a pure
function of the parameters; datum dimensions below are constants, not genes.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

import cadquery as cq
import numpy as np
from scipy.interpolate import PchipInterpolator

from .params import FUSELAGE_CONTROL_X, Params

V = cq.Vector

# Datum dimensions: they define the registered photo cameras and the blueprint's explicit dimensions.
LONG_SEMISPAN, SHORT_SEMISPAN = 740.0, 710.0
WING_ROOT_CHORD, WING_TIP_CHORD, WING_LE, WING_SWEEP = 340.0, 275.0, 250.0, 32.0
WING_ROOT_THICKNESS, WING_OUTER_THICKNESS, WING_ROUND_LENGTH = 62.0, 52.0, 80.0
STAB_SEMISPAN, STAB_ROOT_CHORD, STAB_TIP_CHORD, STAB_LE, STAB_SWEEP = 345.0, 161.0, 115.0, 895.0, 26.0
STAB_ROUND_LENGTH = 30.0
CANOPY_X0, CANOPY_LENGTH, DECK_Z = 210.0, 240.0, 53.0
FIN_HEIGHT, AFT_DECK_Z = 125.0, 48.0
PROPELLER_DIAMETER = 340.0
ENGINE_YAW_DEG = 3.0

# Default fuselage cross-sections: x, half-width, half-depth, centre height.
FUSELAGE_STATIONS = [(0, 27, 29, 10), (30, 33, 41, 7), (95, 37.5, 55, -2), (210, 37.5, 59, -6), (250, 37.5, 59, -6),
                     (300, 36, 59, -6), (470, 29, 58, -6), (600, 23, 55, -4), (760, 17, 51, -2), (895, 12, 47.5, .5),
                     (1000, 8, 47.5, .5), (1045, 4, 47.5, .5), (1065, .7, 43, 1)]
PIVOT = V(-4, 0, 10)


@dataclass
class Part:
    name: str
    shape: cq.Shape
    group: str
    color: tuple[float, float, float]
    airframe: bool = True   # contributes to the silhouette fitted against photographs


def _smooth(x, xs, ys):
    return float(PchipInterpolator(xs, ys)(x))


def _ellipse(x, ry, rz, zc, y=0.0) -> cq.Wire:
    ry, rz = max(ry, .2), max(rz, .2)
    if ry >= rz:
        edge = cq.Edge.makeEllipse(ry, rz, V(x, y, zc), V(1, 0, 0), V(0, 1, 0))
    else:
        edge = cq.Edge.makeEllipse(rz, ry, V(x, y, zc), V(1, 0, 0), V(0, 0, 1))
    return cq.Wire.assembleEdges([edge])


def _loft(wires, margin: float = 4.0) -> cq.Solid:
    """Smooth loft through the sections; ruled if the interpolation overshoots them.

    A smooth interpolating loft can blow up when neighbouring sections differ sharply. The
    solid must stay inside the union of the section boxes (plus a margin), otherwise the
    sections are joined linearly instead.
    """
    boxes = [w.BoundingBox() for w in wires]
    limits = [min(b.xmin for b in boxes) - margin, max(b.xmax for b in boxes) + margin,
              min(b.ymin for b in boxes) - margin, max(b.ymax for b in boxes) + margin,
              min(b.zmin for b in boxes) - margin, max(b.zmax for b in boxes) + margin]
    try:
        solid = cq.Solid.makeLoft(wires, False)
        bb = solid.BoundingBox()
        inside = (bb.xmin >= limits[0] and bb.xmax <= limits[1] and bb.ymin >= limits[2] and bb.ymax <= limits[3]
                  and bb.zmin >= limits[4] and bb.zmax <= limits[5])
        if inside and solid.isValid() and solid.Volume() > 0:
            return solid
    except Exception:
        pass
    return cq.Solid.makeLoft(wires, True)


# ---------------------------------------------------------------- fuselage
def fuselage_section(x: float, p: Params) -> tuple[float, float, float]:
    xs = [s[0] for s in FUSELAGE_STATIONS]
    ry, rz, zc = (_smooth(x, xs, [s[k] for s in FUSELAGE_STATIONS]) for k in (1, 2, 3))
    cx = list(FUSELAGE_CONTROL_X)
    width = _smooth(x, cx, [p[f"fuselage_width_scale_{int(c)}"] for c in cx])
    depth = _smooth(x, cx, [p[f"fuselage_depth_scale_{int(c)}"] for c in cx])
    dz = _smooth(x, cx, [p[f"fuselage_dz_{int(c)}"] for c in cx])
    return ry * width, rz * depth, zc + dz


def fuselage(p: Params) -> cq.Solid:
    xs = [s[0] for s in FUSELAGE_STATIONS]
    stops = sorted(set(xs + [(a + b) / 2 for a, b in zip(xs, xs[1:])]))
    return _loft([_ellipse(x, *fuselage_section(x, p)[:2], fuselage_section(x, p)[2]) for x in stops])


# ---------------------------------------------------------------- wing and stabilizer
def _foil(u: float) -> float:
    return 5 * (.2969 * math.sqrt(max(0, u)) - .126 * u - .3516 * u * u + .2843 * u ** 3 - .1036 * u ** 4)


_FOIL_PEAK = max(_foil(i / 10000) for i in range(10001))
_FOIL_U = [(1 - math.cos(math.pi * k / 24)) / 2 for k in range(25)]


def wing_section(y: float, span: float, p: Params) -> tuple[float, float, float]:
    """Start station, chord and thickness of a wing of semispan `span` at |y|."""
    outer = span - WING_ROUND_LENGTH
    if y <= outer:
        q = y / outer
        return WING_LE + WING_SWEEP * q, WING_ROOT_CHORD + (WING_TIP_CHORD - WING_ROOT_CHORD) * q, \
            WING_ROOT_THICKNESS - (WING_ROOT_THICKNESS - WING_OUTER_THICKNESS) * q
    q = min(1.0, (y - outer) / WING_ROUND_LENGTH)
    qs = [0, .5, .75, .9375, 1]
    dx = [0, 3, 8, p["wing_tip_sweep_94"], p["wing_tip_sweep_100"]]
    te = [275, 273, 268, p["wing_tip_te_94"], p["wing_tip_te_100"]]
    hh = [52, 51, 48, p["wing_tip_thickness_94"], p["wing_tip_thickness_100"]]
    d = _smooth(q, qs, dx)
    return WING_LE + WING_SWEEP + d, _smooth(q, qs, te) - d, _smooth(q, qs, hh)


def stab_section(y: float, span: float, p: Params) -> tuple[float, float, float]:
    outer = span - STAB_ROUND_LENGTH
    if y <= outer:
        q = y / outer
        return STAB_LE + STAB_SWEEP * q, STAB_ROOT_CHORD + (STAB_TIP_CHORD - STAB_ROOT_CHORD) * q, \
            p["stab_root_thickness"] + (p["stab_tip_thickness"] - p["stab_root_thickness"]) * q
    q = min(1.0, (y - outer) / STAB_ROUND_LENGTH)
    inset = p["stab_tip_inset"] * (1 - math.sqrt(max(0, 1 - q * q)))
    return STAB_LE + STAB_SWEEP + inset, STAB_TIP_CHORD - 2 * inset, p["stab_tip_thickness"] * (1 - .55 * q * q)


def _airfoil_points(y: float, start: float, chord: float, thick: float, z: float) -> tuple[list, list]:
    """Upper and lower section points, leading edge to trailing edge (they share the leading edge)."""
    upper, lower = [], []
    for u in _FOIL_U:
        half = max(.3, _foil(u) / _FOIL_PEAK * thick / 2)
        upper.append(V(start + chord * u, y, z + half))
        lower.append(V(start + chord * u, y, z - half))
    lower[0] = upper[0]
    return upper, lower


def _airfoil_wire(y: float, start: float, chord: float, thick: float, z: float) -> cq.Wire:
    upper, lower = _airfoil_points(y, start, chord, thick, z)
    # One direction around the section (LE -> upper -> TE -> lower -> LE) keeps lofted sections untwisted.
    edges = [cq.Edge.makeSpline(upper), cq.Edge.makeLine(upper[-1], lower[-1]),
             cq.Edge.makeSpline(lower[::-1])]
    return cq.Wire.assembleEdges(edges)


def _panel(side: float, span: float, section, z: float, p: Params, round_length: float) -> cq.Solid:
    outer = span - round_length
    # Ruled loft: exact for the straight taper, and robust. Smooth interpolating lofts blow up
    # across the abrupt taper-to-tip change, so the rounded tip uses 16 closely spaced sections.
    ys = [0.0] + [outer + round_length * k / 16 for k in range(17)]
    wires = [_airfoil_wire(side * y, *section(y, span, p), z) for y in ys]
    return cq.Solid.makeLoft(wires, True)


def _panel_stations(span: float, round_length: float) -> list[float]:
    outer = span - round_length
    return [0.0] + [outer + round_length * k / 16 for k in range(17)]


def panel_mesh(kind: str, long_side: bool, p: Params):
    """Triangle mesh of a wing or stabilizer half, straight from its section points.

    The panel is a ruled loft, so this is the CAD surface itself (flat between the sections),
    without the cost of tessellating it with OpenCASCADE. `kind` is "wing" or "stabilizer".
    Returns (vertices (N, 3), triangles (M, 3)).
    """
    import numpy as np
    if kind == "wing":
        span, section, z, side, round_length = (LONG_SEMISPAN if long_side else SHORT_SEMISPAN), wing_section, p["wing_z"], \
            (1.0 if long_side else -1.0), WING_ROUND_LENGTH
    else:
        span, section, z, side, round_length = STAB_SEMISPAN, stab_section, p["stab_z"], (1.0 if long_side else -1.0), STAB_ROUND_LENGTH
    rings = []
    for y in _panel_stations(span, round_length):
        upper, lower = _airfoil_points(side * y, *section(y, span, p), z)
        loop = upper + lower[:0:-1]              # LE -> TE on top, TE -> LE underneath, no repeated point
        rings.append(np.array([(v.x, v.y, v.z) for v in loop]))
    n = len(rings[0])
    vertices = np.vstack(rings)
    triangles = []
    for r in range(len(rings) - 1):
        for i in range(n):
            a, b = r * n + i, r * n + (i + 1) % n
            c, d = (r + 1) * n + (i + 1) % n, (r + 1) * n + i
            triangles += [(a, b, c), (a, c, d)]
    for r in (0, len(rings) - 1):                 # end caps as fans about the section centre
        centre = len(vertices)
        vertices = np.vstack([vertices, rings[r].mean(axis=0)])
        triangles += [(centre, r * n + i, r * n + (i + 1) % n) for i in range(n)]
    return vertices, np.array(triangles, dtype=np.int64)


def wing(long_side: bool, p: Params) -> cq.Solid:
    span = LONG_SEMISPAN if long_side else SHORT_SEMISPAN
    return _panel(1.0 if long_side else -1.0, span, wing_section, p["wing_z"], p, WING_ROUND_LENGTH)


def stabilizer(side: float, p: Params) -> cq.Solid:
    return _panel(side, STAB_SEMISPAN, stab_section, p["stab_z"], p, STAB_ROUND_LENGTH)


# ---------------------------------------------------------------- fin
def _bezier(points):
    return cq.Edge.makeBezier([V(*pt) for pt in points])


def fin(p: Params) -> cq.Solid:
    le, cr, af, te = p["fin_le_dx"], p["fin_crown_dx"], p["fin_aft_dx"], p["fin_te_dx"]
    t = p["fin_thickness"]
    segs = [
        [(865 + le, 48), (900 + le, 80), (945 + cr / 2, 125), (985 + cr, 162)],
        [(985 + cr, 162), (993 + cr, 170), (1000 + cr, 173), (1010 + cr, 173)],
        [(1010 + cr, 173), (1030 + (cr + af) / 2, 172), (1050 + af, 160), (1058 + af, 152)],
        [(1058 + af, 152), (1063 + af, 147), (1064 + af, 142), (1064 + af, 132)],
        [(1064 + af, 132), (1065 + (af + te) / 2, 80), (1066 + te, 20), (1066 + te, -32)],
        [(1066 + te, -32), (1067 + te, -43), (1060 + te, -47), (1049 + te, -47)],
    ]
    y0 = -t / 2
    edges = [cq.Edge.makeBezier([V(x, y0, z) for x, z in s]) for s in segs]
    edges.append(cq.Edge.makeLine(V(1049 + te, y0, -47), V(990, y0, -47)))
    edges.append(cq.Edge.makeLine(V(990, y0, -47), V(865 + le, y0, 48)))
    face = cq.Face.makeFromWires(cq.Wire.assembleEdges(edges))
    solid = cq.Solid.extrudeLinear(face, V(0, t, 0))
    try:
        rounded = cq.Workplane("XY").add(solid).edges().fillet(2.0).val()
        if rounded.isValid():
            return rounded
    except Exception:
        pass
    return solid


# ---------------------------------------------------------------- canopy
def canopy_profile(t: float, p: Params) -> float:
    crest = p["canopy_crest"]
    knots = [0, .12, .27, .42, .53, .67, .81, .92, 1]
    values = [0, .37, .76, 1, .97, .79, .51, .23, 0]
    mapped = [k * crest / .42 if k <= .42 else crest + (k - .42) * (1 - crest) / (1 - .42) for k in knots]
    full = [v if v in (0, 1) else min(.995, v * p["canopy_fullness"]) for v in values]
    return _smooth(t, mapped, full)


def canopy(p: Params) -> cq.Solid:
    ts = [0, .03, .08, .14, .22, .30, .38, .46, .54, .62, .70, .78, .86, .93, .97, 1]
    wires = []
    for t in ts:
        x = CANOPY_X0 + CANOPY_LENGTH * t
        sill = DECK_Z - p["canopy_sill_dip"] * math.sin(math.pi * t)
        top = DECK_Z + p["canopy_rise"] * canopy_profile(t, p)
        top = max(top, sill + .8)   # the end sections are tiny but never degenerate
        ry = max(.4, p["canopy_half_width"] * math.sin(math.pi * t) ** p["canopy_width_exponent"])
        arc = [V(x, ry * math.cos(a), sill + (top - sill) * math.sin(a)) for a in np.linspace(0, math.pi, 17)]
        wires.append(cq.Wire.assembleEdges([cq.Edge.makeSpline(arc), cq.Edge.makeLine(arc[-1], arc[0])]))
    return _loft(wires)


# ---------------------------------------------------------------- landing gear
def spat(sign: float, p: Params) -> cq.Solid:
    x0, length = p["spat_x_nose"], p["spat_length"]
    ry, rz, zc = p["spat_half_width"], p["spat_half_height"], p["gear_axle_z"]
    wires = []
    for u in (0, .02, .06, .12, .2, .28, .35, .5, .65, .8, .9, .96, 1.0):
        if u < .35:
            f, lift = math.sqrt(max(0, 1 - ((u - .35) / .35) ** 2)), 0.0
        else:
            q = (u - .35) / .65
            f, lift = 1 - .82 * q ** 1.6, 9 * q * q
        wires.append(_ellipse(x0 + length * u, max(.4, ry * f), max(.4, rz * f), zc + lift, sign * p["gear_axle_y"]))
    return _loft(wires)


def _cyl(a, b, radius):
    a, b = V(*a), V(*b)
    return cq.Solid.makeCylinder(radius, (b - a).Length, a, (b - a).normalized())


def _ellipsoid(center, radii) -> cq.Shape:
    sphere = cq.Solid.makeSphere(1.0, V(0, 0, 0), V(0, 0, 1), -90, 90, 360)
    matrix = cq.Matrix([[radii[0], 0, 0, center[0]], [0, radii[1], 0, center[1]], [0, 0, radii[2], center[2]]])
    return sphere.transformGeometry(matrix)


# ---------------------------------------------------------------- assembly
def _engine_yaw(shape: cq.Shape) -> cq.Shape:
    return shape.rotate(PIVOT, PIVOT + V(0, 0, 1), -ENGINE_YAW_DEG)


def _spinner() -> cq.Solid:
    return _loft([_ellipse(x, r, r, 10) for x, r in [(-48, 1), (-43, 10), (-33, 20), (-18, 27), (-5, 28)]])


def _blade(sign: float) -> cq.Solid:
    wires, n = [], 8
    for j in range(n + 1):
        rr = 18 + 152 * j / n
        width = (10 + 10 * math.sin(math.pi * j / n)) * (1 - .9 * (j / n) ** 9)
        ang = math.atan(140 / (2 * math.pi * rr))
        chord = V(math.sin(ang), 0, sign * math.cos(ang))
        thick = V(math.cos(ang), 0, -sign * math.sin(ang))
        ring = [V(-4, sign * rr, 10) + chord * (max(width, 1.7) * math.cos(a)) + thick * (1.6 * math.sin(a))
                for a in np.linspace(0, 2 * math.pi, 16, endpoint=False)]
        wires.append(cq.Wire.assembleEdges([cq.Edge.makeSpline(ring + [ring[0]], periodic=False)]))
    return _loft(wires)


WHITE, RED, SILVER, BLACK, GLASS = (.88, .91, .94), (.58, .01, .014), (.52, .58, .64), (.012, .016, .022), (.9, .95, 1)
BUILD_ORDER = ("fuselage", "wing", "stabilizer", "fin", "canopy", "gear", "engine")


def _group_fuselage(p: Params, detail: bool) -> list[Part]:
    body = fuselage(p)
    if detail:   # open cockpit well, as in the Blender model
        body = body.cut(_ellipsoid((330, 0, 98), (112, 29, 66)))
    return [Part("fuselage", body, "fuselage", RED)]


def _group_wing(p: Params, detail: bool) -> list[Part]:
    return [Part("wing_long", wing(True, p), "wing", WHITE), Part("wing_short", wing(False, p), "wing", WHITE)]


def _group_stabilizer(p: Params, detail: bool) -> list[Part]:
    return [Part("stabilizer_left", stabilizer(-1, p), "stabilizer", WHITE),
            Part("stabilizer_right", stabilizer(1, p), "stabilizer", WHITE)]


def _group_fin(p: Params, detail: bool) -> list[Part]:
    return [Part("fin", fin(p), "fin", RED)]


def _group_canopy(p: Params, detail: bool) -> list[Part]:
    return [Part("canopy", canopy(p), "canopy", GLASS)]


def _group_gear(p: Params, detail: bool) -> list[Part]:
    parts = []
    for name, sign in (("left", -1), ("right", 1)):
        top = (p["gear_leg_top_x"], sign * p["gear_leg_top_y"], -12)
        axle = (p["gear_axle_x"], sign * p["gear_axle_y"], p["gear_axle_z"])
        parts.append(Part(f"gear_leg_{name}", _cyl(top, axle, 4.0), "gear", SILVER))
        parts.append(Part(f"wheel_spat_{name}", spat(sign, p), "gear", WHITE))
        wheel_y = sign * (p["gear_axle_y"] + 8)
        parts.append(Part(f"tyre_{name}", _cyl((axle[0], wheel_y - 8 * sign, p["gear_axle_z"] - 2),
                                               (axle[0], wheel_y + 8 * sign, p["gear_axle_z"] - 2), 24), "gear", BLACK))
        if detail:
            parts.append(Part(f"wheel_hub_{name}", _cyl((axle[0], wheel_y - 9 * sign, p["gear_axle_z"] - 2),
                                                        (axle[0], wheel_y + 9 * sign, p["gear_axle_z"] - 2), 8),
                              "gear", SILVER, airframe=False))
    if detail:
        parts.append(Part("tail_wheel", _cyl((919, -5, -111), (919, 5, -111), 11), "gear", BLACK, airframe=False))
        parts.append(Part("tail_wheel_wire", _cyl((880, 0, -38), (919, 0, -111), 2), "gear", SILVER, airframe=False))
    return parts


def _group_engine(p: Params, detail: bool) -> list[Part]:
    x, reach = p["engine_cylinder_x"], p["engine_cylinder_reach"]
    sx0, sx1, sy, sz = p["silencer_x_front"], p["silencer_x_rear"], p["silencer_y"], p["silencer_z"]
    parts = [
        Part("engine_crankcase", _cyl((14, 5, 5), (125, 5, 5), 20), "engine", SILVER),
        Part("engine_cylinder", _cyl((x, 19, 5), (x, reach, 5), 17), "engine", SILVER),
        Part("engine_head", _cyl((x, reach + 1, 5), (x, reach + 6, 5), 19), "engine", SILVER),
        Part("engine_silencer", _cyl((sx0, sy, sz), (sx1, sy, sz), 15), "engine", SILVER),
        Part("engine_silencer_dome", _ellipsoid((sx1, sy, sz), (12, 15, 15)), "engine", SILVER),
        Part("spinner", _spinner(), "propeller", SILVER),
    ]
    if detail:
        for j in range(12):
            parts.append(Part(f"engine_cooling_fin_{j:02d}", _cyl((x, 38 + j * 3.1, 5), (x, 39.2 + j * 3.1, 5), 21),
                              "engine", SILVER, airframe=False))
        parts.append(Part("engine_glow_plug", _cyl((x, reach + 6, 5), (x, reach + 12, 5), 4), "engine", SILVER, airframe=False))
        for sign, name in ((-1, "a"), (1, "b")):
            parts.append(Part(f"propeller_blade_{name}", _blade(sign), "propeller", WHITE, airframe=False))
    for part in parts:   # the whole engine and propeller assembly is yawed about the propeller centre
        part.shape = _engine_yaw(part.shape)
    return parts


GROUP_BUILDERS = {"fuselage": _group_fuselage, "wing": _group_wing, "stabilizer": _group_stabilizer, "fin": _group_fin,
                  "canopy": _group_canopy, "gear": _group_gear, "engine": _group_engine}


def build_group(group: str, p: Params | None = None, detail: bool = False) -> list[Part]:
    """The parts shaped by one gene group; they depend on that group's genes only."""
    return GROUP_BUILDERS[group](p or Params(), detail)


def build(p: Params | None = None, detail: bool = False) -> list[Part]:
    """All parts of the model. `detail` adds the cockpit, engine fins and small hardware."""
    p = p or Params()
    parts = [part for group in BUILD_ORDER for part in build_group(group, p, detail)]
    if detail:
        parts.extend(_cockpit(p, RED, BLACK, SILVER))
    return parts


def _cockpit(p: Params, red, black, silver) -> list[Part]:
    grey = (.62, .65, .67)
    parts = []
    parts.append(Part("seat_cushion", _ellipsoid((372, 0, 43), (23, 22, 5)), "cockpit", grey, airframe=False))
    back = cq.Workplane("XY").polyline([(384, 44), (396, 82), (398, 82), (386, 44)]).close().extrude(20, both=True)
    for z in (51, 59, 67, 75):
        x = 384 + (z - 44) * 12 / 38
        for y in (-8, 8):
            back = back.cut(_cyl((x - 8, y, z), (x + 8, y, z), 3.5))
    parts.append(Part("seat_back", back.val(), "cockpit", grey, airframe=False))
    parts.append(Part("instrument_panel", cq.Solid.makeBox(6, 48, 32, V(282, -24, 49)), "cockpit", black, airframe=False))
    for i, (y, z, r) in enumerate([(-14, 69, 5), (0, 72, 6.5), (14, 69, 5), (0, 59, 4.5)]):
        parts.append(Part(f"instrument_{i}", _cyl((288, y, z), (292, y, z), r), "cockpit", silver, airframe=False))
    parts.append(Part("control_stick", _cyl((350, 0, 52), (345, 0, 69), 1.5), "cockpit", silver, airframe=False))
    return parts
