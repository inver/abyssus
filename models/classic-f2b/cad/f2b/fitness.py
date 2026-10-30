"""Evaluate a parameter set against the photo targets."""
from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

from . import geometry, silhouette
from .params import GENES, GROUPS, Params
from .silhouette import Mesh, Target

PRIOR_WEIGHT = 0.02            # weight of the mean squared normalised deviation from the defaults
_GROUP_GENES = {group: tuple(g.name for g in GENES if g.group == group) for group in GROUPS}


@dataclass
class Evaluation:
    total: float                                   # infinity when the model cannot be built
    views: dict[str, float] = field(default_factory=dict)       # per view loss
    components: dict[str, tuple[float, float]] = field(default_factory=dict)  # per view (area ratio, contour px)
    prior: float = 0.0
    error: str = ""


class Evaluator:
    """Builds, tessellates and scores parameter sets. Per-group meshes are cached, so a candidate
    that changes only some genes re-tessellates only the parts those genes shape."""

    def __init__(self, targets: list[Target], prior_weight: float = PRIOR_WEIGHT, cache_limit: int = 256):
        self.targets = targets
        self.prior_weight = prior_weight
        self._cache: dict[tuple, Mesh] = {}
        self._cache_limit = cache_limit
        self._default = Params().to_vector()

    def _group_mesh(self, group: str, params: Params) -> Mesh:
        key = (group, tuple(params[name] for name in _GROUP_GENES[group]))
        mesh = self._cache.get(key)
        if mesh is None and group in ("wing", "stabilizer"):
            mesh = self._panel_meshes(group, params)
            self._store(key, mesh)
        elif mesh is None:
            parts = geometry.build_group(group, params)
            for part in parts:
                if not part.shape.isValid() or part.shape.Volume() <= 0:
                    raise ValueError(f"invalid solid {part.name}")
            mesh = silhouette.tessellate(parts)
            self._store(key, mesh)
        return mesh

    def _store(self, key: tuple, mesh: Mesh) -> None:
        if len(self._cache) >= self._cache_limit:
            self._cache.pop(next(iter(self._cache)))
        self._cache[key] = mesh

    @staticmethod
    def _panel_meshes(group: str, params: Params) -> Mesh:
        """Wing and stabilizer meshes straight from their sections (ruled lofts): about 50 times
        faster than tessellating the solids, and the same surface."""
        kind = "wing" if group == "wing" else "stabilizer"
        parts = [geometry.panel_mesh(kind, True, params), geometry.panel_mesh(kind, False, params)]
        offset = len(parts[0][0])
        return Mesh(np.vstack([parts[0][0], parts[1][0]]), np.vstack([parts[0][1], parts[1][1] + offset]))

    def mesh(self, params: Params) -> Mesh:
        meshes = [self._group_mesh(group, params) for group in geometry.BUILD_ORDER]
        offsets = np.cumsum([0] + [len(m.vertices) for m in meshes[:-1]])
        return Mesh(np.vstack([m.vertices for m in meshes]),
                    np.vstack([m.triangles + off for m, off in zip(meshes, offsets)]))

    def prior(self, params: Params) -> float:
        return float(np.mean((params.to_vector() - self._default) ** 2))

    def evaluate(self, params: Params, use_prior: bool = True) -> Evaluation:
        try:
            mesh = self.mesh(params)
        except Exception as error:   # an unbuildable candidate is simply the worst candidate
            return Evaluation(float("inf"), error=f"{type(error).__name__}: {error}")
        views, components = {}, {}
        for target in self.targets:
            xor, contour = silhouette.view_loss(silhouette.render_mask(mesh, target.camera), target)
            components[target.name] = (xor, contour)
            views[target.name] = silhouette.combine(xor, contour)
        prior = self.prior_weight * self.prior(params) if use_prior else 0.0
        return Evaluation(float(np.mean(list(views.values()))) + prior, views, components, prior)
