import math

import numpy as np
import pytest

from f2b import fitness, geometry, silhouette
from f2b.cameras import load_cameras
from f2b.params import Params

VIEWS = ["photo7", "img_4"]


def synthetic_targets(params, names=VIEWS):
    evaluator = fitness.Evaluator([])
    mesh = evaluator.mesh(params)
    cameras = load_cameras()
    return [silhouette.make_target(n, cameras[n], silhouette.render_mask(mesh, cameras[n])) for n in names]


@pytest.fixture(scope="module")
def target_params():
    return Params({"fin_te_dx": 6.0, "spat_length": 175.0, "canopy_rise": 72.0, "gear_axle_y": 228.0})


@pytest.fixture(scope="module")
def targets(target_params):
    return synthetic_targets(target_params)


def test_perfect_match_scores_zero(target_params, targets):
    result = fitness.Evaluator(targets).evaluate(target_params, use_prior=False)
    assert result.total < 1e-6


def test_the_prior_is_separate_and_zero_at_the_defaults(targets):
    defaults = Params()
    assert fitness.Evaluator(targets).prior(defaults) == 0.0
    assert fitness.Evaluator(targets).prior(Params({"fin_te_dx": 6.0})) > 0.0


@pytest.mark.parametrize("change", [{"fin_te_dx": -12.0, "fin_crown_dx": -25.0}, {"spat_length": 130.0, "spat_half_height": 18.0}])
def test_a_clearly_different_shape_scores_higher(target_params, targets, change):
    evaluator = fitness.Evaluator(targets)
    perfect = evaluator.evaluate(target_params, use_prior=False).total
    worse = evaluator.evaluate(Params({**target_params.as_dict(), **change}), use_prior=False).total
    assert worse > perfect + 1e-4


def test_an_unbuildable_candidate_gets_an_infinite_loss(targets, monkeypatch):
    def broken(group, params, detail=False):
        raise RuntimeError("loft failed")
    monkeypatch.setattr(geometry, "build_group", broken)
    result = fitness.Evaluator(targets).evaluate(Params({"fin_thickness": 9.0}))
    assert math.isinf(result.total) and "loft failed" in result.error


def test_group_cache_reuses_unchanged_parts(targets):
    evaluator = fitness.Evaluator(targets)
    evaluator.evaluate(Params())
    count = len(evaluator._cache)
    evaluator.evaluate(Params({"fin_thickness": 9.0}))
    assert len(evaluator._cache) == count + 1       # only the fin group was rebuilt
