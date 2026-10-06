# model-import Specification

## Purpose

Lets users bring an OBJ, FBX, 3DS, DAE, glTF or GLB model into an Abyssus project as an ordinary native glTF model
asset, and optionally place it in the open scene. Before anything is written, they can check its size, orientation and
animations in a live preview.

## Requirements

### Requirement: Import is offered on the Assets node

Import Model... SHALL be available on the Assets node of a recognised project. It SHALL let the user choose one `.obj`,
`.fbx`, `.3ds`, `.dae`, `.gltf` or `.glb` file. A project whose `.abss` file fails native validation SHALL be refused
before the dialog opens, and nothing SHALL be written. A Blender file (`.blend`) SHALL be refused with a message that
the format is not supported and that the model can be exported from Blender as glTF.

#### Scenario: Import into the Untitled project

- **WHEN** the user chooses Import Model... on the Untitled project's Assets node and picks `crate.obj`
- **THEN** the import dialog opens for `crate.obj` with the folder name `model_crate`

#### Scenario: A project that is not native

- **WHEN** the project's `.abss` file has no `format: "abyssus"` marker
- **THEN** the action says the project file is not a supported native document, and no dialog opens

#### Scenario: A Blender file

- **WHEN** the user picks `ship.blend` through the chooser's all-files filter
- **THEN** the action says Blender files are not supported and suggests exporting glTF from Blender, and nothing is written

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

The dialog SHALL show which values came from the file or its format.

#### Scenario: An FBX file in centimetres, Z up

- **WHEN** the chosen FBX file states a unit scale of 1 cm and Z up
- **THEN** the unit shows cm and the up axis shows Z, both marked as read from the file

#### Scenario: An OBJ file

- **WHEN** the chosen file is an OBJ
- **THEN** the unit shows m and the up axis shows Y, both marked as defaults

### Requirement: Unit and up axis of DAE and glTF sources

A DAE file's unit and up axis SHALL start from its `<asset>` element (`unit meter` and `up_axis`), or metres and Y up
when it states neither. An `up_axis` of `X_UP` SHALL pre-fill Y, and the dialog SHALL say that the file states X up,
which is not offered. A glTF or GLB file SHALL start from metres and Y up, marked as defined by the format.

#### Scenario: A DAE file in centimetres, Z up

- **WHEN** the chosen DAE file's `<asset>` has `<unit meter="0.01"/>` and `<up_axis>Z_UP</up_axis>`
- **THEN** the unit shows cm and the up axis shows Z, both marked as read from the file, and the preview shows the model upright at its stated size

#### Scenario: A DAE file that states X up

- **WHEN** the chosen DAE file states `X_UP`
- **THEN** the up axis shows Y, and the dialog says the file states X up, which is not offered

#### Scenario: A glTF file

- **WHEN** the chosen file is a `.glb`
- **THEN** the unit shows m and the up axis shows Y, both marked as defined by the format

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
metallic-roughness. For a skinned and animated source, the skeleton, the vertex weights and
every animation, with its name and timing, SHALL be kept.

#### Scenario: An animated FBX character

- **WHEN** an FBX character with a skeleton and the animations `Idle` and `Run` is imported
- **THEN** the asset's model has both animations with their names and durations, and when placed in a scene it plays `Idle` in a loop

#### Scenario: A textured 3DS model

- **WHEN** a 3DS model whose material names a texture file beside it is imported
- **THEN** the asset's model shows that texture

### Requirement: glTF materials are kept as they are

A glTF or GLB source's metallic and roughness factors and textures, and its occlusion texture, SHALL be kept as they
are, and SHALL NOT be reported as approximated.

#### Scenario: A glTF model's PBR material

- **WHEN** a `.glb` whose material has metallic 1 and roughness 0.3 is imported
- **THEN** the asset's model has metallic 1 and roughness 0.3, and nothing is reported as approximated

### Requirement: What is left out is reported

Cameras, lights, non-triangle geometry (points and lines), missing texture files and unsupported texture formats SHALL
be left out of the model. Material terms that cannot be kept, such as specular colour, SHALL be reported as approximated.
The dialog SHALL list each before Create, and the asset's source record SHALL record each.

#### Scenario: An OBJ file whose texture is missing

- **WHEN** an OBJ file's `.mtl` names `wood.png` and that file does not exist
- **THEN** the dialog lists `wood.png` as missing, Create stays enabled, and the material keeps its colour

### Requirement: glTF features the model cannot hold are reported

From a glTF or GLB source, morph targets and material or texture extensions the renderer does not use (such as
`KHR_materials_transmission` or `KHR_texture_transform`) SHALL be left out, listed in the dialog before Create, and
recorded in the asset's source record.

#### Scenario: A glTF model with morph targets

