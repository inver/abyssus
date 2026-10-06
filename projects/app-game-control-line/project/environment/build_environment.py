"""Build the airfield's assets from the cached CC0 sources (fetch_sources.py), headless in Blender.

Usage: blender -b --factory-startup --python build_environment.py [-- step ...]
Steps: ground, pad, buildings, railway, props, vegetation (default: all). Blender 3.4 or newer.

Writes native asset folders under ../ControlLine/assets (meta.json, source.json, model.glb with external textures,
terrain.data, textures). Never writes a scene; layout.py stages the scene edit. Distances and positions come from
site_plan.py.
"""
import hashlib
import json
import math
import os
import random
import shutil
import struct
import sys
import time
import uuid
import zlib
from pathlib import Path

import bmesh
import bpy
import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import site_plan as plan  # noqa: E402

ASSETS = HERE.parent / "ControlLine" / "assets"
CACHE = Path(os.environ.get("ABYSSUS_AIRFIELD_CACHE", Path.home() / ".cache" / "abyssus-airfield"))
STAGE = CACHE / "stage"
GENERATOR = "games/control-line/project/environment/build_environment.py"
AMBIENTCG = "https://ambientcg.com/view?id="
POLYHAVEN = "https://polyhaven.com/a/"


# --- native asset folders -------------------------------------------------------------------------------------------

def asset_uuid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "abyssus/control-line/" + name))


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n")


def fresh_folder(name):
    folder = ASSETS / name
    if folder.exists():
        shutil.rmtree(folder)
    folder.mkdir(parents=True)
    return folder


def write_meta(folder, asset_type, additional):
    write_json(folder / "meta.json", {
        "format": "abyssus", "formatVersion": 1, "version": 1,
        "lastModified": int(time.time() * 1000),
        "uuid": asset_uuid(folder.name), "type": asset_type, "additional": additional,
    })


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


# --- textures -------------------------------------------------------------------------------------------------------

def ambient(material, kind="Color"):
    folder = CACHE / "ambientcg" / material
    found = sorted(folder.glob(f"*_{kind}.jpg"))
    if not found:
        raise SystemExit(f"{material} {kind} missing from {folder}: run fetch_sources.py")
    return found[0]


def prepared(source, size, tint=None, name=None):
    """A resized (and tinted) JPEG copy of an image, cached in STAGE."""
    STAGE.mkdir(parents=True, exist_ok=True)
    source = Path(source)
    tag = "" if tint is None else "_" + "-".join(f"{c:.2f}" for c in tint)
    target = STAGE / f"{name or source.stem}_{size}{tag}.jpg"
    if not target.exists():
        image = bpy.data.images.load(str(source))
        width, height = image.size
        if width != size:
            image.scale(size, max(1, round(size * height / width)))
        if tint is not None:
            pixels = np.empty(image.size[0] * image.size[1] * 4, np.float32)
            image.pixels.foreach_get(pixels)
            pixels = pixels.reshape(-1, 4)
            pixels[:, :3] *= np.array(tint, np.float32)
            image.pixels.foreach_set(np.clip(pixels, 0, 1).ravel())
        image.filepath_raw = str(target)
        image.file_format = "JPEG"
        image.save()
        bpy.data.images.remove(image)
    return target


def write_png(path, rgba):
    """RGBA uint8 array (rows top to bottom) as a PNG, without Blender's colour management."""
    height, width, _ = rgba.shape
    raw = b"".join(b"\x00" + rgba[row].tobytes() for row in range(height))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


# --- ground ---------------------------------------------------------------------------------------------------------

GROUND_LAYERS = {
    "texture_airfield_ground_grass": ("Ground003", "terrain base: dry, patchy grass"),
    "texture_airfield_ground_dirt": ("Ground079S", "terrain R: compacted light-brown soil"),
    "texture_airfield_ground_asphalt": ("Road012A", "terrain G: street asphalt"),
    "texture_airfield_ground_ballast": ("Gravel040", "terrain B: railway ballast"),
    "texture_airfield_ground_concrete": ("Concrete034", "terrain A: sidewalks"),
}
SPLAT_SIZE = 2048
LAYER_TILE = 3.0  # metres per repeat of a ground layer


def texture_asset(name, source_file, provenance):
    folder = fresh_folder(name)
    target = folder / Path(source_file).name.replace(Path(source_file).stem, name)
    shutil.copyfile(source_file, target)
    write_meta(folder, "TEXTURE", {"file": target.name})
    write_json(folder / "source.json", provenance)
    return asset_uuid(name)


def terrain_asset(name, size, uv, heights, layers, provenance):
    folder = fresh_folder(name)
    resolution = int(round(math.sqrt(heights.size)))
    (folder / "terrain.data").write_bytes(heights.astype(">f4").tobytes())
    write_meta(folder, "TERRAIN", {
        "terrainFile": "terrain.data", "size": size, "uv": uv,
        "splatMap": layers.get("splatMap"), "splatBase": layers.get("splatBase"),
        "splatR": layers.get("splatR"), "splatG": layers.get("splatG"), "splatB": layers.get("splatB"),
        "splatA": layers.get("splatA"),
    })
    write_json(folder / "source.json", dict(provenance, resolution=resolution))


def site_grid(size, resolution):
    """World x, z of a terrain's vertices: rows from north (z = -size/2) to south, as terrain.data stores them."""
    axis = np.linspace(-size / 2, size / 2, resolution)
    return np.meshgrid(axis, axis)


