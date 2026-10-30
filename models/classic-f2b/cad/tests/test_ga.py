import functools

import numpy as np
import pytest

from f2b import fitness, ga, silhouette
from f2b.cameras import load_cameras
from f2b.params import GENES, NAMES, Params

CENTRE = np.linspace(0.2, 0.8, len(GENES))


def _sphere(vector):
    return float(np.sum((vector - CENTRE) ** 2))


def sphere_factory():
    return _sphere


def test_search_is_reproducible_and_independent_of_worker_count():
    config = ga.GAConfig(population=16, generations=10, seed=7)
    start = np.full(len(GENES), 0.5)
    one = ga.run(sphere_factory, config, start)
    again = ga.run(sphere_factory, config, start)
    two = ga.run(sphere_factory, ga.GAConfig(population=16, generations=10, seed=7, workers=2), start)
    for other in (again, two):
        assert np.array_equal(one.best, other.best)
        assert [row["best"] for row in one.history] == [row["best"] for row in other.history]
    other_seed = ga.run(sphere_factory, ga.GAConfig(population=16, generations=10, seed=8), start)
    assert not np.array_equal(one.best, other_seed.best)


def test_best_loss_never_increases_and_result_stays_in_bounds():
    result = ga.run(sphere_factory, ga.GAConfig(population=20, generations=15, seed=1), np.full(len(GENES), 0.9))
    best = [row["best"] for row in result.history]
    assert all(b2 <= b1 for b1, b2 in zip(best, best[1:]))
    assert best[-1] < best[0]
    assert np.all((result.best >= 0) & (result.best <= 1))
    fitted = Params.from_vector(result.best)
    assert all(g.low <= fitted[g.name] <= g.high for g in GENES)


def test_only_free_genes_change():
    free = ("fin_te_dx", "spat_length")
    start = Params().to_vector()
    result = ga.run(sphere_factory, ga.GAConfig(population=12, generations=6, seed=2, free=free), start)
    changed = {NAMES[i] for i in np.flatnonzero(result.best != start)}
    assert changed <= set(free) and changed


def test_search_recovers_a_known_shape():
    truth = Params({"fin_te_dx": 7.0, "fin_crown_dx": 12.0, "spat_length": 178.0, "canopy_rise": 73.0, "gear_axle_y": 229.0})
    cameras = load_cameras()
    names = ["photo7", "img_4"]
    mesh = fitness.Evaluator([]).mesh(truth)
    targets = [silhouette.make_target(n, cameras[n], silhouette.render_mask(mesh, cameras[n])) for n in names]
    factory = functools.partial(make_value_function, names, truth.as_dict())
    free = tuple(name for name, value in truth.as_dict().items() if value != Params()[name])
    default_loss = fitness.Evaluator(targets).evaluate(Params(), use_prior=False).total
    result = ga.run(factory, ga.GAConfig(population=16, generations=8, seed=3, free=free, init_sigma=0.2), Params().to_vector())
    assert result.best_loss <= 0.5 * default_loss


def make_value_function(names, truth_values):
    """Worker-side factory: targets rendered from the known shape, loss without the prior."""
    cameras = load_cameras()
    truth = Params(truth_values)
    mesh = fitness.Evaluator([]).mesh(truth)
    targets = [silhouette.make_target(n, cameras[n], silhouette.render_mask(mesh, cameras[n])) for n in names]
    evaluator = fitness.Evaluator(targets)
    return lambda vector: evaluator.evaluate(Params.from_vector(vector), use_prior=False).total
