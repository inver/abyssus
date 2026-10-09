# Spec Delta

## MODIFIED Requirements

### Requirement: Main menu

The game SHALL open on a main menu offering Start, Scores, Settings and Quit, usable with the arrow keys and Enter, with a
connected flight stick's hat and trigger, or with the mouse. Start SHALL be disabled, with a message, when the bundled field scene has no plane.

#### Scenario: Start

- **WHEN** the player chooses Start
- **THEN** the plane select screen opens

#### Scenario: No planes

- **WHEN** the field scene holds no entity with a `PlaneComponent`
- **THEN** Start is disabled and the menu says no plane was found in the field scene

#### Scenario: Menu with a flight stick

- **WHEN** a flight stick is connected, the main menu is shown and the player pushes the hat down and presses the trigger
- **THEN** the second item, Scores, is chosen and the score table opens

#### Scenario: Settings

- **WHEN** the player chooses Settings on the main menu
- **THEN** the joystick settings screen opens, and Escape returns to the main menu

### Requirement: Plane select from the field scene

The plane select screen SHALL offer every entity of the field scene that has a `PlaneComponent`, in the order of their
names, showing each plane's name, class, line length, mass and fuel time, and moving the camera to the selected parked
plane. Left and right (or the stick's hat left and right) SHALL change the plane, Enter (or the stick's trigger) SHALL start the flight with it, and Escape (or the stick's back button) SHALL return to the main
menu.

#### Scenario: Bundled planes

- **WHEN** the plane select screen opens on the bundled `ControlLine` field
- **THEN** it offers `Racer`, `Stunter` and `Trainer` in that order, starting with `Racer`

#### Scenario: A plane added in Abyssus

- **WHEN** a fourth plane entity with a `PlaneComponent` is added to the field scene in Abyssus and the game is started
  again
- **THEN** the plane select screen offers it too, with no code change

#### Scenario: Choosing a plane with a flight stick

- **WHEN** the plane select screen shows `Racer`, and the player pushes the stick's hat right and presses the trigger
- **THEN** the screen moves to `Stunter`, and the flight starts with `Stunter`

### Requirement: Flying

The flight screen SHALL show the scene from the pilot's eyes, turning to follow the plane, and a HUD with line tension,
laps, score, combo multiplier and fuel left. W or Up SHALL tilt the handle up and S or Down down, reaching full tilt in
0.15 s and returning to neutral when released; moving the mouse vertically SHALL set the tilt by its distance from the
window's centre; a connected flight stick's pitch axis SHALL set the tilt by its deflection, pulling back tilting up, with a dead zone around
centre, using the axis, direction and dead zone saved in the joystick settings. Whichever of keys, mouse and stick was used last SHALL control the handle. Escape or the stick's back button SHALL pause, offering Resume and Main
menu.

#### Scenario: Pause

- **WHEN** the player presses Escape while flying
- **THEN** the flight stops advancing and the pause menu offers Resume and Main menu

#### Scenario: Stick pitch sets the tilt

- **WHEN** a flight stick is connected, a flight is under way and the player pulls the stick half way back
- **THEN** the handle tilts half way up, and returns to neutral when the stick is released to its centre

#### Scenario: Saved settings apply

- **WHEN** the joystick settings name another axis as the pitch axis and invert it, and the player starts a flight and deflects that axis forward
- **THEN** the handle tilts up, and the stick's former pitch axis no longer tilts it

#### Scenario: Stick at rest does not take control

- **WHEN** the player holds W, the handle is full up, and a connected stick's axis only jitters inside its dead zone
- **THEN** the keys keep controlling the handle

#### Scenario: Last used device wins

- **WHEN** the player moves the stick, then presses S
- **THEN** the handle follows the S key until the stick is moved again

#### Scenario: Stick unplugged

- **WHEN** the flight stick is disconnected during a flight
- **THEN** the handle goes to neutral if the stick was in control, the flight carries on, and the keys and the mouse still work

#### Scenario: No stick

- **WHEN** no flight stick is connected
- **THEN** the game behaves exactly as it does with keys and mouse only, with no error
