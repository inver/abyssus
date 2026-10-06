# Spec Delta

## ADDED Requirements

### Requirement: Crash severity

A flight that ends as a crash because the plane touched the ground SHALL record its impact speed: how fast the plane
came down onto the ground just before the touch. It SHALL record a severity from that speed: little below 6 m/s, medium
from 6 m/s and below 10 m/s, full from 10 m/s. A flight that lands, or ends because the lines went slack, SHALL have no
severity.

#### Scenario: A hard touch breaks the gear

- **WHEN** `Trainer` comes down onto the ground upright at 4.5 m/s
- **THEN** the flight ends as a crash with a little severity

#### Scenario: A harder touch breaks the wing and the tail

- **WHEN** `Trainer` comes down onto the ground upright at 8 m/s
- **THEN** the flight ends as a crash with a medium severity

#### Scenario: A dive wrecks the plane

- **WHEN** `Trainer` comes down onto the ground at 14 m/s
- **THEN** the flight ends as a crash with a full severity

#### Scenario: A landing has no severity

- **WHEN** the engine has stopped and `Trainer` glides onto the ground upright at 2 m/s
- **THEN** the flight ends as a landing with no severity
