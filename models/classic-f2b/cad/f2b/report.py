"""Outputs of a fit: parameters, loss history, per-view losses, overlays and the fitted STEP."""
from __future__ import annotations

import csv
import json
from pathlib import Path

import cv2
import numpy as np

from . import export, geometry
from .cameras import load_photo
from .fitness import Evaluation, Evaluator
from .params import GENES, Params
from .silhouette import Target, render_mask


def overlay(target: Target, model_mask: np.ndarray, title: str) -> np.ndarray:
    """The photograph with the target contour (green) and the model contour (red), prop disc greyed."""
    photo = load_photo(target.camera)[:, :, :3].copy()
    shade = photo.copy()
    shade[target.ignore] = (shade[target.ignore] * 0.55 + 90 * 0.45).astype(np.uint8)
    for mask, colour in ((target.mask, (0, 220, 0)), (model_mask > 0, (0, 0, 255))):
        contours, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_LIST, cv2.CHAIN_APPROX_NONE)
        cv2.drawContours(shade, contours, -1, colour, 1)
    cv2.rectangle(shade, (0, 0), (shade.shape[1], 26), (255, 255, 255), -1)
    cv2.putText(shade, title, (8, 19), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (20, 20, 20), 1, cv2.LINE_AA)
    return shade


def evaluation_json(evaluation: Evaluation) -> dict:
    return {"total": evaluation.total, "prior": evaluation.prior,
            "views": {view: {"loss": evaluation.views[view], "area_ratio": evaluation.components[view][0],
                             "contour_px": evaluation.components[view][1]} for view in evaluation.views}}


def write_overlays(out: Path, evaluator: Evaluator, defaults: Params, fitted: Params) -> None:
    for label, params in (("before", defaults), ("after", fitted)):
        mesh = evaluator.mesh(params)
        evaluation = evaluator.evaluate(params, use_prior=False)
        for target in evaluator.targets:
            title = f"{target.name} {label}: loss {evaluation.views[target.name]:.4f} (green: photo, red: model)"
            cv2.imwrite(str(out / f"overlay-{target.name}-{label}.jpg"),
                        overlay(target, render_mask(mesh, target.camera), title), [cv2.IMWRITE_JPEG_QUALITY, 90])


def write_history(path: Path, history: list[dict]) -> None:
    with open(path, "w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(history[0]))
        writer.writeheader()
        writer.writerows(history)


def gene_table(defaults: Params, fitted: Params, tolerance: float = 0.02) -> list[dict]:
    """Every gene with its default and fitted value; genes within `tolerance` of a bound are flagged."""
    rows = []
    for gene in GENES:
        span = gene.high - gene.low
        value = fitted[gene.name]
        rows.append({"name": gene.name, "group": gene.group, "unit": gene.unit, "default": defaults[gene.name],
                     "fitted": value, "low": gene.low, "high": gene.high,
                     "at_bound": bool(value - gene.low < tolerance * span or gene.high - value < tolerance * span),
                     "moved": bool(abs(value - defaults[gene.name]) > 0.02 * span)})
    return rows


def write_report(out: Path, evaluator: Evaluator, defaults: Params, fitted: Params, history: list[dict],
                 holdout: dict | None = None, detail_step: bool = True) -> None:
    out.mkdir(parents=True, exist_ok=True)
    fitted.save(out / "params-fitted.json")
    write_history(out / "history.csv", history)
    before, after = evaluator.evaluate(defaults, use_prior=False), evaluator.evaluate(fitted, use_prior=False)
    (out / "losses.json").write_text(json.dumps({"defaults": evaluation_json(before), "fitted": evaluation_json(after)}, indent=2))
    (out / "genes.json").write_text(json.dumps(gene_table(defaults, fitted), indent=2))
    write_overlays(out, evaluator, defaults, fitted)
    if holdout is not None:
        (out / "holdout.json").write_text(json.dumps(holdout, indent=2))
    if detail_step:
        export.export(geometry.build(fitted, detail=True), out / "model", "Classic_F2B_fitted")
