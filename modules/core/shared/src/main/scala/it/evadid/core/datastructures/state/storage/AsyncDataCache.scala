package it.evadid.core.datastructures.state.storage

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncDataState, AsyncState}
import it.evadid.core.datastructures.state.storage.AsyncDataCache.*
import it.evadid.distribution.command.SerializedException

import java.time.LocalDateTime
import scala.collection.mutable
import scala.concurrent.*
import scala.util.{Failure, Success}

abstract class AsyncDataCache[I, O](storageName: String, debug: Boolean = false, printError: Boolean = false) {

  // actual cache
  private val cachedRequests: mutable.HashMap[I, CachedRequest[I, O]] = new mutable.HashMap(50, 0.25)

  private var cache_hits: Long = 0
  private var cache_misses: Long = 0

  private var execution_requested: Long = 0
  private var execution_succeeded: Long = 0

  private def emptyAsyncState: State[AsyncDataState[Nothing, O]] = State(AsyncDataLoading[Nothing, O]())

  private def ensureCache(input: I, forceReloading: CachedRequest[I, O] => Boolean = _ => false): CachedRequest[I, O] = {
    val cachedElement: Option[CachedRequest[I, O]] = cachedRequests.get(input)
    //logInfo("ensuring cache for " + formatInputForLogging(input) + " (forceReloading: " + forceReloading + ", cached: " +isInCache + ")")
    if (cachedElement.isEmpty) {
      cache_misses = cache_misses + 1
      startExecution(input, emptyAsyncState)
    }
    else if (forceReloading(cachedElement.get)) {
      val useVar: State[AsyncDataState[Nothing, O]] = cachedElement.map(_.getVariable).getOrElse(emptyAsyncState)
      startExecution(input, useVar)
    }
    else {
      cache_hits = cache_hits + 1
      cachedElement.get
    }
  }

  private def startExecution(input: I, outputVar: State[AsyncDataState[Nothing, O]]): StartedRequest[I, O] = {
    execution_requested = execution_requested + 1
    val fetchedRequest = StartedRequest(this, input, outputVar)
    cachedRequests.put(input, fetchedRequest)
    executeLoading(input)(ExecutionContext.Implicits.global).onComplete {
      case Success(outputData) => fetchedRequest.succeeded(outputData)
      case Failure(error) => fetchedRequest.failed(error)
    }(using ExecutionContext.Implicits.global)
    logInfo("requested execution for " + formatInputForLogging(input))

    fetchedRequest
  }

  // public api

  def removeFromCache(toDelete: List[I] = List()): Unit = {
    cachedRequests.synchronized {
      toDelete.foreach(curInput => {
        cachedRequests.get(curInput).foreach(curRequest => {
          cachedRequests.put(curInput, DeletedRequest(this, curInput, curRequest.getVariable))
        })
      })
    }
  }

  def cacheCopy(): Map[I, O] = {
    cachedRequests.synchronized {
      cachedRequests
        .toList
        .filter(_._2.outputIfPresent.nonEmpty)
        .map(tup => (tup._1, tup._2.outputIfPresent.get))
        .toMap
    }
  }

  def getOutputIfLoaded(input: I): Option[O] = {
    cachedRequests.synchronized {
      cachedRequests.get(input).flatMap(_.outputIfPresent)
    }
  }

  def loadAllAsFuture(inputs: List[I], maximumAge: LocalDateTime)(implicit ec: ExecutionContext): Future[Map[I, AsyncDataStateFinished[Nothing, O]]] = {

    def handleInput(input: I): Future[(I, AsyncDataStateFinished[Nothing, O])] =
      loadAsFuture(input, maximumAge)
        .map[(I, AsyncDataStateFinished[Nothing, O])](res => (input, AsyncDataSuccess(res)))
        .recover { case e: Throwable => (input, AsyncDataFailed(e, None)) }

    Future.traverse(inputs)(handleInput).map(_.toMap)

  }

  def loadAsFuture(input: I, maximumAge: LocalDateTime)(implicit ec: ExecutionContext): Future[O] = {
    cachedRequests.synchronized {
      ensureCache(input, cache => cache.requestCompletedAt.isEmpty || cache.requestCompletedAt.get.isBefore(maximumAge))
    }.createFuture
  }

  def loadAsFuture(input: I, forceReloading: Boolean = false)(implicit ec: ExecutionContext): Future[O] = {
    cachedRequests.synchronized {
      ensureCache(input, _ => forceReloading).createFuture
    }
  }

  def loadIntoVariable(input: I, forceReloading: Boolean = false)(implicit ec: ExecutionContext): AsyncData[Nothing, O] = {
    cachedRequests.synchronized {
      val state: State[AsyncDataState[Nothing, O]] = ensureCache(input, _ => forceReloading).getVariable
      AsyncState(state.observable)
    }
  }

  def reloadAll()(implicit ec: ExecutionContext): Unit = {
    cachedRequests.synchronized {
      val allKeys = cachedRequests.keys.toList
      removeFromCache(allKeys)
      allKeys.foreach(input => loadIntoVariable(input, forceReloading = true)(using ec))
    }
  }

  // abstract methods for child classes

  protected def executeLoading(in: I)(ec: ExecutionContext): Future[O]

