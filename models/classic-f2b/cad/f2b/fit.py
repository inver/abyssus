"""`python -m f2b.fit`: fit the parameters to the photo silhouettes and write the report."""
from __future__ import annotations

import argparse
import functools
import os
from pathlib import Path

import numpy as np

from . import ga, report, silhouette, targets
from .cameras import load_cameras
from .fitness import PRIOR_WEIGHT, Evaluator
from .params import NAMES, Params

OUT = Path(__file__).resolve().parents[1] / "out" / "fit"


def build_evaluator(views: tuple[str, ...], prior_weight: float = PRIOR_WEIGHT, target_dir: Path | None = None) -> Evaluator:
    cameras, masks = load_cameras(), targets.load_targets(target_dir or targets.TARGET_DIR)
    return Evaluator([silhouette.make_target(name, cameras[name], masks[name]) for name in views], prior_weight)


def loss_function(views: tuple[str, ...], prior_weight: float, target_dir: str | None):
    """Worker-side factory (picklable through functools.partial): vector -> loss with the prior."""
    evaluator = build_evaluator(views, prior_weight, Path(target_dir) if target_dir else None)
    return lambda vector: evaluator.evaluate(Params.from_vector(vector)).total


def fit(views: tuple[str, ...], config: ga.GAConfig, prior_weight: float = PRIOR_WEIGHT,
        target_dir: Path | None = None, on_generation=None) -> ga.GAResult:
    factory = functools.partial(loss_function, views, prior_weight, str(target_dir) if target_dir else None)
    return ga.run(factory, config, Params().to_vector(), on_generation)


def holdout_check(views: tuple[str, ...], config: ga.GAConfig, prior_weight: float, target_dir: Path | None,
                  log=print) -> dict:
    """Leave each view out in turn; compare its loss under the fit of the others with the defaults'."""
    full = build_evaluator(views, prior_weight, target_dir)
    results = {}
    for left_out in views:
        rest = tuple(v for v in views if v != left_out)
        log(f"hold-out fit without {left_out}")
        result = fit(rest, config, prior_weight, target_dir)
        fitted = Params.from_vector(result.best)
        before = full.evaluate(Params(), use_prior=False).views[left_out]
        after = full.evaluate(fitted, use_prior=False).views[left_out]
        train_before = np.mean([full.evaluate(Params(), use_prior=False).views[v] for v in rest])
        train_after = np.mean([full.evaluate(fitted, use_prior=False).views[v] for v in rest])
        results[left_out] = {"held_out_default": before, "held_out_fitted": after, "improved": bool(after < before),
                             "train_default": float(train_before), "train_fitted": float(train_after)}
    return results


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=OUT)
    parser.add_argument("--views", default="photo7,photo8,img_4,img_1")
    parser.add_argument("--population", type=int, default=48)
    parser.add_argument("--generations", type=int, default=60)
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--workers", type=int, default=max(1, (os.cpu_count() or 2) - 1))
    parser.add_argument("--free", help="comma separated gene names to fit (default: all)")
    parser.add_argument("--prior-weight", type=float, default=PRIOR_WEIGHT)
    parser.add_argument("--holdout-population", type=int, default=32)
    parser.add_argument("--holdout-generations", type=int, default=30)
    parser.add_argument("--skip-holdout", action="store_true")
    parser.add_argument("--no-step", action="store_true", help="skip the fitted STEP export")
    args = parser.parse_args(argv)
    views = tuple(args.views.split(","))
    free = tuple(args.free.split(",")) if args.free else None
    if free and not set(free) <= set(NAMES):
        parser.error(f"unknown gene(s): {sorted(set(free) - set(NAMES))}")
    config = ga.GAConfig(args.population, args.generations, args.seed, free=free, workers=args.workers)
    log = lambda row: print(row if isinstance(row, str) else
                            f"gen {row['generation']:3d} best {row['best']:.5f} mean {row['mean']:.5f} evals {row['evaluations']} {row['seconds']}s",
                            flush=True)
    result = fit(views, config, args.prior_weight, on_generation=log)
    fitted = Params.from_vector(result.best)
    holdout = None
    if not args.skip_holdout:
        hold_config = ga.GAConfig(args.holdout_population, args.holdout_generations, args.seed + 1, free=free, workers=args.workers)
        holdout = holdout_check(views, hold_config, args.prior_weight, None, log)
    evaluator = build_evaluator(views, args.prior_weight)
    report.write_report(args.out, evaluator, Params(), fitted, result.history, holdout, detail_step=not args.no_step)
    print(f"report written to {args.out}")


if __name__ == "__main__":
    main()
