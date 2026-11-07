## MODIFIED Requirements

### Requirement: Transform is applied once, in linear light

The transform SHALL be applied exactly once to every pixel a lit surface writes to the screen, after lighting, fog and emissive. It is
applied either as the last step of the surface's fragment, or by a host's final pass over a frame those surfaces wrote in linear light
without it. Within one frame a host SHALL use one of the two for all its lit surfaces. A pass that already writes display colour (a
sky whose shader tone maps and encodes itself) SHALL be shown as it is, without the transform. A pixel SHALL NOT be tone mapped or
encoded twice by overlapping passes (sky, terrain, models, foliage, lines). The tone map SHALL map 0 to 0, be monotonic, and keep a
mid-grey of 0.18 within 25% of 0.18 at exposure 1 before the encode. The encode SHALL be the sRGB transfer function.

#### Scenario: Black and ordering

- **WHEN** linear values 0, 0.05, 0.18, 1 and 16 are transformed at exposure 1
- **THEN** the results are 0 for 0, strictly increasing, and all at most 1

#### Scenario: Exposure scales light

- **WHEN** the exposure is doubled for a fixed linear value below the tone map's shoulder
- **THEN** the linear value before the tone map doubles

#### Scenario: Display-referred sky is not transformed

- **WHEN** a sky that writes display colour is drawn and then the terrain and models are drawn over it with the transform on
- **THEN** a pixel of the sky that no later pass covers equals the same pixel drawn with the transform off

#### Scenario: Final pass equals per-shader transform

- **WHEN** a lit PBR model is drawn once with the transform at the end of its shader and once in linear light followed by a final pass applying the same transform
- **THEN** the two images agree within 2/255 per channel
