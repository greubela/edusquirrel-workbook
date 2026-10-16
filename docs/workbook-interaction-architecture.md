# Interaction models, renderers and editors

The saved interaction value is the source of truth. A shared interaction defines
an exercise; a client renderer connects it to the workbook; an optional standalone
editor changes the supplied value. This guide states the intended dependency
boundaries and names current integration limits explicitly.

## Modules and responsibilities

| Layer | Location | Responsibility |
| --- | --- | --- |
| Domain/interaction | `modules/core/shared` | Saved data, configuration, pure algorithms, validation, grading/scaffolding contracts and cross-platform codecs |
| Platform bridges | `modules/core/js`, `modules/core/jvm` | Platform-specific adapters; the JS `StateHelper` bridge is not JVM code |
| Renderer | `modules/client` | Reactive preview/labels, state binding and editor/fullscreen composition |
| Editor | `modules/client` | Controls and transient drafts/selection; changes the supplied bound `Var` |
| Worker/server | `modules/worker`, `modules/server` | Execute supported shared commands using dependencies available on that backend |

Shared core must work on JVM and Scala.js. Do not import Laminar, browser JSON/XML
APIs, DOM or a client renderer into it. Put reusable domain calculations there;
keep only browser integration and presentation in client. This does not prohibit
client-local UI state. Saved answer types must remain deserializable without the
client. The legacy EVA2 client model is currently adapted to a shared persisted
floor/experiment at that boundary; it is not the desired template for new domain
models.

```mermaid
flowchart TD
    F[Client HtmlRenderFactory] --> R[Client renderer]
    R --> I[Shared interaction]
    R --> E[Optional client editor]
    I --> V[Shared value and requirements]
    I --> H[Shared history and codecs]
    E --> V
    R --> S[Workbook sync and display controls]
```

An editor should be usable outside a workbook: receive its `Var[T]`, domain
requirements/configuration and required services. Observe changes through its Var
signal rather than adding editor-listener callbacks. It should not look up
its interaction, own a second persistence store or call a workbook renderer.
The renderer supplies those inputs and handles placement in the workbook.
A small inline interaction need not introduce a separate editor or fullscreen.

## Element types and state

[WorkbookElement.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/abstractions/WorkbookElement.scala)
defines the common ID, children and definition factory:

- `WorkbookDisplayElement` displays content, normally resolved by a language-map ID.
- `WorkbookStructureElement[T]` groups elements; workbook, sections and exercise
  containers are examples. `TwoColumnPanel` and `ExerciseGroup` also expose children
  for normal rendering/serialization; their current base type is display.
- `WorkbookInteractionElementFull[T, GA, SA]` owns the saved value, typed grading and
  scaffolding payloads, `defaultValue`, `serializerInteractionContent`,
  `gradingElements` and `scaffoldingElement`.
- `WorkbookInteractionElement[T]` is the compatibility alias for
  `WorkbookInteractionElementFull[T, Any, Any]`; `WorkbookInteractionUntyped` is the
  existential type used when traversing mixed interactions.

The shared `InteractionVariable[T, GA, SA]` owns history and current value through
`State`. The renderer bridges a bound state to a browser `Var` with `StateHelper`.
[State and serialization](state-and-serialization.md) provides complete binding,
`bimap`, observer-lifetime, update-importance and codec examples. The interaction's
`defaultValue` is an initializer, not another live answer. History synchronization
is destination/strategy-dependent; it is not an unconditional database write for
every keystroke.

## Renderer → editor example

This is the current QR pattern: one bound value is used by the preview and editor.
The renderer owns the workbook controls, while `QrCodeEditor` accepts the value and
requirements. Inside `CreateQrCodeInteractionRenderer.createRendering`:

```scala
val state = element.interactionVariable
  .createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR)
  .toAirstreamVar
val open = HtmlButtonElement.withTextLabel("basic/OpenEditor", _ =>
  fullInfo.displayControl.setFullscreen(QrCodeEditor(state, element.requirements)))
AtomarLineRendering.cardLine(element, List(
  ElementCard(LanguageMapContentId("basic/openEditor"), open.getDomElement()),
  ElementCard(LanguageMapContentId("basic/qrTitle"),
    QrCodeView.preview(state.signal, element.requirements))
))
```

