# Second Iteration Block Environment Refactor Design

## 1. Background and Motivation
The first iteration of the turtle block environment exposes Laminar views such as `HtmlBlockDragFromArea` that render palette tabs, create drag payloads via `TurtleBlockDragContext`, and instantiate palette blocks from `TurtleBlockDefinition`.【F:src/main/scala/interactionPlugins/blockEnvironment/firstIteration/HtmlBlockDragFromArea.scala†L1-L78】 While functional, the existing implementation is tightly coupled to turtle-specific shapes and serializers, making it difficult to reuse for newer language-agnostic block types.

The new programming model introduces the `interactionPlugins.blockEnvironment.programming` package, where `BeBlock`, `BeProgram`, and associated connection, rendering, and layout helpers provide a composable representation of block-based programs across languages.【F:src/main/scala/interactionPlugins/blockEnvironment/programming/BeBlock.scala†L1-L18】【F:src/main/scala/interactionPlugins/blockEnvironment/programming/BeProgram.scala†L1-L70】 Future UI components need to integrate with this tree-based model and reuse the renderer infrastructure defined by `BeProgramRenderer`, `BeBlockLayoutManager`, and `BeRendererConfig`.【F:src/main/scala/interactionPlugins/blockEnvironment/programming/rendering/BeProgramRenderer.scala†L1-L78】【F:src/main/scala/interactionPlugins/blockEnvironment/programming/rendering/BeBlockLayoutManager.scala†L1-L74】

## 2. Central Workbook Architecture Overview
EduSquirrel workbook exercises follow a full-interaction pattern: controllers assemble state, scaffolding, and grading into Laminar-based views via `HtmlFullInteractionExercise` derivatives.【F:src/main/scala/workbook/workbookHtmlElements/abstractions/HtmlFullInteractionExercise.scala†L14-L50】 Program-centric exercises previously wrapped turtle-specific editors and execution panes (`HtmlTurtleInteractionContainer`, `TurtleProgramExecutor`) to compose palette, workspace, and feedback panes.【F:src/main/scala/interactionPlugins/blockEnvironment/firstIteration/HtmlTurtleInteractionContainer.scala†L1-L80】【F:src/main/scala/interactionPlugins/blockEnvironment/firstIteration/TurtleProgramExecutor.scala†L1-L81】 Migrating to the `BeBlock` framework requires equivalent Laminar components that can be wired into the existing full-interaction container without leaking turtle-only abstractions.

## 3. Refactored Component Goals
The second iteration aims to:

1. **Adopt `BeBlock` data structures** for palette definitions, workspace state, and serialization.
2. **Reuse `BeProgramRenderer`** for visual layout, removing bespoke SVG calculations.
3. **Maintain workbook container compatibility** by exposing Laminar `HtmlWorkbookElement` derivatives that slot into the existing full-interaction scaffolding.
4. **Enable language-aware behaviour** by delegating code generation to `BeBlock.toCode` implementations and `BeProgram.toPythonString` utilities.【F:src/main/scala/interactionPlugins/blockEnvironment/programming/blocks/BeMotionBlocks.scala†L1-L36】【F:src/main/scala/interactionPlugins/blockEnvironment/programming/blocks/BeBlockFunctionDefinition.scala†L1-L41】

## 4. Target Use Cases
The redesigned components must cover the following learner and instructor scenarios:

- **Palette Browsing:** Learners can switch categories (motion, values, control) and preview available blocks before dragging. Category metadata will be derived from `BeConnectionRole` groupings rather than turtle enums.【F:src/main/scala/interactionPlugins/blockEnvironment/programming/connection/BeConnectionRole.scala†L1-L32】
- **Drag from Palette to Workspace:** Initiate a drag operation that serializes a `BeBlock` template and attaches it to a Laminar drag payload, similar to the first iteration but returning `BeBlock` instances instead of `TurtleBlockDefinition`.
- **Workspace Composition:** Drop zones accept blocks based on compatible `BeConnection` descriptors, using cardinality checks (`BeConnectionCardinality`) to validate insertion points before mutating the `BeProgram` tree.【F:src/main/scala/interactionPlugins/blockEnvironment/programming/connection/BeConnectionCardinality.scala†L1-L23】
- **Block Editing:** Value blocks surface inline editors for strings, numbers, or booleans, propagating updates to associated `BeBlockValue` instances.【F:src/main/scala/interactionPlugins/blockEnvironment/programming/blocks/BeBlockValue.scala†L1-L22】
- **Program Visualization:** Render the assembled program via `BeProgramRenderer`, ensuring layout managers (`BeBlockLayoutManager.SimpleVBoxChildrenLayoutManager`, etc.) compute consistent offsets and shapes.【F:src/main/scala/interactionPlugins/blockEnvironment/programming/rendering/BeProgramRenderer.scala†L12-L73】
- **Code Export & Execution:** Generate language-specific source (initially Python) using `BeProgram.toPythonString` for previews or execution hooks.【F:src/main/scala/interactionPlugins/blockEnvironment/programming/BeProgram.scala†L18-L39】

