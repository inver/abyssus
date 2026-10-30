"""The photo cameras registered by `fit_photo_overlays.py` and `fit_livery2_photos.py`.

World coordinates are millimetres in the model frame; the registration files store
metres. A camera looks down its local -Z axis, as in Blender, with Euler XYZ rotation.
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np

MODEL_DIR = Path(__file__).resolve().parents[2]
TEXTURES = MODEL_DIR / "textures"


@dataclass(frozen=True)
class Camera:
    name: str
    photo: Path
    size: tuple[int, int]          # width, height in pixels
    position: np.ndarray           # millimetres
    rotation: np.ndarray           # 3x3, camera to world
    focal: float                   # pixels

    def project(self, points_mm: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
        """Pixel coordinates (N, 2) and depth along the viewing direction (N,)."""
        local = (np.asarray(points_mm, dtype=float) - self.position) @ self.rotation
        depth = -local[:, 2]
        safe = np.where(depth > 1e-6, depth, 1e-6)
        pixels = np.column_stack((self.size[0] / 2 + self.focal * local[:, 0] / safe,
                                  self.size[1] / 2 - self.focal * local[:, 1] / safe))
        return pixels, depth


def load_photo(camera: Camera) -> np.ndarray:
    """The photograph as BGRA at the camera's registered size (cut-outs keep their transparency)."""
    import cv2
    image = cv2.imread(str(camera.photo), cv2.IMREAD_UNCHANGED)
    if image is None:
        raise FileNotFoundError(camera.photo)
    if image.ndim == 2:
        image = cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)
    if image.shape[2] == 3:
        image = np.dstack([image, np.full(image.shape[:2], 255, np.uint8)])
    if (image.shape[1], image.shape[0]) != tuple(camera.size):
        image = cv2.resize(image, tuple(camera.size), interpolation=cv2.INTER_AREA)
    return image


def euler_xyz(angles) -> np.ndarray:
    """Blender's Euler 'XYZ' rotation matrix: rotate about X first, then Y, then Z."""
    ax, ay, az = angles
    rx = np.array([[1, 0, 0], [0, np.cos(ax), -np.sin(ax)], [0, np.sin(ax), np.cos(ax)]])
    ry = np.array([[np.cos(ay), 0, np.sin(ay)], [0, 1, 0], [-np.sin(ay), 0, np.cos(ay)]])
    rz = np.array([[np.cos(az), -np.sin(az), 0], [np.sin(az), np.cos(az), 0], [0, 0, 1]])
    return rz @ ry @ rx


# Views usable as fitting targets. img_1.png (low front view, weak camera) and the close-ups are excluded.
# img_5.png and img.png are photographs 7 and 8 with their backgrounds removed; img_4.png is the
# red/blue photograph, registered at 1024x766 (the file is the same photograph at twice the size);
# img_6.png is the behind-the-tail view. All four are cut-outs.
FIRST_LIVERY = {"photo7": ("primary", "img_5.png"), "photo8": ("opposite", "img.png")}
SECOND_LIVERY = {"img_6": "img_6.png", "img_4": "img_4.png"}


def load_cameras(model_dir: Path = MODEL_DIR) -> dict[str, Camera]:
    cameras: dict[str, Camera] = {}
    first = json.loads((model_dir / "photo-registration.json").read_text())["views"]
    for name, (key, photo) in FIRST_LIVERY.items():
        view = first[key]
        cameras[name] = Camera(name, model_dir / "textures" / photo, (1200, 900),
                               np.array(view["camera_position_m"]) * 1000, euler_xyz(view["camera_euler_rad"]),
                               view["lens_mm"] * 1200 / 36)
    second = json.loads((model_dir / "photo-registration-livery2.json").read_text())["photos"]
    for name, photo in SECOND_LIVERY.items():
        view = second[photo]
        cameras[name] = Camera(name, model_dir / "textures" / photo, tuple(view["image_size"]),
                               np.array(view["camera_position_m"]) * 1000, euler_xyz(view["camera_euler_rad"]),
                               view["focal_px"])
    return cameras
