"""The airfield's site plan: one place for every measured estimate the ground, the assets and the layout share.

Metres, pilot at the origin, north along -Z, east along +X, the flying surface at y = 0. Distances come from the
reference image (../pics/img.png) at about 0.3 m per pixel, measured from the Pribrezhnaya roadway width and the
lengths of the apartment blocks. They are estimates, not survey data. Standard library only (Python 3.10+, so the
Blender build can import it too).
"""
import math

IMAGE_SCALE = 0.3  # metres per pixel of ../pics/img.png
IMAGE_PILOT = (995, 810)  # pixel of the circle's centre in the image

PAD_RADIUS = 25.0  # 50 m pad; the brief's 35-42 m is too small for the Racer's 21 m line
RING_RADII = (15.0, 18.0, 21.0)  # Trainer, Stunter and Racer line lengths
RING_WIDTH = 0.1
PILOT_CIRCLE_RADIUS = 0.75
BORDER_WIDTH = 0.3
CLEAR_RADIUS = 28.0  # Racer 21 m line + ~1 m handle/plane reach + 3 m margin, rounded up to 3 m past the pad edge
LEVEL_RADIUS = 30.0  # the ground is exactly level (y = 0) this far out

SITE_SIZE = 300  # the site terrain, centred on the pilot
SITE_RESOLUTION = 255
SITE_EDGE_HEIGHT = -1.0
OUTER_SIZE = 600
OUTER_RESOLUTION = 201
OUTER_HEIGHT = -1.1  # outer ground: just under the site's edges
OUTER_SUNK = -6.0  # outer ground under the site footprint, so it never shows through

# Pribrezhnaya Street, west to east (centreline), and Zvezdnaya Street, south to north
STREET = [(-160.0, -66.0), (-60.0, -63.0), (-20.0, -58.0), (30.0, -52.0), (160.0, -38.0)]
STREET_WIDTH = 7.0
SIDEWALK_OFFSET = (4.5, 6.5)  # sidewalk band on both sides, from the centreline
ZVEZDNAYA = [(-30.0, -60.0), (-42.0, -90.0), (-52.0, -120.0), (-60.0, -160.0)]
ZVEZDNAYA_WIDTH = 6.0
EAST_LANE = [(42.0, -50.0), (44.0, -30.0), (47.0, -8.0), (54.0, 14.0), (70.0, 22.0), (160.0, 30.0)]
EAST_LANE_WIDTH = 4.0
ACCESS_PATH = [(6.0, -50.0), (4.0, -40.0), (6.0, -32.0), (3.0, -24.0)]

# the railway: two tracks 4.1 m apart, centred on z = RAIL_Z0 + RAIL_SLOPE * x, west-northwest to east-southeast
RAIL_Z0 = 66.0
RAIL_SLOPE = 0.1
TRACK_SPACING = 4.1
EMBANKMENT_HALF_TOP = 4.0
EMBANKMENT_HALF_BASE = 10.0
EMBANKMENT_TOP = -0.6  # world height of the ballast top, where the sleepers sit

# worn dirt tracks across the plot (west and south), with their widths
TRACKS = [
    ([(-160.0, 38.0), (-95.0, 6.0), (-55.0, -22.0), (-26.0, -50.0)], 2.2),
    ([(-150.0, 62.0), (-80.0, 30.0), (-40.0, 12.0), (-24.0, 6.0)], 1.8),
    ([(-110.0, -55.0), (-70.0, -30.0), (-35.0, -14.0), (-24.0, -8.0)], 1.6),
    ([(-160.0, 44.0), (-60.0, 46.0), (0.0, 47.0), (60.0, 52.0), (160.0, 60.0)], 2.4),
    ([(-160.0, 52.0), (-40.0, 54.0), (40.0, 58.0), (160.0, 68.0)], 1.6),
    ([(-20.0, 46.0), (-12.0, 36.0), (-6.0, 24.5)], 1.6),
    ([(10.0, 47.0), (14.0, 36.0), (12.0, 21.5)], 1.4),
    ([(-150.0, -30.0), (-90.0, -40.0), (-50.0, -50.0)], 1.2),
]

