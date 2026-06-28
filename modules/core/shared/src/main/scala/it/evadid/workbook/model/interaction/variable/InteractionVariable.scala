package it.evadid.workbook.model.interaction.variable

import it.evadid.core.datastructures.state.async.AsyncDataState.AsyncDataSuccess
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncDataState, AsyncState}
import it.evadid.core.datastructures.state.observable.ObservableValue
import it.evadid.core.datastructures.state.observable.ObserverDerivationLogic.DeriveOnlyLastValues
import it.evadid.core.datastructures.state.{ExecutionMethod, State}
import it.evadid.core.util.*
import it.evadid.workbook.model.interaction.*
import it.evadid.workbook.model.interaction.sync.SyncInformation.{SyncCache, SyncInformationWithContext}
import it.evadid.workbook.model.interaction.sync.*
import it.evadid.workbook.model.interaction.variable.InteractionVariableState.{DesignatedInteractionState, InteractionVariableStateChanged}

import java.time.LocalDateTime

case class InteractionVariable[T](underlyingInteraction: WorkbookInteraction[T], debug: Boolean = false) {

  private val defaultHistory = InteractionVariableHistory[T](Set(InteractionVariableState[T](underlyingInteraction.defaultValue, UpdateImportance.DEFAULT, LocalDateTime.now())))

  private val innerState: State[InteractionVariableHistory[T]] = State(defaultHistory)
  private val syncControl: State[Option[SyncControl]] = State(None)

  val keyForSerialization: String = underlyingInteraction.id + "_history"

  lazy val observableValue: ObservableValue[T] = innerState.observable.deriveValue(_.lastState.value, ExecutionMethod.executeSync, DeriveOnlyLastValues)

  lazy val asAsync: AsyncData[Nothing, T] = {
    val obsState: ObservableValue[AsyncDataState[Nothing, T]] = observableValue.deriveValue(cur => AsyncDataSuccess(cur), ExecutionMethod.executeSync, DeriveOnlyLastValues)
    AsyncState(obsState)
  }

  lazy val tmpObservable: ObservableValue[InteractionVariableHistory[T]] = innerState.observable

  def createBoundStateWithUpdateLogic(relevanceFunc: InteractionVariableStateChanged[T] => UpdateImportance): State[T] = this.synchronized {
    val outerState = State[T](currentValue)
    innerState.observable.addObserver(newValue => if (newValue != outerState.now()) outerState.set(currentValue))
    outerState.observable.addObserver(newValue => if (newValue != currentValue) {
      val last = innerState.now().lastState
      val newState = DesignatedInteractionState(newValue, LocalDateTime.now())
      val importance = relevanceFunc(InteractionVariableStateChanged(last, newState))
      setStateFromUserInteraction(newValue, importance, newState.timestamp)
    })
    outerState
  }

  def createBoundStateWithUpdateImportance(importance: UpdateImportance): State[T] = this.synchronized {
    val outerState = State[T](currentValue)
    innerState.observable.addObserver(newValue => if (newValue != outerState.now()) outerState.set(currentValue))
    outerState.observable.addObserver(newValue => if (newValue != currentValue) setStateFromUserInteraction(newValue, importance, LocalDateTime.now()))
    //outerVar.signal.foreach(newValue => if (newValue != currentValue) setStateFromUserInteraction(newValue, importance, LocalDateTime.now()))
    outerState
  }

  def history: InteractionVariableHistory[T] = this.synchronized {
    innerState.now()
  }

  def serializedHistory: InteractionVariableHistorySerialized = this.synchronized {
    innerState.now().serialized(underlyingInteraction.serializer)
  }

  def currentValue: T = this.synchronized {
    innerState.now().lastState.value
  }

  def addHistory(history: InteractionVariableHistorySerialized): Unit = this.synchronized {
    innerState.update(_.withAddedEvents(underlyingInteraction.serializer, history))
  }

  /*
  def executeStore(syncSource: SyncInformationWithContext): Unit = {

    //val serialized = innerState.now().serializedWithStrategy(syncSource.syncStrategy, underlyingInteraction.serializer)
    syncSource.storeTo(keyForSerialization, innerState.now(), underlyingInteraction.serializer)

    if (debug) {
      println("[INFO] synced interaction variable with key'" + keyForSerialization + "' to source: " + syncSource.syncSource
        + ", current value: " + innerState.now().lastState.value
        + ", last update time: " + InfoUtil.datetimeFormattedForLog(innerState.now().lastState.timestamp) + ", total events: " + innerState.now().events.size + ")")
    }
  }*/

  def executeLoad(syncCache: List[SyncCache]): Unit = this.synchronized {
    syncCache.foreach((curCache: SyncCache) => {
      val parsed: SyncInformation.SyncFetchedHistory[T] = curCache.typedHistory[T](keyForSerialization, underlyingInteraction.serializer)
      if (debug && parsed.unparsableElements.states.nonEmpty) {
        println("[Warn] InteractionVariable '" + keyForSerialization + "' could not parse from " + parsed.unparsableElements.states.size + " elements!")
      }
      innerState.update(_.withAddedEvents(parsed.typedElements))
    })
  }

  def resetHistoryAndSyncControl(newSyncControl: Option[SyncControl]): Unit = this.synchronized {
    syncControl.set(newSyncControl)
    innerState.set(defaultHistory)
  }


  /*
    def fetchFromAll(): Unit = this.synchronized {

      val sources: List[SyncInformationWithContext] = syncSources.now()
      val applyFunc: SyncInformationWithContext => Future[InteractionVariableHistory[T]] = _.fetchFrom(keyForSerialization, underlyingInteraction.serializer)

      given ExecutionContext = ExecutionContext.global

      val allHistories: Future[List[InteractionVariableHistory[T]]] = Future.traverse(sources)(applyFunc)

      allHistories.onComplete {
        case Success(curHistories) => curHistories.foreach((curHistory: InteractionVariableHistory[T]) => innerState.update(_.withAddedEvents(curHistory)))
        case Failure(err) => println("[ERROR] could not fetch history from all sources: " + err)
      }

    }

    def resetHistory(): Unit = this.synchronized {
      innerState.set(defaultHistory)
    }

    def resetHistoryAndSyncInfo(newSyncSources: List[SyncInformationWithContext]): Unit = this.synchronized {
      syncSources.set(List())
      innerState.set(defaultHistory)
      syncSources.set(newSyncSources)
    }
  */
  def updateStateFromUserInteraction(updater: T => T, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now()): Unit = this.synchronized {
    val nextState: T = updater(innerState.now().lastState.value)
    setStateFromUserInteraction(nextState, updateSize, timestamp)
  }

  def setStateFromUserInteraction(newValue: T, updateSize: UpdateImportance, timestamp: LocalDateTime = LocalDateTime.now()): Unit = this.synchronized {
    println("InteractionVariable.updateStateFromUserInteraction: " + newValue + " (" + updateSize + ", oldValue: " + innerState.now().lastState.value + ")")

    val lastKnown: InteractionVariableState[T] = innerState.now().lastState
    if (newValue != lastKnown.value || updateSize != lastKnown.updateImportance) {
      val newInteractionState = InteractionVariableState[T](newValue, updateSize, timestamp)
      innerState.update(_.withAddedEvent(newInteractionState))
      if (syncControl.now().nonEmpty) {
        syncControl.now().foreach(_.requestStore(this))
      } else {
        println("[WARN] changed update was not committed because no syncTarget was available!")
      }
    } else {
      // println("Update supressed!")
    }
  }


}
