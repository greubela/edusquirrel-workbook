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
not shared core. A renderer obtains a bound state from the interaction:

```scala
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

def answerInput(element: TextInteraction, fullInfo: FullInfo): Element = {
  val answer = element.interactionVariable
    .createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR)
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
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingStatePythonString

def pythonView(program: Var[ProgrammingState]): Var[String] =
  program.bimap(_.toPython.code)(ProgrammingStatePythonString(_))
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

## History, importance and destinations

`InteractionVariable[T, GA, SA]` stores `InteractionVariableHistory[T, GA, SA]`:
answer events with timestamps/importance, grading results keyed by answer, and
scaffolding results keyed by answer. `currentValue` is derived from history;
`defaultValue` initializes it. Update the bound state instead of writing directly
to IndexedDB or the remote database.

Use `createBoundStateWithUpdateLogic` when relevance depends on the edit, such as
a text renderer distinguishing intermediate typing from a completed sentence.
Importance expresses history significance, not a debounce interval or a guarantee
of immediate storage.

| Importance | History role |
| --- | --- |
| `DEFAULT` | Initial value |
| `TEMPORARY` | Transient edit, retained according to the destination strategy |
| `MINOR` | Intermediate edit |
| `MAJOR` | Significant edit |

`SyncControl.requestStore` requests synchronization; it does not guarantee that
an offline or unauthenticated server has committed the value. Each destination's
`SyncStrategy` selects events. Current local defaults use IndexedDB with
`SYNC_LAST_AND_MAJOR`; configured remote destinations use `SYNC_MAJOR`. Reports
and the workbook's cloud indicator describe actual synchronization status.
Importance does not by itself run every grader; see the current grading paths in
[interaction architecture](workbook-interaction-architecture.md).

## Default uPickle codecs

Use uPickle rather than browser-only JSON/XML APIs for shared values:

```scala
import upickle.default.*

sealed trait Animal derives ReadWriter
case class Dog(name: String) extends Animal derives ReadWriter
case class Cat(name: String) extends Animal derives ReadWriter
case class Snapshot[A](value: A) derives ReadWriter

val original: Animal = Dog("Ada")
val restored = read[Animal](write(original))
val snapshot = read[Snapshot[Int]](write(Snapshot(42)))
```

Derivation requires codecs for the fields/type arguments. If a constructor needs
runtime evidence, reconstruct that evidence when decoding rather than serializing
it. [Point.scala](../modules/core/shared/src/main/scala/it/evadid/core/datastructures/geometry/Point.scala)
provides `given [T: Fractional](using ReadWriter[T]): ReadWriter[Point[T]]` and keeps
its established wire format. Do not replace such codecs blindly with derivation.

Keep canonical codecs for library types in
[DefaultSerializer](../modules/core/shared/src/main/scala/it/evadid/core/util/io/serializer/DefaultSerializer.scala)
and import its givens where required:

```scala
import java.time.LocalDateTime
import it.evadid.core.util.io.serializer.DefaultSerializer.given
case class DatedAnswer(text: String, at: LocalDateTime) derives ReadWriter
```

Owned domain types normally expose a default codec in their companion or through
`derives ReadWriter`. Avoid per-call-site codecs that encode the same type
incompatibly. Live futures, callbacks, controllers, database/DOM handles and
subscriptions need explicit snapshots, not constructor-only derived persistence.

## Definitions, answers and snapshots

The exercise definition, learner answer and live controller are different things:

- `associatedFactory` serializes the authored definition and element references.
  Register a new element in `WorkbookElementFactory` as well as `HtmlRenderFactory`.
- `serializerInteractionContent` handles the learner value. History travels as
  typed history JSON with answer events and grading/scaffolding maps.
- The default `ReadWriter[WorkbookElement]` stores a self-contained element registry,
  including children and section prerequisites. It resolves references through the
  factories and rejects conflicting IDs. A reference-only element JSON needs its
  registry; it is not automatically a self-contained workbook snapshot.

Open hierarchies need a deliberate codec/registry for supported implementations.
`WorkbookElementFactory` and `WorkbookInteractionGrader.universalSerializer` are
existing examples. Adding a type to a hierarchy without registering its codec can
compile but fail during decoding on another execution backend.

Test JSON and MessagePack round trips on both core platforms, defaults and malformed
payloads, and behavior after restoring mutable snapshots. The maintained
[serialization audit](testing-and-serialization-audit.md) explains the inventory and
explicit exceptions.
