# Editor follow-ups

- Java function flowcharts: add an asynchronous source-position-preserving Java-to-BeExpression flow plan. Keep the gutter hidden until it describes the actual function; do not render Python or recovered-source approximations.
- Nested Python declarations: outer graphs stay hidden where a nested definition interrupts the source region. Inner function graphs remain independent.
- Diagnostic gutter: first carry typed source ranges through both parsers and BeExpression conversion. Then render localized `LanguageMap` warnings for syntax errors, unsupported expressions and unknown dependencies; `ProgramAnalysis` already collects the last two per state in the worker. Conversion errors alone cannot accurately locate all three cases; do not guess positions by searching for repeated source fragments.

## Remaining architecture work

- Hints for turtle exercises: only `ProgrammingExerciseFullJava` has a scaffolder, so only it shows the hint button (`FeedbackPanels` shows it in sidebar and overview for every scaffolder). Recreate-shape tasks need `TurtleRecreateShapeInteractionFormat` with `AdditionalInfoLlmScaffolding` instead of `Unit`, a `ProgrammingHintScaffolder` that does not depend on one format, and Snap/block answers printed as Python before prompting. `instructionText` can serve as the public task.
- Unknown dependencies: configurable default dependency environments per exercise; unknown attribute reads (`Other.value`) still become unsupported expressions instead of `BeUnknownDependency` attributes; the UML diagram does not show unknown or library-only classes as read-only boxes.
- `JavaComponentEditor`'s mode/selection synchronization is the next candidate for its own component.
- Static drawings in `CompareShapePreview` and `TurtleStepPanel` still compute on the page thread (deferred); they could read the turtle commands of the worker's `ProgramAnalysis`, but share their comparison with `RecreateShapeGrader` today.
- `flatMapSwitch` over label signals can show a stale text when its source changes in consecutive transactions (seen in the Java editor heading). Java editor texts now select from `JavaEditorLabels.all`; check other `flatMapSwitch` label uses (e.g. `FeedbackPanels`) for the same pattern.
