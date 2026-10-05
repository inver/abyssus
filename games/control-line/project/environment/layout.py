"""Place the airfield's scenery and stage the scene edit (standard library only).

Usage: python3 layout.py

Reads site_plan.py and each built model's bounds (source.json written by build_environment.py), places every instance,
rejects any whose transformed footprint comes within the clear radius of the pilot, and writes:
- placements.json: every instance (asset, position, rotation, scale, footprint) with the estimates used;
- layout.svg: a north-up placement diagram (not a render);
- field-environment.patch.json: the scene edit for apply through editSceneJson (never applied here).
"""
import hashlib
import json
import math
import random
from pathlib import Path

import site_plan as plan

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parent / "ControlLine"
SCENE = PROJECT / "scenes" / "Field.scene"
FIRST_ID = 300
MARGIN = 0.5  # extra metres beyond the clear radius for anything placed


def model_bounds(asset):
    bounds = json.loads((PROJECT / "assets" / asset / "source.json").read_text())["bounds"]
    return bounds["min"], bounds["max"]


def quaternion(yaw, slope=0.0):
    """Rotation about Y by yaw (radians, +X toward -Z), then about the model's own Z by slope (raising its +X end)."""
    cy, sy = math.cos(yaw / 2), math.sin(yaw / 2)
    cs, ss = math.cos(slope / 2), math.sin(slope / 2)
    return {"x": sy * ss, "y": sy * cs, "z": cy * ss, "w": cy * cs}  # q = q_yaw * q_slope


def yaw_along(dx, dz):
    """The yaw that turns the model's +X onto the direction (dx, dz)."""
    return math.atan2(-dz, dx)


def yaw_facing(dx, dz):
    """The yaw that turns the model's -Z (Blender +Y, north) onto the direction (dx, dz)."""
    return math.atan2(-dx, -dz)


def footprint(asset, x, z, yaw, scale):
    lo, hi = model_bounds(asset)
    c, s = math.cos(yaw), math.sin(yaw)
    points = []
    for px in (lo[0], hi[0]):
        for pz in (lo[2], hi[2]):
            # rotation about Y: x' = x cos + z sin, z' = -x sin + z cos
            points.append((x + scale * (px * c + pz * s), z + scale * (-px * s + pz * c)))
    return points


def distance_to_footprint(points, px=0.0, pz=0.0):
    """Distance from (px, pz) to the quadrilateral footprint; 0 inside."""
    hull = sorted(points, key=lambda p: math.atan2(p[1] - sum(q[1] for q in points) / 4, p[0] - sum(q[0] for q in points) / 4))
    inside = all(((b[0] - a[0]) * (pz - a[1]) - (b[1] - a[1]) * (px - a[0])) >= 0 for a, b in zip(hull, hull[1:] + hull[:1]))
    if inside:
        return 0.0
    return min(plan.distance_to_polyline(px, pz, hull + hull[:1]), min(math.hypot(px - a, pz - b) for a, b in hull))


class Layout:
    def __init__(self):
        self.items = []
        self.rng = random.Random(20261006)

    def place(self, name, asset, x, z, yaw=0.0, scale=1.0, y=None, slope=0.0, exempt=False, ground="min"):
        points = footprint(asset, x, z, yaw, scale)
        clear = distance_to_footprint(points)
        if not exempt and clear < plan.CLEAR_RADIUS + MARGIN:
            raise SystemExit(f"{name} at ({x:.1f}, {z:.1f}) is {clear:.1f} m from the pilot")
        if y is None:
            heights = [plan.ground_height(px, pz) for px, pz in points] + [plan.ground_height(x, z)]
            y = min(heights) if ground == "min" else plan.ground_height(x, z)
        self.items.append({"name": name, "asset": asset, "x": x, "y": y, "z": z, "yaw": yaw, "slope": slope,
                           "scale": scale, "footprint": points, "clearance": clear})

    @property
    def blockers(self):
        """Footprints vegetation and houses keep away from: every placed building and prop."""
        return [item["footprint"] for item in self.items
                if item["asset"] != "model_airfield_pad" and "_tree_" not in item["asset"] and "_bush_" not in item["asset"]]


