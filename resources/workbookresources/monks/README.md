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
| `welcomeTitle` (0) | Overview: learning goals and a guide through the sections |
| `monks-story` (1) | Bridge from the lecture, theater slideshow, recursion scheme, tasks A1–A4, the story as code (`ac`: one method with four gaps), A5–A8 with the call stack figure in A8 (tasks 1.1–1.9), summary |
| `monks-tower` (2) | Bridge from section 1, counting slideshow (story ending in the last slide), tasks C1–C5 (2.1–2.5), summary |
| `monks-editor` (3) | Bridge, editor introduction; E1–E3 (tasks 3.1–3.3) introduce the brick methods `stein`, `hoch`, `runter`, `springe`, summary |
| `monks-bridge` (4) | Bridge, drawing recursively with bricks: E4, E5, E6b, E6a (with a shared lead-in on typical mistakes), D1 (4.1–4.5), summary leading over to section 5 |
| `monks-koch` (5) | Final task: explanation of the Koch curve with pictures of depth 1 and 2, K1 analysis, K2 `koch` method with collapsible hints, K3 contract of the method, K4 snowflake, K5 counting calls (5.1–5.5) |
| `finishTitle` (6) | Survey (`LikertScaleInteraction` with eight statements on a five point agreement scale, followed by an opt-in consent checkbox that starts unchecked) and submission |

Every section from 1 to 4 starts with a bridge text (`parta-bridge`, `c0prompt`, `zerobridge`, `partdbridge`) and
ends with a summary container (`summarytitle`, `<part>-summary`) that names the key points and leads over to
the next section. The thread through all of them is the scheme of four elements: base case, simplification,
recursive call and combination.

Task types map to these interactions: `ChoiceInteraction` (choice and multiple select),
`MatchingInteraction` (assignment tasks A4, A5, C1 and C3: statements on the left are connected with
categories on the right by dragging or by clicking both; graded on request with the number of correct
connections), `AnswerTableInteraction` (graded inputs; accepted spellings separated by `|`)
and `ReorderMapIdInteraction`. Language-map keys derive from the task ID (`<id>-question`,
`<id>-opt<i>`, `<id>-row<i>`). Code snippets are language-dependent (German or English identifiers
and comments) and live in `map-de.json` / `map-en.json`; `map-universal.json` holds image paths and
names that need no translation.

The programming tasks E1–E6, K2 and K4 are `TurtleRecreateShapeInteraction`s with Java starting code
(`CreateMonksWorkbook.EditorCode`; from E4 to E6 the brick methods from section 3 are prefilled, the Koch tasks
start with an empty `koch` method). Each target drawing is
recorded from the sample solution (`CreateMonksWorkbook.editorTarget`) with the same helper
methods, so the strokes match what the checked Java runner emits. The runner supports
`Turtle.forward`, `Turtle.turnRight`, `Turtle.turnLeft`, `Turtle.penUp` and `Turtle.penDown`.
Starter programs are `ProgrammingStateJavaStructured`. Exercise interfaces accept `ProgrammingState`.

The pictures of the Koch curve (`koch/koch-1.svg`, `koch/koch-2.svg`) are drawn by hand from computed
coordinates (width 270). They only contain numbers, so both languages share them.

The call stack figure in task 1.9 (`callstack/callstack-de.svg`, `callstack/callstack-en.svg`) shows
`sortieren("7294")`: the growing stack on the way there and the shrinking stack with return values on the way
back. Every frame is one monk and keeps its local variable `groesste`. The figures are generated by
`tools/dev/monks-callstack-figure.py`; the path per language is `filecallstack` in `map-de.json` / `map-en.json`.
It is placed after A8a, whose answer it would give away, so that A8b transfers it from four to five cards.
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
