# Editor follow-ups

- Java function flowcharts: add an asynchronous source-position-preserving Java-to-BeExpression flow plan. Keep the gutter hidden until it describes the actual function; do not render Python or recovered-source approximations.
- Nested Python declarations: outer graphs stay hidden where a nested definition interrupts the source region. Inner function graphs remain independent.
- Diagnostic gutter: first carry typed source ranges through both parsers and BeExpression conversion. Then render localized `LanguageMap` warnings for syntax errors, unsupported expressions and unknown dependencies; `ProgramAnalysis` already collects the last two per state in the worker. Conversion errors alone cannot accurately locate all three cases; do not guess positions by searching for repeated source fragments.

## Remaining architecture work

- Unknown dependencies: configurable default dependency environments per exercise.
- `flatMapSwitch` can end on an outdated value when its source changes in consecutive transactions (`LabelSelectionSpec`). Texts chosen from a finite key set use `LabelSelection` (`formatted` fills `{field}` placeholders). Switches remain only over open sets: the feedback panels' hint content and `SimpleTextEditor`'s configured placeholder (dynamic `LanguageMap` content), and `HtmlLoginElement`'s status, whose keys come from authentication failures and change only across asynchronous steps.