def _overlap(a, b, gap):
    ax0, ax1 = min(p[0] for p in a) - gap, max(p[0] for p in a) + gap
    az0, az1 = min(p[1] for p in a) - gap, max(p[1] for p in a) + gap
    bx0, bx1 = min(p[0] for p in b), max(p[0] for p in b)
    bz0, bz1 = min(p[1] for p in b), max(p[1] for p in b)
    return ax0 < bx1 and bx0 < ax1 and az0 < bz1 and bz0 < az1


def buildings(layout):
    layout.place("Flying circle", "model_airfield_pad", 0.0, 0.0, y=0.0, exempt=True)
    u = plan.UTILITY
    layout.place("Utility building (WC, staff room)", "model_airfield_utility", u["x"], u["z"], u["yaw"])
    for i, (x, z, yaw, asset) in enumerate([
        (39.5, -15.5, math.radians(12), "model_airfield_table_picnic"),
        (40.5, 2.5, math.radians(-6), "model_airfield_table_picnic"),
        (31.5, -17.5, math.radians(90), "model_airfield_table_work"),
        (43.5, -8.0, math.radians(84), "model_airfield_table_work"),
    ]):
        layout.place(f"Preparation table {i + 1}", asset, x, z, yaw)
    for i, (x, z, yaw) in enumerate([(30.5, -21.5, 0.0), (33.0, -22.0, 0.15), (30.0, 5.5, 1.5), (31.5, 7.0, 1.6),
                                     (36.5, -20.5, -0.2)]):
        layout.place(f"Model stand {i + 1}", "model_airfield_model_stand", x, z, yaw)
    street_dir = (130.0, 14.0)
    layout.place("DK Plamya", "model_airfield_dk_plamya", 40.0, -100.0, yaw_along(*street_dir))
    layout.place("Long building", "model_airfield_long_building", 43.0, 31.0, yaw_along(1.0, 0.14))
    layout.place("Blue-roof warehouse", "model_airfield_warehouse", -200.0, 22.0, yaw_along(1.0, 0.36))
    for i, (x, z, yaw) in enumerate([(195, -100, yaw_along(1, 0.1)), (195, -55, yaw_along(1, 0.1)),
                                     (252, -80, yaw_along(-0.1, 1)), (205, -158, yaw_along(1, 0.1)),
                                     (265, -150, yaw_along(1, 0.1)), (190, -10, yaw_along(1, 0.1))]):
        layout.place(f"Apartment block {i + 1}", "model_airfield_apartment_block", float(x), float(z), yaw)


def houses(layout):
    rng = layout.rng
    rail_yaw = yaw_along(1.0, plan.RAIL_SLOPE)
    spots = []
    # south-east, between the eastern lane and the railway, continuing past the site
    for x in range(96, 236, 15):
        for row in (0, 1):
            z = 38 + 0.08 * (x - 96) + row * 15
            if z < plan.rail_z(x) - 14:
                spots.append((x + rng.uniform(-2, 2), z + rng.uniform(-1.5, 1.5)))
    # south, beyond the railway
    for x in range(-230, 236, 16):
        for row in (0, 1, 2):
            z = plan.rail_z(x) + 20 + row * 17
            if z < 230:
                if rng.random() < 0.8:  # gaps for gardens
                    spots.append((x + rng.uniform(-4, 4), z + rng.uniform(-3, 3)))
    for i, (x, z) in enumerate(spots):
        asset = "model_airfield_house_a" if rng.random() < 0.5 else "model_airfield_house_b"
        yaw = rail_yaw + rng.choice((0.0, math.pi)) + rng.uniform(-0.12, 0.12)
        points = footprint(asset, x, z, yaw, 1.0)
        if any(_overlap(points, other, 3.0) for other in layout.blockers):
            continue
        layout.place(f"House {i + 1}", asset, x, z, yaw)


