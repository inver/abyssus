# Spec Delta

## MODIFIED Requirements

### Requirement: Projects and scenes load without the IDE

Given a Mundus project folder, a program that runs no IDE SHALL read the project's name from its `.abss` file, list the
`.scene` files of its `scenes` folder by name, and load any of them, top-level fields and `ecs` block, into entities
carrying the same components and values the plugin shows for that scene.

#### Scenario: Untitled project outside the IDE

- **WHEN** a test with no IDE running reads the `Untitled` project folder
- **THEN** the project's name is `Untitled` and its only scene is `Main Scene`

#### Scenario: Main Scene's entities outside the IDE

- **WHEN** a test with no IDE running loads `Main Scene` of the `Untitled` project
- **THEN** the scene's name is `Ololo`, its sky is `skybox_physical`, it holds the entities with ids 0 to 10, entity
  `0` is `Model 0` of type `OBJECT` rendering `model_29e9be61-6594-4f82-a6cf-44ccf09f71fb`, and entity `8` is
  `Spot Light 8` of type `LIGHT_SPOT`

#### Scenario: Same result inside the IDE

- **WHEN** the Abyssus view, the properties panel and the scene view show `Main Scene` in the IDE
- **THEN** they show the same entities, components and values as before this change
