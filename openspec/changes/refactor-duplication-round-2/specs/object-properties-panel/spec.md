# Spec Delta

## ADDED Requirements

### Requirement: Asset and component fields edit the same way

An editable field in the Abyssus Properties panel SHALL behave the same whether it belongs to an asset's `meta.json` or
to an entity's component. A text field SHALL save when the user presses Enter or moves focus away. A choice field
SHALL save when the user picks a value. A value equal to the current one SHALL write nothing. A refused value SHALL put
the previous value back in the field and show the reason beside it, with nothing written. An accepted value SHALL clear
any reason shown beside that field.

#### Scenario: Confirm an asset field by leaving it

- **WHEN** the user selects the `terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b` asset of the `Untitled` project, types
  `30` into `uv` and moves focus to another field
- **THEN** the `meta.json` holds `30` for `uv` and the field shows `30`

#### Scenario: Confirm a component field by leaving it

- **WHEN** the user selects `Spot Light 8` in `Main Scene` of the `Untitled` project, types `2` into the light's
  `intensity` and moves focus to another field
- **THEN** the scene file holds `2` for that light's `intensity` and the field shows `2`

#### Scenario: Refused values look the same in both panels

- **WHEN** the user enters `abc` into `uv` of that terrain, and then into `intensity` of `Spot Light 8`, pressing Enter
  each time
- **THEN** each field shows its previous value again (`60.0` and `1`), a reason appears beside each, and neither file
  changes

#### Scenario: Equal value writes nothing

- **WHEN** the user presses Enter in `intensity` of `Spot Light 8` without changing `1`
- **THEN** the scene file is not written and no Undo entry is added

#### Scenario: Light field labels are unchanged

- **WHEN** the user selects `Spot Light 8`
- **THEN** its range, cone angle and edge softness fields show the same labels as before this change, and the range
  field shows the same tooltip
