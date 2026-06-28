package it.evadid.core.datastructures.state.storage

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.async.AsyncDataState.*
import it.evadid.core.datastructures.state.async.{AsyncData, AsyncDataState, AsyncState}
import it.evadid.core.datastructures.state.storage.AsyncDataCache.*
import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.{Logger, LoggingLevel}
import it.evadid.util.logging.Logger.DerivedLogger

import java.time.LocalDateTime
import scala.collection.mutable
import scala.concurrent.*
import scala.util.{Failure, Success}

abstract class AsyncDataCache[I, O](baseLogger: Logger) {



  private val cacheLogger: Logger = Logger.deriveFrom(baseLogger, (msg, _) => appendCacheInfoToMsg(msg))

  // actual cache
  private val cachedRequests: mutable.HashMap[I, CachedRequest[I, O]] = new mutable.HashMap(50, 0.25)

  private var cache_hits: Long = 0
  private var cache_misses: Long = 0

  private var execution_requested: Long = 0
  private var execution_succeeded: Long = 0

  private def emptyAsyncState: State[AsyncDataState[Nothing, O]] = State(AsyncDataLoading[Nothing, O]())

  def getSyncIfInCache(input: I, loadIfNotPresent: Boolean = true): Option[O] = cachedRequests.synchronized {
    val res: Option[O] = cachedRequests.get(input).match {
      case Some(finished: SucceededRequest[I, O]) => Some(finished.output)
      case _ => None
    }
    if (res.isEmpty && loadIfNotPresent) ensureCache(input)
    res
  }

  private def ensureCache(input: I, forceReloading: CachedRequest[I, O] => Boolean = _ => false): CachedRequest[I, O] = cachedRequests.synchronized {
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

  private def startExecution(input: I, outputVar: State[AsyncDataState[Nothing, O]]): StartedRequest[I, O] = cachedRequests.synchronized {
    execution_requested = execution_requested + 1
    val fetchedRequest = StartedRequest(this, input, outputVar)
    cachedRequests.put(input, fetchedRequest)
    executeLoading(input)(ExecutionContext.Implicits.global).onComplete {
      case Success(outputData) => fetchedRequest.succeeded(outputData)
      case Failure(error) => fetchedRequest.failed(error)
    }(using ExecutionContext.Implicits.global)
    cacheLogger.logInfo("requested execution for " + formatInputForLogging(input))

    fetchedRequest
  }

  // public api

  def removeFromCache(toDelete: List[I] = List()): Unit = cachedRequests.synchronized {
    cachedRequests.synchronized {
      toDelete.foreach(curInput => {
        cachedRequests.get(curInput).foreach(curRequest => {
          cachedRequests.put(curInput, DeletedRequest(this, curInput, curRequest.getVariable))
        })
      })
    }
  }

  def cacheCopy(): Map[I, O] = cachedRequests.synchronized {
    cachedRequests.synchronized {
      cachedRequests
        .toList
        .filter(_._2.outputIfPresent.nonEmpty)
        .map(tup => (tup._1, tup._2.outputIfPresent.get))
        .toMap
    }
  }
  /*
    def getOutputIfLoaded(input: I): Option[O] = cachedRequests.synchronized {
      cachedRequests.synchronized {
        cachedRequests.get(input).flatMap(_.outputIfPresent)
      }
    }*/

  def loadAllAsFuture(inputs: List[I], maximumAge: LocalDateTime)(implicit ec: ExecutionContext): Future[Map[I, Either[Throwable, O]]] = cachedRequests.synchronized {

    def handleInput(input: I): Future[(I, Either[Throwable, O])] =
      loadAsFuture(input, maximumAge)
        .map(res => input -> Right(res))
        .recover { case e: Throwable => input -> Left(e) }

    Future.traverse(inputs)(handleInput).map(_.toMap)
  }

  def loadAsFuture(input: I, maximumAge: LocalDateTime)(implicit ec: ExecutionContext): Future[O] = cachedRequests.synchronized {
    cachedRequests.synchronized {
      ensureCache(input, cache => cache.requestCompletedAt.isEmpty || cache.requestCompletedAt.get.isBefore(maximumAge))
    }.createFuture
  }

  def loadAsFuture(input: I, forceReloading: Boolean = false)(implicit ec: ExecutionContext): Future[O] = cachedRequests.synchronized {
    cachedRequests.synchronized {
      ensureCache(input, _ => forceReloading).createFuture
    }
  }

  def loadIntoVariable(input: I, forceReloading: Boolean = false)(implicit ec: ExecutionContext): AsyncData[Nothing, O] = cachedRequests.synchronized {
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
    appendCacheInfoToMsg(s"AsyncDataStorage(${this.getClass.getSimpleName})")
  }

  private def appendCacheInfoToMsg(msg: String): String = {
    val cached: List[CachedRequest[I, O]] = cachedRequests.toList.map(_._2)
    val succeeded: List[SucceededRequest[I, O]] = cached.collect { case finished: SucceededRequest[I, O] => finished }
    val failed: List[FailedRequest[I, O]] = cached.collect { case finished: FailedRequest[I, O] => finished }
    val deleted: List[DeletedRequest[I, O]] = cached.collect { case deleted: DeletedRequest[I, O] => deleted }
    val started: List[StartedRequest[I, O]] = cached.collect { case started: StartedRequest[I, O] => started }

    val cacheInfoLines: List[String] = List(
      s"cache status: currently ${cachedRequests.size} elements (${started.size} loading + ${succeeded.size} finished + ${failed.size} failed + ${deleted.size} deleted)",
      Logger.formatPerformance("cache", cache_hits, cache_hits + cache_misses, "cache hits", "total requests"),
      Logger.formatPerformance("fetching", succeeded.size, succeeded.size + failed.size, "succeeded elements", "succeeded + failed elements")
    )
    cacheInfoLines.mkString(msg + "\n    Cache Info:\n        ", "\n        ", "\n")
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
      associatedStore.cacheLogger.logInfo(s"Successfully calculated output: '${associatedStore.formatInputForLogging(input)}' -> '${associatedStore.formatOutputForLogging(output)}'")

      waitingPromise.foreach(promise => promise.success(output))
      outputVar.set(AsyncDataSuccess(output))
      waitingPromise.clear()
    }

    def failed(cause: Throwable): Unit = {
      associatedStore.cachedRequests.put(input, FailedRequest(associatedStore, outputVar, cause))
      associatedStore.cacheLogger.logExceptionWarn(s"Ignoring since fetching failed for input '${associatedStore.formatInputForLogging(input)}", cause)
      outputVar.set(AsyncDataFailed(SerializedException(cause), None))
      waitingPromise.foreach(promise => promise.failure(cause))
    }
  }


}
