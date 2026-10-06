# Spec Delta

## Purpose

Lets users bring an OBJ, FBX or 3DS model into an Abyssus project as an ordinary native glTF model asset. Before
anything is written, they can check its size, orientation and animations in a live preview.

## ADDED Requirements

### Requirement: Import is offered on the Assets node

Import Model... SHALL be available on the Assets node of a recognised project. It SHALL let the user choose one `.obj`,
`.fbx` or `.3ds` file. A project whose `.abss` file fails native validation SHALL be refused before the dialog opens,
and nothing SHALL be written.

#### Scenario: Import into the Untitled project

- **WHEN** the user chooses Import Model... on the Untitled project's Assets node and picks `crate.obj`
- **THEN** the import dialog opens for `crate.obj` with the folder name `model_crate`

#### Scenario: A project that is not native

- **WHEN** the project's `.abss` file has no `format: "abyssus"` marker
- **THEN** the action says the project file is not a supported native document, and no dialog opens

### Requirement: An unreadable source is rejected

A file that cannot be read as a model, or that holds no triangle geometry, SHALL be reported in the dialog with the
reason. Create SHALL stay disabled, and nothing SHALL be written.

#### Scenario: A corrupt FBX file

- **WHEN** the chosen `.fbx` file is truncated
- **THEN** the dialog shows that the file could not be read, with the reason, and Create is disabled

### Requirement: Import settings

The dialog SHALL offer a folder name, a source unit, an up axis and an optional target size:
- The folder name SHALL default to `model_` plus the source file name without its extension. It SHALL be a valid folder
  name, unique among the project's asset folders.
- The unit is m, cm, mm, in or ft. The up axis is Y or Z.

Create SHALL be enabled only while every setting is valid.

#### Scenario: A taken folder name

- **WHEN** the folder name is `tree`, which the Untitled project already has
- **THEN** the dialog says the name is taken and Create is disabled

#### Scenario: An invalid target size

- **WHEN** fit-to-size is on with a size of 0
- **THEN** the dialog says the size must be greater than 0 and Create is disabled

### Requirement: Unit and up axis are pre-filled from the file

