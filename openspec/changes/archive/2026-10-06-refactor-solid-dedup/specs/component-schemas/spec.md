# Spec Delta

## ADDED Requirements

### Requirement: Every field type behaves the same everywhere

A game component field of any type (decimal, whole, boolean, text, choice, entity reference, asset reference,
vector, color) SHALL decode, encode, default and show in the Properties panel by one rule, whether the component is
read when the scene loads, edited in the panel or written back to the file. Omitted defaults, unrelated number text
and key order SHALL stay as the file has them.

#### Scenario: Vector round trip

- **WHEN** a component's vector field is edited in the Properties panel from `{"x": 1, "y": 2, "z": 3}` to `x` 4
- **THEN** the file holds `{"x": 4, "y": 2, "z": 3}`, with `y` and `z` written as before, and a scene load reads
  the same values

#### Scenario: Default stays omitted

- **WHEN** a field is edited back to its default value
- **THEN** the key is omitted from the file as it is today

#### Scenario: Value outside a limit

- **WHEN** a field with a minimum is given a smaller value
- **THEN** the edit is refused with the same message as today and the file is unchanged
