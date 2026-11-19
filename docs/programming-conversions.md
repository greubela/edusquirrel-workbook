# Programming conversions

Each immutable programming state owns one [`ProgrammingStateDerivations`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/ProgrammingStateDerivations.scala), shared by views and graders. Direct state methods expose `toBeExpression`, `toLanguage`, `toJava`, `toPython`, `toSnapXml`, `toJavaStructured` and `toPythonStructured`.

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

Snap green flags become startup; detached scripts become the reserved, uncalled `unusedCode` function. Export restores its body as loose blocks without a green-flag hat, including after Python/Java roundtrips. Saved variable initializers enter the shared tree once. Unsupported Snap classes are rejected rather than silently discarded. The Python frontend binds constructors, member access, instance calls and static calls. Inheritance and unknown decorators remain explicit unsupported source. The Java frontend also binds constructors, explicit and implicit instance receivers, local shadowing and static methods. Its lexical lowering shares the expression binder; the checked Java runtime still enforces its narrower subset. Static Java entry-point wrappers lower to module functions and startup; original wrapper names and formatting are not replayed after a language change.

Checked Java execution remains a separate runtime capability. Its nodes do not extend `BeExpression`. Java unit-test execution can generate runtime instrumentation through `JavaTurtlePythonExport`; that instrumentation is not an editable programming-state conversion and does not select conversion routes. There is no Java-emulation metadata reader or editable Java/Python/Snap bridge.

Regression coverage lives in `ProgrammingStateConversionSpec`, `StructuredProgrammingStateSpec`, `PythonTypeHintConversionSpec` and the turtle execution tests. Checked Java arithmetic, bindings, tracing and runtime tests remain independent of conversion tests.

## Neutral object model and next phase

`BeFunctionCall.target` distinguishes a function, an instance method with a receiver, a static method, and a constructor. Its declaration remains the sole owner of the signature and canonical name. `BeCurrentInstance`, `BeMemberAccess` and `BeAssignMember` represent object operations; `BeDataType.ClassType` represents named object types. Receivers participate in traversal and replacement, and these nodes roundtrip through JSON and MessagePack. Java and Python printers render the same tree; Snap rejects object functionality explicitly. The small drawing simulator also rejects unsupported object execution.

A target sum type is preferable to independent `isStatic`/`isConstructor` flags and receiver fields: callers cannot accidentally retain a receiver on a constructor or static call, and each new call category has one explicit branch. No source-language names or runtime nodes are added to the model. Source placeholders alone retain optional `parsedFromLanguage`; emitting known foreign source fails explicitly. Snap numeric slots recover numeric types; they do not preserve an original Java `int`/`double` distinction.

The next implementation phase is:

1. Bind constructor, member and instance/static-call syntax in the Java/Python frontends to these nodes; cover the object scenario in both source directions.
2. Introduce the sealed expression-centered state hierarchy and the semi-structured source states from the design. Keep full Snap XML/model conversion lossless.
3. Route editor conversion through the prepared `ConversionResult`/`ConversionInfo` values, including unsupported features and numeric-type changes; reject unsupported features rather than silently dropping them.
4. Add first-definition-wins diagnostics for hoisted classes, then extend language-independent execution only where needed.

These are parser/state/API follow-ups; the object model and printer support do not imply that every object-oriented source program can already be parsed or executed.
