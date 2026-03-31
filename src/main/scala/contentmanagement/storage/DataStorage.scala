package contentmanagement.storage

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.{HumanLanguage, LanguageMap}
import workbook.model.info.WorkbookInfo

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

abstract class DataStorage[I, O](storageName: String, debug: Boolean) {

  // Helper Classes

  private trait CachedRequest {
    def createFuture: Future[O]

    def getVariable: Var[Option[O]]
  }

  private case class FinishedRequest(outputVar: Var[Option[O]], output: O) extends CachedRequest {
    def createFuture: Future[O] = Future.successful(output)

    def getVariable: Var[Option[O]] = outputVar
  }

  private case class DeletedRequest(input: I, outputVar: Var[Option[O]]) extends CachedRequest {
    def createFuture: Future[O] = startExecution(input, outputVar).createFuture

    def getVariable: Var[Option[O]] = startExecution(input, outputVar).getVariable
  }

  private case class StartedRequest(input: I, outputVar: Var[Option[O]]) extends CachedRequest {
    private val waitingPromise: mutable.HashSet[Promise[O]] = new mutable.HashSet()

    def getVariable: Var[Option[O]] = outputVar

    def createFuture: Future[O] = {
      val promise = Promise[O]()
      waitingPromise.add(promise)
      promise.future
    }

    def succeeded(output: O): Unit = {
      cachedRequests.put(input, FinishedRequest(outputVar, output))
      execution_succeeded = execution_succeeded + 1
      logInfo(s"Successfully calculated output: '${formatInputForLogging(input)}' -> '${formatOutputForLogging(output)}'")

      waitingPromise.foreach(promise => promise.success(output))
      outputVar.set(Some(output))
      waitingPromise.clear()
    }

    def failed(cause: Throwable): Unit = {
      deleteFromStorage(List(input))
      logError(s"Failed to load output for input '${formatInputForLogging(input)}", cause)

      waitingPromise.foreach(promise => promise.failure(cause))
    }
  }

  // actual cache
  private val cachedRequests: mutable.HashMap[I, CachedRequest] = new mutable.HashMap(50, 0.25)

  private var cache_hits: Long = 0
  private var cache_misses: Long = 0

  private var execution_requested: Long = 0
  private var execution_succeeded: Long = 0

  private def ensureCache(input: I, forceReloading: Boolean = false): CachedRequest = {
    val cachedElement = cachedRequests.get(input)
    //logInfo("ensuring cache for " + formatInputForLogging(input) + " (forceReloading: " + forceReloading + ", cached: " +isInCache + ")")
    if (cachedElement.isEmpty) {
      cache_misses = cache_misses + 1
      startExecution(input, Var(defaultValueWhileLoading(input)))
    }
    else if (forceReloading) {
      val useVar = cachedElement.map(_.getVariable).getOrElse(Var(defaultValueWhileLoading(input)))
      startExecution(input, useVar)
    }
    else {
      cache_hits = cache_hits + 1
      cachedElement.get
    }
  }

  private def startExecution(input: I, outputVar: Var[Option[O]]): StartedRequest = {
    execution_requested = execution_requested + 1
    val fetchedRequest = StartedRequest(input, outputVar)
    cachedRequests.put(input, fetchedRequest)
    executeLoading(input)(ExecutionContext.Implicits.global).onComplete {
      case Success(outputData) => fetchedRequest.succeeded(outputData)
      case Failure(error) => fetchedRequest.failed(error)
    }(ExecutionContext.Implicits.global)
    logInfo("requested execution for " + formatInputForLogging(input))

    fetchedRequest
  }

  // public api

  def deleteFromStorage(toDelete: List[I] = List()): Unit = {
    cachedRequests.synchronized {
      toDelete.foreach(curInput => {
        cachedRequests.get(curInput).foreach(curRequest => {
          curRequest.getVariable.set(None)
          cachedRequests.put(curInput, DeletedRequest(curInput, curRequest.getVariable))
        })
      })
    }
  }

  def loadAsFuture(input: I, forceReloading: Boolean = false)(implicit ec: ExecutionContext): Future[O] = {
    cachedRequests.synchronized {
      ensureCache(input, forceReloading).createFuture
    }
  }

  def loadIntoVariable(input: I, forceReloading: Boolean = false)(implicit ec: ExecutionContext): Var[Option[O]] = {
    cachedRequests.synchronized {
      ensureCache(input, forceReloading).getVariable
    }
  }

  def createSignalDependendVar(inputSignal: Signal[I])(implicit ec: ExecutionContext): Var[Option[O]] = {
    cachedRequests.synchronized {
      val resultVar: Var[Option[O]] = Var(None)
      inputSignal.foreach(newValue => {
        val actualVar = loadIntoVariable(newValue)(ec)
        resultVar.set(actualVar.now())
        actualVar.signal.foreach(newValue => {
          resultVar.set(newValue)
        })(unsafeWindowOwner)
      })(unsafeWindowOwner)
      resultVar
    }
  }

  // abstract methods for child classes

  protected def executeLoading(in: I)(ec: ExecutionContext): Future[O]

  protected def defaultValueWhileLoading(in: I): Option[O]

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

  private def logError(str: String, throwable: Throwable): Unit = {
    throwable.printStackTrace()
    println(s"[Error] for data storage '$storageName': " + str
      + "\n    thrown error: " + throwable.getMessage
      + "\n    cache: " + cache_hits + " hits, " + cache_misses + " misses"
      + s"\n    " + cacheInfoString
    )
  }


  private def cacheInfoString: String = {
    val cached: List[CachedRequest] = cachedRequests.toList.map(_._2)
    val finished: List[FinishedRequest] = cached.collect { case finished: FinishedRequest => finished }
    val deleted: List[DeletedRequest] = cached.collect { case deleted: DeletedRequest => deleted }
    val started: List[StartedRequest] = cached.collect { case started: StartedRequest => started }
    "cache state: " + cachedRequests.size + " elements (" + started.size + " currently loading, " + finished.size + " finished, " + deleted.size + " deleted)"
  }


}

object DataStorage {

  val fileDataStore: FileDataStorage = FileDataStorage()

  private val languageMapStorage: LabelLanguageMapStorage = LabelLanguageMapStorage(LanguageMapTriplesStorage(fileDataStore))

  def labelSignalFromLanguageMapName(languageMapName: String, workbookInfoVar: Var[WorkbookInfo]): Signal[String] = {
    println("request language map for name: " + languageMapName)
    val languageMapVar = languageMapStorage.loadIntoVariable(languageMapName)(ExecutionContext.Implicits.global)
    languageMapVar.signal.combineWith(workbookInfoVar.signal).map(tup => {
      tup._1 match {
        case Some(value: LanguageMap[HumanLanguage]) => tup._2.languageStringFromMap(value)
        case None => tup._2.languageStringFromMap(LabelLanguageMapStorage.languageMapLoadingMap)
      }
    })


  }


}


