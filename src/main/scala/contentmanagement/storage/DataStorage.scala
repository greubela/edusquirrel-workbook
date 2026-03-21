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

  protected def executeLoading(in: I)(ec: ExecutionContext): Future[O]

  protected def initialValueWhileLoading(in: I): Option[O]

  protected def formatInputForLogging(in: I): String

  protected def formatOutputForLogging(out: O): String

  private val cachedOutputVars: mutable.HashMap[I, Var[Option[O]]] = new mutable.HashMap(50, 0.25)
  private val cachedCalculations: mutable.HashMap[I, O] = new mutable.HashMap(50, 0.25)

  private var cache_hits: Long = 0
  private var cache_misses: Long = 0

  private var execution_requested: Long = 0
  private var execution_succeeded: Long = 0

  private def logInfo(str: String): Unit = if (debug) {
    println(s"[INFO] for data storage '$storageName': " + str
      + s"\n    cache performance (${cachedOutputVars.size} elements): $cache_hits  hits + $cache_misses +  misses"
      + s"\n    calculation history: $execution_succeeded/$execution_requested succeeded so far (${execution_succeeded * 1.0 / execution_requested}%)"
      + s"\n    cache:\n    -" + cachedOutputVars.toMap.toList.map(tup => formatInputForLogging(tup._1) + " -> " + tup._2.now().map(formatOutputForLogging).getOrElse("None")).mkString("\n    -")
    )
  }

  private def logError(str: String, throwable: Throwable): Unit = {
    throwable.printStackTrace()
    println(s"[Error] for data storage '$storageName': " + str
      + "\n    thrown error: " + throwable.getMessage
      + "\n    cache: " + cache_hits + " hits, " + cache_misses + " misses"
      + "\n    cache size: " + cachedOutputVars.size + ", currently loading: " + cachedOutputVars.size)
  }

  def deleteFromStorage(toDelete: List[I] = List()): Unit = {
    toDelete.foreach(desc => cachedOutputVars.remove(desc))
    toDelete.foreach(desc => cachedCalculations.remove(desc))
  }

  def createSignalDependendVar(inputSignal: Signal[I])(implicit ec: ExecutionContext): Var[Option[O]] = {
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

  def loadAsFuture(input: I, forceReloading: Boolean = false)(implicit ec: ExecutionContext): Future[O] = {
    if (cachedCalculations.contains(input) && !forceReloading) {
      cache_hits = cache_hits + 1
      logInfo(s"cache hit for input '${formatInputForLogging(input)}''")
      Future.successful(cachedCalculations(input))
    } else if (cachedCalculations.contains(input) && forceReloading) {
      requestExecution(input)(ec)._1
    } else {
      cache_misses = cache_misses + 1
      logInfo(s"cache miss for input '${formatInputForLogging(input)}''")
      requestExecution(input)(ec)._1
    }
  }


  private def getOrCreateVar(input: I)(implicit ec: ExecutionContext): Var[Option[O]] = {
    if (cachedOutputVars.contains(input)) cachedOutputVars(input)
    else {
      val res = Var[Option[O]](initialValueWhileLoading(input))
      cachedOutputVars.put(input, res)
      res
    }
  }

  def loadIntoVariable(input: I, forceReloading: Boolean = false)(implicit ec: ExecutionContext): Var[Option[O]] = {
    if (cachedOutputVars.contains(input) && !forceReloading) {
      cache_hits = cache_hits + 1
      logInfo(s"cache hit for input '${formatInputForLogging(input)}''")
      cachedOutputVars(input)
    } else if (cachedCalculations.contains(input) && forceReloading) {
      requestExecution(input)(ec)._2
    } else {
      cache_misses = cache_misses + 1
      logInfo(s"cache miss for input '${formatInputForLogging(input)}''")
      requestExecution(input)(ec)._2
    }
  }

  private def requestExecution(input: I)(implicit ec: ExecutionContext): (Future[O], Var[Option[O]]) = {
    val promise = Promise[O]()
    val varRes = getOrCreateVar(input)
    execution_requested = execution_requested + 1
    executeLoading(input)(ec).onComplete {
      case Success(outputData) => {
        execution_succeeded = execution_succeeded + 1
        cachedCalculations.put(input, outputData)
        varRes.set(Some(outputData))
        promise.success(outputData)
        logInfo(s"Successfully calculated output: '${formatInputForLogging(input)}' -> '${formatOutputForLogging(outputData)}'")
      }
      case Failure(error) => {
        promise.failure(error)
        logError(s"Failed to load output for input '${formatInputForLogging(input)}", error)
      }
    }(ec)

    (promise.future, varRes)
  }

  def startLoading(input: I)(implicit ec: ExecutionContext): Unit = loadIntoVariable(input)(ec)


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
