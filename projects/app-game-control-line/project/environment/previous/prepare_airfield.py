"""Package downloaded CC0 models and stage field edits without writing a scene.

The archives are downloaded separately from the official URLs below.
Staged edits must be applied with the project's editSceneJson writer.
"""
from pathlib import Path, PurePosixPath
import hashlib
import json
import math
import random
import struct
import time
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1] / "ControlLine"
OUT = Path(__file__).resolve().parent
SOURCES = {
    "nature": ("https://kenney.nl/assets/nature-kit", "https://kenney.nl/media/pages/assets/nature-kit/37ac38a37b-1677698939/kenney_nature-kit.zip", "Models/GLTF format"),
    "industrial": ("https://kenney.nl/assets/city-kit-industrial", "https://kenney.nl/media/pages/assets/city-kit-industrial/0ec35b139d-1788171848/kenney_city-kit-industrial_2.0.zip", "Models/GLB format"),
    "commercial": ("https://kenney.nl/assets/city-kit-commercial", "https://kenney.nl/media/pages/assets/city-kit-commercial/a742d900eb-1753115042/kenney_city-kit-commercial_2.1.zip", "Models/GLB format"),
}
MODELS = {
    "model_airfield_oak": ("nature", "tree_oak_dark"),
    "model_airfield_poplar": ("nature", "tree_thin_dark"),
    "model_airfield_broadleaf": ("nature", "tree_default_dark"),
    "model_airfield_hall": ("industrial", "building-a"),
    "model_airfield_shed": ("industrial", "building-k"),
    "model_airfield_garages": ("industrial", "building-s"),
    "model_airfield_apartments": ("commercial", "building-j"),
}


def glb_json(data):
    magic, version, size = struct.unpack_from("<III", data)
    assert magic == 0x46546C67 and version == 2 and size == len(data)
    length, kind = struct.unpack_from("<II", data, 12)
    assert kind == 0x4E4F534A
    return json.loads(data[20:20 + length])


def meta(name, asset_type, additional):
    return {"format": "abyssus", "formatVersion": 1, "version": 1,
            "lastModified": int(time.time() * 1000),
            "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, "abyssus/control-line/" + name)),
            "type": asset_type, "additional": additional}


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n")


