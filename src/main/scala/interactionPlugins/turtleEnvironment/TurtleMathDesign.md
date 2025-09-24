# Turtle Math & Reporter Blocks Design

## Goals
- Allow turtle editor users to build numeric expressions with nested blocks instead of raw numeric inputs.
- Support common arithmetic operators (addition, subtraction, multiplication, division, modulo, power).
- Support frequently used unary math functions (sin, cos, tan, abs, sqrt, round, floor, ceil) with trigonometric functions available in degrees.
- Present parameter sockets as color-coded variable slots that accept reporter blocks, matching TurtleStitch-like UI expectations.
- Keep compatibility with the turtle runtime (grading/execution) by producing evaluable expressions during program flattening.

## Current State Summary
- `TurtleBlockDefinition` describes stack blocks with optional single numeric/boolean parameter. The value is stored directly on the block (`Option[Double]`).
- Parameter UI renders as inline inputs (`input[type=number|checkbox]`), preventing nested structures.
- `TurtleStructuredBlock` only keeps `inside` stacks; parameters are not modeled as child nodes.
- `TurtleProgramState` flattens structured blocks into sequential `TurtleCommand`s with literal numeric arguments. Execution assumes concrete `Double` values.
- Palette categories are limited to control/motion/pen/logic; there are no reporter/math blocks to drop into parameters.

## Proposed Features & Blocks
### Reporter Block Types
- **Numeric literal** – editable number bubble defaulting to block-specific defaults.
- **Boolean literal** – toggle block for true/false (to eventually cover logic sockets).
- **Binary arithmetic** – blocks for `__ + __`, `__ - __`, `__ × __`, `__ ÷ __`, `__ mod __`, `__ ^ __`.
- **Unary math functions** – blocks for `sin(__)`, `cos(__)`, `tan(__)`, `sqrt(__)`, `abs(__)`, `round(__)`, `floor(__)`, `ceil(__)`.
- **Angle conversion helpers** – optional `deg2rad(__)`/`rad2deg(__)` (if needed for trig alignment).

Each reporter block:
- Uses a pill/rounded SVG (`TurtleBlockSvgShape.ReporterBlock`) and exposes parameter sockets (usually two for binary, one for unary).
- Returns a *numeric* value type to satisfy motion parameters.

### Variable Slots & Colors
- Replace inline inputs with drop zones labelled by parameter name (e.g., “steps”, “degrees”).
- Provide default literal reporter blocks pre-seeded in each slot; display placeholder color even when empty.
- Assign consistent colors per slot type (e.g., motion distance, angle, repeat counts) via metadata on `TurtleBlockDefinition`.

### Palette Organization
- Add new categories: `Reporter` (for literals) and `Operators`/`Math` (for arithmetic & trig).
- Palette entries render reporter shapes with preview labels (e.g., “add”, “sin”).

## Data Model Changes
1. **Socket Definitions**
   - Extend `TurtleBlockDefinition` with `sockets: List[TurtleBlockSocketDefinition]` describing named parameter slots.
   - Each socket specifies:
     - `id` (stable identifier, e.g., `distance`).
     - `label`/placeholder text.
     - `valueType` (`Numeric` | `Boolean`).
     - `color` for placeholder UI.
     - `defaultExpression` (factory function returning default reporter block tree).
   - Legacy `parameter: Option[TurtleBlockParameter]`/`value` fields become derived from sockets (with migration for simple numeric inputs).

2. **Structured Block Tree**
   - Update `TurtleStructuredBlock` to keep `inside` children **and** `socketChildren: Map[String, List[TurtleStructuredBlock]]`.
   - Provide helpers to fetch/update socket content, ensuring each socket only keeps a single reporter root (list length ≤ 1) for unary numeric slots, or two for binary operator left/right positions.
   - Adjust program mutation methods (`insertBlocks`, `detachFrom`, etc.) to address sockets via extended paths (e.g., `[blockId, "socket:distance"]`).

3. **Expression Representation**
   - Introduce `sealed trait TurtleExpression` (Literal, UnaryOp, BinaryOp, VariableRef, CommandResult etc.).
   - Each reporter block maps to a `TurtleExpression` generator. Stack blocks convert their sockets into expressions when building commands.
   - Boolean expressions may re-use the same trait or a sibling `TurtlePredicate`.

4. **Command Model**
   - Change motion commands to carry expressions instead of plain doubles (e.g., `Forward(distance: TurtleExpression)`).
   - Provide evaluation utilities: `TurtleExpression.evaluate(context)` returning `Double`, with simple context for constants (since variables currently come from expressions themselves).

## UI / Interaction Updates
- Render sockets as dedicated drop targets showing placeholder label & color.
- When a block is dropped into a socket:
  - Remove existing content and insert new reporter tree.
  - Dragging a reporter block out should detach it, leaving placeholder & default literal.
- Reporter blocks themselves allow parameter sockets to display inline drop zones; they do not show “below” drop zones unless specifically allowed (e.g., to chain inside parameter?).
- Keep existing drag context, extending `TurtleDragPayload.EditorBlockGroup` to know whether group originates from socket vs. stack for correct reinsertion.

## Program Flattening & Execution
- Update `TurtleStructuredBlock.flattenCommands` to build `TurtleCommand`s by walking sockets, turning nested blocks into expressions via helper `TurtleExpression.fromNode`.
- `programText` for commands should pretty-print expression (e.g., `forward((a + b))`).
- Modify `TurtleProgramExecutor` to evaluate expressions at runtime before executing actions (with safe guards for invalid operations like division by zero).
- Provide evaluation-time math functions (sin/cos/tan expect degrees; convert internally before calling `math.sin` etc.).

## Additional Considerations
- **Validation**: ensure sockets are never empty by auto-inserting literal defaults; fallback to zero when expression is missing.
- **Serialization**: confirm `TurtleEditorState` string output remains deterministic by using expression renderings.
- **Compatibility**: existing programs should load by wrapping stored numeric values into literal reporter nodes when migrating from old structure.
- **Styling**: update CSS for reporter shapes, socket drop states, and color palette tokens.

This design enables flexible expression building while preserving compatibility with the existing turtle execution engine and UI expectations.
