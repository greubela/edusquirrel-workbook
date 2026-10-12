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

The fifteen text responses keep their original task IDs (1b–1g and 2b–2j).
A fourth section adds three independent `TurtleRecreateShapeInteraction`
exercises, `monks-koch-0` through `monks-koch-2`: a straight line, one triangular
bump, and the next Koch iteration. Each starts unsolved and supports the Python
and block editors, including user-defined recursive functions. Targets contain
1, 4 and 16 connected segments, with total horizontal extent 270.

Model, recursion and serialization checks:

```
sbt 'client/testOnly *MonksWorkbookSpec' 'coreJVM/testOnly *BeExpressionToTurtleCommandsSpec'
node tools/dev/monks-workbook.test.mjs
```

After `buildClientDev`, the optional browser check navigates every story frame
and solves all three Koch targets, checking that code survives reopening:

```
MONKS_BROWSER_TEST=1 node tools/dev/monks-workbook.test.mjs
```

It requires Chromium (`CHROMIUM_PATH`, default `/usr/bin/chromium`). The local
routing fixture blocks external requests; `CODEMIRROR_TEST_BUNDLE` can point to
a bundle of `homepage/js/CodeMirrorLoader.js` using its pinned CDN packages for
an offline editor check.

The homepage card uses the separate square promotional tablet mock-up at
`resources/img/art/mockup/monks-workbook.png`; it is not part of the slideshow.