def main():
    bounds = {}
    for folder, (kit, source_name) in MODELS.items():
        page, url, prefix = SOURCES[kit]
        archive = Path("/private/tmp/abyssus-" + kit + "-kit.zip")
        with zipfile.ZipFile(archive) as z:
            data = z.read(prefix + "/" + source_name + ".glb")
            doc = glb_json(data)
            target = ROOT / "assets" / folder
            target.mkdir(exist_ok=True)
            (target / "model.glb").write_bytes(data)
            for image in doc.get("images", []):
                uri = image.get("uri")
                if uri and not uri.startswith("data:"):
                    relative = PurePosixPath(uri)
                    assert not relative.is_absolute() and ".." not in relative.parts
                    image_path = target / str(relative)
                    image_path.parent.mkdir(parents=True, exist_ok=True)
                    image_path.write_bytes(z.read(prefix + "/" + uri))
            (target / "License.txt").write_bytes(z.read("License.txt"))
            positions = [doc["accessors"][p["attributes"]["POSITION"]]
                         for m in doc["meshes"] for p in m["primitives"]]
            low = [min(a["min"][i] for a in positions) for i in range(3)]
            high = [max(a["max"][i] for a in positions) for i in range(3)]
            bounds[folder] = {"min": low, "max": high, "size": [high[i] - low[i] for i in range(3)]}
            write_json(target / "meta.json", meta(folder, "MODEL", {"file": "model.glb", "format": "GLTF", "binary": True, "materials": []}))
            write_json(target / "source.json", {"author": "Kenney", "license": "CC0-1.0", "sourcePage": page,
                       "download": url, "sourceModel": prefix + "/" + source_name + ".glb",
                       "sha256": hashlib.sha256(data).hexdigest(), "archiveSha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
                       "bounds": bounds[folder], "note": "Unmodified downloaded model; scene scaling is approximate."})

    # Ground for the reference's wider neighborhood, below the site surface.
    surround = ROOT / "assets" / "terrain_airfield_surroundings"
    surround.mkdir(exist_ok=True)
    (surround / "terrain.data").write_bytes(struct.pack(">f", 1.0) * (201 * 201))
    grass_meta = json.loads((ROOT / "assets/texture_grass/meta.json").read_text())
    assert grass_meta["format"] == "abyssus" and type(grass_meta["formatVersion"]) is int and grass_meta["formatVersion"] == 1
    write_json(surround / "meta.json", meta(surround.name, "TERRAIN", {"terrainFile": "terrain.data", "size": 600, "uv": 120.0,
               "splatMap": None, "splatBase": grass_meta["uuid"], "splatR": None, "splatG": None, "splatB": None, "splatA": None}))

    entities = {}
    placements = []

    def add(name, asset, x, z, size, yaw=0, y=0, model=True):
        entity_id = str(100 + len(entities))
        angle = math.radians(yaw) / 2
        scale = [size[i] / bounds[asset]["size"][i] for i in range(3)] if model else [1, 1, 1]
        # Center asymmetric downloaded meshes by their measured X/Z bounds.
        center = [(bounds[asset]["min"][i] + bounds[asset]["max"][i]) / 2 * scale[i] for i in range(3)] if model else [0, 0, 0]
        radians = math.radians(yaw)
        px = x - math.cos(radians) * center[0] - math.sin(radians) * center[2]
        pz = z + math.sin(radians) * center[0] - math.cos(radians) * center[2]
        py = y - bounds[asset]["min"][1] * scale[1] if model else y
        components = {
            "NameComponent": {"name": name},
            "TypeComponent": {"type": "OBJECT" if model else "TERRAIN"},
            "PositionComponent": {"localPosition": dict(zip(("x", "y", "z"), [round(px, 5), round(py, 5), round(pz, 5)])),
                                  "localRotation": {"x": 0, "y": round(math.sin(angle), 7), "z": 0, "w": round(math.cos(angle), 7)},
                                  "localScale": dict(zip(("x", "y", "z"), [round(s, 5) for s in scale]))},
            "RenderComponent": {"renderable": {"kind": "asset", "shaderKey": "defaultShader" if model else "terrain",
                                "asset": {"type": "MODEL" if model else "TERRAIN", "assetName": asset}}},
        }
        entities[entity_id] = {"components": components}
        placements.append({"id": entity_id, "name": name, "asset": asset, "centerX": x, "centerZ": z,
                           "width": size[0], "height": size[1], "depth": size[2], "yaw": yaw})

    add("Airfield surroundings", "terrain_airfield_surroundings", -300, -300, [600, 0, 600], y=-1.02, model=False)
    # Immediate sheds follow the generated ground's eastern paved footprints.
    for name, asset, x, z, dimensions in [
        ("Field workshop", "model_airfield_shed", 37, 33, [22, 4.5, 9]),
        ("Field garage row", "model_airfield_garages", 53, 31, [37, 4, 8]),
        ("East workshop", "model_airfield_shed", 76, 12, [12, 4, 24]),
        ("North east shed", "model_airfield_shed", 47, -35, [16, 4.5, 10]),
        ("Plamya hall approximation", "model_airfield_hall", 37, -106, [38, 13, 42]),
        ("Hall annex", "model_airfield_hall", 89, -91, [34, 8, 22]),
        ("West garage block", "model_airfield_garages", -151, -96, [78, 4, 14]),
        ("West field storage", "model_airfield_shed", -207, -23, [57, 5, 14]),
        ("East apartment block 1", "model_airfield_apartments", 196, -150, [58, 16, 13]),
        ("East apartment block 2", "model_airfield_apartments", 208, -97, [62, 16, 13]),
        ("East apartment block 3", "model_airfield_apartments", 214, -60, [54, 16, 13]),
        ("Northern apartment block", "model_airfield_apartments", 50, -206, [62, 16, 13]),
    ]:
        add(name, asset, x, z, dimensions, yaw=6)

    rng = random.Random(534928)
    tree_number = 0
    # Cluster around the pad, road and neighborhood; no trees inside 35m.
    clusters = [(-52, -35, 15, 20, 8), (-78, -59, 20, 12, 8), (6, -49, 22, 7, 9),
                (31, -71, 14, 10, 7), (76, -45, 18, 16, 9), (88, 41, 14, 22, 9),
                (-32, 67, 28, 9, 8), (103, -129, 28, 17, 9), (-13, -123, 17, 31, 9),
                (143, -66, 16, 35, 9), (169, 45, 35, 15, 7)]
    for cx, cz, sx, sz, count in clusters:
        for _ in range(count):
            x, z = cx + rng.uniform(-sx, sx), cz + rng.uniform(-sz, sz)
            if math.hypot(x, z) < 35:
                continue
            if any(p["asset"].startswith("model_airfield_") and p["asset"] not in list(MODELS)[:3]
                   and abs(x - p["centerX"]) < p["width"] / 2 + 4
                   and abs(z - p["centerZ"]) < p["depth"] / 2 + 4 for p in placements):
                continue
            asset = list(MODELS)[(tree_number + 1) % 3]
            height = rng.uniform(7, 12)
            original_size = bounds[asset]["size"]
            tree_size = [v * height / original_size[1] for v in original_size]
            if math.hypot(x, z) - max(tree_size[0], tree_size[2]) / 2 < 35:
                continue
            tree_number += 1
            add(f"Airfield tree {tree_number:02}", asset, round(x, 2), round(z, 2), tree_size, yaw=rng.uniform(0, 360))

    original = (ROOT / "scenes/Field.scene").read_bytes()
    original_json = json.loads(original)
    assert original_json["format"] == "abyssus" and type(original_json["formatVersion"]) is int and original_json["formatVersion"] == 1
    assert not set(entities).intersection(original_json["ecs"]["entities"])
    write_json(OUT / "field-environment.patch.json", {
        "patchVersion": 1, "target": "ControlLine/scenes/Field.scene",
        "expectedSha256": hashlib.sha256(original).hexdigest(),
        "fieldEntity": "0", "fieldAsset": "terrain_airfield_povolzhsky", "fieldPosition": {"x": -100, "y": -1, "z": -85.5},
        "appendEntities": entities,
        "instructions": "Apply atomically using editSceneJson, checking expectedSha256 first. Retain every unrelated field and number literal. No scenery colliders are added."})
    write_json(OUT / "placements.json", placements)
    print(f"Packaged {len(MODELS)} original CC0 models; staged {tree_number} trees, 12 buildings and surrounding ground. Field.scene is unchanged.")


if __name__ == "__main__":
    main()
