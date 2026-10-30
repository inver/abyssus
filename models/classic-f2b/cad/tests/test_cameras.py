import json

import numpy as np
import pytest

from f2b.cameras import MODEL_DIR, load_cameras

SECOND_LIVERY_LANDMARKS = {
    # name -> world millimetres, as used by fit_livery2_photos.py (near wing is the long one in img.png)
    "img_4": {"near wing tip LE": (282, 740, 0), "near wing tip TE": (557, 740, 0),
            "far wing tip LE": (282, -710, 0), "far wing tip TE": (557, -710, 0),
            "near stab tip LE": (921, 345, 5), "near stab tip TE": (1036, 345, 5),
            "far stab tip LE": (921, -345, 5), "far stab tip TE": (1036, -345, 5),
            "spinner nose": (-48, 2, 10), "fin aft top corner": (1064, 0, 142)},
}


def test_first_livery_cameras_reproduce_their_stored_landmark_pixels():
    cameras = load_cameras()
    stored = json.loads((MODEL_DIR / "photo-registration.json").read_text())["views"]
    for name, key in (("photo7", "primary"), ("photo8", "opposite")):
        for landmark in stored[key]["landmarks"]:
            pixel, _ = cameras[name].project(np.array([landmark["world_mm"]], dtype=float))
            assert pixel[0] == pytest.approx(landmark["model_px"], abs=1.0), landmark["name"]


def test_second_livery_camera_matches_its_marked_landmarks():
    camera = load_cameras()["img_4"]
    report = json.loads((MODEL_DIR / "photo-registration-livery2.json").read_text())["photos"]["img_4.png"]
    for landmark in report["landmarks"]:
        world = np.array([SECOND_LIVERY_LANDMARKS["img_4"][landmark["name"]]], dtype=float)
        pixel, depth = camera.project(world)
        assert depth[0] > 0
        assert np.linalg.norm(pixel[0] - landmark["photo_px"]) == pytest.approx(landmark["error_px"], abs=1.5)


def test_every_target_photo_exists_and_has_its_registered_size():
    import cv2
    for camera in load_cameras().values():
        image = cv2.imread(str(camera.photo), cv2.IMREAD_UNCHANGED)
        assert image is not None, camera.photo
        # same aspect ratio as the registered size (img_4.png is the 1024x766 photograph at twice the size)
        assert image.shape[1] / image.shape[0] == pytest.approx(camera.size[0] / camera.size[1], abs=0.01)