# the eastern preparation area (packed dirt) and the utility building (WC + staff room)
PREP_AREA = (29.0, 46.0, -24.0, 8.0)  # min x, max x, min z, max z
UTILITY = {"x": 34.0, "z": -7.0, "yaw": 0.0, "size": (4.0, 3.0, 8.0)}  # width (x), height, depth (z)


def rail_z(x):
    return RAIL_Z0 + RAIL_SLOPE * x


def smoothstep(edge0, edge1, x):
    t = min(max((x - edge0) / (edge1 - edge0), 0.0), 1.0)
    return t * t * (3.0 - 2.0 * t)


def _hash(ix, iz, seed):
    n = (ix * 374761393 + iz * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFFFF) / float(0xFFFFFF)


def value_noise(x, z, cell, seed=1):
    """Smooth noise in [0, 1] with features about `cell` metres apart."""
    fx, fz = x / cell, z / cell
    ix, iz = math.floor(fx), math.floor(fz)
    tx, tz = fx - ix, fz - iz
    tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
    a = _hash(ix, iz, seed) * (1 - tx) + _hash(ix + 1, iz, seed) * tx
    b = _hash(ix, iz + 1, seed) * (1 - tx) + _hash(ix + 1, iz + 1, seed) * tx
    return a * (1 - tz) + b * tz


def height(x, z):
    """World height of the site ground: level near the pad, a gentle southern descent, the railway embankment."""
    d = math.hypot(x, z)
    if d <= LEVEL_RADIUS:
        return 0.0
    away = smoothstep(LEVEL_RADIUS, LEVEL_RADIUS + 15.0, d)
    rz = rail_z(x)
    # south: down about 2 m toward the railway, then settle beyond it
    south = -2.0 * smoothstep(LEVEL_RADIUS, rz - EMBANKMENT_HALF_BASE, z)
    south += 1.0 * smoothstep(rz + EMBANKMENT_HALF_BASE, rz + 40.0, z)
    across = abs(z - rz)
    bank = 1.0 - smoothstep(EMBANKMENT_HALF_TOP, EMBANKMENT_HALF_BASE, across)
    h = south + (EMBANKMENT_TOP - south) * bank
    # gentle undulation, none on the embankment top
    h += (value_noise(x, z, 23.0, 7) - 0.5) * 0.5 * away * (1.0 - bank)
    h *= away if bank < 1.0 else 1.0
    if bank >= 1.0:
        h = EMBANKMENT_TOP
    # every edge settles to the same height, just above the outer ground
    edge = smoothstep(SITE_SIZE / 2 - 35.0, SITE_SIZE / 2 - 1.0, max(abs(x), abs(z)))
    return h + (SITE_EDGE_HEIGHT - h) * edge


def ground_height(x, z):
    """Height at any point: the site terrain inside its square, the outer ground outside it."""
    half = SITE_SIZE / 2
    if abs(x) <= half and abs(z) <= half:
        return height(x, z)
    return OUTER_HEIGHT


def distance_to_polyline(x, z, points):
    best = float("inf")
    for (ax, az), (bx, bz) in zip(points, points[1:]):
        dx, dz = bx - ax, bz - az
        t = max(0.0, min(1.0, ((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz)))
        best = min(best, math.hypot(x - ax - t * dx, z - az - t * dz))
    return best


def polyline_point(points, t):
    """Point and direction at arc length t along points."""
    for (ax, az), (bx, bz) in zip(points, points[1:]):
        length = math.hypot(bx - ax, bz - az)
        if t <= length:
            f = t / length
            return (ax + (bx - ax) * f, az + (bz - az) * f), ((bx - ax) / length, (bz - az) / length)
        t -= length
    (ax, az), (bx, bz) = points[-2], points[-1]
    length = math.hypot(bx - ax, bz - az)
    return (bx, bz), ((bx - ax) / length, (bz - az) / length)


def polyline_length(points):
    return sum(math.hypot(bx - ax, bz - az) for (ax, az), (bx, bz) in zip(points, points[1:]))
