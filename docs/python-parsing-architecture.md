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

## API Boundary Rules

- Stateless algorithmic utilities are implemented as `object`s.
- Orchestrators and pipeline runners are `class`es with constructor-based dependency injection.
- Companion objects (`PythonParser`, `PythonNormalizer`) expose default convenience factories for existing call sites.
