# Python Parsing Architecture

## Overview
The Python front-end is split into two orchestrators:

- `PythonNormalizer`: transforms raw Python text into a canonical representation.
- `PythonParser`: parses normalized Python into workbook VM expressions.

Both are configured through `PythonFrontendConfig`, which centralizes defaults such as indent width and known symbol structures.

## Main Data Flow

1. `PythonNormalizer.runPipeline(source)`
2. `PythonNormalizationPipelineRunner.run(source)`
3. Normalization stages:
   - normalize line endings / detab
   - extract raw lines
   - build statement tree
   - render normalized output
4. `PythonParser.parsePythonWithDetails(normalized)` converts normalized lines to VM code structures.

## Key Modules

- `PythonFrontendConfig`: shared configuration defaults for parser + normalizer.
- `PythonNormalizationPipeline`: typed stage interfaces and default runner implementation.
- `PythonInlineCommentHelper`: shared inline-comment splitting logic.
- `PythonBlockWalker`: shared indentation-aware walker utilities used by parser and normalizer tree-building.

## API Boundary Rules

- Stateless algorithmic utilities are implemented as `object`s.
- Orchestrators and pipeline runners are `class`es with constructor-based dependency injection.
- Companion objects (`PythonParser`, `PythonNormalizer`) expose default convenience factories for existing call sites.
