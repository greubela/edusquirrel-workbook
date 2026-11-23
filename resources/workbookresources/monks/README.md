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

The theater is aggregated into 13 slides and the tower continuation into 4
(`CreateMonksWorkbook.theaterScenes` / `countingScenes`). The theater slides use the
in-game screenshots `theater/monks-theater-01.jpg` to `-13.jpg` (one per slide, in
slide order, downscaled to 1920×1080); the tower slides use key images from the
`beats/` set. Each slide shows its image with the dialogue lines of that part of the story
below it, plus short italic narration lines (`narr-*` keys) that replace the
action-only frames. Unused beat images remain available in the folder.

The full beat-image asset set contains the tray before lifting, the lift, and the retained
card for each selection. Each return shows the retained card at the right, moving left, and resting
at the left. Shifting the smaller cards right is a separate action. The result
is **9, 7, 4, 2** from left to right, matching the script's insertion of each
retained maximum at the left. These illustrative values are not prescribed by
the source PDF.

The counting artwork uses three bricks: red above yellow above blue. Removing a
brick, placing it aside, and drawing its tally each have a separate available image. The
last frame shows the empty tower position and three tally marks.

## Structure

`CreateMonksWorkbook` builds seven sections with stable element IDs (`monks-a2`, `monks-e5`, ...).
Displayed task titles are numbered per section ("Aufgabe 3.3" / "Task 3.3") in the language maps and
are independent of the element IDs. The numbers match the section numbers the navigation shows.

| Section | Content |
| --- | --- |
| `welcomeTitle` (0) | Overview |
| `monks-story` (1) | Theater slideshow, recursion scheme, tasks A1–A4, the story as code (`ac`: one method with four gaps), A5–A8 (tasks 1.1–1.9) |
| `monks-tower` (2) | Counting slideshow (story ending in the last slide), tasks C1–C5 (2.1–2.5) |
| `monks-editor` (3) | Editor introduction; E1–E3 (tasks 3.1–3.3) introduce the brick methods `stein`, `hoch`, `runter`, `springe` |
| `monks-bridge` (4) | Drawing recursively with bricks: E4, E5, E6b, E6a, D1 (4.1–4.5) |
| `monks-koch` (5) | Koch curve practice (5.1–5.3) |
| `finishTitle` (6) | Survey and submission |

Task types map to existing interactions: `ChoiceInteraction` (choice, multiple select and one
row per assignment), `AnswerTableInteraction` (graded inputs; accepted spellings separated by `|`)
and `ReorderMapIdInteraction`. Language-map keys derive from the task ID (`<id>-question`,
`<id>-opt<i>`, `<id>-row<i>`). Code snippets are language-dependent (German or English identifiers
and comments) and live in `map-de.json` / `map-en.json`; `map-universal.json` holds image paths and
names that need no translation.

The programming tasks E1–E8 are `TurtleRecreateShapeInteraction`s with Java starting code
(`CreateMonksWorkbook.EditorCode`; from E4 on the brick methods from section 3 are prefilled). Each target drawing is
recorded from the sample solution (`CreateMonksWorkbook.editorTarget`) with the same helper
methods, so the strokes match what the checked Java runner emits. The runner supports
`Turtle.forward`, `Turtle.turnRight`, `Turtle.turnLeft`, `Turtle.penUp` and `Turtle.penDown`.
Starter programs are `ProgrammingStateJavaStructured`; the Koch tasks start with
`ProgrammingStateSnapScalaModel.empty`. Exercise interfaces accept `ProgrammingState`.
`RecreateTurtleDidacticConfig` restricts the editor to Snap! and Java; no separate
language configuration is added to the interaction. An early Java `return;` maps
to Snap's native `stop this block`, returning from the current call only.
Grading uses the default stroke policy: pen-up moves are ignored, duplicated strokes count as
unexpected, so returning along a drawn line must happen with the pen lifted (`springe`).

## Validation

`MonksWorkbookSpec` checks the typed starter states, conversion of every Java starter
to Snap, bounded execution of the missing-base-case task, and the solved pyramid's
commands before and after Snap conversion. `WorkbookDefinitionsSpec` covers the
workbook's definition and history serializers, including custom table headers.
`tools/dev/monks-workbook.browser.test.mjs` exercises both stories, table headings,
inline code choices and Java/Snap editing through the current renderer.

## Open items

- E6a relies on the Java runner's call-depth limit reporting an execution limit.
- Unit-test tasks, recursion-versus-loop checks and further story images are not part of this edition.
- The Danish texts were machine-translated and still need proofreading.

The homepage card uses the separate square promotional tablet mock-up at
`resources/img/art/mockup/monks-workbook.png`; it is not part of the slideshow.