Read the [complete renderer and imports](../modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/interactionRenderer/qr/CreateQrCodeInteractionRenderer.scala).
`HtmlRenderFactory` dispatches the model to a `LineBasedRenderingFactory`, which
produces an `AtomarLineRendering`. Fullscreen uses `displayControl.setFullscreen`
with an `HtmlAppElement`; editors owning work implement the appropriate
mount-owned cleanup. Closing removes and unmounts the dialog child. Reopening
creates a new mounted editor from current state; there is no fullscreen lifecycle
interface or retained hidden editor. Native resources and subscriptions belong to
Laminar owners.

Local incomplete numeric/text drafts may differ from the saved value while
validation is pending. Decide explicitly whether drafts are persisted: QR keeps
invalid text local, whereas answer tables intentionally save entered strings.
Document close/reset behavior for each editor.

## Grading and scaffolding

Shared core supplies generic grading/scaffolding contracts. An interaction has a list of
`WorkbookInteractionGrader[T, GA]` and at most one
`WorkbookInteractionScaffolder[T, SA]`. Use concrete serializable payload types for
new typed integrations; additional data belongs in `GA`/`SA`, not a subclass of
`GradedState`.

A grader's `gradeStateSafe(state, forceGrading)` returns
`Future[GradingResult[T, GA]]`. Status is `Correct`, `PartiallyCorrect`, `Incorrect`,
`TestError` or `NotExecuted`. Importance is `REQUIRED_TEST`, `OPTIONAL_TEST` or
`HIDDEN_TEST`; `FeedbackEntity` identifies the producer. `RUN_ALWAYS` executes on
a grading request; `RUN_ONLY_MANUALLY` returns `NotExecuted` unless forced.
A result includes the exact graded value, optional localized feedback/additional
data and its timestamp. One grader is one reported result; make independent tests
separate graders if they require separate feedback.

`InteractionVariable.startGrading(syncControl, forceGrading)` collects results into
`GradedState[T, GA](state, testsWithResults)` and stores them by value in history.
`updateStateFromUserInteraction` requests grading; the simple bound-value setters
currently store edits without calling `startGrading`. Importance alone does not
schedule grading or debounce expensive tests. `SlideshowGrader` is an implementation
example; an interaction must actually attach a grader to `gradingElements` to run it.
Register new grader codecs in `WorkbookInteractionGrader.universalSerializer`.

The scaffolder contract is `generateFeedback(currentState):
Future[ScaffoldingResult[T, SA]]`, with feedback tied to the source value.
The generic automatic invocation/display pipeline is not currently wired.
`InteractionInfoPanel` renders synchronization status but its generic grading icon
is empty. Existing task-specific requirement feedback and the separate GPT/chat
or feedback-demo elements do not imply a complete generic grading/scaffolding UI.
These are specific integration limits, not evidence that the worker backend or
shared grading/result models are unfinished.

## Localization and presentation

Use `LanguageMapContentId` for authored instructions and renderer labels. Maps live
in `resources/languageMaps/eva/<group>/map-<language>.json`, with universal content
where appropriate. `LanguageMapStorageControl` registers sources;
`laminarHelper.plaintextStringSignal(id)` follows the selected language and fallback.
`contentIdStringSignal(id, PLAINTEXT_UNDERSCORE_REPLACABLE, replacements)` supplies
underscore substitutions. Avoid taking a one-time language snapshot for controls
that must update after a language switch.

```scala
val title = LanguageMapContentId("basic/openEditor")
h3(text <-- laminarHelper.plaintextStringSignal(title))
```

Put static layout/color rules in dedicated `homepage/css` files, reusing
`generic/colors.css` and `generic/dimensions.css` tokens. Laminar binds semantic
classes and state. Canvas/SVG boundaries may need computed coordinates/style values;
self-contained exports must retain their appearance outside the workbook. Those
values do not justify arbitrary duplicated palettes or static inline layout rules.

## Dependencies and execution

Prefer a few reusable frameworks and cross-platform libraries. Browser programs
should normally be versioned, complete distributions under `resources/programs`;
resolve assets through `fullInfo.contentControl.fileFactory.relativeToTechnicalResources`.
CodeMirror currently uses pinned CDN modules with an offline test bundle, so
vendoring all editor assets is an architectural target, not an accomplished fact.
Snap and JSXGraph have bundled distributions.

