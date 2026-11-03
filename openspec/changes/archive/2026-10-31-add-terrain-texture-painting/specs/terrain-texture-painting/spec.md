# Spec Delta

## Purpose

Lets users decide by hand which texture shows where on a terrain, by painting the weights of its splat map in the Scene view with textures chosen from the project's assets, with live preview and one undoable step per stroke.

## ADDED Requirements

### Requirement: Paint Texture mode

The Properties panel of a terrain entity SHALL offer a Paint Texture toggle beside Sculpt Terrain while the entity's terrain asset is readable. Turning it on SHALL show a brush strip in the Scene view with the layer slots, the radius in world units, the strength from 0 through 1, the mode and the Exclusive option, and SHALL hide the move and rotate gizmos. Turning it off, selecting another entity, or starting Paint Foliage or Sculpt Terrain SHALL end it, and starting it SHALL end those. A circle of the brush radius SHALL be drawn on the terrain surface under the mouse and nowhere when the mouse is not over the selected terrain. Other mouse buttons and the wheel SHALL keep navigating the view.

#### Scenario: Enter the mode
- **WHEN** the user selects entity `1` (`Terrain`) of `Main Scene`, whose terrain has a splat map, and turns on Paint Texture
- **THEN** the brush strip appears with the first layer chosen, and move and rotate gizmos are hidden

#### Scenario: Not available
- **WHEN** the selected entity is a model, or its terrain data is unreadable
- **THEN** Paint Texture is not offered or is disabled with the reason

#### Scenario: Modes exclude each other
- **WHEN** Paint Texture is on and the user turns on Sculpt Terrain
- **THEN** Paint Texture turns off and only Sculpt Terrain holds the view

#### Scenario: Off the terrain
- **WHEN** the mouse points at the sky or at another terrain entity
- **THEN** no circle is drawn and a press does not paint

### Requirement: Layer slots

The strip SHALL show the terrain's five layer slots: Base, then the splat map's channels R, G, B and A, each with the name of its assigned texture asset and a thumbnail, or as empty. The user SHALL be able to choose any slot as the layer to paint and to assign any readable texture asset of the project to a slot or clear it. Assigning SHALL store the chosen texture's `uuid` in the terrain's matching `splatBase`, `splatR`, `splatG`, `splatB` or `splatA` field as one undoable step. A reference that does not resolve SHALL stay visible as unresolved and SHALL still be paintable.

#### Scenario: Assign a texture to a slot
- **WHEN** the user assigns the texture asset `texture_dirt` to slot G
- **THEN** the terrain's `splatG` holds that asset's `uuid`, the slot shows `texture_dirt`, and Undo restores the previous value

#### Scenario: Clear a slot
- **WHEN** the user clears slot B that held a texture
- **THEN** `splatB` becomes `null` and the slot shows as empty

#### Scenario: Unresolved reference
- **WHEN** slot R holds a `uuid` that no texture asset has
- **THEN** the slot shows it as unresolved and painting R still changes the splat map

### Requirement: Painting the splat map

A left-button drag over the terrain SHALL change the weights of the chosen layer's channel in the splat map under the brush. The change SHALL be strongest at the centre and fall smoothly to nothing at the radius, scaled by the strength. A rotated or scaled terrain entity SHALL be painted under the circle that is drawn. Weights SHALL stay from 0 through 255. The modes SHALL be:

- **Paint**: raises the chosen channel; Shift erases.
- **Erase**: lowers every channel, revealing the Base layer.
- **Smooth**: moves each channel toward the average of its neighbours.
- **Fill**: sets the chosen layer over the whole terrain with one click, strength and brush ignored.

Painting the Base slot SHALL lower every channel (the Base shows where all channels are zero). With Exclusive on, which is the default, raising one channel SHALL lower the other three in proportion so that the four weights never sum to more than 255; with Exclusive off only the chosen channel changes.

#### Scenario: Paint a patch
- **WHEN** the user drags across the terrain with Paint, slot R, radius `10`, strength `1`, on a splat map that is zero everywhere
- **THEN** the R weight rises in a band about 20 world units wide along the drag, strongest on the path, and the splat map is unchanged elsewhere

#### Scenario: Exclusive painting
- **WHEN** a texel has R = 255 and the user paints G over it with Exclusive on
- **THEN** G rises, R falls by the same total, and R + G + B + A never exceeds 255

#### Scenario: Not exclusive
- **WHEN** the same stroke is made with Exclusive off
- **THEN** only G changes and R stays 255

#### Scenario: Shift erases
- **WHEN** the user holds Shift and drags over painted texels with Paint
- **THEN** the chosen channel's weight under the brush goes down

#### Scenario: Fill
- **WHEN** the user clicks the terrain with Fill and slot G chosen
- **THEN** every texel has G = 255 and R = B = A = 0

#### Scenario: Fill Base
- **WHEN** the user clicks with Fill and the Base slot chosen
- **THEN** every channel of every texel is 0

#### Scenario: Navigation still works
- **WHEN** the user drags with the right button or turns the wheel in Paint Texture mode
- **THEN** the view orbits, pans or zooms as outside the mode, and the splat map is unchanged

### Requirement: Live preview and strokes

While the button is down, the stroke SHALL show in every open Scene view of the project that shows the terrain, without writing any file. The release SHALL write the splat map as one undoable step, and a stroke that changed nothing SHALL write nothing. Esc SHALL discard the stroke, leaving the files and every view as they were. Undo SHALL restore the previous image and Redo SHALL apply the stroke again, and both SHALL be refused, changing nothing, when the splat image was changed by something else in between.

