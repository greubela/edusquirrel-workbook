# Editor follow-ups

- Java function flowcharts: add an asynchronous source-position-preserving Java-to-BeExpression flow plan. Keep the gutter hidden until it describes the actual function; do not render Python or recovered-source approximations.
- Nested Python declarations: outer graphs stay hidden where a nested definition interrupts the source region. Inner function graphs remain independent.
- Diagnostic gutter: first carry typed source ranges through both parsers and BeExpression conversion. Then render localized `LanguageMap` warnings for syntax errors, unsupported expressions and unknown dependencies; `ProgramAnalysis` already collects the last two per state in the worker. Conversion errors alone cannot accurately locate all three cases; do not guess positions by searching for repeated source fragments.

## Remaining architecture work

- Unknown dependencies: configurable default dependency environments per exercise; unknown attribute reads (`Other.value`) still become unsupported expressions instead of `BeUnknownDependency` attributes; the UML diagram does not show unknown or library-only classes as read-only boxes.
- Static drawings in `CompareShapePreview` and `TurtleStepPanel` still compute on the page thread (deferred); they could read the turtle commands of the worker's `ProgramAnalysis`, but share their comparison with `RecreateShapeGrader` today.
- `flatMapSwitch` can end on an outdated value when its source changes in consecutive transactions (`LabelSelectionSpec`). Eva, the block editor, deferred previews and the feedback panels choose texts with `LabelSelection`. Remaining label switches are in the evacuation, blockchain, compression, mail, neuron and sorting editors/renderers, `HtmlLoginElement` and `DropdownMenuControl`; the feedback panels' hint content switches over dynamic `LanguageMap` content and has no finite option set.
