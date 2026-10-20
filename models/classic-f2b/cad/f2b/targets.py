"""Photo silhouette targets: GrabCut seeded by the default model's silhouette.

The band between "certainly the aircraft" and "certainly not" is kept as narrow as the camera
registration error allows, because the seed biases the result toward the default model by at
most that width. Review overlays are written beside the masks.
"""
from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

from . import geometry, silhouette
from .cameras import Camera, load_cameras, load_photo
from .params import Params

CAD_DIR = Path(__file__).resolve().parents[1]
TARGET_DIR = CAD_DIR / "targets"
# Camera registration error, pixels, per view: sets how far the seed may be off.
BAND = {"photo7": (10, 26), "photo8": (10, 26), "img_4": (8, 16), "img_1": (8, 16)}   # only img_1 is not a cut-out


def grabcut(photo: np.ndarray, seed: np.ndarray, inner: int, outer: int, iterations: int = 8) -> np.ndarray:
    kernel = lambda r: cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * r + 1, 2 * r + 1))
    sure_fg = cv2.erode(seed, kernel(inner)) > 0
    sure_bg = cv2.dilate(seed, kernel(outer)) == 0
    mask = np.where(seed > 0, cv2.GC_PR_FGD, cv2.GC_PR_BGD).astype(np.uint8)
    mask[sure_fg] = cv2.GC_FGD
    mask[sure_bg] = cv2.GC_BGD
    background, foreground = np.zeros((1, 65)), np.zeros((1, 65))
    cv2.setRNGSeed(0)
    cv2.grabCut(photo, mask, None, background, foreground, iterations, cv2.GC_INIT_WITH_MASK)
    result = np.where((mask == cv2.GC_FGD) | (mask == cv2.GC_PR_FGD), 255, 0).astype(np.uint8)
    return clean(result, seed)


def clean(mask: np.ndarray, seed: np.ndarray) -> np.ndarray:
    """Smooth speckle, drop components that do not touch the seed, fill holes."""
    small = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    mask = cv2.morphologyEx(cv2.morphologyEx(mask, cv2.MORPH_OPEN, small), cv2.MORPH_CLOSE, small)
    count, labels = cv2.connectedComponents(mask)
    keep = np.zeros_like(mask)
    for label in range(1, count):
        component = labels == label
        if (component & (seed > 0)).sum() > 0.2 * component.sum():
            keep[component] = 255
    contours, _ = cv2.findContours(keep, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    filled = np.zeros_like(keep)
    cv2.drawContours(filled, contours, -1, 255, thickness=cv2.FILLED)
    return filled


def review_overlay(photo: np.ndarray, mask: np.ndarray, seed: np.ndarray) -> np.ndarray:
    out = photo.copy()
    for image, colour in ((seed, (0, 0, 255)), (mask, (0, 255, 0))):
        contours, _ = cv2.findContours(image, cv2.RETR_LIST, cv2.CHAIN_APPROX_NONE)
        cv2.drawContours(out, contours, -1, colour, 1)
    return out


def default_seed(camera: Camera, mesh: silhouette.Mesh) -> np.ndarray:
    return silhouette.render_mask(mesh, camera)


def cutout_mask(photo: np.ndarray, seed: np.ndarray | None = None) -> np.ndarray | None:
    """The silhouette of a photograph whose background was removed, else None.

    Transparent glazing and stray objects left in the cut-out are handled by `clean`: holes are
    filled, and components that do not overlap the seed (the default model) are dropped."""
    alpha = photo[:, :, 3]
    if (alpha < 128).mean() < 0.02:
        return None
    mask = np.where(alpha >= 128, 255, 0).astype(np.uint8)
    return clean(mask, seed if seed is not None else mask)


def make_targets(out_dir: Path = TARGET_DIR) -> dict[str, np.ndarray]:
    """Cut-out photographs give their silhouette exactly (their transparency); the others use GrabCut."""
    out_dir.mkdir(parents=True, exist_ok=True)
    mesh = silhouette.tessellate(geometry.build(Params()))
    masks = {}
    for name, camera in load_cameras().items():
        photo = load_photo(camera)
        seed = default_seed(camera, mesh)
        mask = cutout_mask(photo, seed)
        if mask is None:
            mask = grabcut(photo[:, :, :3].copy(), seed, *BAND[name])
        cv2.imwrite(str(out_dir / f"{name}.png"), mask)
        cv2.imwrite(str(out_dir / f"{name}-review.jpg"), review_overlay(photo[:, :, :3].copy(), mask, seed),
                    [cv2.IMWRITE_JPEG_QUALITY, 88])
        masks[name] = mask
    return masks


def load_targets(out_dir: Path = TARGET_DIR) -> dict[str, np.ndarray]:
    return {name: cv2.imread(str(out_dir / f"{name}.png"), cv2.IMREAD_GRAYSCALE) for name in load_cameras()}


if __name__ == "__main__":
    for name, mask in make_targets().items():
        print(name, int((mask > 0).sum()))
