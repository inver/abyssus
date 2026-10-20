import cv2
import numpy as np
import pytest

from f2b import geometry, silhouette
from f2b.cameras import MODEL_DIR, load_cameras
from f2b.params import Params

BLENDER_MASKS = {"photo7": "aligned-primary-mask.png", "photo8": "aligned-opposite-mask.png",
                 "img_4": "aligned-livery2-img_4-mask.png", "img_1": "aligned-livery2-img_1-mask.png"}


@pytest.fixture(scope="module")
def default_mesh():
    return silhouette.tessellate(geometry.build(Params()))


def blender_mask(name):
    image = cv2.imread(str(MODEL_DIR / BLENDER_MASKS[name]), cv2.IMREAD_UNCHANGED)
    return image[:, :, 3] > 127


@pytest.mark.parametrize("name", list(BLENDER_MASKS))
def test_default_silhouette_matches_the_blender_silhouette(default_mesh, name):
    camera = load_cameras()[name]
    ignore = silhouette.propeller_disc_mask(camera) > 0
    cad = (silhouette.render_mask(default_mesh, camera) > 0) & ~ignore
    blender = blender_mask(name) & ~ignore
    assert cad.sum() / blender.sum() == pytest.approx(1.0, abs=0.03)
    assert (cad & blender).sum() / (cad | blender).sum() > 0.95


def test_rendering_is_deterministic(default_mesh):
    camera = load_cameras()["img_4"]
    first = silhouette.render_mask(default_mesh, camera)
    second = silhouette.render_mask(default_mesh, camera)
    assert np.array_equal(first, second)
    assert set(np.unique(first)) <= {0, 255}


def test_propeller_disc_surrounds_the_spinner_nose():
    camera = load_cameras()["photo7"]
    disc = silhouette.propeller_disc_mask(camera)
    nose, _ = camera.project(np.array([[-48.0, 2.0, 10.0]]))
    assert disc[int(nose[0][1]), int(nose[0][0])] == 255
    far, _ = camera.project(np.array([[600.0, 0.0, 0.0]]))
    assert disc[int(far[0][1]), int(far[0][0])] == 0


def test_fast_panel_meshes_give_the_same_silhouette_as_the_cad_solids():
    from f2b import fitness
    params = Params({"wing_tip_sweep_94": 40.0, "wing_tip_te_100": 130.0, "stab_tip_inset": 15.0, "wing_z": 4.0})
    fast = fitness.Evaluator([]).mesh(params)
    solids = silhouette.tessellate(geometry.build(params))
    for name in ("photo7", "img_4"):
        camera = load_cameras()[name]
        a = silhouette.render_mask(fast, camera) > 0
        b = silhouette.render_mask(solids, camera) > 0
        assert (a & b).sum() / (a | b).sum() > 0.995, name