The unit and up axis SHALL start from what the source file states, and the user MAY change both:
- an FBX file's unit scale and up axis, when its header states them;
- a 3DS file: metres (with the file's own master scale applied) and Z up;
- an OBJ file, or an FBX file that states neither: metres and Y up.

The dialog SHALL show which values came from the file.

#### Scenario: An FBX file in centimetres, Z up

- **WHEN** the chosen FBX file states a unit scale of 1 cm and Z up
- **THEN** the unit shows cm and the up axis shows Z, both marked as read from the file

#### Scenario: An OBJ file

- **WHEN** the chosen file is an OBJ
- **THEN** the unit shows m and the up axis shows Y, both marked as defaults

### Requirement: Placement and size of the imported model

The model SHALL be converted to metres with +Y up by the chosen unit and up axis. With fit-to-size, it SHALL be scaled
uniformly so that the chosen measure (largest extent, or height) equals the target in metres. In every case, its lowest
point SHALL be at height 0, and the middle of its extent on X and Z SHALL be at 0. Animated models are measured in their
rest pose.

#### Scenario: A crate in centimetres

- **WHEN** a 100 x 100 x 100 unit OBJ crate is imported with unit cm and no fit
- **THEN** the model is 1 m on each side, rests on height 0 and is centred on X and Z

#### Scenario: Fit to a height

- **WHEN** a character is imported with fit-to-size set to a height of 1.8 m
- **THEN** the model is 1.8 m tall and its feet touch height 0

### Requirement: The imported model keeps hierarchy, materials and animations

The written model SHALL keep the source's named node hierarchy, its meshes and its materials. Base colour, base colour
texture, normal map, opacity and two-sidedness SHALL be kept. Other material terms SHALL be approximated as
metallic-roughness. For a skinned and animated source, the skeleton, the vertex weights and every animation, with its
name and timing, SHALL be kept.

#### Scenario: An animated FBX character

- **WHEN** an FBX character with a skeleton and the animations `Idle` and `Run` is imported
- **THEN** the asset's model has both animations with their names and durations, and when placed in a scene it plays `Idle` in a loop

#### Scenario: A textured 3DS model

- **WHEN** a 3DS model whose material names a texture file beside it is imported
- **THEN** the asset's model shows that texture

### Requirement: What is left out is reported

Cameras, lights, non-triangle geometry (points and lines), missing texture files and unsupported texture formats SHALL
be left out of the model. Material terms that cannot be kept, such as specular colour, SHALL be reported as approximated.
The dialog SHALL list each before Create, and the asset's source record SHALL record each.

#### Scenario: An OBJ file whose texture is missing

- **WHEN** an OBJ file's `.mtl` names `wood.png` and that file does not exist
- **THEN** the dialog lists `wood.png` as missing, Create stays enabled, and the material keeps its colour

### Requirement: Textures become files of the asset

Textures used by the kept materials SHALL be written as PNG files inside the new asset folder, and the model SHALL refer
to them there. This covers files next to the source and textures embedded in an FBX file. Two materials using the same
image SHALL share one file.

#### Scenario: An FBX file with an embedded texture

- **WHEN** an FBX file with one embedded texture is imported
- **THEN** the asset folder holds that texture as a PNG file, and the model uses it

### Requirement: Live preview

The dialog SHALL show a 3D preview of the model as Create would write it: the chosen unit, up axis, size, grounding and
centring applied, over a ground grid with a 1 m reference marker. The user SHALL be able to orbit and zoom. The preview
SHALL follow every settings change, and a status line SHALL give the size in metres and the number of animations and
textures.

#### Scenario: Changing the unit updates the preview

- **WHEN** the unit of a previewed model is changed from m to cm
- **THEN** the preview shows the model a hundredth of the size beside the unchanged 1 m marker, and the status line shows the new size

#### Scenario: A model that cannot be converted

- **WHEN** the chosen file cannot be converted
- **THEN** the preview shows the reason instead of a model

### Requirement: Animations play in the preview

For a source with animations, the preview SHALL play one animation in a loop. A drop-down SHALL list every animation by
name and choose which one plays, starting with the first. The choice SHALL NOT change what is written: every animation
is kept.

#### Scenario: Choosing an animation

- **WHEN** the user chooses `Run` in the animation list of a character with `Idle` and `Run`
- **THEN** the preview plays `Run` in a loop, and the written model still has both animations

### Requirement: The preview does not disturb the editor

Closing or cancelling the dialog SHALL release everything the preview created. Open scene views SHALL keep rendering
while the dialog is open and after it closes. Reading and converting the source SHALL NOT freeze the IDE.

#### Scenario: Cancel with a scene view open

- **WHEN** the Untitled `Main Scene.scene` view is open and the user cancels an import dialog that was previewing a model
- **THEN** the scene view keeps drawing its models as before

### Requirement: A native asset with its provenance

Create SHALL write a new asset folder. Its `meta.json` SHALL have `format: "abyssus"`, integral `formatVersion: 1`, a
fresh `uuid`, `type: "MODEL"`, and `additional` with `file: "model.glb"`, `format: "GLTF"` and `binary: true`. A
`source.json` SHALL record the source file name, its SHA-256 and format, the stated and chosen unit and up axis, the
size setting, and everything left out or approximated.

#### Scenario: The asset after import

- **WHEN** `crate.obj` is imported into the Untitled project as `model_crate`
- **THEN** `assets/model_crate` holds `meta.json`, `model.glb`, `source.json` and its textures, and the Untitled assets list shows `model_crate` as an unused MODEL

### Requirement: Import changes only the new asset folder

The import SHALL NOT change the project's scenes or `.abss` file. It SHALL NOT write anything next to the source file,
including textures extracted from it.

#### Scenario: The source folder is left alone

- **WHEN** an FBX file with embedded textures is imported from a folder outside the project
- **THEN** that folder holds exactly the same files as before the import

### Requirement: Undoable import

An import SHALL be one undoable operation of the Import Model action. Undo SHALL remove the created folder. Redo SHALL
restore the same files, bytes and `uuid`. A failure while writing SHALL leave no partial folder, and SHALL report the
reason. Undo SHALL be refused, with a reason, once a scene references the asset.

#### Scenario: Undo an import

- **WHEN** the user imports `crate.obj` into the Untitled project and then chooses Undo
- **THEN** `assets/model_crate` is gone and the Untitled assets list is as before

#### Scenario: Undo after the asset was placed

- **WHEN** the user imports `crate.obj`, places `model_crate` in `Main Scene.scene`, and then tries to undo the import
- **THEN** the undo is refused with a message that a scene uses the asset
