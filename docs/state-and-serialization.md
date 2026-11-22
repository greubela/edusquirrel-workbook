# State, bindings and serialization

The interaction's saved value is the source of truth. Put its type, transitions,
validation and codecs in `modules/core/shared`, so browser, worker and JVM code
can use the same contract. Client-only `Var`s are appropriate for focus, selection,
invalid drafts and other transient UI state; they are not a second answer store.
See [interaction architecture](workbook-interaction-architecture.md) for the layers.

## Changing client state

All changing client application, editor and lifecycle state must live in
`val`-bound Airstream `Var[T]` values. Use `now()` to read a snapshot and
`set`/`update` to change it. Do not use Scala `var` fields, captured mutable locals,
or a `val` holding a mutable collection as an alternative. This includes counters,
request identities and native handles;
it applies even when a value is not directly rendered. Keep values inside a `Var`
immutable and derive other values through signals rather than duplicating state.

```scala
private val requestGeneration = Var(0L)

def invalidate(): Unit = requestGeneration.update(_ + 1)
val canRun = answer.signal.map(_.nonEmpty)
```

A `Var` write made inside an Airstream observer is queued in a child transaction;
an immediate `now()` read can still return the previous value. Prefer derived
signals and pass immutable snapshots through a calculation. Editor commands that
must flush a native draft and then read several updated Vars (tab navigation and
Run) start through the Scala.js execution queue after the click transaction. This
is event ordering, not background-thread execution.

Keep temporary calculations immutable with maps, folds or tail recursion. Do not
introduce Airstream into shared core to satisfy a browser state convention: shared
models and their transitions remain immutable and platform independent. Native
browser/editor APIs own their internal state; Scala keeps references to those
handles in `Var`s and releases them through the owning lifecycle.

## Shared State and browser Var

`State[T]` and `ObservableValue[T]` are shared abstractions. Airstream/Laminar
`Var[T]`, `Signal[T]` and DOM bindings belong to JavaScript. Their bridge lives in
[StateHelper.scala](../modules/core/js/src/main/scala/it/evadid/core/datastructures/state/StateHelper.scala),
not shared core. `fromAirstreamVarToState(var, owner)` adapts the original store,
without mirroring its value. Functional updates, including updates through `biMap`,
compose in the Var's transaction queue. Releasing the owner detaches observations;
a pending operation can still read and update its original store.

A renderer obtains a bound state from the interaction:

```scala
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

def answerInput(element: TextInteraction, fullInfo: FullInfo): Element = {
  val answer = element.interactionVariable
    .createBoundStateWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation)
    .toAirstreamVar
  div(
    textArea(value <-- answer.signal, onInput.mapToValue --> answer.writer),
    p(text <-- answer.signal)
  )
}
```

Pass this same `Var` to an editor and observe its signal for previews. Prefer
`text <--`, `child <--` and `children <--` to rebuilding the workbook imperatively.
Use mount-owned subscriptions for side effects, so closing a component cancels its
observation:

```scala
def observedAnswer(answer: Var[String]): Element = div(
  onMountBind(_ => answer.signal --> (value => org.scalajs.dom.console.log(value)))
)
```

`FullInfo` is the client context. It owns
`homepageInfoState: Var[HomepageInfo]` for the selected workbook, user and display
state. Renderers use its controls/signals; standalone editors receive their value,
configuration and required services instead of reaching into that global context.

## Two-way views and external editors

Use `bimap` when the two conversions express a valid view of the same value:

```scala
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonStructured

def pythonView(program: Var[ProgrammingState]): Var[String] =
  program.bimap(_.toPythonStructured.toString)(code => ProgrammingStatePythonStructured(code))
```

This pattern is suitable when the selected editor is bound to its native
representation. Eva converts the central answer before replacing its editor;
there are no retained inactive editor drafts or separate edit events. See
[Eva programming state](eva-editor-programming-state.md).

Every change to a Var has the same meaning. Observe its signal instead of adding
listener constructor arguments such as `onStateEdited` or `handleFunctionInput`.
Do not store a second answer or synchronization baseline to classify a change as
an edit or a restoration. Native library adapters can compare against their live
document to avoid unnecessary redraws, but that comparison is not a second
persistent state or an edit classification.

