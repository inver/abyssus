# flightgear-aircraft-import Specification

## Purpose
Lets users bring an aircraft from a FlightGear aircraft archive into an Abyssus project as an ordinary native model
asset, with its parts, a known frame and scale, its textures, and a record of where it came from and under what licence.

## Requirements

### Requirement: Import is offered on the Assets node

Import FlightGear Aircraft... SHALL be available on the Assets node of a recognised project. The user chooses a `.zip`
archive. An archive with no aircraft (no `*-set.xml` naming a model), or one that cannot be read, SHALL be rejected with
a reason and SHALL write nothing. An archive with several aircraft SHALL let the user choose one. A project whose `.abss`
file fails native validation SHALL be refused before anything is written.

#### Scenario: Import into the Untitled project

- **WHEN** the user chooses Import FlightGear Aircraft... on the Untitled project's Assets node and picks an archive with one aircraft
- **THEN** the dialog shows that aircraft's description, authors, licence and parts

#### Scenario: An archive without an aircraft

- **WHEN** the chosen archive holds no `*-set.xml` that names a model
- **THEN** the dialog says no aircraft was found and Create stays disabled

### Requirement: Import settings

The dialog SHALL offer a folder name, a size and the parts to keep:
- The folder name defaults to `model_` plus the aircraft folder name. It SHALL be unique among the project's asset
  folders and a valid folder name.
- The size is either the original metres or a target wingspan in metres, greater than 0.
- Each named part of the model is listed. Parts that a `select` animation hides at rest are unticked by default; the
  rest state has every property equal to 0. Every other part is ticked.

Create SHALL be enabled only while the settings are valid and at least one part is ticked.

#### Scenario: The spinning propeller disc is left out by default

- **WHEN** an aircraft shows `Propeller` while rpm is below 500 and `Propeller.2` while rpm is above 300
- **THEN** `Propeller` is ticked and `Propeller.2` is unticked

#### Scenario: A taken folder name

- **WHEN** the folder name equals an existing asset folder of the project
- **THEN** the dialog says the name is taken and Create is disabled

### Requirement: The imported model

Create SHALL write one model built from the aircraft's model XML: its AC3D geometry and the nested sub-models, each
placed at its offsets. Only the ticked parts SHALL be included:
- Each part SHALL keep its name as a node of the model.
- The model SHALL use the Abyssus plane frame: nose toward +Z, up +Y, left wing toward +X. It SHALL be centred on the
  middle of its span and length, and its lowest point SHALL be at height 0.
- With a target wingspan, its overall size along X SHALL equal that span. Otherwise it keeps FlightGear's metres.
- Materials SHALL keep the AC3D diffuse colours and textures. Surfaces AC3D marks two-sided SHALL render from both
  sides.
- Instrument panels, non-AC3D sub-models and unsupported textures SHALL be left out. Each SHALL be listed in the
  dialog before Create, and recorded in the asset's source record.

#### Scenario: A Cessna 172 scaled to a 1 m span

- **WHEN** `c172r` is imported with a 1.0 m target wingspan
- **THEN** the model is 1.0 m across X, its nose propeller is at the +Z end, its wings are above its wheels, and its wheels touch height 0

#### Scenario: A part left out

- **WHEN** the user unticks `Cabin` and creates the asset
- **THEN** the model has no `Cabin` node and every other ticked part is present

### Requirement: Textures

AC3D textures SHALL be found by file name in the aircraft's folder (FlightGear models often hold absolute author
paths). SGI images (`.rgb`, `.rgba`, `.sgi`, `.bw`; verbatim or RLE, 1-4 channels) SHALL be converted to PNG. PNG and
JPEG SHALL be copied. A texture that is missing or in another format SHALL leave its surfaces with their material
colour and SHALL be reported.

#### Scenario: SGI textures become PNG

- **WHEN** `c172r` is imported
- **THEN** its `c172-01.rgb` and `c172-02.rgb` are written as PNG images inside the new asset and the model's materials use them

### Requirement: A native asset with its provenance

Create SHALL write a new asset folder. Its `meta.json` SHALL have `format: "abyssus"`, integral `formatVersion: 1`, a
fresh `uuid`, `type: "MODEL"`, and the model file in `additional.file` with `format: "GLTF"` and `binary: true`. A
`source.json` SHALL record:
- the archive file name and its SHA-256, the aircraft, its description and authors from the set file, and the model path;
- the licence, from the archive's licence file or the set file's `license` entry, or `unknown`;
- the size setting, the parts left out, and everything skipped.

Licence files in the archive (`COPYING`, `LICENSE*`) SHALL be copied into the folder. When no licence is found, the
dialog SHALL warn before Create that redistribution rights are unknown. The project's scenes and `.abss` file SHALL NOT
change, and the new asset SHALL appear as unused.

#### Scenario: An archive without a licence file

- **WHEN** `c172r.zip` is imported
- **THEN** the dialog warns that the archive states no licence, and `source.json` records the licence as `unknown`

### Requirement: Undoable import

An import SHALL be one undoable operation of the Import action. Undo SHALL remove the created folder. Redo SHALL restore
the same files, bytes and `uuid`. A failure while writing SHALL leave no partial folder, and SHALL report the reason.

#### Scenario: Undo an import

- **WHEN** the user imports an aircraft into the Untitled project and then chooses Undo
- **THEN** the new asset folder is gone and the Untitled project's assets list is as before
