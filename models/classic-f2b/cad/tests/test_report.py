import json

import pytest

from f2b import fit
from f2b.params import Params


def test_a_short_fit_writes_a_complete_report(tmp_path):
    out = tmp_path / "fit"
    fit.main(["--out", str(out), "--views", "photo7,img_4", "--population", "6", "--generations", "2", "--workers", "1",
              "--free", "fin_te_dx,spat_length,canopy_rise", "--holdout-population", "4", "--holdout-generations", "1",
              "--no-step"])
    for name in ("params-fitted.json", "history.csv", "losses.json", "genes.json", "holdout.json"):
        assert (out / name).exists(), name
    for view in ("photo7", "img_4"):
        for label in ("before", "after"):
            assert (out / f"overlay-{view}-{label}.jpg").stat().st_size > 0
    losses = json.loads((out / "losses.json").read_text())
    for view in ("photo7", "img_4"):
        assert losses["fitted"]["views"][view]["loss"] <= losses["defaults"]["views"][view]["loss"] + 0.01
    assert losses["fitted"]["total"] <= losses["defaults"]["total"] + 1e-9     # elitism keeps the defaults
    holdout = json.loads((out / "holdout.json").read_text())
    assert set(holdout) == {"photo7", "img_4"}
    assert all(isinstance(entry["improved"], bool) for entry in holdout.values())
    fitted = Params.load(out / "params-fitted.json")
    assert fitted["fin_te_dx"] == fitted["fin_te_dx"]


def test_unknown_gene_is_refused(tmp_path):
    with pytest.raises(SystemExit):
        fit.main(["--out", str(tmp_path), "--free", "not_a_gene", "--skip-holdout"])
