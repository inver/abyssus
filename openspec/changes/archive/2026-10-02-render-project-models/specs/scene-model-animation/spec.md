# Spec Delta

## Purpose

Lets users see animated models move in the read-only scene view, so animated content looks alive
without any user action.

## ADDED Requirements

### Requirement: Animations play automatically

A displayed model that has animations SHALL play its first animation, looping, as soon as the model
appears, driven by real elapsed time so that speed does not depend on the frame rate. Each entity
animates independently, even when entities share a model asset.

#### Scenario: Animated model

- **WHEN** a scene places a model whose glTF file contains an animation
- **THEN** the model is shown moving, repeating when the animation ends

#### Scenario: Static model

- **WHEN** a model has no animations
- **THEN** it is shown in its rest pose, with no cost beyond drawing it

#### Scenario: Two entities with the same animated model

- **WHEN** two entities use the same animated model asset
- **THEN** both animate, each with its own playback time

### Requirement: Animation lifecycle

Animation SHALL stop and release its resources when the model is removed from the scene or the view
is closed, and SHALL NOT make an unreadable or malformed animation fail the rest of the scene.

#### Scenario: Entity removed

- **WHEN** an animated entity is removed from the scene file
- **THEN** it disappears and no animation continues for it
