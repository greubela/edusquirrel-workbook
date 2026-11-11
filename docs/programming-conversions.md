# Programming conversions

Language conversion belongs to [`it.evadid.vm.conversion`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion), in shared core. `ProgrammingState` and `SnapScalaModel` contain the representations and their current persistence codecs; editors select and invoke conversions without implementing them.

Import `it.evadid.vm.conversion.ProgrammingStateConversions.*` to use `state.toLanguage(language)`, `toBeExpression`, `toJava`, `toPython`, `toSnapXml` and `toSnapScalaModel`. These are VM extension methods, not methods implemented by individual state classes. Their dispatcher composes the converters below; there is no second set of converters in the workbook package.

| Transformation | Package under `vm.conversion` |
| --- | --- |
| Java source → expression / checked VM program | `javaexpression`, with checked lowering in `javaexpression.checked` |
| Python source → expression | `pythonexpression` |
| Snap XML → expression, with subset checks | `snapexpression` |
| Expression → Snap XML, preserving custom blocks and layout | `expressionsnap` |
| Expression → Java / JavaScript, Python, Lisp | `expressionjava`, `expressionpython`, `expressionlisp` |
| Python source → Snap XML | `pythonsnap` |
| Checked Java → editable Snap / Python | `javasnap`, `javapython` |
| Edited Java-semantic Snap / Python → Java | `snapjava`, `pythonjava` |
| Full Snap XML → immutable Snap document | `snapxmlmodel` |
| Immutable Snap document → lossless XML | `snapmodelxml` |
| TurtleStitch project / XML → expression and canvas layout | `turtlestitchexpression` |
| State → executable unit-test Python | `unittest` |

`javaediting` shares the metadata and type rules needed by the four Java editing directions. `snapxml` supplies XML scanning and entity handling; `printing` supplies the shared Java-like printer. Shared helpers are implementation support, not alternative conversion routes. Native Java/Python lexical and AST parsers remain in `vm.parsing`; execution remains in `vm.simulation`.

For full projects, `SnapScalaXmlCodec.parse(xml)` produces a `SnapScalaModel.Document`; import `vm.conversion.snapmodelxml.SnapModelToXml.*` to emit its original XML. This path preserves unknown nodes, authored assets and lexical details independently of the executable Snap subset. `ProgrammingStateSnapXml(xml).toSnapScalaModel` wraps that document as a programming state.

Java editing conversions retain their checked numeric, scope and evaluation-order semantics. This refactor changes ownership and imports, not supported language subsets or persisted state formats. See the [semantic design review](programming-conversion-review.md) for the remaining design tradeoffs.

The older TurtleStitch editor uses the same shared conversion layer. Its project adapter has no browser DOM dependency; every parse owns its variable-binding context. Browser mount, Snap runtime and drawing components remain in the client.
