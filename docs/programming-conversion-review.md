# Programming conversion: reducing transformations without hiding semantic differences

This is a design review of the current conversion paths, not an implementation plan already approved for execution. The Java/Python/Snap bridge, workbook graph serialization, Java editor navigation and Snap editor lifecycle are unchanged by this review. The preference is to reduce transformations; the consistency guarantees affected by each alternative must remain explicit.

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

These paths are implemented in the shared [VM conversion packages](programming-conversions.md) and [SnapTurtleCommandExecution](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/SnapEditor/execution/SnapTurtleCommandExecution.scala). The former monolithic Java editing bridge is split by conversion direction, with shared semantic metadata. Both worker backends are implemented; they are not future prerequisites for this review.

## Potential problems

1. **Learner-editable content determines the semantic mode.** Python's `__java_turtle_metadata` declaration and Snap's `EduSquirrel Java Turtle 1` notes select special conversion and execution paths. Removing a marker can change routing; malformed recognized markers are deliberately rejected rather than silently treated as native programs. Moving metadata into a typed mode field would remove this inference, but exported files would still need an explicit way to retain their mode. A filename or a second magic comment would merely relocate the problem.

2. **The displayed language does not fully describe execution semantics.** Generated Python uses `java_turtle` operations and mutable cells; generated Snap uses corresponding custom primitives. A learner can reasonably expect ordinary Python or Snap behavior from the tab label. Importing helpers also makes the generated source less portable to an ordinary interpreter unless those helpers are supplied. This is an existing contract with an explanation cost, not evidence that the helpers can simply be deleted.

3. **Drawing and unit testing use different conversion paths.** The bridge creates editable Python, while unit tests regenerate Python from a checked Java program using another exporter. They share semantic support, but still have separate lowering paths. Native Snap also has two readers: the drawing importer and the stricter [unit-test conversion boundary](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/SnapStateConversion.scala). The latter rejects saved variables, custom blocks and multiple scenes/sprites, among other constraints. A project accepted for drawing is therefore not automatically accepted for testing. A future fix to evaluation order, variable handling or numeric conversion must be verified across the relevant paths; matching drawings alone does not establish equivalent function-call or test-result behavior.

4. **Source generation is used as an intermediate validation step.** Reconstructing Java, reparsing it and then exporting Python adds work, generated identifiers and additional failure locations. However, the Java compiler also performs validation. Removing this step safely means retaining equivalent scope, typing and control-flow checks through an existing checked AST/program entry point; directly constructing unchecked runtime nodes would trade fewer transformations for weaker guarantees.

5. **Conversion intentionally accepts only a subset of the native languages.** Bridge Python has requirements on imports, metadata and method definitions; bridge Snap has requirements on block specifications, scope, scripts and metadata. Valid native Python/Snap edits can therefore become non-convertible. A full Snap document can still be persisted even when it cannot be translated. Unsupported edits should remain saved and editable, with a precise conversion error; supporting more conversions must not discard unknown blocks or authored assets.

6. **Round trips can normalize source, names and layout-related representations.** Executable equivalence, preservation of authored project data, and preservation of exact source text are different contracts. Whitespace need not be preserved perfectly, but comments, meaningful names and authored assets need an explicit policy. A successful execution comparison is not sufficient evidence that a conversion preserved all of them.

7. **Availability checks also perform conversions.** Eva evaluates conversion availability as source changes and performs conversion again for an actual switch. This can build representations that are never used. It is a potential cost, not a measured startup or typing regression. Possible simplifications are attempting conversion only when requested, or sharing one derived source analysis. An independent approximate checker risks disagreeing with the real converter; a cache of separate editable answers would violate the central-state design.

## Consistency that cannot be removed accidentally

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
| **Native execution, explicit conversion** | Keep native source canonical; translate only for an explicitly requested language switch or necessary projection. | Small conceptual surface. Native language differences must be accepted or the convertible subset restricted. Existing Python-authored tests on Java still need an execution strategy. |
| **Explicit Java semantic mode, reuse checked analysis** | Keep one central answer with a typed mode. Reuse the checked program for execution; print target source only for an actual editing/export operation. | Best candidate for reducing repeated source generation while retaining Java behavior. Requires an explicit import/export policy and equivalent validation before any textual round trip is removed. Java helpers may remain necessary. |
| **One canonical cross-language intermediate model** | Editors project from a common model and runtimes lower it. | Stronger uniformity for a deliberately limited language. A model covering all native Python and Snap features would be large; preserving opaque native projects alongside it creates synchronization problems. This is not the lean near-term choice. |

Moving metadata outside learner code alone does not eliminate the transformations or semantic helpers. Conversely, removing the helpers alone does not preserve consistency. The design decision is whether a Python/Snap view of Java remains a Java-semantic editing view or becomes an independent native-language program.

## Suggested next investigation, after a separate decision

Start with **explicit semantic mode and reuse of checked analysis**, while keeping native execution where it already exists. Identify which source-generation/reparse steps can be replaced by validated structured inputs without changing the learner contract. Keep derived analysis transient and tied to the current answer; do not store multiple authoritative programs or introduce a general conversion framework.

Before implementation, agree on the semantic contract after a language switch, how exported/imported files identify their mode, what authored data a conversion must preserve, and whether failed conversions leave only the current representation available. Validate any later change with the existing numeric/control-flow conformance cases plus comparison of direct Java execution, editable Python execution, test-export execution and repeated Snap/Python edits. No bridge or runtime changes are made in this report.
