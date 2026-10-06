# Active airfield environment

Seven downloaded, unmodified Kenney models are packaged as native Abyssus
assets under `../ControlLine/assets/model_airfield_*`. There are three
deciduous tree variants, a shed, a garage row, a civic-building approximation,
and an apartment-building approximation. Each folder includes its original
CC0 license, source URL, model checksum, and measured bounds in `source.json`.

Sources: [Nature Kit](https://kenney.nl/assets/nature-kit),
[City Kit Industrial](https://kenney.nl/assets/city-kit-industrial), and
[City Kit Commercial](https://kenney.nl/assets/city-kit-commercial).

The models are stylized visual approximations, not models of the actual
buildings. Tree species and building heights cannot be determined reliably
from the reference screenshot. Scaling and placement are estimates.

## Scene configuration

The bundled `Field.scene` uses the generated airfield and its surrounding scenery.
`field-environment.patch.json` records the applied change against the original scene. It:

- Selects the previously generated airfield terrain for entity 0 and aligns
  its pilot mark approximately to the origin.
- Appends 92 tree entities, 12 building entities and one 600m surrounding
  ground entity. Entity IDs start at 100 and do not overlap existing IDs.
- Uses meters, with north along -Z and east along +X. The flying area remains
  clear to 35m, including tree crown extents.
- Places workshops and garage rows east/southeast of the circle, the civic
  hall north, apartment blocks east/northeast, and garage/storage buildings west.
- Keeps the pilot, planes, flight parameters, lighting, sky and all unrelated
  scene data. Scenery is visual only, without physics colliders.

`placements.json` records estimated footprint sizes and center positions.
[layout.svg](layout.svg) is a top-down placement diagram, not an in-game render.
The wider neighborhood uses grass ground rather than a surveyed road map;
roads beyond the original site texture are not reconstructed.

## Application record

The user approved the proposed direct, text-preserving scene application by
requesting that the generated airfield be used in the game. Plugin compilation
subsequently passed, but invoking the writer through the test framework was
blocked by unrelated existing test compilation failures. The approved offline
edit was therefore applied after checking the original scene checksum and
validating both documents and all asset metadata with the actual plugin
`AbyssusDocumentFormat`.

Only the field asset reference and Z position were replaced; 105 entities were
appended. Reversing those substitutions and removing the appended block
reproduces the original scene byte for byte. Future scene edits use the normal
editor writer. The preparation script does not write `.scene` files.

## Verification

All seven models were parsed successfully with the project's actual
`AssimpModelLoader` without GL. Their metadata, texture dependencies, licenses,
and binary payloads are present. The patch carries the original scene checksum
so reapplication can reject an unexpected scene. Rendering and placement in
the editor still require a visual check in a copy of the project.

`prepare_airfield.py` records the preparation of asset packages and the original patch from the three
downloaded archives in `/private/tmp/abyssus-{nature,industrial,commercial}-kit.zip`.
It expects the original scene without the appended entities; do not rerun it
against the active scene. `placements.json` remains the placement record.
