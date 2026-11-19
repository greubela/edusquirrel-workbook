# Programming conversions

The shared API is [`ProgrammingStateConversions`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/ProgrammingStateConversions.scala) in `it.evadid.vm.conversion`. Import its extensions to use `toBeExpression`, `toLanguage`, `toJava`, `toPython`, `toSnapXml`, `toJavaStructured` and `toPythonStructured`.

Cross-language conversion follows one route:

```
source state → source frontend → BeExpression → target printer → target state
```

Source-string identity conversions preserve the original text. Snap XML ↔ the full Snap model remains a lossless representation conversion; it does not need an executable expression tree. Structured state constructors retain unfinished source for editing, but cross-language conversion requires supported syntax.

`BeExpression` stores language-neutral definitions, calls, values and control flow. It contains no checked-Java runtime nodes, original Java source, target-language names, metadata-selected semantic modes or implicit Python receiver parameters. Printers supply target syntax, including Python's `self`. Explicit `BeExpressionUnsupported` and `BeExpressionUnparsable` nodes can retain unsupported source for diagnostics.

| Responsibility | Package below `vm.conversion` |
| --- | --- |
| Python frontend | `pythonexpression` |
| Java frontend and separate checked compilation | `javaexpression` |
| Snap frontend, startup and detached scripts | `snapexpression.SnapToExpression`, using `turtlestitchexpression` |
| Shared-tree printers | `expressionpython`, `expressionjava`, `expressionsnap`, `expressionlisp` |
| Source grouping for structured editors | `structured` |
| Lossless Snap project representation | `snapxmlmodel`, `snapmodelxml` |
| Python-to-Snap application retaining project layout | `pythonsnap`, using the same frontend and expression exporter |
| SQL clause blocks | `sqlblocks` |

Snap green flags become startup; detached scripts become uncalled functions. Saved variable initializers enter the shared tree once. Unsupported Snap classes are rejected rather than silently discarded. Mixed static/instance entry-point classes and constructors are outside the current conversion subset. Static Java entry-point wrappers lower to module functions and startup; original wrapper names and formatting are not replayed after a language change.

Checked Java execution remains a separate runtime capability. Its nodes do not extend `BeExpression`. Java unit-test execution can generate runtime instrumentation through `JavaTurtlePythonExport`; that instrumentation is not an editable programming-state conversion and does not select conversion routes. There is no Java-emulation metadata reader or editable Java/Python/Snap bridge.

Regression coverage lives in `ProgrammingStateConversionSpec`, `StructuredProgrammingStateSpec`, `PythonTypeHintConversionSpec` and the turtle execution tests. Checked Java arithmetic, bindings, tracing and runtime tests remain independent of conversion tests.
