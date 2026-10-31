# control-line-display Specification

## Purpose

Lets a player run Control Line fullscreen or in a window from the Settings screen, and keeps the choice for the next start.

## Requirements

### Requirement: Fullscreen setting

The Settings screen SHALL have a Fullscreen checkbox in a Display section. Turning it on SHALL switch the game window to fullscreen at the current display mode, and turning it off SHALL switch it back to a 1280 x 720 window, in both cases at once, with no restart, and with the game left on the Settings screen. The checkbox SHALL show the mode the window is in, SHALL work with the keyboard, the mouse and a controller, and SHALL not need a controller to be connected.

#### Scenario: Turn fullscreen on

- **WHEN** the player turns Fullscreen on in the Settings screen
- **THEN** the window becomes fullscreen at once and the Settings screen is still shown

#### Scenario: Turn it off again

- **WHEN** the player turns Fullscreen off
- **THEN** the window is a 1280 x 720 window again

#### Scenario: Only a gamepad

- **WHEN** a player with only a gamepad focuses Fullscreen on the Settings screen and presses confirm
- **THEN** the checkbox toggles and the window changes mode

#### Scenario: No controller

- **WHEN** no controller is connected
- **THEN** Fullscreen can still be changed

### Requirement: The mode is remembered

The game SHALL keep the setting in `~/.abyssus-control-line/display.json` as `{version: 1, fullscreen: <boolean>}`, written through a temporary file and an atomic move whenever it changes, and SHALL start in the mode the file holds, a window when there is no file. A file that cannot be read SHALL be renamed to `display.json.bad`, the game SHALL start in a window and the Settings screen SHALL say the old display settings could not be read.

#### Scenario: Restart in fullscreen

- **WHEN** the player turns Fullscreen on and starts the game again
- **THEN** the game opens fullscreen and the checkbox is on

#### Scenario: No file

- **WHEN** there is no `display.json`
- **THEN** the game opens in a 1280 x 720 window

#### Scenario: An unreadable file

- **WHEN** `display.json` holds text that is not valid JSON at start
- **THEN** it is renamed to `display.json.bad`, the game opens in a window and the Settings screen says the old display settings could not be read