- **WHEN** a `.gltf` file whose mesh has morph targets is imported
- **THEN** the dialog lists the morph targets as left out, and the written model has the mesh in its base shape

### Requirement: Textures become files of the asset

Textures used by the kept materials SHALL be written as PNG files inside the new asset folder, and the model SHALL refer
to them there. This covers files next to the source, textures embedded in an FBX or GLB file, and textures held as
data URIs in a glTF file. Two materials using the same
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
`source.json` SHALL record the source file name, its path, its SHA-256 and format, the stated and chosen unit and up
axis, the size setting, and everything left out or approximated.

#### Scenario: The asset after import

- **WHEN** `crate.obj` is imported into the Untitled project as `model_crate`
- **THEN** `assets/model_crate` holds `meta.json`, `model.glb`, `source.json` and its textures, and the Untitled assets list shows `model_crate` as an unused MODEL

### Requirement: The source path is recorded

`source.json` SHALL record the source's path as `sourcePath`. It SHALL be relative to the folder of the project's
`.abss` file, with `/` separators, when the source is inside that folder, and absolute otherwise.

#### Scenario: A source inside the project

- **WHEN** `sources/crate.obj` beside the Untitled project's `.abss` file is imported
- **THEN** `source.json` records `sourcePath` as `sources/crate.obj`

#### Scenario: A source outside the project

- **WHEN** `/home/user/Downloads/crate.obj` is imported
- **THEN** `source.json` records `sourcePath` as `/home/user/Downloads/crate.obj`

### Requirement: Import changes only the new asset folder

Without placement, the import SHALL NOT change the project's scenes. With placement, it SHALL change only the chosen
scene, by adding one entity. The import SHALL NOT change the project's `.abss` file, and it SHALL NOT write anything next
to the source file, including textures extracted from it.

#### Scenario: The source folder is left alone

- **WHEN** an FBX file with embedded textures is imported from a folder outside the project
- **THEN** that folder holds exactly the same files as before the import

### Requirement: Placement in the open scene is offered

The dialog SHALL offer to add the imported model to the scene of the selected scene view, naming that scene. The option
SHALL be on by default when such a view exists. It SHALL be disabled, with the reason, when no scene view is open, when
the view is in Play, or when its scene cannot be read as a native scene.

#### Scenario: No scene view open

- **WHEN** the import dialog opens while no scene view is open
- **THEN** the placement option is disabled and says no scene view is open, and Create writes only the asset folder

#### Scenario: A scene in Play

- **WHEN** the selected scene view is playing
- **THEN** the placement option is disabled and says the scene is playing

### Requirement: The imported model is placed like Add Asset

With placement on, Create SHALL add one entity to that scene, as Add Asset from the Scene view does: the next entity
id, the name `Model <id>`, type `OBJECT`, a render component naming the new asset with shader key `defaultShader`, and
the position the view orbits around when Create is chosen. The new entity SHALL be selected afterwards. If the scene
cannot take the entity at Create, nothing SHALL be written, and the reason SHALL be reported.

#### Scenario: Import and place

- **WHEN** `crate.obj` is imported with placement on while the `Main Scene.scene` view, whose highest entity id is `10`, orbits the point `(10, 0, -4)`
- **THEN** `assets/model_crate` is created, the scene gains entity `11` named `Model 11` at `(10, 0, -4)` with a render component of asset `MODEL` `model_crate`, and entity `11` is selected

#### Scenario: The scene is refused at Create

- **WHEN** placement is on and the scene file was made unreadable after the dialog opened
- **THEN** Create reports that the scene cannot be edited, and neither the asset folder nor the scene is written

### Requirement: Undoable import

An import SHALL be one undoable operation of the Import Model action. Undo SHALL remove the created folder and, with
placement, the added entity. Redo SHALL restore the same files, bytes and `uuid`, and the same entity. A failure while
writing SHALL leave no partial folder and no added entity, and SHALL report the reason. Undo SHALL be refused, with a
reason, once a scene references the asset through an edit other than the import's own placement.

#### Scenario: Undo an import

- **WHEN** the user imports `crate.obj` into the Untitled project and then chooses Undo
- **THEN** `assets/model_crate` is gone and the Untitled assets list is as before

#### Scenario: Undo an import that placed the model

- **WHEN** the user imports `crate.obj` with placement into `Main Scene.scene` and then chooses Undo once
- **THEN** entity `11` is gone from the scene, `assets/model_crate` is gone, and Redo brings both back

#### Scenario: Undo after the asset was placed

- **WHEN** the user imports `crate.obj`, places `model_crate` in `Main Scene.scene` by a separate Add Asset, and then tries to undo the import
- **THEN** the undo is refused with a message that a scene uses the asset
