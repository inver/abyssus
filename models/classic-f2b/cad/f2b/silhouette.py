"""Silhouettes of the CAD model through the registered cameras, and the fit loss."""
from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np

from .cameras import Camera
from .geometry import PIVOT, PROPELLER_DIAMETER, Part

SHIFT = 4                      # sub-pixel bits for polygon filling
TESSELLATION = (1.5, 0.35)     # linear (mm) and angular tolerance of the fitting mesh
PROP_DISC_MARGIN_PX = 8


@dataclass
class Mesh:
    vertices: np.ndarray       # (N, 3) millimetres
    triangles: np.ndarray      # (M, 3) indices


def tessellate(parts: list[Part], airframe_only: bool = True, tolerance=TESSELLATION) -> Mesh:
    """One merged triangle mesh of the (airframe) parts."""
    vertices, triangles, offset = [], [], 0
    for part in parts:
        if airframe_only and not part.airframe:
            continue
        verts, tris = part.shape.tessellate(*tolerance)
        if not verts:
            continue
        vertices.append(np.array([(v.x, v.y, v.z) for v in verts]))
        triangles.append(np.array(tris, dtype=np.int64) + offset)
        offset += len(verts)
    return Mesh(np.vstack(vertices), np.vstack(triangles))


def render_mask(mesh: Mesh, camera: Camera) -> np.ndarray:
    """Binary silhouette (uint8, 0 or 255) of the mesh in the camera's image."""
    width, height = camera.size
    pixels, depth = camera.project(mesh.vertices)
    keep = (depth[mesh.triangles] > 1.0).all(axis=1)
    polygons = np.round(pixels[mesh.triangles[keep]] * (1 << SHIFT)).astype(np.int32)
    mask = np.zeros((height, width), np.uint8)
    # One convex fill per triangle: fillPoly over many polygons uses the even-odd rule and would
    # cancel overlapping triangles.
    for triangle in polygons:
        cv2.fillConvexPoly(mask, triangle, 255, lineType=cv2.LINE_8, shift=SHIFT)
    return mask


def propeller_disc_mask(camera: Camera, segments: int = 72) -> np.ndarray:
    """Pixels covered by the propeller disc (blade angle differs per photograph), slightly grown."""
    angle = np.linspace(0, 2 * np.pi, segments, endpoint=False)
    radius = PROPELLER_DIAMETER / 2
    ring = np.column_stack((np.full(segments, PIVOT.x), PIVOT.y + radius * np.cos(angle), PIVOT.z + radius * np.sin(angle)))
    pixels, depth = camera.project(ring)
    mask = np.zeros((camera.size[1], camera.size[0]), np.uint8)
    if (depth > 1).all():
        cv2.fillPoly(mask, [np.round(pixels * (1 << SHIFT)).astype(np.int32)], 255, shift=SHIFT)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * PROP_DISC_MARGIN_PX + 1,) * 2)
    return cv2.dilate(mask, kernel)


# ------------------------------------------------------------------ loss
CONTOUR_WEIGHT = 0.01          # loss per pixel of mean symmetric contour distance


@dataclass
class Target:
    """A photo silhouette to match, with the pixels excluded from the comparison."""
    name: str
    camera: Camera
    mask: np.ndarray           # bool, photo silhouette
    ignore: np.ndarray         # bool, e.g. the propeller disc
    edge: np.ndarray           # bool, photo silhouette contour pixels
    distance: np.ndarray       # float32, distance of every pixel to the photo contour
    area: float


def _contour(mask: np.ndarray) -> np.ndarray:
    return mask & ~cv2.erode(mask.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool)


def make_target(name: str, camera: Camera, photo_mask: np.ndarray) -> Target:
    ignore = propeller_disc_mask(camera) > 0
    mask = photo_mask > 0
    edge = _contour(mask)
    distance = cv2.distanceTransform((~edge).astype(np.uint8), cv2.DIST_L2, 3)
    return Target(name, camera, mask, ignore, edge, distance, float((mask & ~ignore).sum()))


def view_loss(model_mask: np.ndarray, target: Target) -> tuple[float, float]:
    """(symmetric-difference area ratio, mean symmetric contour distance in pixels) for one view."""
    model = model_mask > 0
    valid = ~target.ignore
    xor = float(((model ^ target.mask) & valid).sum()) / target.area
    model_edge = _contour(model)
    to_photo = target.distance[model_edge & valid]
    if not model_edge.any():
        return xor, float(np.hypot(*model.shape))
    distance_to_model = cv2.distanceTransform((~model_edge).astype(np.uint8), cv2.DIST_L2, 3)
    to_model = distance_to_model[target.edge & valid]
    contour = 0.5 * (float(to_photo.mean()) if len(to_photo) else 0.0) + 0.5 * (float(to_model.mean()) if len(to_model) else 0.0)
    return xor, contour


def combine(xor: float, contour: float) -> float:
    return xor + CONTOUR_WEIGHT * contour
