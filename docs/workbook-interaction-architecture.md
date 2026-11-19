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
`WorkbookInteractionElement[F <: InteractionFormat]` declares its saved format.
`InteractionVariable[F]` owns its answer and event history; the format supplies
the state and feedback codecs. `defaultValue` initializes
the answer; it is not another live state. See [state and serialization](state-and-serialization.md).

## Renderer and editor boundary

The renderer registers with `HtmlRenderFactory`, binds the interaction's saved
state and supplies that binding to its controls, preview and optional editor.
For example, the [QR renderer](../modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/qr/CreateQrCodeInteractionRenderer.scala)
shares one value between preview and fullscreen editor:

```scala
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.webElements.editor.qr.{QrCodeEditor, QrCodeView}
import it.evadid.workbook.elements.interactionElements.qr.CreateQrCodeInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

def qrControls(element: CreateQrCodeInteraction, fullInfo: FullInfo): Element = {
  val answer = element.interactionVariable
    .createBoundStateWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation)
    .toAirstreamVar
  div(
    QrCodeView.preview(answer.signal, element.requirements),
    button("Edit", onClick --> (_ =>
      fullInfo.displayControl.setFullscreen(QrCodeEditor(answer, element.requirements))))
  )
}
```

Create only the selected editor. Closing fullscreen unmounts it; native handles
and subscriptions belong to that mount. Reopening starts from the saved answer.
Decide explicitly which incomplete drafts are saved: QR keeps invalid text local,
whereas answer tables save entered strings.

## Grading and scaffolding

An interaction attaches independent `WorkbookInteractionGrader[T, GA]` checks and
at most one `WorkbookInteractionScaffolder[T, SA]`. Results belong to the exact
answer assessed, with localized feedback and optional typed additional data.
`InteractionVariable` requests automatic grading on history updates; each grader's
`runningBehavior` decides whether it executes. LLM grading and hints require an
explicit request through the shared info panel.

Pending and completed computations are cached by answer, independently of display
language. A language change reuses them; result merges retain available feedback
translations. Synchronization includes feedback updates even when the answer is unchanged.

`InteractionInfoSidebar` and `GradingInfoLine` share the core
`GradingSummary`: the score counts fully correct tests, including hidden tests;
partial results belong to the incorrect group. Hidden results contribute counts
but never detail rows. Public descriptions and localized feedback are rendered
as text; internal test programs and LLM instructions are not displayed.

`fullInfo.signals.interactionReporters` holds one registry per loaded workbook
instance. `reportersFor(element)` returns its shared `grading` and `sync` reporters;
icons, inline panels, sidebars and editors consume these reporters rather than
constructing their own. Lookup rejects elements from another workbook instance,
even if their IDs and definitions match.

`GradingReporter.report` derives one current-answer snapshot from history updates.
Its `summary`, `scaffolding` and `hintFeedback` signals share that snapshot.
The core `GradingSummary` owns test grouping, counts and aggregate status;
`ScaffoldingReport` owns hint status. Views choose translated labels, icons and CSS.
For example:

```scala
val reporters = fullInfo.signals.reportersFor(exercise)
val score = reporters.grading.summary.map(_.score)
val syncStatus = reporters.sync.report.map(_.status)
```

Registry entries survive language, display, section and user changes. Replacing or
closing the workbook replaces the registry. Reporter signals subscribe while
observed, detach when their final observer leaves, and read current history when
observed again. `SyncReporter` follows the controller's replaceable environment
binding and reset state, classifying configured destination confirmations as
disabled, unsynced, partial or synced. Retired requests cannot confirm writes in a
replacement binding; old bindings retain their captured credentials for flushing.

Below-exercise feedback panels show completed hints in an initially expanded,
collapsible section, including hint-only exercises. `InteractionHintPanel` consumes
the shared reporter's `hintFeedback`; language changes update its text, and changing
the answer hides stale hints until matching feedback is available.

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

`TurtleGradingLogic` supplies the policy-aware line comparison used by both
`RecreateShapeGrader` and the colored Turtle preview. Command-usage limits remain
an integration gap. The text-feedback example is in the test workbook's
“Text answers with hints and AI feedback” section, in German and English.

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
