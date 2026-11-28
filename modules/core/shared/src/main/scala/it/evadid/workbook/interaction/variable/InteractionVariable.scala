package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataSuccess
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncDataState, AsyncState}
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.state.observable.ObserverDerivationLogic.DeriveOnlyLastValues
import it.evadid.core.datastructures.state.{ExecutionMethod, State}
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.interaction.binding.{InteractionBinding, SyncConnection}
import it.evadid.workbook.interaction.sync.{UpdateImportance, UsageContext}
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.variable.InteractionVariableState.{DesignatedInteractionState, InteractionVariableStateChanged}

import java.time.LocalDateTime
import scala.util.Try

/** The live answer of one interaction instance; identity, not definition equality, distinguishes instances.
  * Its history belongs to the current [[InteractionBinding]]: reads and writes always use that binding, and
  * observers follow a replacement. Until it is bound, an interaction holds an offline binding.
  */
final class InteractionVariable[F <: InteractionFormat](val underlyingInteraction: WorkbookInteractionElement[F]) {

  val keyForSerialization: String = underlyingInteraction.elementId + "_history"

  private val bindingState: State[InteractionBinding[F]] = State(newBinding(InteractionVariable.unboundContext, SyncConnection.offline))

  def binding: InteractionBinding[F] = bindingState.now()
  def bindingObservable: ObservableValue[InteractionBinding[F]] = bindingState.observable

  /** Replaces the binding with a new one for `context`, starting from the default answer. The holder retires the
    * previous binding; a binding whose context stays the same is reconnected instead ([[InteractionBinding.connect]]).
    */
  def bindTo(context: UsageContext, connection: SyncConnection): InteractionBinding[F] = synchronized {
    val next = newBinding(context, connection)
    bindingState.set(next)
    next
  }

  private def newBinding(context: UsageContext, connection: SyncConnection) =
    new InteractionBinding[F](this, context.toSyncContext(keyForSerialization), connection)

  private[interaction] def defaultHistory(): InteractionVariableHistory[F] = InteractionVariableHistory.empty[F].addState(
    InteractionVariableState[InteractionFormat.State[F]](underlyingInteraction.defaultValue, UpdateImportance.DEFAULT, LocalDateTime.now()))

  def control: InteractionScaffoldingAndGradingControl[F] = binding.control

  def currentHistory: InteractionVariableHistory[F] = binding.currentHistory

  def safeLastState: InteractionVariableState[InteractionFormat.State[F]] = synchronized {
    currentHistory.historyStates.lastStateOption.getOrElse(defaultHistory().historyStates.lastStateOption.get)
  }

  def currentValue: InteractionFormat.State[F] = safeLastState.value

  lazy val observableHistory: ObservableValue[InteractionVariableHistory[F]] = synchronized {
    bindingState.observable.switchMap(_.history)
  }

  lazy val observableValue: ObservableValue[InteractionFormat.State[F]] = synchronized {
    observableHistory.deriveValue(_.historyStates.lastStateOption.getOrElse(safeLastState).value, ExecutionMethod.executeSync, DeriveOnlyLastValues)
  }

  lazy val asAsync: AsyncData[Nothing, InteractionFormat.State[F]] = synchronized {
    val obsState: ObservableValue[AsyncDataState[Nothing, InteractionFormat.State[F]]] = observableValue.deriveValue(cur => AsyncDataSuccess(cur), ExecutionMethod.executeSync, DeriveOnlyLastValues)
    AsyncState(obsState)
  }

  /** A view of the current answer for editors: it reads the history and records each write as a user change
    * whose importance `relevanceFunc` decides. The view registers no observers, so editors may create and drop it freely.
    */
  def createBoundStateWithUpdateLogic(relevanceFunc: InteractionVariableStateChanged[InteractionFormat.State[F]] => UpdateImportance): State[InteractionFormat.State[F]] =
    State.fromStorage(() => currentValue, applyUserChange(_, relevanceFunc),
      transform => applyUserChange(transform(currentValue), relevanceFunc), observableValue)

  def createBoundStateWithUpdateImportance(importance: UpdateImportance): State[InteractionFormat.State[F]] =
    createBoundStateWithUpdateLogic(_ => importance)

  private def applyUserChange(newValue: InteractionFormat.State[F],
      relevanceFunc: InteractionVariableStateChanged[InteractionFormat.State[F]] => UpdateImportance): Unit = synchronized {
    if newValue != currentValue then {
      val newState = DesignatedInteractionState(newValue, LocalDateTime.now())
      setStateFromUserInteraction(newValue, relevanceFunc(InteractionVariableStateChanged(safeLastState, newState)), newState.timestamp)
    }
  }

  /** Storage boundary for a stored record: it is merged unless it cannot be read as this element's format or
    * holds an answer the element rejects. Such a record is reported and discarded; the current history stays.
    */
  def restoreHistory(logger: Logger, received: InteractionHistoryJson): Unit =
    Try(received.typedAs(underlyingInteraction.interactionFormat)).fold(
      error => logger.logExceptionWarn(s"Discarded the unreadable stored history of $keyForSerialization", error),
      history => restoreHistory(logger, history))

  def restoreHistory(logger: Logger, received: InteractionVariableHistory[F]): Unit = synchronized {
    Try(received.events.values.foreach(event => underlyingInteraction.validateState(event.associatedState))).fold(
      error => logger.logExceptionWarn(s"Discarded the stored history of $keyForSerialization: the exercise rejects one of its answers", error),
      _ => updateHistory(_.mergeWith(received)))
  }

  /** Changes the current binding's history; a changed answer is graded by the graders that run automatically. */
  def updateHistory(func: InteractionVariableHistory[F] => InteractionVariableHistory[F]): Unit = synchronized {
    val previousValue = currentValue
    binding.updateHistory(func)
    if currentValue != previousValue then control.getOrCalculateGrading()
  }

  /** Back to the default answer; the binding and its completed feedback computations stay. */
  def resetLocalHistory(): Unit = updateHistory(_ => defaultHistory())

  /** Commits a TEMPORARY current answer as MAJOR, so strategies that skip TEMPORARY answers send it. */
  def commitCurrentState(): Unit = synchronized {
    val last = safeLastState
    if last.updateImportance == UpdateImportance.TEMPORARY then
      setStateFromUserInteraction(last.value, UpdateImportance.MAJOR, LocalDateTime.now())
  }

  def updateStateFromUserInteraction(updater: InteractionFormat.State[F] => InteractionFormat.State[F], updateSize: UpdateImportance,
      timestamp: LocalDateTime = LocalDateTime.now()): Unit = synchronized {
    setStateFromUserInteraction(updater(safeLastState.value), updateSize, timestamp)
  }

  def setStateFromUserInteraction(newValue: InteractionFormat.State[F], updateSize: UpdateImportance,
      timestamp: LocalDateTime = LocalDateTime.now()): Unit = synchronized {
    val lastKnown = safeLastState
    if newValue != lastKnown.value || updateSize != lastKnown.updateImportance then
      updateHistory(_.addState(InteractionVariableState[InteractionFormat.State[F]](newValue, updateSize, timestamp)))
  }

}

object InteractionVariable {
  /** The context of an interaction that no holder has bound; it has no destinations. */
  val unboundContext: UsageContext = UsageContext("[unbound]", "[unbound]", "[unbound]")
}