Put optional dependency readiness/loading coordination in Scala.js companions or
services. Return futures, share loads, render an editor-local loading/fallback UI,
and let only consumers await readiness. Do not hold workbook startup behind a
chain of optional editor downloads. CodeMirror follows this pattern; Snap has
readiness hooks but its entry-page bootstrap still loads bundled scripts.
The target of only app/worker bundles in HTML is not yet universal.

The generic execution backend is implemented. Choose local, HTTP/server or worker
execution according to required resources; a main-thread `Future` alone does not
make CPU work parallel. See [execution clients](execution-clients.md) for complete
command definitions/calls and [worker execution](worker-execution-backend.md) for
supported jobs. [Cache/startup](laminar-workbook-catalog-and-cache.md) documents
implemented IndexedDB restoration and independent refresh.

## Current examples and authoring checklist

| Example | Reusable pattern / current boundary |
| --- | --- |
| [QR](qr-code-interaction.md) | Shared symbol/requirements, bound preview and standalone fullscreen editor |
| [Mail](email-simulator.md) | Shared mailbox transitions, local compose drafts and supplied editor state |
| [Compression](compression-workbook.md) | Shared saved experiment data with client controls/playback |
| Choice, answer tables, pixels, Unicode | Inline controls need no separate fullscreen editor |
| [Embroidery](embroidery-preview-workbook.md) | `TurtleRecreateShapeInteraction` stores `ProgrammingState` and target/palette/policy; drawing comparison is not a complete generic grader |
| Evacuation | Shared saved floor/experiment with a client adapter to the legacy EVA2 simulation |
| [SQL](sql-editor.md) | Shared answer/configuration and typed server execution; JDBC is server-only |
| [Eva](eva-editor-programming-state.md) | One active editor, central Var, asynchronous conversions and downstream panels |

For turtle recreation, the current base is `WorkbookInteractionElement[ProgrammingState]`.
`limitTurtleCommandUsage` remains authored configuration, but the current renderer
neither passes it to the editor nor enforces it. Do not describe visual comparison
as complete task grading. For mail, the shared writing-practice model `MailEditor`
and client UI `MailEditor` have different responsibilities despite sharing a name.

When adding an interaction, define shared data/requirements/codecs, register its
factory and renderer, bind one saved state and introduce an editor only when useful.
Test shared behavior/round trips on both platforms, local draft/lifecycle logic in
client, and actual persistence/fullscreen/invalid-draft behavior in browser checks.
The [test guide](core-package-unit-tests.md) and
[serialization inventory](testing-and-serialization-audit.md) provide current commands.

### CodeMirror configuration and asynchronous loading

`CodeMirrorEditor` accepts `config: Var[CodeMirrorEditorConfig]`. The regular
configuration omits the control-flow gutter; `showFlowchartGutter = true` enables
it for supported Python programs. `EvaEditorConfig.codeMirrorConfig` supplies the
settings to the standalone tab adapters. Java keeps its regular line-number
gutter. The former `CodeMirrorEditorFlowchart` wrapper is no longer needed.
Eva's generic navigation and downstream panel boundaries are described in
[Eva editor architecture](eva-editor-programming-state.md).

`CodeMirrorEditorLoading` coordinates one shared module load in Scala.js. It
resolves the module beside `app-loader.js`, including deployments under a path
prefix, and shares one asynchronous module script between startup warmup and mounted editors.
Entry pages do not eagerly import CodeMirror: CDN latency does not delay
`DOMContentLoaded` or workbook startup. The textarea remains bound to the document
while loading, and enhancement uses the latest text and diagnostics. Network
failure or a 15-second timeout retains the textarea. Mount ownership checks
prevent a late response from creating an editor in an obsolete language tab.
Module loading and editor creation yield through a browser timer; no other
workbook action awaits them. DOM construction necessarily
runs on the browser thread, rather than in a Web Worker.

Run `npm run test:workbooks -- codemirror` for delayed/failed imports, stale mounts,
shared loading, text preservation, and gutter alignment. The browser runner bundles
the production modules with their pinned package versions for offline tests.