`state.fingerprint()` identifies the
stored representation; it is not a general semantic-equivalence test.
[SnapProjectXmlSync](../modules/client/src/main/scala/it/evadid/homepage/webElements/editor/code/SnapEditor/SnapProjectXmlSync.scala)
currently compares cleaned XML and handles runtime-pose metadata. The adapter's
subscriptions, polling and cleanup belong to its Laminar mount lifecycle.

`State.biMap` composes pure forward/reverse functions against its backing store.
Core `State` transitions are synchronous; a Var-backed view follows Airstream's
transaction ordering.

Shared core `AsyncData` mappings and recoveries run sequentially in source order,
including across failures. A pending computation delays later inputs; its late
result cannot overwrite a newer result. The first-result future retains its first
finished state while the observable continues to update.

## Typed event histories

`InteractionVariable[F]` owns an immutable `InteractionVariableHistory[F]` in core
`State`. Its current answer, grading and scaffolding views are projections of
answer/start/completion events. The format singleton provides all associated
codecs; see [interaction formats](interaction-format.md) for the types and wire
contract. Update a bound state instead of writing directly to storage.

`createBoundStateWithUpdateLogic` chooses significance from each edit. Importance
is not a debounce interval or a guarantee of immediate persistence:

| Importance | Meaning |
| --- | --- |
| `DEFAULT` | Initial answer |
| `TEMPORARY` | Transient edit or in-progress feedback |
| `MINOR` | Intermediate edit |
| `MAJOR` | Significant edit or completed feedback |

`EventSyncStrategy` selects events to transmit and never prunes persisted history.
`SYNC_EVERYTHING` sends all missing events. `SYNC_MAJOR` selects major events plus
the current answer and all feedback associated with selected answers, including
pending lifecycle events. Other importance strategies filter individual events;
latest/count strategies retain timestamp ties. `Since` includes its boundary.
`clean()` removes temporary events only when explicitly called; synchronization
uses the destination strategy.

Grading and scaffolding run through `interactionVariable.control`. Pending requests
are shared; explicit retries rerun execution errors and unexecuted tests while
reusing completed assessments. Feedback stays associated with its captured answer.
Grading starts/completions are projected by configured grader identity; a newer
start represents a running retry and a completion wins a timestamp tie. Language
changes do not repeat computations; multilingual feedback merges into `LanguageMap`.

User changes and full resets rotate the feedback session and replace computation
caches. Completion callbacks check their generation and captured context before
publishing. A same-user `resetLocalHistory` keeps completed computations reusable.
Captured contexts retain the initiating user's backend token and LLM settings.

## Synchronization boundary

Renderers update their bound value; `InteractionVariable.updateHistory` registers
writes through the captured `ExecutionContextInformation.syncControl`. To await
all configured destinations from client code:

```scala
import scala.concurrent.Future

def syncWorkbook(fullInfo: FullInfo): Future[Unit] =
  fullInfo.syncControl.ensureSynced()
```

`ensureSynced` fetches and stores with forced buffer flushes. Read failures still
allow pending writes to be attempted, then fail the returned future. Production
bindings, event cursors, bulk requests and reset behavior are documented in
[history storage and synchronization](database/interaction-history.md).

## Codecs and element definitions

Use concrete models and derived uPickle codecs; do not add `ReadWriter[Any]` or
serialize arbitrary values with `toString`. A genuinely dynamic payload may use
`ujson.Value`, but history logic operates on typed events, not JSON fields.
Formats without additional grading/scaffolding data use `Unit`.

`WorkbookElementFactory` remains responsible for element definitions. The default
`ReadWriter[WorkbookElement]` includes the registry needed to resolve children and
section prerequisites and rejects conflicting IDs. A reference-only definition
requires that registry. Format-based history exchange does not.

Test JSON and MessagePack roundtrips on both core platforms, malformed inputs,
state transitions and behavior after restoration. Keep current schemas strict;
do not add migration readers or aliases for old fields and names.
