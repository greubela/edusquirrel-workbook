# Editor follow-ups

- Java function flowcharts: add an asynchronous source-position-preserving Java-to-BeExpression flow plan. Keep the gutter hidden until it describes the actual function; do not render Python or recovered-source approximations.
- Nested Python declarations: outer graphs stay hidden where a nested definition interrupts the source region. Inner function graphs remain independent.
- Diagnostic marks: Java unknown-dependency uses are located by the Java parser (`JavaSourceWarnings`) and marked in Full Mode with a localized warning. Still missing: located Python expressions (its parser records only top-level statement lines), locations of unsupported expressions (the adapter does not keep the AST node), and marks in the Class/Function fragment editors (they need fragment offsets in the full source). Do not guess positions by searching for repeated source fragments.

## Remaining architecture work

- Unknown dependencies: configurable default dependency environments per exercise.
- `flatMapSwitch` can end on an outdated value when its source changes in consecutive transactions (`LabelSelectionSpec`). Texts chosen from a finite key set use `LabelSelection` (`formatted` fills `{field}` placeholders). Switches remain only over open sets: the feedback panels' hint content and `SimpleTextEditor`'s configured placeholder (dynamic `LanguageMap` content), and `HtmlLoginElement`'s status, whose keys come from authentication failures and change only across asynchronous steps.