  protected def formatInputForLogging(in: I): String

  protected def formatOutputForLogging(out: O): String

  // logging

  override def toString: String = {
    "DataStorage '" + storageName + "' with " + cacheInfoString
  }

  private def logInfo(str: String): Unit = if (debug) {
    println(s"[INFO] for data storage '$storageName': " + str
      + s"\n    cache performance (${cachedRequests.size} elements): $cache_hits  hits + $cache_misses +  misses"
      + s"\n    calculation history: $execution_succeeded/$execution_requested succeeded so far (${execution_succeeded * 1.0 / execution_requested}%)"
      + s"\n    " + cacheInfoString
    )
  }

  private def logError(str: String, throwable: Throwable): Unit = if (printError) {
    throwable.printStackTrace()
    println(s"[Error] for data storage '$storageName': " + str
      + "\n    thrown error: " + throwable.getMessage
      + "\n    cache: " + cache_hits + " hits, " + cache_misses + " misses"
      + s"\n    " + cacheInfoString
    )
  }


  private def cacheInfoString: String = {
    val cached: List[CachedRequest[I, O]] = cachedRequests.toList.map(_._2)
    val succeeded: List[SucceededRequest[I, O]] = cached.collect { case finished: SucceededRequest[I, O] => finished }
    val failed: List[FailedRequest[I, O]] = cached.collect { case finished: FailedRequest[I, O] => finished }
    val deleted: List[DeletedRequest[I, O]] = cached.collect { case deleted: DeletedRequest[I, O] => deleted }
    val started: List[StartedRequest[I, O]] = cached.collect { case started: StartedRequest[I, O] => started }
    "cache state: " + cachedRequests.size + " elements (" + started.size + " currently loading, " + succeeded.size + " succeeded, " + failed.size + " failed, " + deleted.size + " deleted)"
  }


}

object AsyncDataCache {

  private[storage] trait CachedRequest[I, O] {

    val associatedStore: AsyncDataCache[I, O]

    val outputIfPresent: Option[O]

    def createFuture: Future[O]

    def getVariable: State[AsyncDataState[Nothing, O]]

    def requestCompletedAt: Option[LocalDateTime] = None
  }

  private[storage] case class SucceededRequest[I, O](associatedStore: AsyncDataCache[I, O], outputVar: State[AsyncDataState[Nothing, O]], output: O, timestamp: LocalDateTime = LocalDateTime.now()) extends CachedRequest[I, O] {

    override val outputIfPresent: Option[O] = Some(output)

    def createFuture: Future[O] = Future.successful(output)

    def getVariable: State[AsyncDataState[Nothing, O]] = outputVar

    override def requestCompletedAt: Option[LocalDateTime] = Some(timestamp)
  }

  private[storage] case class FailedRequest[I, O](associatedStore: AsyncDataCache[I, O], outputVar: State[AsyncDataState[Nothing, O]], cause: Throwable, timestamp: LocalDateTime = LocalDateTime.now()) extends CachedRequest[I, O] {

    override val outputIfPresent: Option[O] = None

    def createFuture: Future[O] = Future.failed(cause)

    def getVariable: State[AsyncDataState[Nothing, O]] = outputVar

    override def requestCompletedAt: Option[LocalDateTime] = Some(timestamp)
  }

  private[storage] case class DeletedRequest[I, O](associatedStore: AsyncDataCache[I, O], input: I, outputVar: State[AsyncDataState[Nothing, O]]) extends CachedRequest[I, O] {

    override val outputIfPresent: Option[O] = None

    def createFuture: Future[O] = associatedStore.startExecution(input, outputVar).createFuture

    def getVariable: State[AsyncDataState[Nothing, O]] = associatedStore.startExecution(input, outputVar).getVariable
  }

  private[storage] case class StartedRequest[I, O](associatedStore: AsyncDataCache[I, O], input: I, outputVar: State[AsyncDataState[Nothing, O]]) extends CachedRequest[I, O] {

    override val outputIfPresent: Option[O] = None

    private val waitingPromise: mutable.HashSet[Promise[O]] = new mutable.HashSet()

    def getVariable: State[AsyncDataState[Nothing, O]] = outputVar

    def createFuture: Future[O] = {
      val promise = Promise[O]()
      waitingPromise.add(promise)
      promise.future
    }

    def succeeded(output: O): Unit = {
      associatedStore.cachedRequests.put(input, SucceededRequest(associatedStore, outputVar, output))
      associatedStore.execution_succeeded = associatedStore.execution_succeeded + 1
      associatedStore.logInfo(s"Successfully calculated output: '${associatedStore.formatInputForLogging(input)}' -> '${associatedStore.formatOutputForLogging(output)}'")

      waitingPromise.foreach(promise => promise.success(output))
      outputVar.set(AsyncDataSuccess(output))
      waitingPromise.clear()
    }

    def failed(cause: Throwable): Unit = {
      associatedStore.cachedRequests.put(input, FailedRequest(associatedStore, outputVar, cause))
      associatedStore.logError(s"Failed to load output for input '${associatedStore.formatInputForLogging(input)}", cause)
      outputVar.set(AsyncDataFailed(SerializedException(cause), None))
      waitingPromise.foreach(promise => promise.failure(cause))
    }
  }


}
