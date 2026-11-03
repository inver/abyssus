# control-line-controller-input Specification

## Purpose

Lets a player fly and navigate Control Line with a gamepad, flight stick or wheel: how a device is read, set up per device and kept, how its axis sets the handle's tilt, and how it moves through the menus.

## Requirements

### Requirement: Devices are read when present

The game SHALL read every connected joystick, gamepad or wheel each frame, naming each by its name and a stable id (the device's GUID). A device with a standard gamepad layout SHALL be offered as a gamepad; any other SHALL be offered as a joystick with its raw axes, buttons and hats. With no device connected the game SHALL behave as it does without this capability, and a device SHALL never be needed to play.

#### Scenario: A gamepad

- **WHEN** a Logitech F310 is connected
- **THEN** it is listed by name as a gamepad with a default profile

#### Scenario: A flight stick

- **WHEN** a joystick with no standard gamepad layout is connected
- **THEN** it is listed by name with its raw axes, and the handle axis is not set until the player detects one

#### Scenario: No device

- **WHEN** no device is connected
- **THEN** the keyboard and the mouse fly and navigate exactly as before

### Requirement: A profile per device

Each device SHALL have a profile kept under its GUID, holding the handle axis, that axis's rest value, invert, dead zone (default 10 %), curve (0 for linear to 1 for cubic, default 0) and the pause button. A gamepad's default profile SHALL use the left stick's vertical axis, pushed up for handle up, with rest 0, and the Start button for pause. A joystick's default profile SHALL have no handle axis and no pause button. A profile SHALL stay with its device when other devices are plugged in a different order.

#### Scenario: Two devices swap ports

- **WHEN** a stick set up on the first port is plugged into the second port and a pad into the first
- **THEN** the stick keeps its profile and the pad has its own

#### Scenario: A new gamepad

- **WHEN** a gamepad is seen for the first time
- **THEN** its profile is the gamepad default

### Requirement: The handle axis sets the tilt

While a device controls the handle, its handle axis SHALL set the tilt each step: the axis value is taken relative to the profile's rest value, scaled so that full travel either way is 1 (a rest value of 1 or -1 is a pedal or throttle at one end), shaped by the dead zone (nothing within it, then rising from 0 to 1 over the rest of the travel), then by the curve (`(1 - c) * x + c * x^3`), and inverted when the profile says so. The tilt SHALL then be used exactly as the keys' and the mouse's, between full down and full up.

#### Scenario: A stick pushed fully up

- **WHEN** the handle axis of a default gamepad profile reads fully up
- **THEN** the tilt is 1

#### Scenario: Inside the dead zone

- **WHEN** the axis reads 0.08 from its rest value with a dead zone of 10 %
- **THEN** the tilt is 0

#### Scenario: Past the dead zone

- **WHEN** the axis reads 0.55 from its rest value with a dead zone of 10 % and a linear curve
- **THEN** the tilt is 0.5

#### Scenario: A pedal that rests at one end

- **WHEN** a profile's rest value is -1, the dead zone is 0 and the axis reads 0
- **THEN** the tilt is 0.5, and it is 0 at rest and 1 at the other end of the travel

#### Scenario: Invert

- **WHEN** invert is on and the stick is pushed fully up
- **THEN** the tilt is -1

### Requirement: The last device used controls the handle

The handle SHALL be controlled by the device the player used last among the keys, the mouse and a controller. A controller SHALL take control when its tilt leaves the dead zone and SHALL NOT take it while at rest, so a drifting stick inside its dead zone never overrides the keyboard. In a Watch AI demonstration no device SHALL move the handle.

#### Scenario: Stick then key

- **WHEN** the stick is pushed up and then W is pressed
- **THEN** the keys control the handle until the stick moves out of its dead zone again

#### Scenario: A stick at rest

- **WHEN** the player holds W and a connected stick rests inside its dead zone
- **THEN** W controls the handle

#### Scenario: A demonstration

- **WHEN** the Watch AI flies and the stick is pushed fully up
- **THEN** the handle is not moved by the stick

### Requirement: A device can go away and come back

When the controlling device is unplugged during a flight, the flight SHALL go on, with the handle at neutral until another device controls it. A device plugged in later SHALL use its profile.

#### Scenario: Unplugged in flight

- **WHEN** the stick controlling the handle is unplugged mid-flight
- **THEN** the flight does not end, the handle goes to neutral and the keys and mouse still work

#### Scenario: Plugged in again

- **WHEN** the same stick is plugged in again
- **THEN** its saved profile is used

### Requirement: Detect picks the axis or button

On the settings screen, detecting the handle axis SHALL wait for the player to move an axis of the selected device by at least half its travel from where it was when detecting started, take that axis (the one that moved most if several did) and record its value at the start as the rest value. Detecting a button SHALL take the first button the player presses. Detecting SHALL stop without a change on Escape, on a click on Cancel or after 10 seconds, and, when detecting an axis, on the device's back button (when detecting a button every button is a candidate, so the back button is taken as the binding). While it waits the device SHALL NOT navigate the menus.

#### Scenario: Move an axis

- **WHEN** the player chooses Detect for the handle axis and pushes a stick's vertical axis fully up
- **THEN** that axis becomes the handle axis, with the value it had before the push as its rest value

#### Scenario: Press a button

- **WHEN** the player chooses Detect for the pause button and presses button 7
- **THEN** button 7 is the pause button

#### Scenario: Nothing happens

- **WHEN** the player chooses Detect and moves nothing for 10 seconds
- **THEN** the profile is unchanged

### Requirement: The device navigates the menus

On every menu screen (not while flying and not while detecting), a controller SHALL move focus and press controls the way the keys do: the D-pad or the left stick of a gamepad (pushed past half its travel), or the first hat of a joystick, SHALL act as Up, Down, Left and Right, repeating after 0.4 s and then every 0.12 s while held; the confirm button (A on a gamepad, button 0 on a joystick) SHALL act as Enter, or as Space on a focused checkbox; the back button (B on a gamepad, button 1 on a joystick) SHALL act as Escape. Name entry SHALL accept the offered name with confirm, with no on-screen keyboard.

#### Scenario: Main menu

- **WHEN** the D-pad is pressed down once on the main menu and confirm is pressed
- **THEN** the second entry is chosen

#### Scenario: Plane select and score tabs

- **WHEN** right is pressed on plane select, and later on the score table
- **THEN** the next plane is shown, and the next tab

#### Scenario: Held direction repeats

- **WHEN** the D-pad is held down on a list for one second
- **THEN** focus moves down once at once, then again after 0.4 s and then every 0.12 s

#### Scenario: A checkbox

- **WHEN** confirm is pressed on a focused checkbox
- **THEN** it toggles

#### Scenario: Name entry

- **WHEN** a flight enters the table and confirm is pressed on the name field
- **THEN** the offered name is saved

#### Scenario: Flying

- **WHEN** the stick is pushed or the D-pad is pressed during a human flight
- **THEN** the stick sets the handle and nothing navigates, and only the pause button acts

### Requirement: The pause button

During a human flight the profile's pause button SHALL pause the flight as Escape does.

#### Scenario: Start pauses

- **WHEN** Start is pressed on a gamepad with the default profile during a flight
- **THEN** the flight pauses and the pause menu is shown

### Requirement: Settings are kept in controls.json

The game SHALL keep every profile in `~/.abyssus-control-line/controls.json` as `{version: 1, devices: {<guid>: {...}}}`, written through a temporary file and an atomic move whenever a profile changes. A file that cannot be read SHALL be renamed to `controls.json.bad`, the defaults used, and the settings screen SHALL say so.

#### Scenario: Changes survive a restart

- **WHEN** a player sets invert on a stick and restarts the game
- **THEN** the stick's profile has invert on

#### Scenario: An unreadable file

- **WHEN** `controls.json` holds text that is not valid JSON at start
- **THEN** it is renamed to `controls.json.bad`, the defaults are used and the settings screen says the old settings could not be read
