# Source parsing

Python and Java source reach the shared expression tree through the same two steps
(see [programming conversions](programming-conversions.md) for the state API):

```
source --fastparse--> PyAST / JavaAST --adapter--> ParsedProgram --ParsedProgramBuilder--> BeExpression
```

| Step | Code |
| --- | --- |
| Python grammar and syntax tree | [`PythonAstParserSimple`](../modules/core/shared/src/main/scala/it/evadid/vm/parsing/python/clean/PythonAstParserSimple.scala), `PyAST` |
| Java grammar and syntax tree | [`JavaParser`](../modules/core/shared/src/main/scala/it/evadid/vm/parsing/java/clean/JavaParser.scala), `JavaAST` |
| Adapters to the neutral model | [`PythonToExpression`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/pythonexpression/PythonToExpression.scala), [`JavaToExpression`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/javaexpression/JavaToExpression.scala) |
| Neutral model and binding | [`ParsedProgram`, `ParsedProgramBuilder`, `SymbolTable`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/parsed/) |
| Lexical structure for editors and class hoisting | [`SourceStructure`](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/structured/SourceStructure.scala) |

The adapters keep raw names, literal spellings and declared types; the builder declares
every class (signatures first) and reserves each block's function and class names before
it reads bodies, then binds calls, operators and members. Language rules live in the
adapters: Python's `for _ in range(n)` and an unused Java counter `for (int i = 0; i < n; i++)`
are both a repeat; Java's entry class (the one with a static `main`) is the module, and its
`main` body the main sequence. Constructs without a shared form become explicit unsupported or
unparsable nodes with their source.

Python blocks may use any consistent indentation; tabs count as four spaces, and indented
fragments are dedented. Comments are statements in both grammars (Java keeps them when the
parser is created with `retainComments`, as conversion does; the checked Java runtime does not).

`SourceStructure` is a fastparse lexer for words, comments, strings and nested brackets that
accepts unfinished text. Structured states, the Java function editor and the Python
flowchart gutter read classes, members, methods and code rows from it.

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

