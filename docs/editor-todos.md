# Editor follow-ups

- Diagnostic marks: Java unknown-dependency uses and unreadable statements are located by the Java parser (`JavaSourceWarnings`) and marked with a localized warning in every Java mode (`JavaFragmentRange.localize` moves marks into fragments). Still missing: located Python expressions (its parser records only top-level statement lines) and locations of constructs the Java adapter (not the parser) refuses, since the adapter does not keep the AST node. Do not guess positions by searching for repeated source fragments.

## Remaining architecture work

- Unknown dependencies: configurable default dependency environments per exercise. Conversion binds against the global `SymbolTable.defaultKnownStructures`; an exercise environment would have to reach `ProgrammingState.toBeExpression`, the worker's analysis and flowchart requests, the graders and the stored exercise schema.
- `flatMapSwitch` can end on an outdated value when its source changes in consecutive transactions (`LabelSelectionSpec`). Texts chosen from a finite key set use `LabelSelection` (`formatted` fills `{field}` placeholders); texts of a `LanguageMap` are `localizedText` of it and the page language. Switches remain only over open sets of content IDs that load on demand: `SimpleTextEditor`'s configured placeholder and `HtmlLoginElement`'s status (keys from authentication failures, changing only across asynchronous steps).
