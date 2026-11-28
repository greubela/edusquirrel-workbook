# 2026GdP1

Entry page: [homepage/2026GdP1/index.html](../homepage/2026GdP1/index.html).
The workbook catalogue links this page. Its workbook ID is `2026GdP1`;
interaction IDs are stable (`2026GdP1-task-1` through `-7`, `-reflection`, and
`2026GdP1-java-swap`, `-java-bubble`, `-java-sudoku`, `2026GdP1-sort`).

`CreateGdp1Workbook` defines four sections: overview, introduction, parameters and Java algorithms.
The introduction progresses from explicit square commands to loops, nested loops
and a `quadrat` function, followed by a reflection asking for two distinct benefits
of structured code. The parameter section uses increasing and alternating sizes;
the turtle starts facing right (heading 0, as in Snap and Python).

Programming tasks start in an empty Snap project and also offer Python and the structured
function-based Java editor. The mixed palettes expose only the selected
blocks, add custom-block creation from task 4, and variable controls from task 5.
German and English lesson text and internal LLM instructions live in
`resources/languageMaps/eva/2026gdp1/`. A Danish map satisfies the repository-wide
translation coverage contract; the workbook offers German and English.

Each `TurtleRecreateShapeInteraction` combines a stroke comparison with independent
`ProgramStructureGrader` results. Typed `ProgramStructureRequirement` values inspect
the parsed program for loops, helper definitions, required names/parameters and
allowed commands. Comments do not count as code. Function and parameter names
`quadrat` and `distanz` remain the same in both lesson languages. Structural checks
establish presence/absence, not whether a construct is the most effective solution.
The ordinary drawing test still verifies the complete output.

The reflection uses `TextInputWithScaffoldingAndGrading`: its hint asks guiding
questions and its grader accepts two or more distinct relevant advantages, without
requiring specific wording. AI feedback uses the existing configured backend;
programming tests run independently of AI access.

`Gdp1WorkbookSpec` checks every reference solution in Python, Snap and structured
Java, incorrect structures despite correct graphics, palettes, catalogue
registration and history serialization. `ProgramStructureGraderSpec` covers parsed
structure, comments, malformed code and command restrictions. The catalogue-wide
workbook tests cover JSON and factory serialization.

## Java algorithms section

Three `ProgrammingExerciseFullJava` tasks work on data instead of a turtle. The data is
provided implicitly through the `NumberList` and `Sudoku` libraries; each starting program
wraps them in static methods (`getWert`, `setWert`, `laenge`, later `tausche`;
`getFeld`, `setzeFeld`, `entferneFeld`), which are the students' interface:

1. `tausche(i, j)` with get/set only (no `NumberList.swap`).
2. `bubbleSort()` with the provided `tausche`, which records swaps.
3. `passt(zeile, spalte, zahl)` and a recursive backtracking `loese()` (boolean).

Each test is a `ProgramDataTestGrader`: it calls one method on a prepared list or grid
in the shared interpreter and checks the data, the return value or the recorded
`ListCommand`s (e.g. only adjacent swaps, swap count equal to the inversions). Failures
explain the difference in German or English. The editor shows the data after `main`
(`ExerciseDataView`) and the task beside it. Java `int` division is not preserved in the
shared model, so the sudoku task computes block starts as `zeile - zeile % 3`.
`Gdp1WorkbookSpec` checks reference solutions, starting programs and typical mistakes
(ready-made swap, selection sort, missing block check, missing undo).

Task 11 is a `SortListExercise` (Snap, Python or Java, `SortingMethod.SwapsOnly`; Snap offers the number-list blocks): any algorithm
that sorts `NumberList` by swapping. Its generated tests sort the default test lists
(including empty, single and sorted lists). The recreate-shape editors show the turtle
drawing step by step beside the comparison panel.
