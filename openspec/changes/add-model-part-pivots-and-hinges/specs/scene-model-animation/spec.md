# Spec Delta

## ADDED Requirements

### Requirement: A hinge preview wins over the animation

While a hinge preview of a part is away from 0, the Scene view SHALL draw that part at its previewed deflection about
its rest pose, even when the model's playing animation moves that node; the rest of the model SHALL keep animating.
When the preview returns to 0 or ends, the animation SHALL drive the node again.

#### Scenario: Animated model with a previewed part

- **WHEN** a model whose animation turns its propeller is previewed with a hinge on a flap at 15 degrees
- **THEN** the propeller keeps turning and the flap stays at 15 degrees
