# Refactoring contenders (long files)

This document extends the initial shortlist with additional long files that are good candidates for simplification and readability-focused refactoring.

## Selection notes

- Ranked from a quick static scan by line count, excluding generated/vendor-heavy areas (`target/`, `node_modules/`, `resources/programs/`).
- Focus is **understandability first**, with line-count reduction as a side effect.

## High-priority contenders

### 1) `src/main/scala/content/plantworkshop/PlantWorkshopApp.scala` (~1350 LOC)
**Why it is hard to understand**
- One file currently owns app shell, task routing, task-specific state, and UI details for multiple independent tasks.
- Repeated mode-toggle and feedback patterns create noise and hide task intent.

**Refactor outline**
- Split into task modules (`tasks/Task0...Task5`) plus shared components (`ModeToggle`, `TaskNavigation`, `CodeFeedbackPanel`).
- Replace case-switch task rendering with a task registry (`Vector[TaskDefinition]`) to keep title/index/render logic in one place.
- Keep global state minimal (`currentTask`, `isAdvancedMode`) and move task-local state into each task module.

### 2) `src/main/scala/datastructures/core/vm/parsing/python/PythonParser.scala` (~1061 LOC)
**Why it is hard to understand**
- Mixes token/line processing, parser control flow, class/method handling, and symbol management in one object.
- Large pattern-matching blocks in `parseBlock` and related helpers increase branch complexity.

**Refactor outline**
- Split by responsibility: line preprocessing, statement dispatch, class/method parsing, symbol table.
- Convert statement parsing into small handlers with a dispatch table for easier extension.
- Introduce typed intermediate parse results to reduce tuple-heavy flow and make intent explicit.

### 3) `src/test/scala/contentmanagement/model/vm/parsing/python/PythonParserSpec.scala` (~929 LOC)
**Why it is hard to understand**
- Contains a lot of helper logic + many scenario tests in one suite.
- Test data and assertion utilities are interleaved, which obscures what behavior each test validates.

**Refactor outline**
- Split into focused suites by feature area (`comments`, `type-hints`, `control-flow`, `round-trip`).
- Extract shared helpers into a test utility object/trait.
- Move large inline source snippets into fixture builders or dedicated fixture files.

### 4) `src/main/scala/datastructures/core/vm/parsing/cpp/CppParser.scala` (~719 LOC)
**Why it is hard to understand**
- Repeated parse/error/scope boilerplate in while/if/for parsing paths.
- Single parser class handles lexing-like tasks, statement parsing, diagnostics, and expression conversion.

**Refactor outline**
- Introduce shared control-structure helper methods for keyword+header+body parsing.
- Centralize diagnostic creation to avoid divergent error wording/behavior.
- Move operand/value parsing into a helper object to reduce parser class size.

### 5) `homepage/css/workbook.css` (~1395 LOC)
**Why it is hard to understand**
- Many style concerns (layout, typography, components, states, responsive overrides) are co-located.
- Repeated values and near-duplicate selectors make impact analysis difficult.

**Refactor outline**
- Split stylesheet by concern (`layout.css`, `components.css`, `programming-preview.css`, `utilities.css`).
- Introduce clearer section conventions and deduplicate repeated style blocks.
- Replace one-off hardcoded values with shared variables/tokens where possible.

## Medium-priority contenders

### 6) `src/main/scala/datastructures/core/vm/parsing/python/PythonNormalizer.scala` (~540 LOC)
- Separate normalization stages into named functions/modules (line cleanup, structural normalization, output rendering).
- Share inline-comment scanning utility with `PythonParser` to remove duplicated low-level logic.

### 7) `homepage/css/plantWorkshop.css` (~553 LOC)
- Break into task-independent base styles + task-specific overrides.
- Consolidate repeated card/box/button styles into composable utility classes.

### 8) `src/main/scala/contentmanagement/webElements/svg/shapes/DecorationFactory.scala` (~499 LOC)
- Split into per-decoration families (arrows, split/cross flow, etc.).
- Extract common path-fragment builders and magic constants into named geometry helpers.

### 9) `tools/dev/train_mini_ml.py` (~497 LOC)
- Separate CLI/config parsing, data loading, feature engineering, training, and reporting into functions/modules.
- Add a small typed config object to remove reliance on implicit globals.

### 10) `src/main/scala/interactionPlugins/fileSubmission/turtleStitch/TurtleStitchXmlLoader.scala` (~429 LOC)
- Split XML parsing, validation, and model mapping into independent steps.
- Return structured parse errors instead of mixing concerns in one pass.

### 11) `tools/openai-proxy/app.py` (~359 LOC)
- Split request validation, provider calls, response normalization, and logging/error handling.
- Use explicit service layer functions to simplify endpoint handlers.

### 12) `src/main/scala/interactionPlugins/blockEnvironment/programming/BeProgram.scala` (~353 LOC)
- Separate rendering adaptation, program mutation operations, and serialization helpers.
- Group APIs by domain (`construction`, `editing`, `view/serialization`) for discoverability.

## Prioritization recommendation

If the goal is rapid maintainability improvement, execute in this order:

1. `PlantWorkshopApp.scala`
2. `PythonParser.scala`
3. `PythonParserSpec.scala`
4. `CppParser.scala`
5. `workbook.css`

This sequence balances immediate readability gains against regression risk and implementation effort.