def polyline_distance(x, z, points):
    best = np.full(x.shape, np.inf, np.float32)
    for (ax, az), (bx, bz) in zip(points, points[1:]):
        dx, dz = bx - ax, bz - az
        t = np.clip(((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz), 0, 1)
        best = np.minimum(best, np.hypot(x - ax - t * dx, z - az - t * dz))
    return best


def band(distance, half_width, soft):
    """1 inside half_width of a line, fading to 0 over soft metres."""
    return np.clip((half_width + soft - distance) / soft, 0, 1)


def noise(x, z, cell, seed):
    rng = np.random.default_rng(seed)
    n = 64
    grid = rng.random((n + 1, n + 1)).astype(np.float32)
    grid[n, :], grid[:, n] = grid[0, :], grid[:, 0]  # wraps, so negative coordinates have no seam
    fx, fz = (x / cell) % n, (z / cell) % n
    ix, iz = np.floor(fx).astype(int), np.floor(fz).astype(int)
    tx, tz = fx - ix, fz - iz
    tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
    a = grid[iz, ix] * (1 - tx) + grid[iz, ix + 1] * tx
    b = grid[iz + 1, ix] * (1 - tx) + grid[iz + 1, ix + 1] * tx
    return a * (1 - tz) + b * tz


def splat_map():
    """RGBA weights over the site: R dirt, G asphalt, B ballast, A concrete, each mixed over the layers before it."""
    half = plan.SITE_SIZE / 2
    texel = plan.SITE_SIZE / SPLAT_SIZE
    axis = np.linspace(-half + texel / 2, half - texel / 2, SPLAT_SIZE).astype(np.float32)
    x, z = np.meshgrid(axis, axis)  # row 0 is north: the terrain samples v = local z / size
    d = np.hypot(x, z)
    patchy = noise(x, z, 9.0, 3) * 0.65 + noise(x, z, 2.5, 4) * 0.35

    # dusty, worn plot: compacted soil around the pad, patches everywhere, denser on the plot than among the houses
    dirt = np.clip(1.15 - (d - plan.PAD_RADIUS) / 9.0, 0, 1) * (0.75 + 0.25 * patchy)
    plot = (np.clip((z + 48 - 0.1 * x) / 10, 0, 1) * np.clip((plan.RAIL_Z0 + plan.RAIL_SLOPE * x - 6 - z) / 10, 0, 1)
            * np.clip((80 - x) / 25, 0, 1))
    worn = plot * np.clip((patchy - 0.3) * 2.0, 0, 0.9) + (1 - plot) * np.clip((patchy - 0.55) * 1.8, 0, 0.55)
    dirt = np.maximum(dirt, worn)
    for points, width in plan.TRACKS:
        wobble = (noise(x, z, 4.0, 11) - 0.5) * 0.6
        dirt = np.maximum(dirt, band(polyline_distance(x, z, points) + wobble, width / 2, 0.8) * 0.95)
    dirt = np.maximum(dirt, band(polyline_distance(x, z, plan.ACCESS_PATH), 1.6, 1.0))
    x0, x1, z0, z1 = plan.PREP_AREA
    edge = np.minimum.reduce([x - x0, x1 - x, z - z0, z1 - z]) + (noise(x, z, 3.0, 12) - 0.5) * 3.0
    prep = np.clip(edge / 2.5 + 0.2, 0, 1)
    dirt = np.maximum(dirt, prep * (0.85 + 0.15 * patchy))
    dirt = np.maximum(dirt, band(polyline_distance(x, z, plan.EAST_LANE), plan.EAST_LANE_WIDTH / 2 + 1.5, 1.5) * 0.8)
    rail = np.abs(z - (plan.RAIL_Z0 + plan.RAIL_SLOPE * x))
    dirt = np.maximum(dirt, band(rail, plan.EMBANKMENT_HALF_BASE, 2.0) * 0.6)

    # streets: Pribrezhnaya and Zvezdnaya, and the worn eastern lane
    street = polyline_distance(x, z, plan.STREET)
    asphalt = band(street, plan.STREET_WIDTH / 2, 0.3)
    asphalt = np.maximum(asphalt, band(polyline_distance(x, z, plan.ZVEZDNAYA), plan.ZVEZDNAYA_WIDTH / 2, 0.3))
    lane = band(polyline_distance(x, z, plan.EAST_LANE), plan.EAST_LANE_WIDTH / 2, 0.5)
    asphalt = np.maximum(asphalt, lane * np.clip(0.55 + 0.45 * noise(x, z, 3.0, 5), 0, 1))
    asphalt = np.maximum(asphalt, (d < plan.PAD_RADIUS - 0.5).astype(np.float32))  # under the pad decal

    ballast = band(rail, plan.EMBANKMENT_HALF_TOP + 0.6, 1.2)

    lo, hi = plan.SIDEWALK_OFFSET
    concrete = band(np.abs(street - (lo + hi) / 2), (hi - lo) / 2, 0.25) * (0.85 + 0.15 * patchy)

    rgba = np.stack([dirt, asphalt, ballast, concrete], axis=-1)
    return (np.clip(rgba, 0, 1) * 255 + 0.5).astype(np.uint8)


def build_ground():
    layers = {}
    for name, (material, use) in GROUND_LAYERS.items():
        layers[name] = texture_asset(name, prepared(ambient(material), 1024, name=name), {
            "author": "ambientCG (Lennart Demes)", "license": "CC0-1.0", "sourcePage": AMBIENTCG + material,
            "sourceFile": ambient(material).name, "sha256": sha256(ambient(material)), "use": use,
            "note": "Colour map resized to 1024 px.",
        })
    splat_folder = fresh_folder("texture_airfield_site_splat")
    write_png(splat_folder / "splat.png", splat_map())
    write_meta(splat_folder, "TEXTURE", {"file": "splat.png"})
    write_json(splat_folder / "source.json", {
        "generator": GENERATOR, "license": "CC0-1.0",
        "reference": "../pics/img.png and ../pics/about.txt (layout reference only; not traced)",
        "channels": {"R": "compacted dirt", "G": "asphalt", "B": "railway ballast", "A": "sidewalk concrete"},
        "texel": f"{plan.SITE_SIZE / SPLAT_SIZE:.3f} m",
        "note": "Painted from the street, track, path and railway lines in site_plan.py with procedural wear.",
    })

    x, z = site_grid(plan.SITE_SIZE, plan.SITE_RESOLUTION)
    heights = np.vectorize(plan.height)(x, z).astype(np.float32)
    terrain_asset("terrain_airfield_site", plan.SITE_SIZE, plan.SITE_SIZE / LAYER_TILE, heights, {
        "splatMap": asset_uuid("texture_airfield_site_splat"),
        "splatBase": layers["texture_airfield_ground_grass"], "splatR": layers["texture_airfield_ground_dirt"],
        "splatG": layers["texture_airfield_ground_asphalt"], "splatB": layers["texture_airfield_ground_ballast"],
        "splatA": layers["texture_airfield_ground_concrete"],
    }, {
        "generator": GENERATOR, "license": "CC0-1.0", "placement": {"x": -plan.SITE_SIZE / 2, "y": 0, "z": -plan.SITE_SIZE / 2},
        "note": "Level (y = 0) within 30 m of the pilot; gentle southern descent and railway embankment (site_plan.height).",
    })

    x, z = site_grid(plan.OUTER_SIZE, plan.OUTER_RESOLUTION)
    inside = (np.abs(x) < plan.SITE_SIZE / 2 - 2.5) & (np.abs(z) < plan.SITE_SIZE / 2 - 2.5)
    outer = np.where(inside, plan.OUTER_SUNK, plan.OUTER_HEIGHT).astype(np.float32)
    terrain_asset("terrain_airfield_outer", plan.OUTER_SIZE, plan.OUTER_SIZE / LAYER_TILE, outer, {
        "splatBase": layers["texture_airfield_ground_grass"],
    }, {
        "generator": GENERATOR, "license": "CC0-1.0", "placement": {"x": -plan.OUTER_SIZE / 2, "y": 0, "z": -plan.OUTER_SIZE / 2},
        "note": "Flat horizon ground, sunk under the site terrain so it never shows through.",
    })


# --- models ---------------------------------------------------------------------------------------------------------
# Blender is Z-up; the glTF export turns Blender +Y into -Z, so Blender +Y is north and +X east, as in the scene.

_materials = {}


def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    _materials.clear()


def tex_material(key, color, size=1024, tint=None, normal=None, normal_size=512, roughness=0.85, metallic=0.0,
                 two_sided=False):
    """A material with a colour map (resized, optionally tinted) and an optional OpenGL normal map."""
    if key in _materials:
        return _materials[key]
    material = bpy.data.materials.new(key)
    material.use_nodes = True
    material.use_backface_culling = not two_sided
    nodes, links = material.node_tree.nodes, material.node_tree.links
    bsdf = nodes["Principled BSDF"]
    bsdf.inputs["Roughness"].default_value = roughness
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Specular"].default_value = 0.3
    image = nodes.new("ShaderNodeTexImage")
    image.image = bpy.data.images.load(str(prepared(color, size, tint, name=key + "_color")))
    links.new(image.outputs["Color"], bsdf.inputs["Base Color"])
    if normal is not None:
        normal_image = nodes.new("ShaderNodeTexImage")
        normal_image.image = bpy.data.images.load(str(prepared(normal, normal_size, name=key + "_normal")))
        normal_image.image.colorspace_settings.name = "Non-Color"
        normal_map = nodes.new("ShaderNodeNormalMap")
        links.new(normal_image.outputs["Color"], normal_map.inputs["Color"])
        links.new(normal_map.outputs["Normal"], bsdf.inputs["Normal"])
    _materials[key] = material
    return material


def solid_material(key, rgb, roughness=0.6, metallic=0.0):
    if key in _materials:
        return _materials[key]
    material = bpy.data.materials.new(key)
    material.use_nodes = True
    bsdf = material.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = (*rgb, 1.0)
    bsdf.inputs["Roughness"].default_value = roughness
    bsdf.inputs["Metallic"].default_value = metallic
    _materials[key] = material
    return material


class Builder:
    """One mesh object built face by face. UVs are planar in metres divided by the face material's tile size."""

    def __init__(self, name):
        self.name = name
        self.bm = bmesh.new()
        self.uv = self.bm.loops.layers.uv.new("UVMap")
        self.materials = []
        self.tiles = []

    def _index(self, material, tile):
        if material not in self.materials:
            self.materials.append(material)
            self.tiles.append(tile)
        return self.materials.index(material)

    def face(self, points, material, tile=1.0):
        """A face through points (counter-clockwise seen from outside)."""
        index = self._index(material, tile)
        tile = self.tiles[index]
        verts = [self.bm.verts.new(p) for p in points]
        face = self.bm.faces.new(verts)
        face.material_index = index
        normal = np.zeros(3)
        for a, b in zip(points, points[1:] + points[:1]):  # Newell's method
            normal += np.array([(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])])
        normal /= max(np.linalg.norm(normal), 1e-9)
        if abs(normal[2]) > 0.7:
            axes = (np.array([1.0, 0, 0]), np.array([0, 1.0, 0]))
        else:
            across = np.array([-normal[1], normal[0], 0.0])
            across /= np.linalg.norm(across)
            axes = (across, np.array([0, 0, 1.0]))
        for loop, p in zip(face.loops, points):
            loop[self.uv].uv = (float(np.dot(p, axes[0])) / tile, float(np.dot(p, axes[1])) / tile)
        return face

    def box(self, x0, y0, z0, x1, y1, z1, material, tile=1.0, bottom=False, top_material=None):
        top = top_material or material
        self.face([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], top, tile)
        if bottom:
            self.face([(x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (x1, y0, z0)], material, tile)
        self.face([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], material, tile)  # south (-Y)
        self.face([(x1, y1, z0), (x0, y1, z0), (x0, y1, z1), (x1, y1, z1)], material, tile)  # north (+Y)
        self.face([(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)], material, tile)  # east (+X)
        self.face([(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)], material, tile)  # west (-X)

    def cylinder(self, x, y, z0, z1, r0, r1, material, tile=1.0, segments=12, top=True):
        ring = [(math.cos(2 * math.pi * i / segments), math.sin(2 * math.pi * i / segments)) for i in range(segments)]
        for (ca, sa), (cb, sb) in zip(ring, ring[1:] + ring[:1]):
            self.face([(x + r0 * ca, y + r0 * sa, z0), (x + r0 * cb, y + r0 * sb, z0),
                       (x + r1 * cb, y + r1 * sb, z1), (x + r1 * ca, y + r1 * sa, z1)], material, tile)
        if top:
            self.face([(x + r1 * c, y + r1 * s, z1) for c, s in ring], material, tile)

    def annulus(self, r0, r1, z, material, tile=1.0, segments=192):
        for i in range(segments):
            a, b = 2 * math.pi * i / segments, 2 * math.pi * (i + 1) / segments
            self.face([(r0 * math.cos(a), r0 * math.sin(a), z), (r1 * math.cos(a), r1 * math.sin(a), z),
                       (r1 * math.cos(b), r1 * math.sin(b), z), (r0 * math.cos(b), r0 * math.sin(b), z)], material, tile)

    def wall_ring(self, r, z0, z1, material, outward, tile=1.0, segments=192):
        for i in range(segments):
            a, b = 2 * math.pi * i / segments, 2 * math.pi * (i + 1) / segments
            if not outward:
                a, b = b, a
            self.face([(r * math.cos(a), r * math.sin(a), z0), (r * math.cos(b), r * math.sin(b), z0),
                       (r * math.cos(b), r * math.sin(b), z1), (r * math.cos(a), r * math.sin(a), z1)], material, tile)

    def gable_roof(self, x0, x1, y0, y1, eave, ridge, material, gable_material, tile=1.0, overhang=0.4):
        """Ridge along X over the rectangle; the gable triangles use the wall material."""
        ym = (y0 + y1) / 2
        X0, X1, Y0, Y1 = x0 - overhang, x1 + overhang, y0 - overhang, y1 + overhang
        drop = (ridge - eave) * overhang / (ym - y0)
        self.face([(X0, Y0, eave - drop), (X1, Y0, eave - drop), (X1, ym, ridge), (X0, ym, ridge)], material, tile)
        self.face([(X1, Y1, eave - drop), (X0, Y1, eave - drop), (X0, ym, ridge), (X1, ym, ridge)], material, tile)
        self.face([(x1, y0, eave), (x1, y1, eave), (x1, ym, ridge)], gable_material, tile)
        self.face([(x0, y1, eave), (x0, y0, eave), (x0, ym, ridge)], gable_material, tile)

    def hip_roof(self, x0, x1, y0, y1, eave, top, material, tile=1.0, overhang=0.5):
        X0, X1, Y0, Y1 = x0 - overhang, x1 + overhang, y0 - overhang, y1 + overhang
        inset = (Y1 - Y0) / 2
        ym = (Y0 + Y1) / 2
        self.face([(X0, Y0, eave), (X1, Y0, eave), (X1 - inset, ym, top), (X0 + inset, ym, top)], material, tile)
        self.face([(X1, Y1, eave), (X0, Y1, eave), (X0 + inset, ym, top), (X1 - inset, ym, top)], material, tile)
        self.face([(X1, Y0, eave), (X1, Y1, eave), (X1 - inset, ym, top)], material, tile)
        self.face([(X0, Y1, eave), (X0, Y0, eave), (X0 + inset, ym, top)], material, tile)

    def window(self, side, along, z0, width, height, material, depth=0.04):
        """A window pane just outside a wall: side is ('x', value, sign) or ('y', value, sign)."""
        axis, value, sign = side
        w0, w1 = along - width / 2, along + width / 2
        v = value + sign * depth
        if axis == "y":
            pts = [(w0, v, z0), (w1, v, z0), (w1, v, z0 + height), (w0, v, z0 + height)]
            if sign > 0:
                pts = [(w1, v, z0), (w0, v, z0), (w0, v, z0 + height), (w1, v, z0 + height)]
        else:
            pts = [(v, w1, z0), (v, w0, z0), (v, w0, z0 + height), (v, w1, z0 + height)]
            if sign > 0:
                pts = [(v, w0, z0), (v, w1, z0), (v, w1, z0 + height), (v, w0, z0 + height)]
        self.face(pts, material, 1.0)

    def object(self):
        mesh = bpy.data.meshes.new(self.name)
        self.bm.normal_update()
        self.bm.to_mesh(mesh)
        self.bm.free()
        for material in self.materials:
            mesh.materials.append(material)
        obj = bpy.data.objects.new(self.name, mesh)
        bpy.context.scene.collection.objects.link(obj)
        return obj


def pack_glb(gltf, glb):
    """A .gltf with one .bin buffer as a .glb whose images stay external files (as the loader expects)."""
    doc = json.loads(gltf.read_text())
    buffer = doc["buffers"][0]
    data = (gltf.parent / buffer.pop("uri")).read_bytes()
    buffer["byteLength"] = len(data)
    text = json.dumps(doc, separators=(",", ":")).encode()
    text += b" " * (-len(text) % 4)
    data += b"\x00" * (-len(data) % 4)
    total = 12 + 8 + len(text) + 8 + len(data)
    glb.write_bytes(struct.pack("<III", 0x46546C67, 2, total) + struct.pack("<II", len(text), 0x4E4F534A) + text
                    + struct.pack("<II", len(data), 0x004E4942) + data)


def triangles(objects):
    return sum(sum(len(p.vertices) - 2 for p in o.data.polygons) for o in objects)


def export_model(name, objects, provenance):
    """Exports objects as asset `name`: model.glb with textures/ beside it, meta.json and source.json with bounds."""
    folder = fresh_folder(name)
    temp = STAGE / "export" / name
    if temp.exists():
        shutil.rmtree(temp)
    temp.mkdir(parents=True)
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objects:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = objects[0]
    bpy.ops.export_scene.gltf(filepath=str(temp / "model.gltf"), export_format="GLTF_SEPARATE",
                              use_selection=True, export_texture_dir="textures", export_image_format="AUTO",
                              export_apply=True, export_yup=True, export_animations=False, export_cameras=False,
                              export_lights=False, export_colors=False, export_tangents=False, export_extras=False)
    pack_glb(temp / "model.gltf", folder / "model.glb")
    if (temp / "textures").exists():
        shutil.copytree(temp / "textures", folder / "textures")
    corners = []
    for obj in objects:
        coords = np.empty(len(obj.data.vertices) * 3, np.float32)
        obj.data.vertices.foreach_get("co", coords)
        coords = coords.reshape(-1, 3) @ np.array(obj.matrix_world)[:3, :3].T + np.array(obj.matrix_world)[:3, 3]
        corners += [coords.min(0), coords.max(0)]
    lo, hi = np.min(corners, 0), np.max(corners, 0)
    bounds = {"min": [float(lo[0]), float(lo[2]), float(-hi[1])], "max": [float(hi[0]), float(hi[2]), float(-lo[1])]}
    write_meta(folder, "MODEL", {"file": "model.glb", "format": "GLTF", "binary": True, "materials": []})
    write_json(folder / "source.json", dict(provenance, triangles=triangles(objects), bounds=bounds,
                                            sha256=sha256(folder / "model.glb")))
    print(f"   {name}: {triangles(objects)} triangles, bounds {bounds}")


def generated(description, materials):
    return {"generator": GENERATOR, "license": "CC0-1.0", "description": description,
            "materials": {k: AMBIENTCG + v for k, v in materials.items()}}


# --- the pad --------------------------------------------------------------------------------------------------------

def build_pad():
    reset_scene()
    b = Builder("pad")
    asphalt = tex_material("pad_asphalt", ambient("Road012A"), 1024, normal=ambient("Road012A", "NormalGL"))
    border = tex_material("pad_border", ambient("Road012A"), 512, tint=(0.6, 0.6, 0.62))
    paint = tex_material("pad_paint", ambient("Plaster002"), 512, tint=(1.0, 0.99, 0.95), roughness=0.7)
    inner = plan.PAD_RADIUS - plan.BORDER_WIDTH
    b.face([(inner * math.cos(2 * math.pi * i / 192), inner * math.sin(2 * math.pi * i / 192), 0.01) for i in range(192)],
           asphalt, 4.0)
    b.annulus(inner, plan.PAD_RADIUS, 0.03, border, 2.0)
    b.wall_ring(inner, 0.01, 0.03, border, outward=False, tile=2.0)
    b.wall_ring(plan.PAD_RADIUS, -0.05, 0.03, border, outward=True, tile=2.0)
    for radius in plan.RING_RADII:
        b.annulus(radius - plan.RING_WIDTH / 2, radius + plan.RING_WIDTH / 2, 0.013, paint, 1.0, segments=256)
    b.annulus(plan.PILOT_CIRCLE_RADIUS - plan.RING_WIDTH / 2, plan.PILOT_CIRCLE_RADIUS + plan.RING_WIDTH / 2, 0.013,
              paint, 1.0, segments=48)
    export_model("model_airfield_pad", [b.object()], dict(generated(
        "Paved flying circle decal: 50 m dark asphalt disc with a darker raised 0.3 m border, white rings at the "
        "15, 18 and 21 m line radii and a 1.5 m pilot circle. Lies 1 cm above the level terrain.",
        {"asphalt": "Road012A", "paint": "Plaster002"}), estimate="Diameter is an estimate (image: 48-55 m)."))


# --- buildings ------------------------------------------------------------------------------------------------------

GLASS = (0.07, 0.09, 0.11)


def build_utility():
    reset_scene()
    b = Builder("utility")
    walls = tex_material("utility_walls", ambient("Plaster002"), 1024, tint=(0.96, 0.94, 0.88))
    plinth = tex_material("utility_plinth", ambient("Concrete036"), 512)
    roof = tex_material("utility_roof", ambient("CorrugatedSteel005"), 1024, normal=ambient("CorrugatedSteel005", "NormalGL"),
                        roughness=0.5, metallic=0.4)
    door = tex_material("utility_door", ambient("Planks037A"), 512, tint=(0.55, 0.6, 0.55))
    glass = solid_material("glass", GLASS, 0.15, 0.1)
    w, h, d = plan.UTILITY["size"]
    x0, x1, y0, y1 = -w / 2, w / 2, -d / 2, d / 2
    b.box(x0 - 0.05, y0 - 0.05, -0.3, x1 + 0.05, y1 + 0.05, 0.3, plinth, 1.0)
    b.box(x0, y0, 0.3, x1, y1, h, walls, 2.0)
    b.box(x0 - 0.35, y0 - 0.35, h, x1 + 0.35, y1 + 0.35, h + 0.15, roof, 2.0, bottom=True)
    # the pad is west: two doors (WC, staff room) and the staff room window face it
    b.window(("x", x0, -1), -2.0, 0.3, 0.9, 2.0, door)
    b.window(("x", x0, -1), 1.2, 0.3, 0.9, 2.0, door)
    b.window(("x", x0, -1), 2.9, 1.1, 1.0, 1.0, glass)
    b.window(("y", y1, 1), 0.0, 1.1, 1.2, 1.0, glass)
    b.window(("x", x1, 1), -2.0, 2.0, 0.6, 0.4, glass)
    b.window(("x", x1, 1), -0.6, 2.0, 0.6, 0.4, glass)
    b.cylinder(1.2, -2.5, h + 0.15, h + 1.0, 0.06, 0.06, roof, 1.0, segments=8)
    export_model("model_airfield_utility", [b.object()], dict(generated(
        "Small single-storey utility building east of the circle: WC and staff room (light plaster walls, grey "
        "corrugated roof, two doors and a window facing the pad).",
        {"walls": "Plaster002", "plinth": "Concrete036", "roof": "CorrugatedSteel005", "doors": "Planks037A"}),
        use="WC and staff room", estimate="Footprint 4 x 8 m, height 3 m: estimates."))


def build_dk():
    reset_scene()
    b = Builder("dk_plamya")
    walls = tex_material("dk_walls", ambient("Concrete048"), 1024, tint=(1.0, 0.9, 0.76))
    trim = tex_material("dk_trim", ambient("Plaster002"), 512)
    plinth = tex_material("dk_plinth", ambient("Concrete036"), 512)
    roof = tex_material("dk_roof", ambient("RoofingTiles006"), 1024, tint=(0.62, 0.42, 0.32),
                        normal=ambient("RoofingTiles006", "NormalGL"))
    glass = solid_material("glass", GLASS, 0.15, 0.1)
    x0, x1, y0, y1, h = -18, 18, -12, 12, 11
    b.box(x0 - 0.2, y0 - 0.2, -0.5, x1 + 0.2, y1 + 0.2, 0.8, plinth, 1.5)
    b.box(x0, y0, 0.8, x1, y1, h, walls, 3.0)
    b.box(x0 - 0.3, y0 - 0.3, h, x1 + 0.3, y1 + 0.3, h + 0.4, trim, 2.0, bottom=True)
    b.hip_roof(x0, x1, y0, y1, h + 0.4, h + 5.0, roof, 2.0, overhang=0.6)
    for z in (2.2, 6.6):
        for i in range(9):
            along = x0 + 2.5 + i * 3.875
            if z < 5 and abs(along) < 8.5:
                continue  # behind the portico
            b.window(("y", y0, -1), along, z, 1.6, 3.2, glass)
            b.window(("y", y1, 1), along, z, 1.6, 3.2, glass)
        for i in range(5):
            along = y0 + 2.8 + i * 4.6
            b.window(("x", x0, -1), along, z, 1.6, 3.2, glass)
            b.window(("x", x1, 1), along, z, 1.6, 3.2, glass)
    # the portico faces south, toward the street
    b.box(-8.5, y0 - 4.5, -0.5, 8.5, y0, 1.0, plinth, 1.5)
    for i in range(6):
        b.cylinder(-7.5 + i * 3.0, y0 - 3.6, 1.0, 9.0, 0.42, 0.36, trim, 1.0, segments=12, top=False)
    b.box(-8.5, y0 - 4.5, 9.0, 8.5, y0, 10.2, trim, 2.0, bottom=True)
    b.window(("y", y0, -1), 0.0, 1.0, 3.0, 3.5, glass)
    b.window(("y", y0, -1), -5.0, 1.0, 2.0, 3.0, glass)
    b.window(("y", y0, -1), 5.0, 1.0, 2.0, 3.0, glass)
    export_model("model_airfield_dk_plamya", [b.object()], dict(generated(
        "Approximation of the DK \"Plamya\" house of culture: beige two-storey hall with a brown hipped roof and a "
        "columned portico facing the street.", {"walls": "Concrete048", "trim": "Plaster002", "plinth": "Concrete036",
                                                "roof": "RoofingTiles006"}),
        identity="Intended representation of DK \"Plamya\" from the brief; not an architectural replica."))


def build_warehouse():
    reset_scene()
    b = Builder("warehouse")
    walls = tex_material("warehouse_walls", ambient("Concrete034"), 1024, tint=(0.92, 0.92, 0.9))
    roof = tex_material("warehouse_roof", ambient("CorrugatedSteel007A"), 1024, tint=(0.45, 0.7, 1.0),
                        normal=ambient("CorrugatedSteel007A", "NormalGL"), roughness=0.45, metallic=0.4)
    doors = tex_material("warehouse_doors", ambient("CorrugatedSteel005"), 512, roughness=0.5, metallic=0.4)
    glass = solid_material("glass", GLASS, 0.15, 0.1)
    x0, x1, y0, y1, h = -30, 30, -8, 8, 6
    b.box(x0, y0, -0.5, x1, y1, h, walls, 3.0)
    b.gable_roof(x0, x1, y0, y1, h, h + 1.8, roof, walls, 2.0, overhang=0.5)
    for x in (-21, -7, 7, 21):
        b.window(("y", y0, -1), x, 0.0, 4.0, 4.5, doors)
    for i in range(14):
        b.window(("y", y1, 1), x0 + 3 + i * 4.2, 3.8, 2.4, 1.2, glass)
    export_model("model_airfield_warehouse", [b.object()], dict(generated(
        "Long industrial warehouse with a bright blue corrugated metal roof, west-southwest of the field, parallel "
        "to the railway.", {"walls": "Concrete034", "roof": "CorrugatedSteel007A (tinted blue)", "doors": "CorrugatedSteel005"})))


def build_long_building():
    reset_scene()
    b = Builder("long_building")
    walls = tex_material("long_walls", ambient("Plaster002"), 1024, tint=(0.86, 0.83, 0.76))
    roof = tex_material("long_roof", ambient("Concrete036"), 512, tint=(0.7, 0.7, 0.7))
    door = tex_material("long_door", ambient("CorrugatedSteel005"), 512, roughness=0.5, metallic=0.4)
    glass = solid_material("glass", GLASS, 0.15, 0.1)
    x0, x1, y0, y1, h = -22, 22, -5, 5, 4
    b.box(x0, y0, -0.5, x1, y1, h, walls, 2.5)
    b.box(x0 - 0.25, y0 - 0.25, h, x1 + 0.25, y1 + 0.25, h + 0.3, roof, 2.0, bottom=True)
    for i in range(11):
        b.window(("y", y1, 1), x0 + 2.5 + i * 3.9, 1.2, 1.6, 1.3, glass)
        b.window(("y", y0, -1), x0 + 2.5 + i * 3.9, 1.2, 1.6, 1.3, glass)
    for x in (-15, 9):
        b.window(("y", y1, 1), x + 1.95, 0.0, 1.2, 2.2, door)
    export_model("model_airfield_long_building", [b.object()], dict(generated(
        "Long single-storey service building south-east of the field.",
        {"walls": "Plaster002", "roof": "Concrete036", "doors": "CorrugatedSteel005"})))


def build_apartment_block():
    reset_scene()
    b = Builder("apartment_block")
    walls = tex_material("apartment_walls", ambient("Concrete034"), 1024, tint=(0.93, 0.9, 0.84))
    plinth = tex_material("apartment_plinth", ambient("Concrete036"), 512)
    roof = tex_material("apartment_roof", ambient("Concrete036"), 512, tint=(0.6, 0.6, 0.6))
    door = tex_material("apartment_door", ambient("Planks037A"), 512, tint=(0.6, 0.45, 0.35))
    glass = solid_material("glass", GLASS, 0.15, 0.1)
    x0, x1, y0, y1, h = -32, 32, -6, 6, 15
    b.box(x0, y0, -0.5, x1, y1, 0.8, plinth, 1.5)
    b.box(x0, y0, 0.8, x1, y1, h, walls, 3.0, top_material=roof)
    b.box(x0, y0, h, x1, y1, h + 0.6, walls, 3.0, top_material=roof)
    for floor in range(5):
        z = 1.7 + floor * 2.8
        for i in range(18):
            along = x0 + 2.0 + i * 3.53
            b.window(("y", y0, -1), along, z, 1.4, 1.45, glass)
            if floor > 0 or i % 4 != 2:
                b.window(("y", y1, 1), along, z, 1.4, 1.45, glass)
        for along in (-3.0, 3.0):
            b.window(("x", x0, -1), along, z, 1.0, 1.45, glass)
            b.window(("x", x1, 1), along, z, 1.0, 1.45, glass)
    for i in range(4):
        along = x0 + 2.0 + (4 * i + 2) * 3.53
        b.window(("y", y1, 1), along, 0.8, 1.2, 2.1, door)
        b.box(along - 1.0, y1, 3.0, along + 1.0, y1 + 1.2, 3.12, plinth, 1.0, bottom=True)
    export_model("model_airfield_apartment_block", [b.object()], dict(generated(
        "Five-storey panel apartment block (64 x 12 m) of the eastern residential background.",
        {"walls": "Concrete034", "plinth and roof": "Concrete036", "doors": "Planks037A"})))


def build_house(name, wall_tint, roof_kind):
    reset_scene()
    b = Builder(name)
    walls = tex_material(name + "_walls", ambient("Plaster002"), 512, tint=wall_tint)
    plinth = tex_material("house_plinth", ambient("Concrete036"), 512)
    if roof_kind == "tiles":
        roof = tex_material(name + "_roof", ambient("RoofingTiles006"), 512, tint=(0.75, 0.5, 0.4),
                            normal=ambient("RoofingTiles006", "NormalGL"), normal_size=256)
    else:
        roof = tex_material(name + "_roof", ambient("CorrugatedSteel005"), 512, roughness=0.5, metallic=0.4,
                            normal=ambient("CorrugatedSteel005", "NormalGL"), normal_size=256)
    door = tex_material("house_door", ambient("Planks037A"), 256)
    glass = solid_material("glass", GLASS, 0.15, 0.1)
    x0, x1, y0, y1, h = -4.5, 4.5, -3.5, 3.5, 3.0
    b.box(x0, y0, -0.5, x1, y1, 0.4, plinth, 1.0)
    b.box(x0, y0, 0.4, x1, y1, h, walls, 2.0)
    b.gable_roof(x0, x1, y0, y1, h, h + 2.3, roof, walls, 1.5, overhang=0.4)
    for along in (-2.8, 0.0, 2.8):
        b.window(("y", y0, -1), along, 1.2, 1.1, 1.2, glass)
    for along in (-2.0, 2.0):
        b.window(("y", y1, 1), along, 1.2, 1.1, 1.2, glass)
    b.window(("x", x1, 1), -1.5, 0.4, 0.9, 2.0, door)
    b.window(("x", x0, -1), 0.0, 1.2, 1.0, 1.1, glass)
    export_model(name, [b.object()], dict(generated(
        "Single-storey private house with a gable roof, for the residential edges and gardens.",
        {"walls": "Plaster002", "plinth": "Concrete036", "roof": "RoofingTiles006" if roof_kind == "tiles" else "CorrugatedSteel005",
         "door": "Planks037A"})))


def build_buildings():
    build_utility()
    build_dk()
    build_warehouse()
    build_long_building()
    build_apartment_block()
    build_house("model_airfield_house_a", (0.97, 0.9, 0.72), "steel")
    build_house("model_airfield_house_b", (0.78, 0.84, 0.88), "tiles")


# --- railway and street furniture -----------------------------------------------------------------------------------

RAIL_SEGMENT = 50.0


def build_railway():
    reset_scene()
    b = Builder("railway")
    rail = tex_material("rail_steel", ambient("Metal041B"), 512, roughness=0.45, metallic=0.7)
    sleeper = tex_material("rail_sleeper", ambient("Concrete036"), 512, tint=(0.85, 0.83, 0.8))
    pole = tex_material("catenary_pole", ambient("Concrete036"), 512)
    wire = solid_material("catenary_wire", (0.16, 0.14, 0.12), 0.4, 0.8)
    half = RAIL_SEGMENT / 2
    for track in (-plan.TRACK_SPACING / 2, plan.TRACK_SPACING / 2):
        x = -half + 0.275
        while x < half:
            b.box(x - 0.14, track - 1.35, -0.05, x + 0.14, track + 1.35, 0.15, sleeper, 1.0)
            x += 0.55
        for side in (-0.795, 0.795):
            b.box(-half, track + side - 0.0375, 0.15, half, track + side + 0.0375, 0.33, rail, 1.0)
        # overhead line: contact wire, messenger wire and droppers
        b.box(-half, track - 0.007, 5.993, half, track + 0.007, 6.007, wire, 1.0, bottom=True)
        b.box(-half, track - 0.008, 7.292, half, track + 0.008, 7.308, wire, 1.0, bottom=True)
        x = -half + 3.125
        while x < half:
            b.box(x - 0.005, track - 0.005, 6.0, x + 0.005, track + 0.005, 7.3, wire, 1.0)
            x += 6.25
    # one catenary pole per segment on the north side, with cantilevers over both tracks
    y = plan.TRACK_SPACING / 2 + 3.3
    b.cylinder(0.0, y, -0.3, 9.6, 0.22, 0.16, pole, 1.0, segments=10)
    for z in (6.25, 7.55):
        b.box(-0.04, -plan.TRACK_SPACING / 2 - 0.6, z - 0.04, 0.04, y, z + 0.04, wire, 1.0, bottom=True)
    export_model("model_airfield_railway", [b.object()], dict(generated(
        "50 m segment of the double-track electrified railway: concrete sleepers, rails 1520 mm gauge, tracks "
        "4.1 m apart, one catenary pole with cantilevers, contact and messenger wires. Origin at sleeper bottom, "
        "track along +X.", {"rails": "Metal041B", "sleepers and pole": "Concrete036"}),
        candidates="The supplied Sketchfab track needs a login; a generated segment is used instead."))


def build_street_lamp():
    reset_scene()
    b = Builder("street_lamp")
    pole = tex_material("lamp_pole", ambient("Concrete034"), 512)
    metal = solid_material("lamp_metal", (0.32, 0.33, 0.34), 0.5, 0.6)
    lens = solid_material("lamp_lens", (0.85, 0.85, 0.8), 0.3)
    b.cylinder(0.0, 0.0, -0.3, 9.0, 0.13, 0.08, pole, 1.0, segments=10)
    b.box(-0.04, 0.0, 8.75, 0.04, 1.8, 8.83, metal, 1.0, bottom=True)
    b.box(-0.14, 1.55, 8.6, 0.14, 2.15, 8.78, metal, 1.0)
    b.box(-0.11, 1.6, 8.58, 0.11, 2.1, 8.6, lens, 1.0, bottom=True)
    export_model("model_airfield_street_lamp", [b.object()], dict(generated(
        "Concrete street lighting pole with a cantilever luminaire (arm along +Y, toward the street when placed).",
        {"pole": "Concrete034"}), candidates="The supplied Sketchfab lamppost needs a login; a generated pole is used instead."))


def build_model_stand():
    reset_scene()
    b = Builder("model_stand")
    wood = tex_material("stand_wood", ambient("Planks037A"), 512)
    felt = solid_material("stand_felt", (0.18, 0.3, 0.2), 0.95)
    for x in (-0.55, 0.55):
        for y in (-0.22, 0.22):
            b.box(x - 0.03, y - 0.03, 0.0, x + 0.03, y + 0.03, 0.78, wood, 1.0)
        b.box(x - 0.04, -0.26, 0.2, x + 0.04, 0.26, 0.26, wood, 1.0, bottom=True)
        b.box(x - 0.06, -0.24, 0.78, x + 0.06, 0.24, 0.84, felt, 1.0, bottom=True)
    b.box(-0.6, -0.03, 0.2, 0.6, 0.03, 0.26, wood, 1.0, bottom=True)
    export_model("model_airfield_model_stand", [b.object()], dict(generated(
        "Wooden model stand (two padded cradles) for preparing control-line models.", {"wood": "Planks037A"})))


def build_props():
    build_railway()
    build_street_lamp()
    build_model_stand()
    for name, source, use in (("model_airfield_table_picnic", "wooden_picnic_table", "worktable with benches"),
                              ("model_airfield_table_work", "painted_wooden_table", "worktable")):
        reset_scene()
        objects = import_polyhaven(source)
        for obj in objects:
            for slot in obj.material_slots:
                slot.material = rebuilt_material(slot.material, source, 1024)
        export_model(name, objects, polyhaven_provenance(source, use, "Textures resized to 1024 px (normal 512 px); ARM map dropped."))


# --- Poly Haven models and vegetation -------------------------------------------------------------------------------

def polyhaven_provenance(source, use, note):
    info_file = CACHE / "polyhaven" / source / "info.json"
    authors = None
    if info_file.exists():
        authors = json.loads(info_file.read_text()).get("authors")
    gltf = next((CACHE / "polyhaven" / source).glob("*.gltf"))
    return {"author": authors or "Poly Haven", "license": "CC0-1.0", "sourcePage": POLYHAVEN + source,
            "sourceFile": gltf.name, "sha256Source": sha256(gltf), "use": use, "note": note}


def import_polyhaven(source, keep=None):
    """Imports a cached Poly Haven glTF; returns its mesh objects (only `keep` if given), parents cleared."""
    gltf = next((CACHE / "polyhaven" / source).glob("*.gltf"))
    bpy.ops.import_scene.gltf(filepath=str(gltf))
    meshes = []
    for obj in list(bpy.data.objects):
        if obj.type == "MESH" and (keep is None or obj.name == keep):
            matrix = obj.matrix_world.copy()
            obj.parent = None
            obj.matrix_world = matrix
            meshes.append(obj)
    for obj in list(bpy.data.objects):
        if obj not in meshes:
            bpy.data.objects.remove(obj)
    return meshes


def rebuilt_material(original, source, size, two_sided=False):
    """The original's colour and normal maps, resized, without its ARM map; opaque (leaves are real geometry)."""
    files = [Path(bpy.path.abspath(n.image.filepath)) for n in original.node_tree.nodes
             if n.type == "TEX_IMAGE" and n.image and n.image.filepath]
    color = next((f for f in files if "_diff_" in f.name), None)
    normal = next((f for f in files if "_nor_gl_" in f.name), None)
    if color is None:
        raise SystemExit(f"{source}: no colour map in {original.name}")
    key = original.name.replace(".", "_")
    return tex_material(key, color, size, normal=normal, normal_size=size // 2, roughness=0.85, two_sided=two_sided)


def mesh_arrays(obj):
    me = obj.data
    me.calc_loop_triangles()
    verts = np.empty(len(me.vertices) * 3, np.float32)
    me.vertices.foreach_get("co", verts)
    tris = np.empty(len(me.loop_triangles) * 3, np.int64)
    me.loop_triangles.foreach_get("vertices", tris)
    loops = np.empty(len(me.loop_triangles) * 3, np.int64)
    me.loop_triangles.foreach_get("loops", loops)
    uv = np.empty(len(me.loops) * 2, np.float32)
    me.uv_layers.active.data.foreach_get("uv", uv)
    return verts.reshape(-1, 3), tris.reshape(-1, 3), uv.reshape(-1, 2)[loops].reshape(-1, 3, 2)


def triangle_islands(tris, vertex_count):
    """Island id per triangle: connected components over shared vertices (label propagation with pointer jumping)."""
    labels = np.arange(vertex_count)
    a = tris.ravel()
    b = np.roll(tris, -1, axis=1).ravel()
    while True:
        low = np.minimum(labels[a], labels[b])
        new = labels.copy()
        np.minimum.at(new, a, low)
        np.minimum.at(new, b, low)
        new = new[new]
        if np.array_equal(new, labels):
            break
        labels = new
    return labels[tris[:, 0]]


def mesh_from_arrays(name, verts, tris, uvs, material, smooth):
    used, inverse = np.unique(tris.ravel(), return_inverse=True)
    me = bpy.data.meshes.new(name)
    me.vertices.add(len(used))
    me.vertices.foreach_set("co", verts[used].ravel())
    me.loops.add(len(tris) * 3)
    me.loops.foreach_set("vertex_index", inverse)
    me.polygons.add(len(tris))
    me.polygons.foreach_set("loop_start", np.arange(0, len(tris) * 3, 3))
    me.polygons.foreach_set("loop_total", np.full(len(tris), 3))
    me.polygons.foreach_set("use_smooth", np.full(len(tris), smooth))
    me.uv_layers.new(name="UVMap").data.foreach_set("uv", uvs.reshape(-1, 2).ravel())
    me.update(calc_edges=True)
    me.validate()
    me.materials.append(material)
    obj = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(obj)
    return obj


def decimate(obj, ratio):
    if ratio >= 1.0:
        return
    bpy.context.view_layer.objects.active = obj
    modifier = obj.modifiers.new("decimate", "DECIMATE")
    modifier.ratio = ratio
    bpy.ops.object.modifier_apply(modifier=modifier.name)


TREES = {
    # asset: source, object, target height (m), leaves kept, leaf scale, leaf decimation,
    #        branch islands kept, branch decimation, trunk decimation
    "model_airfield_tree_broadleaf": ("island_tree_01", None, 9.5, 0.13, 2.6, 0.12, 300, 0.2, 0.1),
    "model_airfield_tree_acacia": ("jacaranda_tree", None, 12.0, 0.08, 2.8, 0.12, 200, 0.2, 0.03),
    "model_airfield_tree_young": ("tree_small_02", None, 7.5, 0.1, 2.5, 0.12, 150, 0.25, 0.1),
    "model_airfield_bush_tall": ("island_tree_02", None, 3.2, 0.09, 2.3, 0.12, 120, 0.25, 0.08),
    "model_airfield_bush_low": ("island_tree_03", None, 1.8, 0.06, 2.4, 0.12, 80, 0.25, 0.06),
}
USES = {
    "model_airfield_tree_broadleaf": "broadleaf tree (elm/oak stand-in)",
    "model_airfield_tree_acacia": "wide-crowned tree (white acacia stand-in)",
    "model_airfield_tree_young": "young slender broadleaf",
    "model_airfield_bush_tall": "tall steppe bush",
    "model_airfield_bush_low": "low steppe bush",
}


def build_tree(name, source, keep, height, leaf_keep, leaf_scale, leaf_ratio, branch_islands, branch_ratio, trunk_ratio):
    reset_scene()
    rng = np.random.default_rng(sum(map(ord, name)))  # deterministic per asset
    original = import_polyhaven(source, keep)[0]
    bpy.context.view_layer.objects.active = original
    original.select_set(True)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    verts_all, _, _ = mesh_arrays(original)
    base = verts_all[:, 2].min()
    foot = verts_all[verts_all[:, 2] < base + 0.5]
    centre = np.array([foot[:, 0].mean(), foot[:, 1].mean(), base], np.float32)
    scale = height / (verts_all[:, 2].max() - base)
    parts = []
    for index, material in enumerate(original.data.materials):
        verts, tris, uvs = mesh_arrays(original)
        mask = np.array([p.material_index == index for p in original.data.loop_triangles])
        tris, uvs = tris[mask], uvs[mask]
        if len(tris) == 0:
            continue
        verts = (verts - centre) * scale
        kind = "leaves" if ("leaves" in material.name or "twig" in material.name) else \
            "branches" if "branches" in material.name else "trunk"
        islands = triangle_islands(tris, len(verts))
        ids, sizes = np.unique(islands, return_counts=True)
        if kind == "leaves":
            chosen = ids[rng.random(len(ids)) < leaf_keep]
            keep_tri = np.isin(islands, chosen)
            tris, uvs, islands = tris[keep_tri], uvs[keep_tri], islands[keep_tri]
            # enlarge each kept leaf about its own centre, so the thinned crown keeps its coverage
            vert_island = np.full(len(verts), -1)
            vert_island[tris.ravel()] = np.repeat(islands, 3)
            used = vert_island >= 0
            sums = np.zeros((len(verts), 3))
            counts = np.zeros(len(verts))
            np.add.at(sums, vert_island[used], verts[used])
            np.add.at(counts, vert_island[used], 1)
            centres = sums[vert_island[used]] / counts[vert_island[used], None]
            verts = verts.copy()
            verts[used] = centres + (verts[used] - centres) * leaf_scale
            ratio, size, two_sided, smooth = leaf_ratio, 1024, True, False
        elif kind == "branches":
            chosen = ids[np.argsort(sizes)[::-1][:branch_islands]]
            keep_tri = np.isin(islands, chosen)
            tris, uvs = tris[keep_tri], uvs[keep_tri]
            ratio, size, two_sided, smooth = branch_ratio, 512, True, True
        else:
            ratio, size, two_sided, smooth = trunk_ratio, 1024, False, True
        part = mesh_from_arrays(f"{name}_{kind}_{index}", verts, tris, uvs,
                                rebuilt_material(material, source, size, two_sided), smooth)
        decimate(part, ratio)
        print(f"   {name} {kind} ({material.name}): {triangles([part])} triangles")
        parts.append(part)
    bpy.data.objects.remove(original)
    export_model(name, parts, polyhaven_provenance(source, USES[name], (
        f"Reduced for the game: {leaf_keep:.1%} of the leaf clusters kept and enlarged x{leaf_scale}, the "
        f"{branch_islands} largest branch pieces kept, then collapsed (leaves x{leaf_ratio}, branches x{branch_ratio}, "
        f"trunk x{trunk_ratio}); scaled to {height} m; textures resized, ARM map dropped, leaves opaque and two-sided.")))


def build_vegetation():
    only = os.environ.get("ABYSSUS_AIRFIELD_ONLY")
    for name, spec in TREES.items():
        if only and only not in name:
            continue
        build_tree(name, *spec)


STEPS = {"ground": build_ground, "pad": build_pad, "buildings": build_buildings, "props": build_props,
         "vegetation": build_vegetation}


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    steps = argv or list(STEPS)
    for step in steps:
        print("== step", step)
        STEPS[step]()


if __name__ == "__main__":
    main()
