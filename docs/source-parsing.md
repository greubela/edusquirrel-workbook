# Source parsing

Java and Python source is parsed in steps by one entry point,
[`SourceParsing.parse(source, language)`](../modules/core/shared/src/main/scala/it/evadid/vm/parsing/source/SourceParsing.scala).
No other code calls a grammar: the grammars are `private[parsing]`, and callers read the step they need from the
returned source (recent sources are memoized, and each step is a lazy value computed once). See
[programming conversions](programming-conversions.md) for the state API.

| Step | Result | Accepts unfinished/invalid code |
| --- | --- | --- |
| 1 `structured` | `JavaSourceStructured` / `PythonSourceStructured`: classes, functions, fields, parameters, spans and lines, library ranges, masked text | yes (tolerant fastparse lexer) |
| 2 `syntax` | `JavaSyntax` (located `JavaAST`, functions matched to step 1) / `PythonSyntax` (`PyAST`), or a `SyntaxFailure` | unreadable statements become explicit nodes |
| — `neutral` | language-neutral `ParsedProgram` | as step 2 |
| 3 `parsed(environment)` | `JavaProgramParsed` / `PythonProgramParsed`: `BeExpression` plus `JavaFunctionParsed` / `PythonFunctionParsed` (step-1 function, syntax node, `BeDefineFunction`) | conversion errors for Java |

```scala
val source = SourceParsing.parse(code, SourceLanguage.Java)
source.structured.functions.map(f => (f.name, f.span.startLine, f.parameterList)) // step 1, always available
source.syntax.map(_.functions)                                                      // step 2
source.parsed(environment).map(_.expression)                                        // step 3
```

Step 1 ([`parsing/source`](../modules/core/shared/src/main/scala/it/evadid/vm/parsing/source/)) lexes words,
comments, strings and nested brackets and reads declarations from them; it is what editors (Java function editor,
structure dialog, flowchart gutters, UML), structured states and class hoisting use. It masks library definitions
(e.g. the generated `Turtle` class) with blanks so later steps keep offsets but skip them. Step 2 runs on the masked
text: one located Java grammar (parentheses and `//` comments retained; the turtle compiler treats comments like empty
statements; parser input limits apply) and the Python grammar.

The step-1 specs (`JavaStructuredSpec`, `PythonStructuredSpec`) test the intermediate structures most thoroughly;
`SourceParsingSpec` covers the later steps.

## Neutral model

The adapters ([`JavaToExpression`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/javaexpression/JavaToExpression.scala),
[`PythonToExpression`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/pythonexpression/PythonToExpression.scala))
turn syntax into [`ParsedProgram`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/parsed/); they keep raw
names, literal spellings and declared types. `ParsedProgramBuilder` declares every class (signatures first) and reserves
each block's function and class names before it reads bodies, then binds calls, operators and members. Both use the
shared `countingLoop` ([loops](programming-conversions.md#loops)); Java's entry class (the one with a static `main`) is
the module, and its `main` body the main sequence. Constructs without a shared form become explicit unsupported or
unparsable nodes with their source. Python blocks may use any consistent indentation; tabs count as four spaces, and
indented fragments are dedented.

## Canonical function names

Calls are bound in `ParsedProgramBuilder` through
`SymbolTable.ParseContext.resolveFunction`, identically for Python and Java. Existing supplied/source definitions
win over built-in name resolution. Each block reserves its function/class declarations
before calls are read, so aliases such as `fd` remain user-owned in recursive calls
and calls before their declaration. Callable parameters and locals also shadow turtle
aliases. This is declaration awareness, not a complete module/import or lexical
function-binding implementation.

[`TurtleCommand`](../modules/core/shared/src/main/scala/it/evadid/core/datastructures/vectorShapes/svg/TurtleCommand.scala)
owns the canonical alias table and one shared `turtleClass: BeDefineClass`.
`resolveCanonicalNames` and `resolveTurtleMethod` accept either a `String` or a
`BeEntityName` and return `Option`: unknown names remain unresolved. Qualified
names recognize only `turtle`/`Turtle`, not arbitrary objects with similar methods.
Aliases resolve to the prepared method/name objects, including their language maps.
The current defaults still use universal labels; resolution does not invent translations.

```scala
TurtleCommand.resolveCanonicalNames("turtle.rt") // Some(prepared right name)
TurtleCommand.resolveTurtleMethod("fd")          // Some(shared forward method)
TurtleCommand.resolveTurtleMethod(entityName)    // same lookup for a BeEntityName
```

The known-definition cache reuses that class rather than defining another turtle.
Calls with the signature's arity reuse its method directly. The builder adapts
parameter slots for other supplied argument counts without mutating
the shared class; the Snap conversion boundary still validates supported call arities.

`KnownBeEntityNames.resolveCanonicalNames` composes turtle resolution with
`resolveSystemMethods`, which gives `print`, `println` and `System.out.println`
one printing identity. Built-in function construction uses the same prepared names.
Unknown calls create a fallback name only after these lookups; user declarations and
variables are never globally canonicalized by `BeEntityName` factories.
Human-language labels and canonical identity remain separate from programming-language
code emission: resolving a printing name does not itself implement Java output syntax.

