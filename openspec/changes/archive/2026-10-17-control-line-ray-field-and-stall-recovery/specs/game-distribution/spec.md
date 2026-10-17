# Spec Delta

## ADDED Requirements

### Requirement: Development-only ray view stays out of distributions

The opt-in ray-traced field view and the ray-tracing modules it loads SHALL remain a development-only path: the
shipped game's source set and runtime classpath SHALL NOT reference them, running the ray view in a checkout SHALL
NOT change what the packaging or export tasks produce, and the shipped-classpath verification SHALL keep covering
the shipped classpath and SHALL pass with the ray source set present.

#### Scenario: Shipped classpath check with ray code present

- **WHEN** the shipped-classpath check runs in a checkout that contains the ray field source set
- **THEN** it passes, because the shipped classpath includes neither the ray source set nor the ray-tracing modules

#### Scenario: Distribution excludes ray code

- **WHEN** Control Line's distribution is built with the ray field source set present
- **THEN** it contains no ray-tracing module and no ray source-set class, and the packaged game starts and plays as
  before

#### Scenario: Play export unchanged by the ray view

- **WHEN** the Play launch file is exported after the ray view has been available in development
- **THEN** its classpath holds the same modules as before, with no ray-tracing module and no ray source-set output