## 5. Proposed Second Iteration Modules
All new Scala (and supporting Markdown) files for this refactor will live in `interactionPlugins.blockEnvironment.secondInteration`.

### 5.1 Palette & Drag Context
- **`BeBlockPaletteModel`**: Collects available `BeBlock` templates grouped by semantic category, exposing helper methods to instantiate palette entries with prefilled `BeConnection` metadata.
- **`HtmlBeBlockPalette`**: Laminar component mirroring `HtmlBlockDragFromArea` but driven by `BeBlock` templates. Uses a `Var[PaletteCategory]` to toggle tabs, constructs previews via `BeProgramRenderer` configured with minimal `BeProgram` stubs for each block, and sets drag payloads to serialized block descriptors.
- **`BeDragContext`**: Successor to `TurtleBlockDragContext`, carrying the currently dragged `BeBlock`, computing drop compatibility, and exposing helper signals for hover/selection feedback.

### 5.2 Workspace Surface
- **`BeWorkspaceState`**: Holds the mutable `BeProgram` tree and exposes transaction-like mutations (insert child, replace block, remove branch) with automatic validation against connection roles and cardinalities.
- **`HtmlBeWorkspace`**: Displays the active program. Subscribes to `BeWorkspaceState` streams, rebuilds `BeProgramRenderer` instances on change, and layers drop targets around computed `boundsTree` entries.
- **`BeWorkspaceInteractionController`**: Bridges palette drag events and workspace state updates, ensuring UI gestures translate into tree mutations and providing undo/redo hooks for future work.

### 5.3 Exercise Container Integration
- **`HtmlBeBlockEnvironment`**: High-level component assembling palette, workspace, and execution panes. Implements `HtmlWorkbookElement` so it can plug into existing full-interaction layouts alongside scaffolding and grader views.【F:src/main/scala/workbook/workbookHtmlElements/abstractions/HtmlFullInteractionExercise.scala†L14-L50】
- **`BeProgramExecutor`**: Wraps `BeProgram.toPythonString` and future simulators (`BeSimulator`) to provide run/reset buttons, mirroring the turtle executor pane while operating on the new program model.【F:src/main/scala/interactionPlugins/blockEnvironment/programming/BeProgram.scala†L41-L63】【F:src/main/scala/interactionPlugins/blockEnvironment/programming/BeSimulator.scala†L1-L20】
- **`BeExerciseContent`**: Defines starter programs, goals, and metadata, replacing `TurtleExerciseContent` with structures that produce `BeProgram` instances for initial state.【F:src/main/scala/interactionPlugins/blockEnvironment/firstIteration/TurtleExerciseContent.scala†L1-L74】

## 6. Data Flow & State Management
1. **Initialization**: `BeExerciseContent` supplies a starter `BeProgram`, palette catalog, and renderer configuration. The exercise container instantiates `BeWorkspaceState` with this starter tree.
2. **Rendering Loop**: `HtmlBeWorkspace` observes the workspace state, runs `BeProgramRenderer.render()`, and updates the SVG canvas. Palette previews reuse the same renderer to ensure consistent visuals.
3. **User Interaction**: Dragging from the palette calls `BeDragContext.startPaletteDrag(blockTemplate)`, storing metadata for drop validation. Dropping onto workspace queries `BeWorkspaceState` for a `DropTargetDescriptor` derived from the hovered node's `BeConnection` definitions.
4. **State Mutation**: When a drop is accepted, `BeWorkspaceState` rebuilds the `BeProgram` tree via helper methods (e.g., `insertChild`, `replaceSubtree`). Observers fire, re-rendering the workspace and enabling dependent UI (code preview, execution).
5. **Code Generation & Execution**: A derived signal converts the current `BeProgram` to Python using `BeProgram.toPythonString`, feeding preview panels or `BeProgramExecutor` run commands.

## 7. Open Questions & Follow-Ups
- **Layout Sizing**: `BeBlockLayoutManager.getNiceSize` currently returns placeholders; refactor should confirm sizing heuristics before relying on nice-size calculations for palette thumbnails.【F:src/main/scala/interactionPlugins/blockEnvironment/programming/rendering/BeBlockLayoutManager.scala†L21-L70】
- **Connection Catalog**: Need a canonical mapping between palette categories and `BeConnectionRole`/`BeDataType` combinations to ensure consistent filtering.
- **Undo/Redo & History**: First iteration lacks structured history management; consider leveraging workbook-level state patterns for reliable undo stacks.
- **Accessibility**: Drag-and-drop should expose keyboard alternatives; evaluate how Laminar's event streams can be extended to support focus-based insertion commands.

## 8. Deliverables
- Scala implementations for the modules listed in Section 5 inside `interactionPlugins.blockEnvironment.secondInteration`.
- Updated exercise wiring integrating the new components into full-interaction exercises, replacing turtle-specific containers.
- Follow-up tasks for renderer polish and accessibility as outlined in Section 7.
