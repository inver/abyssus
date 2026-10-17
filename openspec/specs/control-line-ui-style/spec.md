# control-line-ui-style Specification

## Purpose

Defines how Control Line's screens, HUD and menus look, so a player sees one consistent, legible visual style on any window size and display scale.

## Requirements

### Requirement: Screens render at any window scale
Every screen and the HUD SHALL be visible and laid out as authored in a window of any size and on displays with a scale factor above 1, and pointer clicks SHALL hit the controls they are drawn over.

#### Scenario: HiDPI window
- **WHEN** the game runs in a window whose back buffer is twice its logical size
- **THEN** the main menu is drawn over the whole window and clicking a button presses that button

#### Scenario: Centred content
- **WHEN** a screen's content is authored to be centred
- **THEN** it is centred in the window, not placed against the top edge

### Requirement: One visual language
All ten screens SHALL use the same palette, type and component styles: a dark navy ground, an orange accent for the primary action and key values, muted blue-grey for secondary text and borders, condensed display type for titles, buttons and large numbers, and a monospaced face for body text and data. Primary buttons SHALL be solid accent, other buttons outlined, and the focused button SHALL be clearly marked in addition to colour. Screens the design does not draw SHALL follow the same rules.

#### Scenario: Menu look
- **WHEN** the main menu opens
- **THEN** it shows the large two-line title, the track illustration, a solid accent first button, outlined other buttons and the key hint line

#### Scenario: Unspecified screens match
- **WHEN** assist select, pause, score tabs and the debrief are opened
- **THEN** their buttons, panels and text use the same styles as the designed screens

### Requirement: Legible HUD over the field
The flying HUD SHALL sit on translucent dark panels so its text stays readable over sky and ground, and SHALL show tension as a bar, with fuel, laps, score and combo (or the F2B values) as before.

#### Scenario: Readable over sky
- **WHEN** the plane flies in front of a bright sky
- **THEN** every HUD value remains readable on its panel

### Requirement: Style assets are bundled
The fonts and illustrations used by the screens SHALL be resources of the UI asset, loadable without network access, produced by a repeatable tool, and shipped with their licences. The renderer SHALL support showing a label in upper case without changing its text.

#### Scenario: Offline start
- **WHEN** the game starts with no network
- **THEN** every screen shows its fonts and images

#### Scenario: Upper-case label
- **WHEN** a label has the upper-case style and its text is "Free flight"
- **THEN** it is drawn as "FREE FLIGHT" while the bound or authored text and the editor's value stay "Free flight"