#### Scenario: Two views
- **WHEN** two Scene views show the same terrain and the user paints in one
- **THEN** the other shows the stroke as it is painted

#### Scenario: Release writes once
- **WHEN** the user drags a stroke and releases the button
- **THEN** the splat image on disk changes once, and one Undo restores the image from before the stroke

#### Scenario: Empty stroke
- **WHEN** the user presses and releases with Erase where every weight is already 0
- **THEN** no file is written and the undo history is unchanged

#### Scenario: Esc
- **WHEN** the user presses Esc during a stroke
- **THEN** the terrain looks as before the press and no file changed

#### Scenario: Image changed meanwhile
- **WHEN** the splat image was replaced outside Abyssus after a stroke and the user chooses Undo
- **THEN** the undo is refused and the image is not touched

### Requirement: Create splat map

A terrain whose `splatMap` is `null`, or names an asset that does not resolve, SHALL offer Create splat map in the strip instead of painting. Create splat map SHALL ask for a resolution from 64 through 2048 pixels (default 1024), SHALL make a new texture asset folder with a fresh `uuid` holding an RGBA image of that size with every weight 0, SHALL store that `uuid` in the terrain's `splatMap`, and SHALL do all of it as one undoable step. The terrain SHALL look the same before and after, since all weights are 0.

#### Scenario: Create for a new terrain
- **WHEN** the terrain has `splatMap: null` and the user chooses Create splat map with 1024
- **THEN** a texture asset with a 1024 by 1024 image exists, the terrain's `splatMap` holds its `uuid`, and painting is possible

#### Scenario: Undo create
- **WHEN** the user undoes the creation
- **THEN** the new asset folder is gone and `splatMap` is `null` again

#### Scenario: Name collision
- **WHEN** an asset folder with the proposed name already exists
- **THEN** the creation uses another free name and does not touch the existing folder

### Requirement: Splat map that cannot be painted

Painting SHALL be unavailable, with the reason shown, when the terrain's splat image cannot be read, is not an image with an alpha channel, is not square, is larger than 2048 pixels on a side, or is smaller than 64. No file SHALL be changed by showing the reason. The user SHALL be able to leave the mode and to choose Create splat map to replace the reference.

#### Scenario: Not RGBA
- **WHEN** the splat map is an image without an alpha channel
- **THEN** the strip says it cannot be painted and a press paints nothing

#### Scenario: Too large
- **WHEN** the splat map is 4096 by 4096
- **THEN** the strip says the image is larger than 2048 pixels and a press paints nothing

### Requirement: Shared splat maps

When another terrain asset of the project references the same splat map texture, the strip SHALL say so before the first stroke and SHALL offer Make unique copy for this terrain. Make unique copy SHALL copy the image into a new texture asset with a fresh `uuid`, point only this terrain's `splatMap` at it, and be one undoable step. Without it, a stroke SHALL still be allowed and SHALL change the map for every terrain that uses it.

#### Scenario: Warning
- **WHEN** terrains `terrain_a` and `terrain_b` both name texture `texture_splat` and the user turns on Paint Texture on `terrain_a`
- **THEN** the strip says that `terrain_b` shares the splat map and offers Make unique copy

#### Scenario: Make unique copy
- **WHEN** the user chooses Make unique copy
- **THEN** `terrain_a` references a new texture asset with the same image, `terrain_b` still references `texture_splat`, and the next stroke changes only the new image

### Requirement: Files and format

Painting SHALL read and write only `additional.splatMap`, `splatBase`, `splatR`, `splatG`, `splatB` and `splatA` of a terrain `meta.json` and the image and `meta.json` of texture assets, and SHALL create a texture asset only with `format: "abyssus"`, `formatVersion: 1`, `type: "TEXTURE"` and `additional.file`. Every `meta.json` it changes SHALL be validated as native before any write, and an invalid or unsupported one SHALL be left unchanged with the reason shown. The splat image SHALL be written as an RGBA PNG whose colour values are exactly the weights (never premultiplied by alpha). Unknown fields, key order and number text of a changed `meta.json` SHALL be kept. No `formatVersion` changes and no other file SHALL be written.

#### Scenario: Weights survive the file
- **WHEN** a stroke writes weights R=200, G=0, B=0, A=0 on a texel and the image is read back
- **THEN** the texel reads R=200, G=0, B=0, A=0

#### Scenario: Alpha channel weights
- **WHEN** the user paints slot A to weight 100
- **THEN** the image stores A = 100 and the colour channels keep their own weights unchanged

#### Scenario: Invalid meta
- **WHEN** the terrain's `meta.json` has no `format: "abyssus"`
- **THEN** Paint Texture is not available and no file is written

#### Scenario: Other fields kept
- **WHEN** Create splat map changes a terrain `meta.json` that has an unknown extension field and custom key order
- **THEN** only `splatMap` changes and every other byte of the file stays

### Requirement: Dependents follow the painted map

After a stroke is written, every Scene view SHALL show the painted splat map, Play and the game SHALL load it as any splat map, and the ray tracing view SHALL use the new image. The terrain's heights, the foliage and the physics SHALL be unaffected.

#### Scenario: Ray tracing
- **WHEN** ray tracing is on and the user paints and releases
- **THEN** the traced terrain uses the painted weights after the release

#### Scenario: Game
- **WHEN** the project is played after a stroke
- **THEN** the terrain shows the painted textures with the same blend as the Scene view