def railway(layout):
    direction = (1.0, plan.RAIL_SLOPE)
    length = math.hypot(*direction)
    step = 50.0 / length
    x = -300.0 + step / 2
    i = 0
    while x < 300.0:
        z = plan.rail_z(x)
        x0, x1 = x - step / 2, x + step / 2
        y0 = plan.ground_height(x0, plan.rail_z(x0)) + 0.05
        y1 = plan.ground_height(x1, plan.rail_z(x1)) + 0.05
        slope = math.atan2(y1 - y0, 50.0)
        i += 1
        layout.place(f"Railway {i}", "model_airfield_railway", x, z, yaw_along(*direction), y=(y0 + y1) / 2,
                     slope=slope)
        x += step


def street_lamps(layout):
    total = plan.polyline_length(plan.STREET)
    t = 12.0
    i = 0
    while t < total:
        (x, z), (dx, dz) = plan.polyline_point(plan.STREET, t)
        t += 34.0
        if abs(x + 30) < 8 or abs(x - 42) < 6:  # junctions with Zvezdnaya and the eastern lane
            continue
        south = (-dz, dx)  # the pad's side of the street
        lx, lz = x + south[0] * 7.0, z + south[1] * 7.0
        i += 1
        layout.place(f"Street lamp {i}", "model_airfield_street_lamp", lx, lz, yaw_facing(-south[0], -south[1]))


def kept_off(x, z, radius):
    """Whether vegetation of this radius may grow at (x, z): off the pad, streets, paths, railway and prep area."""
    if math.hypot(x, z) < plan.CLEAR_RADIUS + MARGIN + radius:
        return False
    if plan.distance_to_polyline(x, z, plan.STREET) < plan.SIDEWALK_OFFSET[1] + 1.0 + radius * 0.5:
        return False
    if plan.distance_to_polyline(x, z, plan.ZVEZDNAYA) < plan.ZVEZDNAYA_WIDTH / 2 + 1.5:
        return False
    if plan.distance_to_polyline(x, z, plan.EAST_LANE) < plan.EAST_LANE_WIDTH / 2 + 1.5:
        return False
    if plan.distance_to_polyline(x, z, plan.ACCESS_PATH) < 2.5:
        return False
    if abs(z - plan.rail_z(x)) < plan.EMBANKMENT_HALF_BASE + radius * 0.5:
        return False
    if any(plan.distance_to_polyline(x, z, points) < width / 2 + 0.8 for points, width in plan.TRACKS):
        return False
    x0, x1, z0, z1 = plan.PREP_AREA
    if x0 - 2 < x < x1 + 2 and z0 - 2 < z < z1 + 2:
        return False
    return True


def tree_density(x, z):
    """Relative tree cover from the reference: dense to the north-east and east by the houses, sparse on the plot."""
    street = -58.0 + 0.1 * x
    if z < street - 8 and x > 15:
        return 0.9  # north-east: around DK Plamya and the residential blocks
    if z < street - 8:
        return 0.35  # north-west: garages and gardens
    if x > 60 and z < plan.rail_z(x) - 10:
        return 0.75  # east: houses and gardens
    if z > plan.rail_z(x) + 10:
        return 0.45  # south of the railway: private gardens
    if abs(z - street) < 12:
        return 0.3  # verge trees along the street
    return 0.06  # the worn plot


def vegetation(layout):
    rng = layout.rng
    species = [("model_airfield_tree_broadleaf", 0.4), ("model_airfield_tree_acacia", 0.3),
               ("model_airfield_tree_young", 0.3)]
    def scatter(count, extent, density, choose, spacing, prefix):
        attempts = 0
        n = 0
        while n < count and attempts < count * 200:
            attempts += 1
            x, z = rng.uniform(-extent, extent), rng.uniform(-extent, extent)
            if rng.random() > density(x, z):
                continue
            asset = choose()
            scale = rng.uniform(0.8, 1.2)
            lo, hi = model_bounds(asset)
            radius = max(-lo[0], hi[0], -lo[2], hi[2]) * scale
            if not kept_off(x, z, radius):
                continue
            yaw = rng.uniform(-math.pi, math.pi)
            points = footprint(asset, x, z, yaw, scale)
            if any(_overlap(points, other, 1.0) for other in layout.blockers):
                continue
            if any(math.hypot(x - px, z - pz) < spacing * scale for px, pz in placed_centres):
                continue
            if distance_to_footprint(points) < plan.CLEAR_RADIUS + MARGIN:
                continue
            n += 1
            placed_centres.append((x, z))
            layout.place(f"{prefix} {n}", asset, x, z, yaw, scale, ground="centre")
        return n

    placed_centres = []

    def tree():
        r = rng.random()
        for asset, share in species:
            if r < share:
                return asset
            r -= share
        return species[-1][0]

    trees = scatter(125, 200, tree_density, tree, 5.5, "Tree")
    bush_density = lambda x, z: 0.5 * tree_density(x, z) + (0.35 if abs(x) < 150 and abs(z) < 150 else 0.05)
    bushes = scatter(90, 150, bush_density,
                     lambda: "model_airfield_bush_tall" if rng.random() < 0.45 else "model_airfield_bush_low",
                     4.0, "Bush")
    print(f"{trees} trees, {bushes} bushes")


