# Spec Delta

## MODIFIED Requirements

### Requirement: Screens are authored UI
Every screen and the flying HUD of the game SHALL be defined in a UI asset of the bundled `ControlLine` project and presented by the shared UI renderer, driven by the game's flow state. The game SHALL NOT keep a hand-written layout for any of them. Except for the added main-menu weather selector, the screens, their text, their order, their keyboard and mouse controls and their results (including the score tables, the F2B sheet, the debrief rows and the name entry limits) SHALL be those of the game before the change. Editing the asset in Abyssus SHALL change the game's screens without a code change. Game state that the screens display (laps, score, combo, fuel, time, tension, the next manoeuvre, warnings, score rows, sheet rows, debrief rows and deductions) SHALL reach the UI only as typed bindings published by the game.

#### Scenario: Same screens, same controls
- **WHEN** a player moves through main menu, assist select, plane select, flight, pause, game over, name entry, scores and a pattern debrief
- **THEN** each screen shows the same information as before and Up, Down, Left, Right, Enter, Escape, Space, R, V and 1 / 2 / 3 do what they did

#### Scenario: Edit a screen without code
- **WHEN** a designer changes the main menu's title text in the UI asset and starts the game
- **THEN** the new title is shown with no code change

#### Scenario: Free and pattern HUD
- **WHEN** a free flight and a pattern flight at each assist level are flown
- **THEN** the free HUD shows laps, score, combo and fuel, the pattern HUD shows fuel and time, and the Mid and Noob levels also show the next manoeuvre, the pause count and the height warning, as before

#### Scenario: Authored weather control
- **WHEN** the player changes weather in the main menu by keyboard or mouse
- **THEN** the authored UI displays Default or After the rain through typed game bindings and the field appearance follows it without a handwritten layout


## ADDED Requirements

### Requirement: Session weather selection

The authored main menu SHALL offer a keyboard- and mouse-accessible Weather choice of Default or After the rain. Initially it SHALL follow the scene. A player change SHALL override it for the current application session across menus, flights and retries, without writing project files or affecting game rules. Restarting SHALL follow the scene again.

#### Scenario: Follow saved weather
- **WHEN** the game starts with a scene saved as After the rain
- **THEN** the menu shows After the rain and the field displays wet ground and puddles

#### Scenario: Override and retry
- **WHEN** the player changes weather to After the rain, starts a flight and retries it
- **THEN** the chosen weather remains active through both flights and all project file bytes remain unchanged

#### Scenario: Default override on a wet scene
- **WHEN** the scene is saved as After the rain and the player chooses Default
- **THEN** the field displays its original dry appearance for the session and restarting the game restores the scene's After the rain choice

#### Scenario: Existing navigation
- **WHEN** the weather selector is added alongside the current flight, scores and quit menu entries
- **THEN** those entries retain their actions and availability and weather is reachable without consuming flight control keys during play

