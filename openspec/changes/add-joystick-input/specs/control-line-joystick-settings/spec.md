# Spec Delta

## Purpose

Lets the player set up a flight stick for the control-line game: see what the stick reports, choose which axis and
buttons the game uses, and keep those choices between runs.

## ADDED Requirements

### Requirement: Settings screen

The joystick settings screen SHALL be reachable from the main menu, SHALL be usable with the keyboard, the mouse
and a connected stick's hat, and SHALL always be left by Escape or the stick's back button, whatever has been assigned.

#### Scenario: Leaving with the keyboard

- **WHEN** the settings screen is shown and the player presses Escape
- **THEN** the main menu is shown again

#### Scenario: Leaving with a button assigned elsewhere

- **WHEN** the back button has been assigned to a button that the stick does not have, and the player presses Escape
- **THEN** the main menu is shown again

### Requirement: Live readout

The screen SHALL show the connected stick's name and the live value of each of its axes, buttons and hat. When no
stick is connected it SHALL say so, and show the readout once one is plugged in, without leaving the screen.

#### Scenario: Axis moves

- **WHEN** a stick is connected and the player pushes one axis half way
- **THEN** that axis's readout shows about half a deflection

#### Scenario: No stick

- **WHEN** no stick is connected
- **THEN** the screen says no joystick was found and every setting stays editable

### Requirement: Pitch axis, direction and dead zone

The player SHALL be able to choose the pitch axis by moving the stick while the screen waits for it, to invert it, and to set the dead zone. The
choices SHALL apply to the next flight and to the readout's pitch marker immediately.

#### Scenario: Choose the axis

- **WHEN** the player selects the pitch axis setting and pulls the stick's second axis back
- **THEN** that axis is shown as the pitch axis

#### Scenario: Cancel the choice

- **WHEN** the screen waits for a pitch axis and the player presses Escape
- **THEN** the pitch axis keeps its former value

### Requirement: Assigning buttons

The player SHALL be able to assign the confirm and back buttons by pressing the stick's button while the screen waits for it. The hat SHALL keep moving through menus whatever is assigned.

#### Scenario: Assign confirm

- **WHEN** the player selects the confirm setting and presses the stick's third button
- **THEN** the third button is shown as confirm, and in menus it confirms

### Requirement: Settings are kept

The settings SHALL be saved when the player leaves the screen and read at the next start. A file that cannot be read SHALL be renamed with a `.bad` suffix and the defaults used, with the screen saying so. A file written by an unknown version SHALL be treated the same way.

#### Scenario: Saved across runs

- **WHEN** the player inverts the pitch axis, leaves the screen, quits and starts the game again
- **THEN** the settings screen shows the pitch axis inverted

#### Scenario: Damaged file

- **WHEN** the saved file holds text that is not valid settings
- **THEN** the file is renamed with a `.bad` suffix, the defaults apply and the screen says the saved settings could not be read

### Requirement: Defaults and reset

Without a saved file the settings SHALL be the Thrustmaster T.16000M's layout: the Y axis pulled back tilts up, the trigger confirms and the second button goes back. A Reset to defaults action SHALL restore them.

#### Scenario: Reset

- **WHEN** the player has changed the pitch axis and chooses Reset to defaults
- **THEN** the settings show the T.16000M's layout again
