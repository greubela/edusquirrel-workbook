# Interaction models, renderers and editors

An interaction defines an exercise and owns its saved answer. Its renderer binds
that answer to workbook controls; an optional editor changes the supplied value.
Keep the saved answer as the single source of truth.

## Responsibilities and locations

| Layer | Location | Responsibility |
| --- | --- | --- |
| Model | `modules/core/shared/.../workbook/` | Exercise definitions, saved values, requirements, grading/scaffolding and codecs |
| Renderer | `modules/client/.../homepage/workbook/htmlRenderer/` | Workbook placement, localization, previews and editor composition |
| Editor | `modules/client/.../homepage/webElements/editor/` | Controls and transient drafts, bound to a supplied `Var[T]` |
| Platform adapters | `modules/core/js/`, `modules/core/jvm/` | Browser/JVM integration kept outside shared models |
| Execution | `modules/worker/`, `modules/server/` | Commands requiring background computation or server resources |

Shared models must run on JVM and Scala.js without DOM, Laminar or renderer
dependencies. Editors should also work outside a workbook: accept the answer,
configuration and services rather than looking up an interaction or owning a
second persistence store. Small inline interactions need no separate editor.

[WorkbookElement.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/abstractions/WorkbookElement.scala)
separates display elements, structural containers and interactions.
`InteractionVariable[T, GA, SA]` owns the answer and its history; `GA` and `SA`
are concrete serializable grading/scaffolding payloads. `defaultValue` initializes
the answer; it is not another live state. See [state and serialization](state-and-serialization.md).

## Renderer and editor boundary

The renderer registers with `HtmlRenderFactory`, binds the interaction's saved
state and supplies that binding to its controls, preview and optional editor.
For example, the [QR renderer](../modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/qr/CreateQrCodeInteractionRenderer.scala)
shares one value between preview and fullscreen editor:

```scala
val state = element.interactionVariable
  .createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR)
  .toAirstreamVar
val editor = QrCodeEditor(state, element.requirements)
val preview = QrCodeView.preview(state.signal, element.requirements)
```

Create only the selected editor. Closing fullscreen unmounts it; native handles
and subscriptions belong to that mount. Reopening starts from the saved answer.
Decide explicitly which incomplete drafts are saved: QR keeps invalid text local,
whereas answer tables save entered strings.

## Grading and scaffolding

An interaction attaches independent `WorkbookInteractionGrader[T, GA]` checks and
at most one `WorkbookInteractionScaffolder[T, SA]`. Results belong to the exact
answer assessed, with localized feedback and optional typed additional data.
Keep grading policy explicit: ordinary bound-value edits do not automatically
run graders, and expensive LLM grading runs only on request.

The standard interaction info panel provides manual hints and invalidates their
display when the answer or language changes. Grading display is interaction-specific.
Synchronization includes feedback updates even when the answer itself is unchanged.

`TextInputWithScaffoldingAndGrading` owns its description, string answer, hint IDs
and grading-criterion IDs. Each LLM grader evaluates one criterion in the selected
language and identifies itself as `FeedbackEntity.LlmEntity`. The final marker
(`CORRECT`, `INCORRECT`, `PARTIALL_CORRECT`) becomes the grade and is omitted from
plaintext feedback. One classification-only retry is allowed for a missing marker;
another invalid response is a grading error. Transcripts are typed additional data.

## Authoring examples

| Example | Architectural pattern |
| --- | --- |
| [QR](qr-code-interaction.md) | Shared requirements, bound preview and standalone fullscreen editor |
| [Turtle recreation](embroidery-preview-workbook.md) | `TurtleRecreateShapeInteraction` owns a program and target drawing; the renderer composes the programming editor and comparison preview |
| Text feedback | Inline text editor and standard hint controls; see [the model](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/gpt/TextInputWithScaffoldingAndGrading.scala) and [renderer](../modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/basic/TextInputWithScaffoldingAndGradingRenderer.scala) |

Turtle drawing comparison is not a complete generic grader; command-usage limits
remain an integration gap. The text-feedback example is in the test workbook's
“Text answers with hints and AI feedback” section, in German and English.

## Presentation, dependencies and registration

- Store authored text in `resources/languageMaps/eva/<group>/`; use language signals
  for controls that must update when the selected language changes.
- Put styles in dedicated `homepage/css/` files and reuse shared color/dimension
  tokens. Laminar supplies semantic classes and changing state.
- Keep native dependency readiness and typed handles in core JS's
  `it.evadid.facades`. Optional preloading must not block the initial page render.
- Register new interaction factories and renderers, plus grader codecs in
  `WorkbookInteractionGrader.universalSerializer`.
- Test shared behavior/codecs on JVM and Scala.js, and browser workflows involving
  persistence, language changes and editor lifetimes.

Related guides: [browser loading](browser-startup-loading.md),
[Eva editor architecture](eva-editor-programming-state.md),
[execution clients](execution-clients.md), [workers](worker-execution-backend.md),
[cache/startup](laminar-workbook-catalog-and-cache.md) and
[tests](core-package-unit-tests.md).
