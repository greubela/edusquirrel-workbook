# Python Parsing Architecture

## Overview
The Python front-end is split into two orchestrators:

- `PythonNormalizer`: transforms raw Python text into a canonical representation.
- `PythonParser`: parses normalized Python into workbook VM expressions.

Both are configured through `PythonFrontendConfig`, which centralizes defaults such as indent width and known symbol structures.

## Data flow and location

`PythonParser.default.parsePythonWithDetails(source)` normalizes raw source before
building VM expressions and returning definitions/symbol information. Callers
should not normalize it a second time. Normalization handles line endings and
indentation, builds a statement tree, and renders canonical source.

The Python-to-VM conversion frontend lives under
[it/evadid/vm/conversion/pythonexpression](../modules/core/shared/src/main/scala/it/evadid/vm/conversion/pythonexpression/),
with typed stages in `normalization/`. Parser and normalizer share configuration
and lexical/tree utilities so their treatment of Python stays consistent. Tests
run on both JVM and Scala.js. The independent Python AST parser remains in
`vm.parsing.python.clean`; see [programming conversions](programming-conversions.md)
for the state conversion API.

## Canonical function names

Function calls are identified in `PythonParser.parseFunctionCall` and bound in
`PythonSymbolTable.ParseContext.resolveFunction`. Existing supplied/source definitions
win over built-in name resolution. Each block reserves its function/class declarations
before calls are read, so aliases such as `fd` remain user-owned in recursive calls
and calls before their declaration. Callable parameters and locals also shadow turtle
aliases. This is declaration awareness within the existing frontend, not a complete
Python module/import or lexical function-binding implementation.

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
Calls with the signature's arity reuse its method directly. The permissive Python
frontend adapts parameter slots for other supplied argument counts without mutating
the shared class; the Snap conversion boundary still validates supported call arities.

`KnownBeEntityNames.resolveCanonicalNames` composes turtle resolution with
`resolveSystemMethods`, which gives `print`, `println` and `System.out.println`
one printing identity. Built-in function construction uses the same prepared names.
Unknown calls create a fallback name only after these lookups; user declarations and
variables are never globally canonicalized by `BeEntityName` factories.
Human-language labels and canonical identity remain separate from programming-language
code emission: resolving a printing name does not itself implement Java output syntax.

## API Boundary Rules

- Stateless algorithmic utilities are implemented as `object`s.
- Orchestrators and pipeline runners are `class`es with constructor-based dependency injection.
- Companion objects (`PythonParser`, `PythonNormalizer`) expose default convenience factories for existing call sites.
