## MODIFIED Requirements

### Requirement: glTF features the model cannot hold are reported

From a glTF or GLB source, morph targets and material or texture extensions the renderer does not use (such as
`KHR_materials_transmission` or `KHR_texture_transform`) SHALL be left out, listed in the dialog before Create, and
recorded in the asset's source record. `KHR_materials_emissive_strength` is used by the renderer: it SHALL be kept in the written
model, and SHALL NOT be listed as left out or recorded under the skipped items.

#### Scenario: A glTF model with morph targets

- **WHEN** a `.gltf` file whose mesh has morph targets is imported
- **THEN** the dialog lists the morph targets as left out, and the written model has the mesh in its base shape

#### Scenario: A GLB with emissive strength

- **WHEN** a GLB whose `Emissive` material has emissive factor `(0.39, 0.78, 1.0)` and emissive strength 10 is imported
- **THEN** the dialog does not list `KHR_materials_emissive_strength`, the asset's `source.json` does not record it under `skipped`, and the written `model.glb` keeps both the factor and the strength 10
