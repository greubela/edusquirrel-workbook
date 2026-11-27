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
slide order, downscaled to 1920×1080); the tower slides likewise use the screenshots
`theater2/monks-tower-01.jpg` to `-04.jpg`, downscaled to 1920×1080. Each slide shows its image with the dialogue lines of that part of the story
below it (`computarius-<slide>-<line>` / `counting-<slide>-<line>`); stage directions are italic or in
parentheses. Unused beat images remain available in the folder.

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

`CreateMonksWorkbook` builds six sections with stable element IDs (`monks-a2`, `monks-e5`, ...). Each
container groups the questions of one exercise; its title key (`cont-1-2`, `sec3Ex1Title`, ...) names it.

| Section | Content |
| --- | --- |
| `welcomeTitle` | Overview |
| `monks-story` (1) | Theater slideshow; story and recursion scheme (A1, A3, A4); course of the recursion with the Java method and the call stack figure (A2, AN, A8b, A8c); properties of the recursion (A7, A6) |
| `monks-tower` (2) | Counting slideshow; story and theory (C1, C2, C2b, `towerCode`); properties of the counting (`strichliste`) |
| `monks-editor` (3) | Getting to know the editor (E1, E3); recursive drawing with bricks (E4, E5, E6b) |
| `monks-koch` (4) | Koch curve explained per depth with pictures, K1 analysis table; K4 snowflake |
| `finishTitle` | Closing text, survey (opt-in consent checkbox that starts unchecked, `LikertScaleInteraction` with eight statements on a five point scale, free comments) and submission |

Task types map to these interactions: `WorkbookContentChoiceInteraction` (choice and multiple select),
`MatchingInteraction` (A4 and C1: statements on the left are connected with categories on the right by
dragging or by clicking both), `AnswerTableInteraction` (graded inputs; accepted spellings separated by `|`)
and `ReorderMapIdInteraction`. Language-map keys derive from the task ID (`<id>-question`, `<id>-opt<i>`,
`<id>-row<i>`). Code snippets are language-dependent (German or English identifiers) and live in the
per-language maps; `map-universal.json` holds image paths. Every map lists exactly the keys the workbook
uses, in order of first use (`MonksWorkbookSpec`).

The programming tasks are `TurtleRecreateShapeInteraction`s with Java starting code
(`CreateMonksWorkbook.EditorCode`; from E4 on the brick methods `stein`, `hoch`, `runter` and `springe` are
prefilled, K4 starts with an empty `koch` method). The lead text before each editor is also the instruction in
the editor's side panel. Each target drawing is
recorded from the sample solution (`CreateMonksWorkbook.editorTarget`) with the same helper
methods, so the strokes match what the checked Java runner emits. The runner supports
`Turtle.forward`, `Turtle.turnRight`, `Turtle.turnLeft`, `Turtle.penUp` and `Turtle.penDown`.
Starter programs are `ProgrammingStateJavaStructured`. Exercise interfaces accept `ProgrammingState`.

The pictures of the Koch curve (`koch/koch-0.svg` to `koch/koch-2.svg`) are drawn by hand from computed
coordinates (width 270). They only contain numbers, so both languages share them.

The call stack figure in section 1 (`callstack/callstack-de.svg`, `callstack/callstack-en.svg`) shows
`sortieren("7294")`: the growing stack on the way there and the shrinking stack with return values on the way
back. Every frame is one monk and keeps its local variable `groesste`. The figures are generated by
`tools/dev/monks-callstack-figure.py`; the path per language is `filecallstack` in the language maps.
A8b transfers it from four to five cards.
`RecreateTurtleDidacticConfig` restricts the editor to Snap!, Python and Java; no separate
language configuration is added to the interaction. An early Java `return;` maps
to Snap's native `stop this block`, returning from the current call only.
Grading uses the default stroke policy: pen-up moves are ignored, duplicated strokes count as
unexpected, so returning along a drawn line must happen with the pen lifted (`springe`).

## Validation

`MonksWorkbookSpec` checks the language maps against the keys the workbook uses, the editor leads,
the typed starter states, conversion of every Java starter to Snap and Python, and the solved pyramid,
stairs and snowflake before and after conversion. `WorkbookDefinitionsSpec` covers the
workbook's definition and history serializers, including custom table headers.
`tools/dev/monks-workbook.browser.test.mjs` exercises both stories, table headings,
inline code choices and Java/Snap editing through the current renderer;
`tools/dev/monks-workbook-solution.browser.test.mjs` solves every graded interaction.

## Open items

- Unit-test tasks, recursion-versus-loop checks and further story images are not part of this edition.
- The Danish texts were machine-translated and still need proofreading.

The homepage card uses the separate square promotional tablet mock-up at
`resources/img/art/mockup/monks-workbook.png`; it is not part of the slideshow.
