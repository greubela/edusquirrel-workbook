# Finite Automaton Builder & Simulator – Design Document

## 1. Reference UI Analysis
The reference screenshot of the existing “Finite Automaton Builder & Simulator” highlights several must-have features for the new implementation:

- A toolbar at the top of the editor column with buttons for adding states and transitions as well as automatic layout options (circular and force layouts).
- A mode selector that allows switching between deterministic (DFA) and non-deterministic (NFA) behaviour.
- A large editor canvas where states are rendered as nodes that can be dragged to new positions and connected with labelled transitions.
- Visual indicators for the start state and accepting states, with interactions accessible via right-click.
- A simulator panel with an input field, control buttons (reset, step forward/backward, play, pause) and a step-by-step trace of the current run.
- A dedicated area for exercise instructions.
- Sections that list example words that **should** be accepted and those that **should not**; these sections are tied to a “Run Tests” action that evaluates the automaton against both lists and reports success or failure per word.

All of these elements should appear in the automaton plugin so that the experience matches the reference system.

## 2. Existing Interaction Plugin Architecture
The current code base contains two interaction plugins (`gpt` and `turtleEnvironment`) that showcase the expected structure and interaction model.

- **Shared abstractions**: Both plugins are built on top of `HtmlFullInteractionExercise` and `HtmlFullInteractionModel`. They provide editor, scaffolding, and grading states, feedback models, and connect them via a `FullInteractionController`.
- **Turtle environment**: This plugin offers the richest example. Key parts are:
  - `TurtleExerciseContent` – an extension of `ExerciseContent` with domain specific metadata.
  - `HtmlTurtleInteractionModel` – wires up editor state, scaffolding, grading, drag context, and exposes Laminar components via a `FullInteractionVisualizer` implementation.
  - `TurtleBlockProgram`, `TurtleBlockDragContext`, `HtmlTurtleEditorArea`, and `TurtleEditorInteractionComponent` – provide the block-based editor functionality.
  - `TurtleScaffolder` and `TurtleGrader` – implement the domain logic for hints and grading.
  - `HtmlTurtleInteractionContainer` – arranges columns (palette, editor, instruction/result panels) using shared CSS classes such as `turtle-full-exercise` and `turtle-column`.
- **GPT interaction**: A lighter example that still follows the same exercise/model/container pattern, showing how the generic container and labels can be reused with domain-specific content.

The new automaton plugin should mirror this structure: dedicated content class, interaction model, scaffolder/grader, editor component(s), and a custom interaction container that reuses the shared styling approach.

## 3. Automaton Plugin Design
### Goals
Create a draggable finite automaton editor with DFA/NFA support, a step-wise simulator, and automated tests for exemplar word lists. The layout should match the column-based appearance of the turtle environment and integrate with the existing full-interaction abstractions.

### Data Model
- `AutomatonMode` enum (DFA/NFA).
- `AutomatonNode` (id, label, coordinates, start/accepting flags).
- `AutomatonTransition` (id, source, target, set of symbols).
- Editor, scaffolding, and grading states extend `InteractionState` to provide serialisable snapshots.
- `AutomatonTestCase` and `AutomatonTestResult` capture expectations and outcomes for the word lists.

### Behaviour & Domain Logic
- `AutomatonEditorStore` manages mutations (add/move/delete nodes, add/remove transitions, apply layouts, toggle flags) and propagates updates to the Laminar vars maintained by the interaction model.
- `AutomatonSimulator` produces a trace of active states for a given input word, supporting DFA and NFA semantics. It powers both the live simulator panel and the automated tests.
- `AutomatonScaffolder` offers lightweight guidance (e.g., reminders to mark start/accepting states, or to add transitions) and updates the scaffolding feedback stream.
- `AutomatonGrader` runs the full test suite, summarises pass/fail counts, and reports a grading grade.

### UI Composition
- **Editor column**: Toolbar (add state/transition, layout buttons, mode toggle), draggable canvas with nodes/transitions, context menu for start/accept toggles.
- **Simulator column**: Input string field, playback controls, textual trace of the simulation, acceptance status for the current step.
- **Instruction & Tests column**: Exercise instructions, scaffolding hints, the two word lists, a “Run Tests” button, and a result list coloured to indicate correctness.
- Styling is provided via an automaton-specific stylesheet injected by the container while reusing shared colour variables and the `turtle-*` layout classes for consistent spacing.

### Integration
- `HtmlAutomatonInteractionModel` initialises the editor with a default single start node and supplies Laminar components for the container.
- `HtmlAutomatonInteractionContainer` arranges the columns (editor, simulator, instruction/tests) matching the existing interaction plugin layouts.
- `HtmlAutomatonExercise` exposes the plugin through the shared exercise abstraction so it can be instantiated alongside the other interactions.

### Testing & Validation
- Unit-style validation is achieved through interactive use plus the automated grader results. Developers can run the provided test button to ensure the automaton meets the sample exercise (“input length divisible by three”).
- The Laminar components rely on existing observable patterns used by the turtle environment, ensuring reactivity and UI updates are consistent across plugins.

This design keeps the new plugin aligned with the existing architecture while delivering the specialised functionality shown in the reference screenshot.
