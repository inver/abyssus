import math

import pytest

from f2b import geometry as g
from f2b.params import GENES, GROUPS, Params

PRINCIPAL = ["fuselage", "wing_long", "wing_short", "stabilizer_left", "stabilizer_right", "fin"]


def by_name(parts):
    return {part.name: part for part in parts}


def check_datums(parts, tolerance=0.5):
    parts = by_name(parts)
    box = {name: part.shape.BoundingBox() for name, part in parts.items()}
    assert box["wing_long"].ymax == pytest.approx(740, abs=tolerance)
    assert box["wing_long"].ymin == pytest.approx(0, abs=tolerance)
    assert box["wing_short"].ymin == pytest.approx(-710, abs=tolerance)
    for name in ("stabilizer_left", "stabilizer_right"):
        assert box[name].ylen == pytest.approx(345, abs=tolerance)
        assert box[name].xlen == pytest.approx(161, abs=tolerance)          # root chord
    for name in ("wing_long", "wing_short"):
        assert box[name].xlen == pytest.approx(340, abs=tolerance)          # root chord
        assert box[name].zlen == pytest.approx(62, abs=tolerance)           # root thickness
    assert box["canopy"].xlen == pytest.approx(240, abs=tolerance)
    assert box["fin"].zmax - g.AFT_DECK_Z == pytest.approx(125, abs=tolerance)
    # tip corner stations are fixed by construction where the rounded tip begins
    for span in (740, 710):
        start, chord, _ = g.wing_section(span - g.WING_ROUND_LENGTH, span, Params())
        assert (start, start + chord) == (pytest.approx(282), pytest.approx(557))


def test_default_principal_solids_are_valid_closed_solids():
    parts = by_name(g.build(Params()))
    for name in PRINCIPAL:
        shape = parts[name].shape
        assert shape.isValid(), name
        assert shape.Volume() > 0, name
        assert len(shape.Shells()) == 1, name
    check_datums(parts.values())


@pytest.mark.parametrize("group", GROUPS)
@pytest.mark.parametrize("bound", ["low", "high"])
def test_datums_hold_with_a_group_at_its_bound(group, bound):
    values = {gene.name: gene.low if bound == "low" else gene.high for gene in GENES if gene.group == group}
    parts = g.build(Params(values))
    for part in parts:
        if part.name in PRINCIPAL:
            assert part.shape.isValid() and part.shape.Volume() > 0, part.name
    check_datums(parts)


def test_building_twice_gives_identical_parts():
    first, second = g.build(Params()), g.build(Params())
    assert [p.name for p in first] == [p.name for p in second]
    for a, b in zip(first, second):
        assert a.shape.Volume() == b.shape.Volume()
        boxes = a.shape.BoundingBox(), b.shape.BoundingBox()
        assert boxes[0].xmax == boxes[1].xmax and boxes[0].zmin == boxes[1].zmin


def test_engine_side_is_positive_y():
    parts = by_name(g.build(Params()))
    assert parts["engine_cylinder"].shape.Center().y > 0
    assert parts["wing_long"].shape.Center().y > 0


def test_detail_build_adds_cockpit_and_propeller_of_the_datum_diameter():
    parts = by_name(g.build(Params(), detail=True))
    assert {"seat_back", "instrument_panel", "propeller_blade_a"} <= set(parts)
    blades = [parts["propeller_blade_a"].shape.BoundingBox(), parts["propeller_blade_b"].shape.BoundingBox()]
    diameter = max(b.ymax for b in blades) - min(b.ymin for b in blades)
    assert diameter == pytest.approx(g.PROPELLER_DIAMETER, abs=6)   # 3 degree engine yaw skews the span slightly
    assert parts["fuselage"].shape.isValid()
