# Mons Komputarius scene artwork

The workbook is built by `CreateMonksWorkbook`, launched at
`homepage/monksWorkbook/index.html`, and localized through
`resources/languageMaps/eva/monksworkbook/`.

Source: Andre Greubel, **Rekursion mit den Mönchen von Mons Komputarius**,
version dated 29.04.2025, supplied as “2025 04 29 Rekursion Mönche angefangen.pdf”.
The theater script is on pages 5–6; the counting continuation is on page 3.

The original Image01–Image18 artwork remains available. The `beats/` JPEGs were
created with imagegen from those sepia references. Related frames were generated
in four-frame sheets, mechanically separated and exported as individual images.
The separate artwork under `resources/img/art/marie/monkworkbook/` uses a
different style and is not mixed into the slideshow.

The theater now has 69 frames and the tower continuation has 17. Each frame has
one dialogue pane: one speaking turn with that speaker's mouth open, or `...`
with everyone silent. Speech and actions are separate frames. Shared artwork
is used only with the same caption (for example, the master's “In der Tat!”).
There is no stage-direction pane.

Each card selection shows the tray before lifting, the lift, and the retained
card. Each return shows the retained card at the right, moving left, and resting
at the left. Shifting the smaller cards right is a separate action. The result
is **9, 7, 4, 2** from left to right, matching the script's insertion of each
retained maximum at the left. These illustrative values are not prescribed by
the source PDF.

The counting story uses three bricks: red above yellow above blue. Removing a
brick, placing it aside, and drawing its tally each have a separate image. The
last frame shows the empty tower position and three tally marks.

## Structure

`CreateMonksWorkbook` builds six sections with stable element IDs (`monks-a2`, `monks-e5`, ...):

| Section | Content |
| --- | --- |
| `monks-editor` (0) | Editor introduction and tasks E1–E3 |
| `monks-story` (A) | Theater slideshow, recursion scheme, tasks A1–A8 |
| `monks-phases` (B) | Outward/return path, tasks B1–B3 |
| `monks-tower` (C) | Counting slideshow (existing frames only, text conclusion), tasks C1–C6 and E4–E6b |
| `monks-bridge` (D) | Contract, multiple calls, tasks D1–D2, E7, E8 |
| `monks-koch` (3) | Koch curve practice |

Task types map to existing interactions: `ChoiceInteraction` (choice, multiple select and one
row per assignment), `AnswerTableInteraction` (graded inputs; accepted spellings separated by `|`)
and `ReorderMapIdInteraction`. Language-map keys derive from the task ID (`<id>-question`,
`<id>-opt<i>`, `<id>-row<i>`); code blocks and category names that need no translation are in
`map-universal.json`.

## Open items

- E1–E8 are placeholders showing task text and Java starting code. The editors need
  `Turtle.turnLeft`, `Turtle.penUp` and `Turtle.penDown` in the checked Java runner (currently only
  `forward` and `turnRight`), then targets built from the sample solutions.
- E6a relies on the Java runner's call-depth limit reporting an execution limit.
- Unit-test tasks, recursion-versus-loop checks and further story images are not part of this edition.

The homepage card uses the separate square promotional tablet mock-up at
`resources/img/art/mockup/monks-workbook.png`; it is not part of the slideshow.
