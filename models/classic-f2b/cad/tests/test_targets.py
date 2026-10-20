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
    assert names == {"photo7", "photo8", "img_4", "img_1"}
    for excluded in ("img_2", "img_3", "cockpit-reference"):
        assert excluded not in names
        assert not (targets.TARGET_DIR / f"{excluded}.png").exists()


@pytest.mark.parametrize("name", ["photo7", "photo8", "img_4", "img_1"])
def test_each_mask_overlaps_the_default_silhouette(default_mesh, name):
    camera = load_cameras()[name]
    ignore = silhouette.propeller_disc_mask(camera) > 0
    seed = (silhouette.render_mask(default_mesh, camera) > 0) & ~ignore
    mask = (targets.load_targets()[name] > 0) & ~ignore
    assert (seed & mask).sum() / (seed | mask).sum() >= 0.8


def test_cutout_photographs_give_their_silhouette_with_glazing_filled(default_mesh):
    for name in ("photo7", "photo8", "img_4"):
        camera = load_cameras()[name]
        alpha = load_photo(camera)[:, :, 3] >= 128
        mask = targets.load_targets()[name] > 0
        assert (alpha & mask).sum() / alpha.sum() > 0.97          # nothing real is lost ...
        assert mask.sum() / alpha.sum() < 1.06                    # ... and little is added (canopy holes)
        _, labels = cv2.connectedComponents(mask.astype(np.uint8))
        assert labels.max() <= 3                                    # stray cut-out debris is gone


def test_regenerating_a_grabcut_target_reproduces_the_committed_mask(default_mesh):
    camera = load_cameras()["img_1"]
    photo = load_photo(camera)
    assert targets.cutout_mask(photo) is None          # opaque photograph: GrabCut is used
    seed = silhouette.render_mask(default_mesh, camera)
    again = targets.grabcut(photo[:, :, :3].copy(), seed, *targets.BAND["img_1"]) > 0
    committed = targets.load_targets()["img_1"] > 0
    assert (again & committed).sum() / (again | committed).sum() > 0.99
