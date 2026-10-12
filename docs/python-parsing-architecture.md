# Python Parsing Architecture

## Overview
The Python front-end is split into two orchestrators:

- `PythonNormalizer`: transforms raw Python text into a canonical representation.
- `PythonParser`: parses normalized Python into workbook VM expressions.

Both are configured through `PythonFrontendConfig`, which centralizes defaults such as indent width and known symbol structures.

## Main Data Flow

1. `PythonParser.parsePythonWithDetails(source)` accepts raw source and invokes its injected normalizer.
2. `PythonNormalizer.normalizePython(source)` uses `runPipeline(source)` and `PythonNormalizationPipelineRunner.run(source)`.
3. Normalization stages:
   - normalize line endings / detab
   - extract raw lines
   - build statement tree
   - render normalized output
4. The parser converts the normalized output into VM expressions and returns definitions/symbol information in `CodeParsingResult`. Callers do not need to normalize the source a second time.

Source is under [it/evadid/vm/parsing/python](../modules/core/shared/src/main/scala/it/evadid/vm/parsing/python/); typed normalization stages live in its `normalization/` subdirectory. Tests run on both core JVM and Scala.js, including `PythonNormalizationStagesSpec` and `PythonParserSpec`.

## Key Modules

- `PythonFrontendConfig`: shared configuration defaults for parser + normalizer.
- `PythonNormalizationPipeline`: typed stage interfaces and default runner implementation.
- `PythonInlineCommentHelper`: shared inline-comment splitting logic.
- `PythonBlockWalker`: shared indentation-aware walker utilities used by parser and normalizer tree-building.

## API Boundary Rules

- Stateless algorithmic utilities are implemented as `object`s.
- Orchestrators and pipeline runners are `class`es with constructor-based dependency injection.
- Companion objects (`PythonParser`, `PythonNormalizer`) expose default convenience factories for existing call sites.
