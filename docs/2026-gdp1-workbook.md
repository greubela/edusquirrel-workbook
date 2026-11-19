# 2026GdP1

Entry page: [homepage/2026GdP1/index.html](../homepage/2026GdP1/index.html).
The workbook catalogue links this page. Its workbook ID is `2026GdP1`;
interaction IDs are stable (`2026GdP1-task-1` through `-7`, plus `-reflection`).

`CreateGdp1Workbook` defines three sections: overview, introduction and parameters.
The introduction progresses from explicit square commands to loops, nested loops
and a `quadrat` function, followed by a reflection asking for two distinct benefits
of structured code. The parameter section uses increasing and alternating sizes;
the final orientation sequence is down, down, up, down, down. The turtle starts facing
right (heading 0, as in Snap and Python), so right-turn squares hang below a shared line
and rows extend horizontally; the mirrored third square uses left turns (`right(-90)`).
Gaps are always 100 units between neighboring square edges, without connecting strokes.

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

Drawing execution, previews and grading use
`state.toBeExpression.turtleCommandExecutionInfo.completedCommands` for every editor.
The recreate-shape editor selects the shared interpreter rather than the separate
checked Java runtime or Python/Snap execution backends. The Java tab uses
`JavaStructuredFunctionBasedEditor` bound to the central state; edits publish
`ProgrammingStateJavaStructured` instead of retaining a second raw-source state.
