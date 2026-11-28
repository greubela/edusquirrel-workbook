# State, bindings and serialization

The saved answer is the source of truth. Put its type, transitions,
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

A renderer obtains a bound `Var` with `fullInfo.interactions.boundVar(element, importance)`;
the [QR example](workbook-interaction-architecture.md#renderer-and-editor-boundary)
shares it between preview and editor.

The bound `Var` is a deduplicated view of the page's answers: it reads the element's
current answer and records each write as a user change. It observes only while its
signal has observers, so creating one per mount leaks nothing. Pass this same `Var` to
an editor and observe its signal for previews. Prefer
`text <--`, `child <--` and `children <--` to rebuilding the workbook imperatively.
Use mount-owned subscriptions for side effects, so closing a component cancels its
observation:

```scala
def observedAnswer(answer: Var[String]): Element = div(
  onMountBind(_ => answer.signal --> (value => org.scalajs.dom.console.log(value)))
)
```

`FullInfo` is the client context. `HomepageControl` owns the `Var[HomepageInfo]`
(workbook, user, display, language); the core `InteractionController` owns the answers
(`InteractionInfo`) in a core `State`. `HomepageState` performs changes that concern
both; see [answer state](#answer-state-and-sessions). Renderers read the answers through
the `InteractionSignals` views (signals and Var views over the controller) and
`fullInfo.signals`; standalone editors receive their value, configuration and required
services instead of reaching into that global context.

## Two-way views and external editors

Use `bimap` when the two conversions express a valid view of the same value:

```scala
import it.evadid.workbook.interactions.programming.state.ProgrammingState
import it.evadid.workbook.interactions.programming.state.ProgrammingStatePythonStructured

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

`state.fingerprint()` identifies the stored representation; it is not a general
semantic-equivalence test.

`State.biMap` composes pure forward/reverse functions against its backing store.
Core `State` transitions are synchronous; a Var-backed view follows Airstream's
transaction ordering.

Shared core `AsyncData` mappings and recoveries run sequentially in source order,
including across failures. A pending computation delays later inputs; its late
result cannot overwrite a newer result. The first-result future retains its first
finished state while the observable continues to update.

## Answer state and sessions

Interactions are exercise definitions without state. The answers of one sync context
(program, workbook, user) form one immutable
[`InteractionInfo`](../modules/core/shared/src/main/scala/it/evadid/workbook/model/InteractionInfo.scala):
per interaction its `InteractionHistory[F]` and what each destination is known to
hold, plus the `SyncConnection` (destinations with credentials, execution context) and a
session identity. An interaction belongs to it by serialization key and instance; another
instance under the same key reads its default answer and its writes are ignored.

The core [`InteractionController`](../modules/core/shared/src/main/scala/it/evadid/workbook/control/InteractionController.scala)
makes every change as a pure transition of its core `State`, and owns the feedback
computations of the current session, the sync phases (store, read, reset with a per-user
pause) and the pending stores. Effects follow the observed change: a changed history is
stored through the injected scheduler (the client defers to the next task, so an answer and
its grading starts share one store), and a changed answer runs the graders that run
automatically for its importance. The client adds only Airstream views
([`InteractionSignals`](../modules/core/js/src/main/scala/it/evadid/workbook/control/InteractionSignals.scala))
and the page's window listeners, which send pending stores when the page is hidden; another
front end uses the same controller.

`HomepageState` forwards page changes in a fixed order: send the pending stores, select the
user, credentials or workbook, let the answers follow the page, read everything. Following
reads the page's `InteractionScope` when the change applies, so several switches in one
transaction bind the final page:

| Scope after the change | Answers |
| --- | --- |
| Same context and interaction instances | kept; a user or credential change reconnects them, forgetting what removed destinations held |
| Anything else | a new session with default answers |

A full reset binds a new session for the same scope before deleting the remote histories. A
replaced session keeps its own connection: its pending writes finish there, its feedback is
dropped, and its reads never reach the new state. Each history view is a projection of
answer/start/completion events; the format singleton provides all codecs ([interaction
formats](interaction-format.md)).

`boundVar(element, relevance)` chooses significance from each edit; typing editors
publish after a pause rather than per keystroke ([Eva](eva-editor-programming-state.md)).
Importance is not a debounce interval or a guarantee of immediate persistence:

| Importance | Meaning |
| --- | --- |
| `DEFAULT` | Initial answer |
| `TEMPORARY` | Transient edit or in-progress feedback |
| `MINOR` | Intermediate edit |
| `MAJOR` | Significant edit or completed feedback |

Which events are cleaned up is part of the [event contract](interaction-format.md#event-contract);
which are transmitted is chosen by the [sync strategies](database/interaction-history.md#binding-and-event-flow).

Grading and scaffolding run through `interactions.feedback(element)`. The history is the
record of feedback: statuses are read from it, and starts and results are published into it
while it holds their answer. A request joins a pending request for the same answer.
Otherwise it runs the graders without a completed result that run for the request and, when
forced, those whose result asks for a retry; other results are kept. A start read from
another device marks that answer pending but does not stop a request from grading here.
Results computed in the session are reused after `resetLocalHistory` and recorded again. A
new session (another user, a full reset) starts without them, and a running request keeps
the execution context it started with, including the backend token and LLM settings.

## Synchronization boundary

Renderers update their bound value; the observed change schedules the store. To await
all configured destinations from client code:

```scala
import scala.concurrent.Future

def syncWorkbook(fullInfo: FullInfo): Future[Unit] =
  fullInfo.interactions.ensureSynced()
```

`ensureSynced` fetches and stores with forced buffer flushes, after the current
Airstream transaction. Read failures still
allow pending writes to be attempted, then fail the returned future. Production
bindings, event cursors, bulk requests and reset behavior are documented in
[history storage and synchronization](database/interaction-history.md).

## Codecs

Only types crossing a [serialization boundary](serialization-boundaries.md) have codecs;
that guide lists the boundaries and test expectations. History logic operates on typed
events, never on JSON fields. Element definitions are covered in
[session data](session-data-serialization.md). Keep current schemas strict; do not add
migration readers or aliases for old fields and names.
