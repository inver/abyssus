"""Download the airfield's CC0 sources (sources.json) into a cache folder.

Usage: python3 fetch_sources.py [cache]   (default: $ABYSSUS_AIRFIELD_CACHE or ~/.cache/abyssus-airfield)

Poly Haven models come as glTF with their 1k textures, ambientCG materials as JPG zips. Every file's SHA-256 goes
into sources.lock.json; a later run downloads only missing files and fails if a cached file no longer matches.
Standard library only.
"""
from pathlib import Path
import hashlib
import json
import os
import sys
import urllib.request
import zipfile

HERE = Path(__file__).resolve().parent
LOCK = HERE / "sources.lock.json"
AGENT = {"User-Agent": "abyssus-airfield-fetch/1"}


def cache_dir():
    if len(sys.argv) > 1:
        return Path(sys.argv[1])
    return Path(os.environ.get("ABYSSUS_AIRFIELD_CACHE", Path.home() / ".cache" / "abyssus-airfield"))


def get_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=AGENT)) as r:
        return json.load(r)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(url, target, lock):
    if target.exists():
        expected = lock.get(url)
        if expected and sha256(target) != expected:
            raise SystemExit(f"{target} does not match sources.lock.json; delete it to download again")
        lock.setdefault(url, sha256(target))
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    print("download", url)
    part = target.with_suffix(target.suffix + ".part")
    with urllib.request.urlopen(urllib.request.Request(url, headers=AGENT)) as r, open(part, "wb") as f:
        while chunk := r.read(1 << 20):
            f.write(chunk)
    digest = sha256(part)
    if url in lock and lock[url] != digest:
        part.unlink()
        raise SystemExit(f"{url} changed upstream: {digest} is not the locked {lock[url]}")
    part.rename(target)
    lock[url] = digest


def main():
    sources = json.loads((HERE / "sources.json").read_text())
    lock = json.loads(LOCK.read_text()) if LOCK.exists() else {}
    cache = cache_dir()

    haven = sources["polyhaven"]
    for asset in haven["models"]:
        files = get_json(f"https://api.polyhaven.com/files/{asset}")["gltf"][haven["resolution"]]["gltf"]
        folder = cache / "polyhaven" / asset
        folder.mkdir(parents=True, exist_ok=True)
        info = get_json(f"https://api.polyhaven.com/info/{asset}")
        (folder / "info.json").write_text(json.dumps({"name": info.get("name"), "authors": info.get("authors")}, indent=2))
        download(files["url"], folder / Path(files["url"]).name, lock)
        for relative, item in files["include"].items():
            download(item["url"], folder / relative, lock)

    for material, spec in sources["ambientcg"]["materials"].items():
        name = f"{material}_{spec['resolution']}-JPG"
        archive = cache / "ambientcg" / f"{name}.zip"
        download(f"https://ambientcg.com/get?file={name}.zip", archive, lock)
        folder = cache / "ambientcg" / material
        if not folder.exists():
            with zipfile.ZipFile(archive) as z:
                z.extractall(folder)

    LOCK.write_text(json.dumps(dict(sorted(lock.items())), indent=2) + "\n")
    print("cache:", cache)


if __name__ == "__main__":
    main()