def entity(item):
    terrain = item["asset"].startswith("terrain_")
    return {"components": {
        "NameComponent": {"name": item["name"]},
        "TypeComponent": {"type": "TERRAIN" if terrain else "OBJECT"},
        "PositionComponent": {
            "localPosition": {k: round(item[k], 3) for k in ("x", "y", "z")},
            "localRotation": {k: round(v, 6) for k, v in quaternion(item["yaw"], item["slope"]).items()},
            "localScale": {k: round(item["scale"], 4) for k in ("x", "y", "z")},
        },
        "RenderComponent": {"renderable": {
            "kind": "asset", "shaderKey": "terrain" if terrain else "defaultShader",
            "asset": {"type": "TERRAIN" if terrain else "MODEL", "assetName": item["asset"]},
        }},
    }}


def svg(layout):
    size, half = 800, 250.0
    k = size / (2 * half)

    def px(x, z):
        return f"{(x + half) * k:.1f},{(z + half) * k:.1f}"

    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size + 60}" font-family="sans-serif" font-size="11">',
           f'<rect width="{size}" height="{size}" fill="#c9c08f"/>',
           f'<rect x="{(half - plan.SITE_SIZE / 2) * k}" y="{(half - plan.SITE_SIZE / 2) * k}" width="{plan.SITE_SIZE * k}" height="{plan.SITE_SIZE * k}" fill="#b9ad7c" stroke="#8a7f55" stroke-dasharray="4 3"/>']
    for points, width in plan.TRACKS:
        out.append(f'<polyline points="{" ".join(px(*p) for p in points)}" fill="none" stroke="#e2c99a" stroke-width="{width * k:.1f}"/>')
    for points, width, colour in ((plan.STREET, plan.STREET_WIDTH, "#555"), (plan.ZVEZDNAYA, plan.ZVEZDNAYA_WIDTH, "#555"),
                                  (plan.EAST_LANE, plan.EAST_LANE_WIDTH, "#777"), (plan.ACCESS_PATH, 3.0, "#e2c99a")):
        out.append(f'<polyline points="{" ".join(px(*p) for p in points)}" fill="none" stroke="{colour}" stroke-width="{width * k:.1f}"/>')
    out.append(f'<line x1="0" y1="{(plan.rail_z(-half) + half) * k:.1f}" x2="{size}" y2="{(plan.rail_z(half) + half) * k:.1f}" stroke="#7a7a7a" stroke-width="{2 * plan.EMBANKMENT_HALF_TOP * k:.1f}"/>')
    for item in layout.items:
        a = item["asset"]
        if "tree" in a or "bush" in a:
            lo, hi = model_bounds(a)
            r = max(-lo[0], hi[0], -lo[2], hi[2]) * item["scale"] * k
            out.append(f'<circle cx="{(item["x"] + half) * k:.1f}" cy="{(item["z"] + half) * k:.1f}" r="{r:.1f}" fill="{"#4f6b35" if "tree" in a else "#7d8f4e"}" fill-opacity="0.8"/>')
        elif a == "model_airfield_pad":
            out.append(f'<circle cx="{half * k}" cy="{half * k}" r="{plan.PAD_RADIUS * k}" fill="#333"/>')
            for r in plan.RING_RADII:
                out.append(f'<circle cx="{half * k}" cy="{half * k}" r="{r * k}" fill="none" stroke="#fff" stroke-width="0.6"/>')
        else:
            colour = {"model_airfield_warehouse": "#2f6fd0", "model_airfield_dk_plamya": "#7a4b35",
                      "model_airfield_railway": "none", "model_airfield_utility": "#eee"}.get(a, "#a9a49a")
            if a == "model_airfield_street_lamp":
                out.append(f'<circle cx="{(item["x"] + half) * k:.1f}" cy="{(item["z"] + half) * k:.1f}" r="1.5" fill="#222"/>')
            elif colour != "none":
                pts = sorted(item["footprint"], key=lambda p: math.atan2(p[1] - item["z"], p[0] - item["x"]))
                out.append(f'<polygon points="{" ".join(px(*p) for p in pts)}" fill="{colour}" stroke="#333" stroke-width="0.5"/>')
    out.append(f'<circle cx="{half * k}" cy="{half * k}" r="{plan.CLEAR_RADIUS * k}" fill="none" stroke="#d33" stroke-dasharray="3 2"/>')
    out.append(f'<text x="8" y="{size + 18}">North up. 1 px = {1 / k:.2f} m. Red dashes: {plan.CLEAR_RADIUS:.0f} m clear radius. '
               'Inner square: the 300 m site terrain.</text>')
    out.append(f'<text x="8" y="{size + 36}">Placement diagram of estimates from ../pics/img.png, not a render or survey.</text>')
    out.append(f'<text x="{size / 2 - 4}" y="14">N</text></svg>')
    return "\n".join(out) + "\n"


