import cv2
import numpy as np
import pytest

from f2b import geometry, silhouette, targets
from f2b.cameras import load_cameras, load_photo
from f2b.params import Params


@pytest.fixture(scope="module")
def default_mesh():
    return silhouette.tessellate(geometry.build(Params()))


def test_one_binary_mask_and_review_overlay_per_usable_photograph():
    for name, camera in load_cameras().items():
        mask = cv2.imread(str(targets.TARGET_DIR / f"{name}.png"), cv2.IMREAD_GRAYSCALE)
        assert mask is not None and (mask.shape[1], mask.shape[0]) == camera.size
        assert set(np.unique(mask)) <= {0, 255}
        assert (targets.TARGET_DIR / f"{name}-review.jpg").exists()


def test_excluded_views_are_not_targets():
    names = set(load_cameras())
    assert names == {"photo7", "photo8", "img_4", "img_6"}
    for excluded in ("img_1", "img_2", "img_3", "cockpit-reference"):
        assert excluded not in names
        assert not (targets.TARGET_DIR / f"{excluded}.png").exists()


@pytest.mark.parametrize("name", ["photo7", "photo8", "img_4", "img_6"])
def test_each_mask_overlaps_the_default_silhouette(default_mesh, name):
    camera = load_cameras()[name]
    ignore = silhouette.propeller_disc_mask(camera) > 0
    seed = (silhouette.render_mask(default_mesh, camera) > 0) & ~ignore
    mask = (targets.load_targets()[name] > 0) & ~ignore
    assert (seed & mask).sum() / (seed | mask).sum() >= 0.8


def test_cutout_photographs_give_their_silhouette_with_glazing_filled(default_mesh):
    for name in ("photo7", "photo8", "img_4", "img_6"):
        camera = load_cameras()[name]
        alpha = load_photo(camera)[:, :, 3] >= 128
        mask = targets.load_targets()[name] > 0
        assert (alpha & mask).sum() / alpha.sum() > 0.97          # nothing real is lost ...
        assert mask.sum() / alpha.sum() < 1.06                    # ... and little is added (canopy holes)
        _, labels = cv2.connectedComponents(mask.astype(np.uint8))
        assert labels.max() <= 3                                    # stray cut-out debris is gone


def test_grabcut_recovers_the_silhouette_of_an_opaque_photograph(default_mesh):
    """The fallback for photographs without transparency: a cut-out pasted on a pavement-like background."""
    camera = load_cameras()["img_6"]
    photo = load_photo(camera)
    alpha = photo[:, :, 3] >= 128
    rng = np.random.default_rng(0)
    ground = cv2.GaussianBlur(rng.integers(60, 140, photo.shape[:2] + (3,), dtype=np.uint8), (0, 0), 3)
    opaque = np.where(alpha[:, :, None], photo[:, :, :3], ground).astype(np.uint8)
    assert targets.cutout_mask(np.dstack([opaque, np.full(alpha.shape, 255, np.uint8)])) is None
    seed = silhouette.render_mask(default_mesh, camera)
    found = targets.grabcut(opaque, seed, *targets.BAND["img_6"]) > 0
    truth = targets.load_targets()["img_6"] > 0
    assert (found & truth).sum() / (found | truth).sum() > 0.9
