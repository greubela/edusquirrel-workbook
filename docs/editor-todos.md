# Editor follow-ups

- Java function flowcharts: add an asynchronous source-position-preserving Java-to-BeExpression flow plan. Keep the gutter hidden until it describes the actual function; do not render Python or recovered-source approximations.
- Nested Python declarations: outer graphs stay hidden where a nested definition interrupts the source region. Inner function graphs remain independent.
- Diagnostic gutter: first carry typed source ranges through both parsers and BeExpression conversion. Then render localized `LanguageMap` warnings for syntax errors, unsupported expressions and unknown dependencies. Conversion errors alone cannot accurately locate all three cases; do not guess positions by searching for repeated source fragments.

## Remaining architecture work

- Add configurable default dependency environments and `BeUnknownDependency`. Collect missing classes, static attributes and methods (for example `System.out2.println`) and verify generated standalone source compiles. The current checkpoint only makes known dependencies definition structures and binds stdout/stderr to `SystemFunctionality`.
- Finish moving Java component toolbar, mode, dialog, status and validation strings into `blockeditor` language maps. Shared Eva/block-editor vocabulary and console labels have moved; Java-specific strings remain.
- Separate Java dialog/source-selection responsibilities from `JavaComponentEditor`; review its remaining internal helper types. Eva conversion state, execution ownership/status and the UML model now have separate files.
- Extend Python keyword-argument parsing for direct `print(..., file=sys.stderr)` input. Generated stderr calls currently use a recognized helper for round trips.
- Complete full Scala/browser/CI validation of this checkpoint, including localized Run controls, shared-panel layout, grader-owned previews and icon loading. Focused panel/model/flowchart tests and static checks passed before the final dependency changes; the full dependency checkpoint has not yet passed validation.
