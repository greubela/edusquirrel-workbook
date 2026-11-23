package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataSuccess
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncDataState, AsyncState}
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.state.observable.ObserverDerivationLogic.DeriveOnlyLastValues
import it.evadid.core.datastructures.state.{ExecutionMethod, State}
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}
import it.evadid.workbook.interaction.sync.{TypingUpdateImportance, UpdateImportance}
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.interaction.variable.InteractionVariableState.{DesignatedInteractionState, InteractionVariableStateChanged}
import it.evadid.workbook.meta.ExecutionContextInformation

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext
import scala.util.Try

object InteractionVariable {


}

case class InteractionVariable[F <: InteractionFormat](underlyingInteraction: WorkbookInteractionElement[F], debug: Boolean = false) {

  given ec: ExecutionContext = ExecutionContext.global

  def currentHistory: InteractionVariableHistory[F] = this.synchronized {
    innerState.now()
  }

  def currentValue: InteractionFormat.State[F] = this.synchronized {
    safeLastState.value
  }


  private val defaultState = InteractionVariableState[InteractionFormat.State[F]](underlyingInteraction.defaultValue, UpdateImportance.DEFAULT, LocalDateTime.now())
  private val defaultHistory: InteractionVariableHistory[F] = InteractionVariableHistory.empty[F].addState(defaultState)

  def safeLastState: InteractionVariableState[InteractionFormat.State[F]] = innerState.now().historyStates.lastStateOption.getOrElse(defaultState)

  private val innerState: State[InteractionVariableHistory[F]] = State(defaultHistory)

  private val feedbackGeneration = State(0L)
  private val feedbackOwner = State(Option.empty[AnyRef])
  private val feedbackControl = State(new InteractionScaffoldingAndGradingControl(this))
  def control: InteractionScaffoldingAndGradingControl[F] = feedbackControl.now()
  private[variable] def currentFeedbackGeneration: Long = feedbackGeneration.now()

  /** Bind reused workbook instances to the current user; their computation caches are session-owned. */
  def bindFeedbackSession(owner: AnyRef): Unit = synchronized {
    if !feedbackOwner.now().exists(_ eq owner) then {
      if feedbackOwner.now().nonEmpty then resetForNewSession()
      else invalidateFeedback()
      feedbackOwner.set(Some(owner))
    }
  }

  private def invalidateFeedback(): Unit = {
    feedbackGeneration.update(_ + 1)
    feedbackControl.set(new InteractionScaffoldingAndGradingControl(this))
  }

  def resetForNewSession(): Unit = synchronized {
    invalidateFeedback()
    innerState.set(defaultHistory)
  }

  private[variable] def publishFeedback(generation: Long, context: ExecutionContextInformation)
      (update: InteractionVariableHistory[F] => InteractionVariableHistory[F]): Unit = synchronized {
    if generation == currentFeedbackGeneration && context.isCurrent then updateHistory(update, context)
  }

  val keyForSerialization: String = underlyingInteraction.elementId + "_history"


  lazy val observableHistoryWithDefault: ObservableValue[InteractionVariableHistory[F]] = innerState.observable.deriveValue(curHistory => {
    if (curHistory.historyStates.lastStateOption.nonEmpty) curHistory
    else defaultHistory.addState(defaultState)
  })

  lazy val observableValue: ObservableValue[InteractionFormat.State[F]] = this.synchronized {
    observableHistoryWithDefault.deriveValue(_.historyStates.lastStateOption.getOrElse(safeLastState).value, ExecutionMethod.executeSync, DeriveOnlyLastValues)
  }

  lazy val asAsync: AsyncData[Nothing, InteractionFormat.State[F]] = this.synchronized {
    val obsState: ObservableValue[AsyncDataState[Nothing, InteractionFormat.State[F]]] = observableValue.deriveValue(cur => AsyncDataSuccess(cur), ExecutionMethod.executeSync, DeriveOnlyLastValues)
    AsyncState(obsState)
  }

  def createBoundStateWithUpdateLogic(relevanceFunc: InteractionVariableStateChanged[InteractionFormat.State[F]] => UpdateImportance, context: ExecutionContextInformation): State[InteractionFormat.State[F]] = this.synchronized {
    val outerState = State[InteractionFormat.State[F]](currentValue)
    observableValue.addObserver(newValue => if (newValue != outerState.now()) outerState.set(currentValue))
    outerState.observable.addObserver(newValue => if (newValue != currentValue) {
      val last = safeLastState
      val newState = DesignatedInteractionState(newValue, LocalDateTime.now())
      val importance = relevanceFunc(InteractionVariableStateChanged(last, newState))
      setStateFromUserInteraction(newValue, importance, newState.timestamp, context)
    })
    outerState
  }

  /** Binds a code editor: each change's importance follows [[TypingUpdateImportance]] for the latest typed key. */
  def createBoundStateForTyping(lastKey: () => Option[TypingUpdateImportance.TypedKey], context: ExecutionContextInformation): State[InteractionFormat.State[F]] =
    createBoundStateWithUpdateLogic(change => TypingUpdateImportance.decide(lastKey(),
      currentHistory.allStates.filter(_.updateImportance == UpdateImportance.MAJOR).map(_.timestamp).maxOption,
      change.newState.timestamp), context)

  def createBoundStateWithUpdateImportance(importance: UpdateImportance, context: ExecutionContextInformation): State[InteractionFormat.State[F]] = this.synchronized {
    createBoundStateWithUpdateLogic(_ => importance, context)
  }

  /** Storage boundary for a stored record: it is merged unless it cannot be read as this element's format or
    * holds an answer the element rejects. Such a record is reported and discarded; the current history stays.
    */
  def restoreHistory(logger: Logger, received: InteractionHistoryJson, context: ExecutionContextInformation): Unit =
    Try(received.typedAs(underlyingInteraction.interactionFormat)).fold(
      error => logger.logExceptionWarn(s"Discarded the unreadable stored history of $keyForSerialization", error),
      history => restoreHistory(logger, history, context))

  def restoreHistory(logger: Logger, received: InteractionVariableHistory[F], context: ExecutionContextInformation): Unit = this.synchronized {
    Try(received.events.values.foreach(event => underlyingInteraction.validateState(event.associatedState))).fold(
      error => logger.logExceptionWarn(s"Discarded the stored history of $keyForSerialization: the exercise rejects one of its answers", error),
      _ => {
        val merged = currentHistory.mergeWith(received).cleanedHistory()
        if merged != currentHistory then updateHistory(_ => merged, context)
      })
  }

  def updateHistory(func: InteractionVariableHistory[F] => InteractionVariableHistory[F], executionContextInformation: ExecutionContextInformation): Unit = this.synchronized {
    val previousValue = safeLastState.value
    innerState.update(oldVal => {
      val transformedHistory = func(oldVal)
      transformedHistory.events.values.foreach(event => underlyingInteraction.validateState(event.associatedState))
      val withCurrentState = if (transformedHistory.historyStates.lastStateOption.isEmpty)
        transformedHistory.addState(defaultState)
      else transformedHistory
      withCurrentState.cleanedHistory()
    })
    if (executionContextInformation.syncControl.nonEmpty) {
      executionContextInformation.syncControl.get.store(this, false)
    }
    if safeLastState.value != previousValue then control.getOrCalculateGrading(executionContextInformation, false)
  }

  def resetLocalHistory(): Unit = this.synchronized {
    updateHistory(_ => defaultHistory, ExecutionContextInformation.default)
  }

  /** Commits a TEMPORARY current answer as MAJOR, so strategies that skip TEMPORARY answers send it. */
  def commitCurrentState(context: ExecutionContextInformation): Unit = this.synchronized {
    val last = safeLastState
    if last.updateImportance == UpdateImportance.TEMPORARY then
      setStateFromUserInteraction(last.value, UpdateImportance.MAJOR, LocalDateTime.now(), context)
  }

  def updateStateFromUserInteraction(updater: InteractionFormat.State[F] => InteractionFormat.State[F], updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now(), context: ExecutionContextInformation): Unit = this.synchronized {
    val nextState: InteractionFormat.State[F] = updater(safeLastState.value)
    setStateFromUserInteraction(nextState, updateSize, timestamp, context)
  }


  def setStateFromUserInteraction(newValue: InteractionFormat.State[F], updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now(), context: ExecutionContextInformation): Unit = this.synchronized {
    //useLogger.logInfo(s"InteractionVariable.updateStateFromUserInteraction(${innerStateHistory.now().lastState.value} -> $newValue ($timestamp, $updateSize)")
    try {
      val lastKnown: InteractionVariableState[InteractionFormat.State[F]] = safeLastState
      if (newValue != lastKnown.value || updateSize != lastKnown.updateImportance) {
        val newInteractionState = InteractionVariableState[InteractionFormat.State[F]](newValue, updateSize, timestamp)
        updateHistory(_.addState(newInteractionState), context)
      } else if (context.syncControl.nonEmpty) {
        context.syncControl.get.syncLogger.logInfo(s"InteractionVariable.updateStateFromUserInteraction was ignored because it was identical to the last known value!")
      }
    } catch case e: Exception => {
      if (context.syncControl.nonEmpty) {
        context.syncControl.get.syncLogger.logExceptionWarn(s"InteractionVariable.updateStateFromUserInteraction failed with exception", e)
      }
      throw e
    }
  }

}
