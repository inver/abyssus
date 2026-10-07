# Spec Delta

## Purpose

Defines how the Control Line game judges one F2B manoeuvre from the plane's flown path, as an F2B judge would. It
covers recognising what was flown, building the ideal track from the F2B description, and turning the differences into
a mark from 1 to 10.

## ADDED Requirements

### Requirement: The flight hemisphere

Manoeuvres SHALL be judged on a flight hemisphere centred above the pilot at 1.5 m, its base. Parallels are measured
upward from the base, so the 45° parallel is at 45° of line elevation above it. The ±30 cm tolerance of the F2B
descriptions SHALL be measured as the plane's height above the ground against 1.5 m.

#### Scenario: Level at the base

- **WHEN** `Stunter` flies level laps at 1.7 m above the ground
- **THEN** the judge takes it as level flight at the base, within the ±30 cm tolerance

#### Scenario: Above the tolerance

- **WHEN** `Stunter` flies the two inverted laps at 2.0 m above the ground
- **THEN** the judge takes them as 20 cm above the tolerance

### Requirement: Recognising the manoeuvre

The judge SHALL recognise each manoeuvre from the shape of the path on the hemisphere. It looks at inside and outside
turns, sharp corners and how many there are, passes over the pilot, and level or inverted laps at the base. It SHALL
count the repeat figures. Recognition SHALL work through the zenith and SHALL NOT depend on how well the figure was
flown.

#### Scenario: Squares are not loops

- **WHEN** the pilot flies two inside loops with four sharp corners each
- **THEN** they are recognised as Two inside square loops, not as inside loops

#### Scenario: Eights are not loops

- **WHEN** the pilot flies an inside loop followed at once by an outside loop through the same crossing point, twice
- **THEN** they are recognised as Two horizontal eights

#### Scenario: Overhead eights

- **WHEN** the pilot flies loops that cross directly above the pilot
- **THEN** they are recognised as Two overhead eights, and the loops are counted although they pass the zenith

### Requirement: The judged segment

Each manoeuvre SHALL be judged only from its "Start of manoeuvre" to its "End of manoeuvre" as its F2B description
defines them. The recommended entry and exit procedures and the pauses between manoeuvres SHALL NOT change a mark.

#### Scenario: A sloppy exit

- **WHEN** the pilot ends the three inside loops cleanly and then wobbles while descending to inverted flight
- **THEN** the mark of Three inside loops is the same as with a clean exit

### Requirement: The ideal track

For every manoeuvre the judge SHALL build the ideal track that its F2B description defines, with its sizes, heights,
angles and segment lengths. It SHALL be anchored where the pilot flew the manoeuvre, at the reference the description
names, for example the lateral reference of loops, the intersection point of eights, or the take-off point.

#### Scenario: Loops where they were flown

- **WHEN** the pilot flies the three inside loops with their first vertical at a quarter lap past the take-off point
- **THEN** the ideal loops are three superimposed circles there, bottoms at the base and tops tangent to the 45°
  parallel

#### Scenario: Square loop size

- **WHEN** the judge builds Two inside square loops
- **THEN** the ideal square has vertical sides, its top on the 45° parallel, its bottom at the base and a width of ⅛ lap

### Requirement: A perfect manoeuvre marks 10

A manoeuvre whose judged segment follows its ideal track, within the tolerances of its F2B description, SHALL be marked
10.

#### Scenario: Ideal loops

- **WHEN** the flown path of Three inside loops is the ideal track, with bottoms within ±30 cm of the base
- **THEN** it is marked 10.0

### Requirement: Deviations lower the mark

A completed manoeuvre's mark SHALL fall from 10 as its flown path strays from the ideal track, and SHALL never be below
1. A larger error of the same kind SHALL never give a higher mark. Marks SHALL be given in steps of 0.1.

#### Scenario: Low bottoms

- **WHEN** Three inside loops is flown with bottoms 0.5 m, then 1.0 m, then 2.0 m above the tolerance
- **THEN** the three marks are each lower than the one before or equal to it, and all are at least 1.0

#### Scenario: A badly flown manoeuvre still counts

- **WHEN** Two inside triangular loops is flown complete but with rounded corners and the apex at 60°
- **THEN** it is marked between 1.0 and 10.0, not 0

### Requirement: What a mark judges

A mark SHALL account for every aspect the manoeuvre's F2B description sets out. These are its height at the base and
on the 45° parallel, vertical and angled lines, the tightness of corners, segment lengths in laps, repeat figures on
top of each other, a crossing point kept in one place, and symmetry. For Take-off and Landing they include the ground
roll and the glide.

#### Scenario: Repeats not on top of each other

- **WHEN** the second inside square loop is flown ¼ lap further round than the first
- **THEN** it is marked lower than the same squares flown on top of each other

#### Scenario: Round corners

- **WHEN** Two inside square loops is flown with corners of 4 m radius
- **THEN** it is marked lower than the same squares with corners of 1 m radius

#### Scenario: A short glide

- **WHEN** the plane touches down half a lap after leaving the base with the engine stopped
- **THEN** Landing is marked lower than with a glide of 1 lap
