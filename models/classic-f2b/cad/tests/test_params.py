import json

import numpy as np
import pytest

from f2b import params
from f2b.params import GENES, NAMES, ParameterError, Params


def test_defaults_are_within_their_own_bounds():
    for gene in GENES:
        assert gene.low <= gene.default <= gene.high, gene.name
    Params()  # validates every default


def test_names_are_unique_and_grouped():
    assert len(set(NAMES)) == len(NAMES)
    assert {"fuselage", "fin", "canopy", "gear", "wing", "stabilizer", "engine"} <= set(params.GROUPS)


def test_out_of_range_value_is_refused_with_its_name():
    with pytest.raises(ParameterError, match="fin_thickness"):
        Params({"fin_thickness": 99.0})


def test_unknown_name_is_refused_with_its_name():
    with pytest.raises(ParameterError, match="wing_span"):
        Params({"wing_span": 1450.0})


def test_json_round_trip_is_identical(tmp_path):
    original = Params({"canopy_rise": 70.0, "gear_axle_y": 230.5})
    path = tmp_path / "p.json"
    original.save(path)
    assert json.loads(path.read_text())["canopy_rise"] == 70.0
    assert Params.load(path) == original


def test_vector_round_trip_and_clipping():
    original = Params({"spat_length": 150.0})
    assert Params.from_vector(original.to_vector()) == original
    wild = np.full(len(GENES), 7.0)
    clipped = Params.from_vector(wild)
    assert all(clipped[g.name] == g.high for g in GENES)
    with pytest.raises(ParameterError):
        Params.from_vector(np.zeros(3))
