# Source parsing

All textual `ProgrammingState` parsing enters through
[`SourceParsing.parse(source, language)`](../modules/core/shared/src/main/scala/it/evadid/vm/parsing/source/SourceParsing.scala).
Java, Python and Snap own separate, lazy stages behind this entry point. Recent small
sources are reused globally; large projects stay with their owner. Java/Python binding
results are cached per `ProgrammingEnvironment`.
Editors and analyses read these stages instead of invoking grammars themselves.

| Stage | Java / Python | Snap |
| --- | --- | --- |
| `structured` | Tolerant FastParse tokens and declarations with source spans | Tolerant FastParse XML tokens with spans and completion flags |
| `syntax` | Located syntax tree or a parsing failure | Validated, lossless `SnapScalaModel.Document` or an error |
| Conversion | `neutral` produces `ParsedProgram`; `parsed(environment)` binds it to `BeExpression` | `project` reads Snap structure; `parsed` binds blocks and startup scripts to `BeExpression` |

Unfinished or invalid text remains inspectable in `structured`. Later stages may fail
or preserve unreadable statements as explicit source placeholders; they must not
silently discard them. Java/Python library definitions are masked with spaces before
syntax parsing, preserving source offsets.

```scala
val source = SourceParsing.parse(code, SourceLanguage.Java)
val declarations = source.structured.functions // available while typing
val program = source.parsed(environment)        // may report a conversion failure
```

Typed models enter converters directly: a `SnapScalaModel.Document` must not be
serialized to XML and parsed again to obtain expressions. Snap XML retains lexical
spelling and unknown extensions; DTDs are never resolved or fetched. Bare block
fragments are grouped into a script from the decoded nodes, without reparsing.

Java/Python adapters produce the shared `ParsedProgram`; its builder reserves
class/function signatures before binding bodies. User declarations and local names
shadow built-in aliases. `TurtleCommand` and `KnownBeEntityNames` own canonical
library names; printers choose language-specific spellings independently of identity.

Tests: `JavaStructuredSpec`, `PythonStructuredSpec`, `SnapSourceSpec` and
`SourceParsingSpec`. See [programming conversions](programming-conversions.md)
for state formats, environments and conversion semantics.