def main():
    layout = Layout()
    buildings(layout)
    railway(layout)
    street_lamps(layout)
    houses(layout)
    vegetation(layout)

    scenery = [dict(name="Airfield outer ground", asset="terrain_airfield_outer", x=-plan.OUTER_SIZE / 2, y=0.0,
                    z=-plan.OUTER_SIZE / 2, yaw=0.0, slope=0.0, scale=1.0)] + layout.items
    entities = {str(FIRST_ID + i): entity(item) for i, item in enumerate(scenery)}
    worst = min(item["clearance"] for item in layout.items if item["asset"] != "model_airfield_pad")
    print(f"{len(layout.items)} instances; nearest scenery {worst:.1f} m from the pilot")

    (HERE / "placements.json").write_text(json.dumps({
        "estimates": {
            "imageScale": f"{plan.IMAGE_SCALE} m per pixel of ../pics/img.png (roadway width, apartment block lengths)",
            "padDiameter": 2 * plan.PAD_RADIUS, "ringRadii": plan.RING_RADII, "clearRadius": plan.CLEAR_RADIUS,
            "axes": "metres, pilot at the origin, north along -Z, east along +X",
        },
        "instances": [{k: (round(v, 3) if isinstance(v, float) else v) for k, v in item.items() if k != "footprint"}
                      for item in layout.items],
    }, indent=1) + "\n")
    (HERE / "layout.svg").write_text(svg(layout))
    (HERE / "field-environment.patch.json").write_text(json.dumps({
        "patchVersion": 2,
        "target": "ControlLine/scenes/Field.scene",
        "expectedSha256": hashlib.sha256(SCENE.read_bytes()).hexdigest(),
        "fieldEntity": "0",
        "fieldAsset": "terrain_airfield_site",
        "fieldPosition": {"x": -plan.SITE_SIZE / 2, "y": 0, "z": -plan.SITE_SIZE / 2},
        "removeAssetsPrefix": ["model_airfield_", "terrain_airfield_"],
        "appendEntities": entities,
        "instructions": "Apply through editSceneJson after checking expectedSha256: point the field entity at the new "
                        "site terrain and position, remove every other entity drawn with an airfield asset, append "
                        "appendEntities. Unrelated keys and number text stay as they are. No scenery colliders.",
    }, indent=1) + "\n")


if __name__ == "__main__":
    main()
