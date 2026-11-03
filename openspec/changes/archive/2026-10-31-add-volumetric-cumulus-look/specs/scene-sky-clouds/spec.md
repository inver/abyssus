# Spec Delta

## ADDED Requirements

### Requirement: Volumetric heaped clouds

Volumetric clouds of a heaped band (cumulus) SHALL show rounded heads standing on a flat base: the top and the side
facing the sun bright white, the base a soft grey, a lighter rim where the cloud thins toward the sun, and edges that are crisp rather
than a smooth gradient. The shape SHALL come from the band's coverage and the cloud asset's own noise, so coverage and
wind agree with the other techniques. Bands of other types SHALL look as they did.

#### Scenario: Heads on a flat base

- **WHEN** a sky with a heaped low band is drawn volumetrically in daylight, seen from below at a low angle
- **THEN** each cloud has a rounded bright top, a flatter grey base at one altitude, and an edge crisper than the same
  sky drawn with shells

#### Scenario: Silver rim

- **WHEN** the camera looks toward the sun at a heaped cloud
- **THEN** the cloud's edge is clearly lighter than the sky behind it

#### Scenario: Other bands unchanged

- **WHEN** a band of cirrus is drawn volumetrically
- **THEN** it looks as it did before this change
