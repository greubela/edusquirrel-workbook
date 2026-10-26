package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataSuccess
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncDataState, AsyncState}
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.state.observable.ObserverDerivationLogic.DeriveOnlyLastValues
import it.evadid.core.datastructures.state.{ExecutionMethod, State}
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.WorkbookInteractionElementFull
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.interaction.sync.{SyncControl, UpdateImportance}
import it.evadid.workbook.interaction.variable.InteractionVariableState.{DesignatedInteractionState, InteractionVariableStateChanged}
import upickle.default.*

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object InteractionVariable {

}

type InteractionVariableUntyped = InteractionVariable[?, ?, ?]


case class InteractionVariable[T: ReadWriter, GA: ReadWriter, SA: ReadWriter](underlyingInteraction: WorkbookInteractionElementFull[T, GA, SA], debug: Boolean = false) {

  given ec: ExecutionContext = ExecutionContext.global

  private val defaultHistory = {
    val defaultState = InteractionVariableState[T](underlyingInteraction.defaultValue, UpdateImportance.DEFAULT, LocalDateTime.now())
    InteractionVariableHistory[T, GA, SA](Set(defaultState), Map(), Map())
  }

  private val innerState: State[InteractionVariableHistory[T, GA, SA]] = State(defaultHistory)
  private case class GradingRequest(state: T, graders: List[WorkbookInteractionGrader[T, GA]], forced: Boolean,
      locale: Option[(LanguageMapIdResolver, HumanLanguage)])
  private val gradingRequests = State(Map.empty[GradingRequest, Future[Unit]])

  private def safeLastState: InteractionVariableState[T] = innerState.now().lastStateOption.getOrElse(InteractionVariableState[T](underlyingInteraction.defaultValue, UpdateImportance.DEFAULT, LocalDateTime.now()))

  //  val fallbackLogger: SyncLogger = SyncLogger(Logger.withNameAndPrefixes(Some(s"InteractionVariableFallbackLogger(${keyForSerialization})"), PrintToStdLogger.printEverything))

  val keyForSerialization: String = underlyingInteraction.elementId + "_history"

  lazy val observableState: ObservableValue[InteractionVariableHistory[T, GA, SA]] = innerState.observable

  lazy val observableValue: ObservableValue[T] = innerState.observable.deriveValue(_.lastStateOption.getOrElse(safeLastState).value, ExecutionMethod.executeSync, DeriveOnlyLastValues)

  lazy val asAsync: AsyncData[Nothing, T] = {
    val obsState: ObservableValue[AsyncDataState[Nothing, T]] = observableValue.deriveValue(cur => AsyncDataSuccess(cur), ExecutionMethod.executeSync, DeriveOnlyLastValues)
    AsyncState(obsState)
  }


  def createBoundStateWithUpdateLogic(syncControl: SyncControl, relevanceFunc: InteractionVariableStateChanged[T] => UpdateImportance): State[T] = this.synchronized {
    val outerState = State[T](currentValue)
    innerState.observable.addObserver(newValue => if (newValue != outerState.now()) outerState.set(currentValue))
    outerState.observable.addObserver(newValue => if (newValue != currentValue) {
      val last = safeLastState
      val newState = DesignatedInteractionState(newValue, LocalDateTime.now())
      val importance = relevanceFunc(InteractionVariableStateChanged(last, newState))
      setStateFromUserInteraction(syncControl, newValue, importance, newState.timestamp)
    })
    outerState
  }

  def createBoundStateWithUpdateImportance(syncControl: SyncControl, importance: UpdateImportance): State[T] = this.synchronized {
    val outerState = State[T](currentValue)
    innerState.observable.addObserver(newValue => if (newValue != outerState.now()) outerState.set(currentValue))
    outerState.observable.addObserver(newValue => if (newValue != currentValue) setStateFromUserInteraction(syncControl, newValue, importance, LocalDateTime.now()))
    outerState
  }

  def history: InteractionVariableHistory[T, GA, SA] = this.synchronized {
    innerState.now()
  }

  def serializeHistory(value: InteractionVariableHistory[T, GA, SA]): String = write(value)

  def deserializeHistory(json: String): InteractionVariableHistory[T, GA, SA] = {
    val payload = it.evadid.workbook.interaction.sync.model.HistoryJson.payload(json)
    require(payload.obj.contains("events") || payload.obj.contains("states"), "Missing history events")
    val typed = if (payload.obj.contains("events")) read[InteractionVariableHistory[T, GA, SA]](payload)
    else InteractionVariableHistory.empty[T, GA, SA]
    val legacyEvents = payload.obj.get("states").toList.flatMap(_.arr).map { state =>
      InteractionVariableState[T](
        scala.util.Try(read[T](ujson.Str(state("serializedValue").str)))
          .getOrElse(underlyingInteraction.serializerInteractionContent.deserialize(state("serializedValue").str)),
        UpdateImportance.valueOf(state("updateImportance").str),
        LocalDateTime.parse(state("timestamp").str)
      )
    }.toSet
    typed.copy(events = typed.events ++ legacyEvents)
  }

  /*def serializedHistory: InteractionVariableHistorySerialized = this.synchronized {
    innerState.now().serialized(underlyingInteraction.serializerInteractionContent)
  }*/

  def currentValue: T = this.synchronized {
    safeLastState.value
  }

  def updateHistory(func: InteractionVariableHistory[T, GA, SA] => InteractionVariableHistory[T, GA, SA]): Unit = this.synchronized {
    innerState.update(func)
  }

  def withAddedEvents(logger: Logger, serializedHistory: String): Unit = {
    try {
      val res = deserializeHistory(serializedHistory)
      innerState.update(current => current.withAddedEvents(res.events).copy(
        gradings = current.gradings ++ res.gradings, scaffoldings = current.scaffoldings ++ res.scaffoldings
      ))
    } catch case (err: Throwable) => {
      logger.logExceptionWarn(s"InteractionVariableHistory -> withAddedEvents: cannot parse serializedHistory, ignoring content'${serializedHistory}'", err)
      this
    }
  }

  def executeLoad(syncControl: SyncControl): Unit = this.synchronized {
    val histories = syncControl.createCurrentReport(this).curRemoteHistory.values
      .flatMap(_.lastKnownRemoteValue.map(_.dataValue)).toVector
    innerState.update(_.mergeWith(histories))
  }

  def resetLocalHistory(): Unit = this.synchronized {
    innerState.set(defaultHistory)
  }

  def updateStateFromUserInteraction(syncControl: SyncControl, updater: T => T, updateSize: UpdateImportance, availableExecutionClients: List[ExecutionClient], timestamp: LocalDateTime = LocalDateTime.now()): Unit = this.synchronized {
    val nextState: T = updater(safeLastState.value)
    setStateFromUserInteraction(syncControl, nextState, updateSize, timestamp)
    startGrading(syncControl, availableExecutionClients)
  }

  def startGrading(syncControl: SyncControl, availableExecutionClients: List[ExecutionClient], forceGrading: Boolean = false,
      resolver: Option[LanguageMapIdResolver] = None): Future[Unit] = {
    val source = this.synchronized { innerState.now().lastStateOption.map(_.value) }
    resolver match {
      case None => startGradingFor(syncControl, availableExecutionClients, forceGrading, source, None, None)
      case Some(current) => current.languageFuture().flatMap { language =>
        val captured = new LanguageMapIdResolver(State(language).observable) {
          override def resolveMap(id: it.evadid.core.datastructures.language.LanguageMapContentId) = current.resolveMap(id)
        }
        startGradingFor(syncControl, availableExecutionClients, forceGrading, source, Some(captured), Some(current -> language))
      }
    }
  }

  private def startGradingFor(syncControl: SyncControl, availableExecutionClients: List[ExecutionClient], forceGrading: Boolean,
      lastValue: Option[T], resolver: Option[LanguageMapIdResolver], locale: Option[(LanguageMapIdResolver, HumanLanguage)]): Future[Unit] = this.synchronized {
    val curState = innerState.now()
    val graders = underlyingInteraction.gradingElements
    val request = lastValue.map(state => GradingRequest(state, graders, forceGrading, locale))
    val covered = lastValue.exists { state =>
      curState.gradings.get(state).exists { cached =>
        cached.state == state && graders.forall { grader =>
          val results = cached.testsWithResults.filter(result => result.gradedState == state && result.grader == grader)
          results.maxByOption(_.gradingTime).exists { latest =>
            results.filter(_.gradingTime == latest.gradingTime)
              .forall(result => !result.gradingStatus.isInstanceOf[GradingStatus.TestError])
          }
        }
      }
    }
    request.flatMap(gradingRequests.now().get) match {
      case Some(pending) => pending
      case None if graders.nonEmpty && lastValue.nonEmpty && (forceGrading || !covered) =>
        val key = request.get
        val completed = Promise[Unit]()
        gradingRequests.update(_ + (key -> completed.future))
        def release(): Unit = this.synchronized {
          gradingRequests.update { pending =>
            if pending.get(key).exists(_ eq completed.future) then pending - key else pending
          }
        }
        val futures = scala.util.Try(Future.traverse(graders) { grader =>
          resolver match {
            case Some(value) => grader.gradeStateSafe(key.state, forceGrading, availableExecutionClients, value)
            case None => grader.gradeStateSafe(key.state, forceGrading, availableExecutionClients)
          }
        }).fold(Future.failed, identity)
        val published = futures.flatMap { results =>
          val current = locale.fold(Future.successful(true)) { (source, language) =>
            source.languageFuture().map(_ == language)
          }
          current.map(Option.when(_)(results))
        }.map {
          case None => () // The selected feedback language changed while the request was running.
          case Some(resList) =>
            require(resList.forall(result => result.gradedState == key.state && key.graders.contains(result.grader)),
              "Grading results must match the requested answer and configured graders.")
            this.synchronized {
              release()
              innerState.update { history =>
                val previous = history.gradings.get(key.state).filter(_.state == key.state).toList
                  .flatMap(_.testsWithResults).filter(_.gradedState == key.state)
                val merged = key.graders.flatMap { grader =>
                  val incoming = resList.filter(_.grader == grader)
                  val executed = previous.filter(result => result.grader == grader && result.gradingStatus != GradingStatus.NotExecuted())
                  if !key.forced && incoming.forall(_.gradingStatus == GradingStatus.NotExecuted()) && executed.nonEmpty then executed
                  else incoming
                }.toSet
                history.updateGradings(_ + (key.state -> GradedState[T, GA](key.state, merged)))
              }
            }
            ()
        }.flatMap { _ =>
          Option(syncControl).fold(Future.unit)(_.requestStore(this).map(_ => ()))
        }.recoverWith { case err =>
          Option(syncControl).foreach(_.controlLogger.logExceptionWarn("Ignore Grading after error at Grading", err))
          Future.failed(err)
        }
        published.onComplete { result =>
          release()
          completed.tryComplete(result)
          ()
        }
        completed.future
      case _ => Future.unit
    }

  }

  def setStateFromUserInteraction(syncControl: SyncControl, newValue: T, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now()): Unit = this.synchronized {
    //useLogger.logInfo(s"InteractionVariable.updateStateFromUserInteraction(${innerStateHistory.now().lastState.value} -> $newValue ($timestamp, $updateSize)")
    try {
      val lastKnown: InteractionVariableState[T] = safeLastState
      if (newValue != lastKnown.value || updateSize != lastKnown.updateImportance) {
        val newInteractionState = InteractionVariableState[T](newValue, updateSize, timestamp)
        innerState.update(_.withAddedEvent(newInteractionState))
        syncControl.requestStore(this)
      } else {
        syncControl.syncLogger.logInfo(s"InteractionVariable.updateStateFromUserInteraction was ignored because it was identical to the last known value!")
      }
    } catch case e: Exception => {
      e.printStackTrace()
      syncControl.syncLogger.logExceptionWarn(s"InteractionVariable.updateStateFromUserInteraction failed with exception", e)
      throw e
    }
  }

}
