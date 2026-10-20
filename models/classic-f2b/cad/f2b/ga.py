"""A real-coded genetic algorithm over the normalised gene vector.

Tournament selection, simulated binary crossover (SBX), polynomial mutation and elitism.
All randomness comes from one generator that is consumed in a fixed order in the parent
process, and evaluation is a pure function of the vector, so results do not depend on how
many worker processes evaluate the population.
"""
from __future__ import annotations

import multiprocessing
import time
from dataclasses import dataclass, field
from typing import Callable

import numpy as np

from .params import NAMES

Factory = Callable[[], Callable[[np.ndarray], float]]     # builds an evaluation function (once per process)


@dataclass(frozen=True)
class GAConfig:
    population: int = 48
    generations: int = 60
    seed: int = 0
    tournament: int = 3
    crossover_rate: float = 0.9
    sbx_eta: float = 15.0
    mutation_eta: float = 20.0
    mutation_rate: float | None = None      # per free gene; default 1 / number of free genes
    elites: int = 2
    init_sigma: float = 0.12                # spread of the initial population around the start vector
    free: tuple[str, ...] | None = None     # gene names the search may change (default: all)
    workers: int = 1


@dataclass
class GAResult:
    best: np.ndarray
    best_loss: float
    history: list[dict] = field(default_factory=list)
    evaluations: int = 0


_EVALUATE: Callable[[np.ndarray], float] | None = None


def _init_worker(factory: Factory) -> None:
    global _EVALUATE
    _EVALUATE = factory()


def _worker_evaluate(vector: np.ndarray) -> float:
    return _EVALUATE(vector)


def sbx(a: np.ndarray, b: np.ndarray, free: np.ndarray, eta: float, rng: np.random.Generator) -> tuple[np.ndarray, np.ndarray]:
    """Simulated binary crossover, applied to each free gene with probability one half."""
    u = rng.random(len(a))
    swap = rng.random(len(a)) < 0.5
    beta = np.where(u <= 0.5, (2 * u) ** (1 / (eta + 1)), (1 / (2 * (1 - u + 1e-12))) ** (1 / (eta + 1)))
    c1 = 0.5 * ((1 + beta) * a + (1 - beta) * b)
    c2 = 0.5 * ((1 - beta) * a + (1 + beta) * b)
    use = free & swap
    return np.where(use, c1, a), np.where(use, c2, b)


def polynomial_mutation(x: np.ndarray, free: np.ndarray, rate: float, eta: float, rng: np.random.Generator) -> np.ndarray:
    hit = (rng.random(len(x)) < rate) & free
    u = rng.random(len(x))
    delta = np.where(u < 0.5, (2 * u) ** (1 / (eta + 1)) - 1, 1 - (2 * (1 - u)) ** (1 / (eta + 1)))
    return np.clip(np.where(hit, x + delta, x), 0.0, 1.0)


def run(factory: Factory, config: GAConfig, start: np.ndarray,
        on_generation: Callable[[dict], None] | None = None) -> GAResult:
    rng = np.random.default_rng(config.seed)
    n = len(start)
    free = np.ones(n, bool) if config.free is None else np.isin(NAMES, config.free) if n == len(NAMES) else np.ones(n, bool)
    rate = config.mutation_rate if config.mutation_rate is not None else 1.0 / max(1, int(free.sum()))
    pool = None
    if config.workers > 1:
        pool = multiprocessing.get_context("spawn").Pool(config.workers, _init_worker, (factory,))
        evaluate_many = lambda vectors: pool.map(_worker_evaluate, vectors, chunksize=1)
    else:
        evaluate = factory()
        evaluate_many = lambda vectors: [evaluate(v) for v in vectors]
    cache: dict[bytes, float] = {}
    evaluations = 0

    def score(vectors: list[np.ndarray]) -> list[float]:
        nonlocal evaluations
        pending = list(dict.fromkeys(v.tobytes() for v in vectors if v.tobytes() not in cache))
        lookup = {key: np.frombuffer(key, dtype=float) for key in pending}
        for key, loss in zip(pending, evaluate_many([lookup[k] for k in pending])):
            cache[key] = float(loss)
        evaluations += len(pending)
        return [cache[v.tobytes()] for v in vectors]

    try:
        population = [np.array(start, dtype=float)]
        for _ in range(config.population - 1):
            noise = rng.normal(0.0, config.init_sigma, n) * free
            population.append(np.clip(start + noise, 0.0, 1.0))
        losses = score(population)
        history: list[dict] = []
        started = time.time()

        def order() -> None:
            nonlocal population, losses
            ranking = sorted(range(len(population)), key=lambda i: (losses[i], i))
            population, losses = [population[i] for i in ranking], [losses[i] for i in ranking]

        def log(generation: int) -> None:
            finite = [l for l in losses if np.isfinite(l)]
            row = {"generation": generation, "best": losses[0], "mean": float(np.mean(finite)),
                   "median": float(np.median(finite)), "evaluations": evaluations,
                   "seconds": round(time.time() - started, 1)}
            history.append(row)
            if on_generation:
                on_generation(row)

        order()
        log(0)
        for generation in range(1, config.generations + 1):
            def select() -> np.ndarray:
                picks = rng.integers(0, len(population), config.tournament)
                return population[min(picks, key=lambda i: (losses[i], i))]
            children: list[np.ndarray] = []
            while len(children) < config.population - config.elites:
                a, b = select(), select()
                if rng.random() < config.crossover_rate:
                    a, b = sbx(a, b, free, config.sbx_eta, rng)
                children.append(polynomial_mutation(a, free, rate, config.mutation_eta, rng))
                if len(children) < config.population - config.elites:
                    children.append(polynomial_mutation(b, free, rate, config.mutation_eta, rng))
            child_losses = score(children)
            population = population[:config.elites] + children
            losses = losses[:config.elites] + child_losses
            order()
            log(generation)
        return GAResult(population[0], losses[0], history, evaluations)
    finally:
        if pool is not None:
            pool.close()
            pool.join()
