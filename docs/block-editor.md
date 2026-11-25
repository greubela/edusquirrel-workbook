# Block editor

Eva's **Blocks** tab edits the language-neutral expression representation
(`ProgrammingStateBeExpression`, language `BlockDisplay`) as nested blocks.
Its editor, [`BlockProgramEditor`](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/blocks/BlockProgramEditor.scala),
works on a `Var[BeProgram]`; the Eva tab binds it to the central answer with `bimap`:

```scala
val program = state.bimap {
  case ProgrammingStateBeExpression(expression) => BeProgram(expression)
  case _ => BeProgram.empty // inactive while Eva switches to another language
}(edited => ProgrammingStateBeExpression(edited.fullProgram))
BlockProgramEditor(program, BeBlockLibrary.turtle, labels.blocks, humanLanguage)
```

Every edit replaces the program in that Var. Drag state, the view options and the
value draft are the editor's transient `Var`s. Other tabs convert from the same
`BeExpression`; there is no Python or regex step between the blocks and the drawing.

## Editing (core, `vm.editing`)

| Type | Responsibility |
| --- | --- |
| `BeProgramEditing` | Pure edits addressed by `BeNodePath` (child roles from the root): slots, `insert`, `move`, `remove`, `replace`. Slots enforce sequence capacity and required types; a statement cannot move into itself. |
| `BeValueInput` | Parameter, repeat-count and assigned values. Typed positions accept literals (`true`, `2024-05-01`); otherwise the text is parsed with `SourceParsing`, calls are bound to the program's own functions, and `BeProgramInterpreter.foldConstants` computes operators on literals (`3+5` → `8`, `add(3+5, 3)` → `add(8, 3)`). The result must fit the expected type; unknown variables are rejected. |
| `BeBlockLibrary` | Templates parsed from Python, so inserted blocks convert and execute like typed code. |

## Drawing (core, `vectorShapes.blocks`)

`BeExpressionBlocks.program` builds an `AppShapeElement` tree from the expression:
`BlockArrangement`s (around-child, rows, columns, sequence rows, fixed) lay out
`BlockPaths` outlines and centered text; `FlowLayout` draws the control-flow
overlay (`FlowShape` per row, nested via a stack). Elements carry a typed
`BlockRole` (`Statement`, `Slot`, `Value`) that the client uses for hit testing.
`BlockHighlight` recolours slots while dragging; layout does not depend on it.
The CodeMirror flowchart gutter uses the same `FlowLayout`.

The drawings reproduce the former legacy block shapes: geometry, text positions
and colours are pinned by `BlockShapeFixtureSpec` against recorded legacy output
(`modules/client/src/test/resources/blocks/legacy-block-drawings.json`). Blocks are
drawn one control-flow segment below the overlay origin so that both align.

## Interaction

Templates are dragged from the library into the nearest accepting slot (pointer
events, so touch works too); program statements move between slots or are removed
by dropping them on the library. Enter or Space on a focused template appends it.
Clicking a value opens a popup whose Apply is disabled while the text is rejected.

Tests: `BeProgramEditingSpec`, `BeExpressionBlocksSpec` (shared core),
`BlockProgramEditorSpec`, `BlockShapeFixtureSpec` (client, jsdom) and
`tools/dev/block-editor.browser.test.mjs` (Chromium).
