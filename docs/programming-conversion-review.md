# Programming conversion semantics and trade-offs

The conversion layer preserves one authoritative program while supporting editing
and execution across Java, Python and Snap. Reducing conversions is useful only
when the required language semantics and authored data remain explicit.

## Current paths

The central answer is one `Var[ProgrammingState]`. Changing languages converts that answer and installs the converted representation. It does not retain an inactive editor's original source as an alternative answer. Persistence of a full typed Snap project is separate from the smaller subset supported by cross-language conversion.

| Operation | Current path | Consequence |
| --- | --- | --- |
| Java class to editable Python | Java source → checked Java program → `javapython.JavaToPython` → Python source with Java helpers and metadata | The Python view deliberately retains Java semantics. |
| Java class to editable Snap | Checked Java program → custom blocks and metadata notes → Snap XML/model | This is a Java editing project, rather than a normal native turtle project. |
| Edited bridge Python/Snap to Java | Parse the edited representation and metadata → reconstruct Java source → check/compile Java | Edits are reconstructed; the bridge does not replay a hidden saved Java program. |
| Java class drawing | Checked Java program → Java worker runtime → turtle commands | No Python translation is required for this execution path. |
| Native Python drawing | Current Python source → Pyodide worker → turtle callbacks | The current source can be executed directly. |
| Native Snap drawing | XML → legacy importer → `BeExpression` → generated Python → Pyodide worker | The drawing service executes a translated subset, not the entire native Snap runtime. |
| Bridge Python drawing | Current Python source plus installed Java helpers → Pyodide worker → turtle callbacks | The editable Python form is executed with Java emulation helpers. |
| Bridge Snap drawing | Snap → Java → checked program → editing Python → Pyodide worker | Several representation changes occur before execution. |
| Unit tests on bridge Python/Snap | Bridge representation → Java source → checked program → `JavaTurtlePythonExport` → Pyodide worker | Test execution uses a different Python export from the editable Python form. |
| Unit tests on native Snap | Typed project validation → restricted execution expression → Python → Pyodide worker | The accepted subset is narrower than the drawing importer's subset. |

Shared converters live in [VM conversion packages](programming-conversions.md).
[SnapTurtleCommandExecution](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/SnapEditor/execution/SnapTurtleCommandExecution.scala)
coordinates the client execution paths.

## Constraints and trade-offs

| Concern | Current contract and cost |
| --- | --- |
| Semantic mode | Python's `__java_turtle_metadata` and Snap's `EduSquirrel Java Turtle 1` notes select Java-semantic paths. Malformed recognized markers are rejected; removing markers changes routing. |
| Portability | Generated Python/Snap needs Java helpers/custom primitives. The displayed language alone does not imply native semantics or standalone portability. |
| Drawing versus tests | Separate lowering paths share semantic support. The [Snap test boundary](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/snapexpression/SnapStateConversion.scala) additionally rejects saved variables, custom blocks and multiple scenes/sprites. Acceptance for drawing does not guarantee acceptance for testing. |
| Source regeneration | Reconstructing/reparsing Java adds work and failure locations, but also supplies type/scope/control-flow validation. Fewer transformations must retain those checks. |
| Supported subset | A full Snap document can be saved even when translation fails. Unsupported edits stay editable; conversion reports errors rather than discarding unknown blocks or assets. |
| Round trips | Executable equivalence, authored-data preservation and exact text/layout preservation are separate contracts. Execution checks alone do not establish all three. |
| Availability checks | Conversion is attempted as source changes and again on a switch. Sharing derived analysis could reduce duplicate work, but an approximate checker must not disagree with the converter or create another authoritative answer. |

## Required semantic behavior

Ordinary translations are not enough to retain Java behavior:

| Example | Java behavior | Ordinary Python behavior |
| --- | --- | --- |
| Integer `5 / 2` | `2` | `/` produces `2.5`. |
| Integer `-5 / 2` | `-2`, truncating toward zero | `//` produces `-3`, so replacing `/` with `//` is insufficient. |
| Increment `2147483647` as an `int` | Wraps to `-2147483648` | Python integers grow without 32-bit overflow. |
| Floating division by zero | Java floating-point infinities/NaN as applicable | Ordinary Python division raises an exception. |

Prefix/postfix updates, short-circuit evaluation, mutable method parameters, recursive locals, scope, signed zero and non-finite numbers also need explicit treatment. Existing [conversion tests](../modules/core/shared/src/test/scala/it/evadid/vm/conversion/ProgrammingStateConversionSpec.scala) cover many of these cases, repeated view changes, edited bridge programs, malformed metadata and native Snap normalization. This is useful evidence for the checked subset, not a proof of general Java/Python/Snap equivalence.

## Alternatives

| Direction | Transformations | Consistency and cost |
| --- | --- | --- |
| Native execution with explicit conversion | Translate only on language switches or required projections | Smaller surface; accept native differences or restrict the convertible subset, and define how cross-language tests execute |
| Explicit Java mode with shared checked analysis | Execute checked programs; generate source for editing/export | Retains Java semantics with less regeneration, but needs explicit mode/import policy and equivalent validation |
| Canonical cross-language model | Editors project from one model and runtimes lower it | Uniformity for a limited language; supporting full native features and opaque projects adds substantial model/synchronization cost |

## Open design question

The alternatives above are not implemented policies. A change must define whether
Python/Snap views of Java retain Java semantics or become independent native programs,
how imports identify that mode, and which authored data conversion preserves.
Derived analysis should remain transient and tied to the current answer. Validate
changes with direct execution, test-export execution and repeated editing round trips.
