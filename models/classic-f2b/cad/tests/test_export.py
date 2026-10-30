import re

import cadquery as cq
import numpy as np
import pytest

from f2b import export, geometry
from f2b.params import Params


def read_binary_stl(path):
    data = np.fromfile(path, dtype=np.uint8)
    count = int(np.frombuffer(data[80:84], dtype="<u4")[0])
    record = np.dtype([("n", "<f4", 3), ("v", "<f4", (3, 3)), ("a", "<u2")])
    return np.frombuffer(data[84:84 + 50 * count].tobytes(), dtype=record)["v"]


@pytest.fixture(scope="module")
def exported(tmp_path_factory):
    out = tmp_path_factory.mktemp("export")
    parts = geometry.build(Params(), detail=True)
    return parts, export.export(parts, out), out


def test_step_contains_every_named_part(exported):
    parts, written, _ = exported
    text = written["step"].read_text(errors="ignore")
    names = set(re.findall(r"PRODUCT\('([^']+)'", text))
    assert {part.name for part in parts} <= names


def test_step_reloads_with_the_same_bounding_box(exported):
    parts, written, _ = exported
    loaded = export.shape_bounds(cq.importers.importStep(str(written["step"])).val())
    assert loaded == pytest.approx(export.assembly_bounds(parts), abs=0.1)


def test_one_stl_per_part_in_millimetres(exported):
    parts, written, out = exported
    for part in parts:
        assert (out / "stl" / f"{part.name}.stl").stat().st_size > 0
    triangles = read_binary_stl(written["wing_long"])
    assert triangles[:, :, 1].max() == pytest.approx(740, abs=0.5)
