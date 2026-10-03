# Mundus light fixture

`scenes/Mundus Lights.scene` is the unmodified output of the local Mundus editor's
`SceneStorage.saveScene`, renamed from `.json` to `.scene` for this repository.

Source checkout: `/Users/inv3r/Development/gamedev/Mundus`, commit
`128175e064a915e043f024a565f935d4c6883292`. Runtime: its existing
`projects/app-editor/build/libs/Scene Editor 3D-0.3.0.jar`; the jar may have been
built from a different revision. Generation used a temporary LWJGL application
and the editor's `MapperConfig`, `EcsConfigurator`, `SceneConverter`, and
`SceneStorage`, without changing the Mundus checkout.

The directional light was created with `LightService.createDirectionLight(-1)`.
This checkout has no spot-light creation action. The spot was created using
`EcsService.createEntityWithDirection` with Mundus's `SpotLight`, `LightComponent`,
and `TypeComponent.Type.LIGHT_SPOT`, sharing the directional service's icon
delegates. The ECS world was processed before saving. Rendering systems were
omitted from the temporary world's configuration; serializer registration was
unchanged.

Directional root id: `1`; spot root id: `4`. Each has a direction handle and
line entity. Both roots use archetype `12`: Parent, Name, Type, Position, Render,
Pickable, Light, Dependencies. The default root ParentComponent value is omitted
from the saved components, but ParentComponent remains in the archetype.
Handle archetype: `6`; line archetype: `14`.

The LightComponent identifier is
`com.mbrlabs.mundus.commons.core.ecs.component.LightComponent`.
Both saved LightComponents are empty objects: this version's `light` field is
transient. The fixture therefore does not establish persistence of color,
intensity, or range, or support for the proposed four-component entity without
editor handles. Those compatibility questions remain unresolved.

`scenes/Creation Baseline.scene` is the original committed Untitled scene, copied as a
stable baseline for creation tests while the original project is being edited in a live IDE.
