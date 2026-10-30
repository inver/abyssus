## Why

The heavyweight scene GL canvas covers IntelliJ notification balloons on macOS, obscuring their text and actions.

## What Changes

Present a lightweight snapshot while an overlapping Swing overlay is visible, then resume live rendering when it leaves. Retain the existing safe GL surface and context lifecycle.

## Capabilities

### New Capabilities
- `scene-notification-layering`: Keep IDE overlays visible above the scene canvas.

### Modified Capabilities
None.

## Impact

Plugin scene canvas hosting and regression tests. No document changes or library changes. Live rendering pauses during overlap.
