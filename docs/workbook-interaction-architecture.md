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
second persistence store. Small inline interactions need no separate editor; add one only when a
task needs a sustained workspace (mail triage, programming, simulation), and reuse text, choice,
table and ordering interactions first.

[WorkbookElement.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/abstractions/WorkbookElement.scala)
separates display elements, structural containers and interactions.
`WorkbookInteractionElement[F <: InteractionFormat]` declares its saved format and holds no
state. Its answer and event history live in the page's `InteractionInfo`, changed through
`fullInfo.interactions`; the format supplies the state and feedback codecs. `defaultValue`
initializes the answer; it is not another live state. See [state and serialization](state-and-serialization.md).

## Renderer and editor boundary

The renderer registers with `HtmlRenderFactory`, binds the interaction's saved
state and supplies that binding to its controls, preview and optional editor.
For example, the [QR renderer](../modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/qr/CreateQrCodeInteractionRenderer.scala)
shares one value between preview and fullscreen editor:

```scala
import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.editor.qr.{QrCodeEditor, QrCodeView}
import it.evadid.workbook.elements.interactionElements.qr.CreateQrCodeInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

def qrControls(fullInfo: FullInfo, element: CreateQrCodeInteraction): Element = {
  val answer = fullInfo.interactions.boundVar(element, UpdateImportance.MAJOR)
  div(
    QrCodeView.preview(fullInfo, answer.signal, element.requirements),
    button("Edit", onClick --> (_ =>
      fullInfo.displayControl.setFullscreen(QrCodeEditor(fullInfo, answer, element.requirements))))
  )
}
```

Renderers, editors and UI elements receive the page's `FullInfo` as an explicit
first parameter; only the composition root (`HtmlFullWorkbookApp`, startup) holds
the page instance. `AtomarLineRendering.editorCardLine` builds the common
"preview + open editor" line.

Create only the selected editor. Closing fullscreen unmounts it; native handles
and subscriptions belong to that mount. Reopening starts from the saved answer.
Decide explicitly which incomplete drafts are saved: QR keeps invalid text local,
whereas answer tables save entered strings.

## Grading and scaffolding

An interaction attaches independent `WorkbookInteractionGrader[T, GA]` checks and
at most one `WorkbookInteractionScaffolder[T, GA, SA]`, which reads the recorded latest
answer and gradings as `ScaffoldingEvidence`. Results belong to the exact answer assessed,
with localized feedback and optional typed additional data. Add automated
feedback only where the task has a defensible grading contract.

`WorkbookInteractionGrader.shouldRunGrader(forceGrading, importance)` runs a grader when
grading was requested (the check button) or when its `runningBehavior` (`RUN_ALWAYS`,
`RUN_ON_AT_LEAST_MINOR`, `RUN_ONLY_MANUALLY`) accepts the answer's `UpdateImportance`.
Choices, table cells, matchings and reorder tasks are graded on request; slideshow visits
automatically. LLM grading and hints run only on explicit request; each LLM grader assesses
one criterion. Programming exercises share `ProgrammingHintScaffolder`.

Choice options and feedback carry `WorkbookContent` (a language-map ID, a translated map
or literal content), resolved by `LaminarRenderHelper.workbookContentSignal` without
flattening the saved model to strings. Grader codecs are registered in the interaction's
`InteractionFormat`. Feedback is recorded per answer, independently of display language;
a language change reuses it and merges feedback translations. Request rules are in
[state and serialization](state-and-serialization.md#answer-state-and-sessions).

Feedback views derive everything from the history; nothing is stored separately:

- `GradingReporter.report` derives one current-answer snapshot; the core `GradingSummary`
  and `ScaffoldingReport` own grouping, counts and status, while views own labels, icons and CSS.
  [`CheckReport`](../modules/core/shared/src/main/scala/it/evadid/workbook/meta/CheckReport.scala)
  derives the learner's checks and the `wrongParts` that renderers mark.
- `fullInfo.reporters.reportersFor(element)` returns the shared `grading` and `sync` reporters of
  one loaded workbook instance. Components consume them rather than constructing their own;
  lookups reject elements of another instance. Reporter signals observe history only while observed.
- [`FeedbackPanels`](../modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/controlElements/interactionInfo/FeedbackPanels.scala)
  is the one component set for both placements: below the exercise in the overview and beside the
  fullscreen editor. Hidden tests appear only as status rows; internal test programs and LLM
  instructions are never displayed.

```scala
val reporters = fullInfo.reporters.reportersFor(exercise)
val score = reporters.grading.summary.map(_.score)
val syncStatus = reporters.sync.report.map(_.status)
```

Costly drawings use [`DeferredRendering`](../modules/client/src/main/scala/it/evadid/homepage/webElements/basic/DeferredRendering.scala):
they redraw only after the answer has rested and keep the previous drawing, dimmed, meanwhile.
Step replays (`htmlRenderer/interactionRenderer/steps/`) keep only the selected step in
`StepNavigator` and derive everything else from the answer.

## Authoring examples

| Example | Architectural pattern |
| --- | --- |
| [QR](qr-code-interaction.md) | Shared requirements, bound preview and standalone fullscreen editor |
| [Turtle recreation](embroidery-preview-workbook.md) | `TurtleRecreateShapeInteraction` owns a program and target drawing; the renderer composes the programming editor and comparison preview |
| Text feedback | Inline text editor and standard hint controls; see [the model](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/gpt/TextInputWithScaffoldingAndGrading.scala) and [renderer](../modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/basic/TextInputWithScaffoldingAndGradingRenderer.scala) |

The text-feedback example is in the test workbook's “Text answers with hints and AI
feedback” section.

## Presentation, dependencies and registration

- Store authored text in `resources/languageMaps/eva/<group>/`; use language signals
  for controls that must update when the selected language changes.
- Put styles in dedicated `homepage/css/` files and reuse shared color/dimension
  tokens. Laminar supplies semantic classes and changing state.
- Keep native dependency readiness and typed handles in core JS's
  `it.evadid.facades`. Optional preloading must not block the initial page render.
- Register the element factory and `HtmlRenderFactory` renderer. Add grader codecs
  to the formats that allow them in `InteractionFormat`; element-definition codecs
  also use `WorkbookInteractionGrader.universalSerializer`.
- Test shared behavior/codecs on JVM and Scala.js, and browser workflows involving
  persistence, language changes and editor lifetimes.

Related guides: [browser loading](browser-startup-loading.md),
[Eva editor architecture](eva-editor-programming-state.md),
[execution clients](execution-clients.md), [workers](worker-execution-backend.md),
[cache/startup](laminar-workbook-catalog-and-cache.md) and
[tests](core-package-unit-tests.md).
