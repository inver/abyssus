# Spec Delta

## ADDED Requirements

### Requirement: Settings screen

The main menu SHALL offer Settings, between Scores and Quit, which opens the settings screen. The settings screen SHALL have a Display section with the Fullscreen checkbox
(`control-line-display`), and a Controller section: the connected devices listed by name (or a note that no controller was found), a way to choose one, and for the chosen
device its handle axis with a live bar of the tilt the device sets, a Detect button for the handle axis, an Invert checkbox, a dead zone slider (0 to 50 %), a curve slider
(linear to cubic), its pause button with a Detect button, and Reset, which restores the device's default profile. Changes SHALL apply at once and be saved. Back or Escape SHALL
return to the main menu. The screen SHALL be operable with the keyboard, the mouse and a controller, and SHALL work with no device connected.

#### Scenario: Open settings

- **WHEN** the player chooses Settings on the main menu
- **THEN** the settings screen opens, with the Fullscreen checkbox and the connected devices listed

#### Scenario: No controller

- **WHEN** no device is connected
- **THEN** the settings screen says no controller was found, still offers Fullscreen, and Back returns to the main menu

#### Scenario: Live preview

- **WHEN** the player pushes the handle axis of the chosen device
- **THEN** the live bar follows the tilt the device would set, with its dead zone, invert and curve applied

#### Scenario: Change and leave

- **WHEN** the player turns Invert on and presses Back
- **THEN** the setting is saved and a flight with that device uses it

#### Scenario: Reset

- **WHEN** the player presses Reset for a gamepad
- **THEN** its profile is the gamepad default

#### Scenario: Only a controller

- **WHEN** a player uses only a gamepad
- **THEN** the pad's D-pad and confirm button move through the settings screen, change the sliders with Left and Right, and toggle Invert

## MODIFIED Requirements

### Requirement: Flying
The flight screen SHALL show the scene from the pilot's eyes, turning to follow the plane, and a HUD with line tension, laps, score, combo multiplier and fuel left in Free flight. F2B flights SHALL show line tension, fuel and time against 7:00 plus the selected assist display. In human flights, W or Up SHALL tilt the handle up and S or Down down, reaching full tilt in 0.15 s and returning to neutral when released; moving the mouse vertically SHALL set the tilt by its distance from the window's centre; the handle axis of a connected controller SHALL set the tilt as `control-line-controller-input` describes; and whichever of these was used last SHALL control the handle. In AI demonstrations only the bot SHALL control the handle. Escape, or the pause button of the controller in use, SHALL pause, offering Resume and Main menu.

#### Scenario: Pause
- **WHEN** the player presses Escape while flying
- **THEN** the flight stops advancing and the pause menu offers Resume and Main menu

#### Scenario: Pause with a controller
- **WHEN** the player presses the pause button of the controller in use while flying
- **THEN** the flight stops advancing and the pause menu offers Resume and Main menu

#### Scenario: A Pro HUD
- **WHEN** a human F2B flight at Pro has flown 1:12
- **THEN** the HUD shows tension, fuel left and `1:12 / 7:00`, and no manoeuvre names, laps or marks

#### Scenario: A stick flies the plane
- **WHEN** the player pushes a connected stick's handle axis up during a human flight
- **THEN** the handle tilts up by the amount the device sets and the plane's elevator follows it
