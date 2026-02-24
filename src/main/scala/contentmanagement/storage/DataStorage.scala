package contentmanagement.storage

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.image.ImageDescription
import org.scalajs.dom
import org.scalajs.dom.URL

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

abstract class DataStorage[I, O](storageName: String, debug: Boolean) {

  protected def executeLoading(in: I)(ec: ExecutionContext): Future[O]

  protected def initialValueWhileLoading(in: I): Option[O]

  protected def formatInputForLogging(in: I): String
  protected def formatOutputForLogging(out: O): String

  private val cachedOutputVars: mutable.HashMap[I, Var[Option[O]]] = new mutable.HashMap(50, 0.25)

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

  def loadIntoVariable(input: I, forceReloading: Boolean = false)(implicit ec: ExecutionContext): Var[Option[O]] = {
    if (cachedOutputVars.contains(input)) {
      cache_hits = cache_hits + 1
      val resultVar = cachedOutputVars(input)
      logInfo(s"cache hit for input '${formatInputForLogging(input)}''")
      if (forceReloading) requestExecution(input, resultVar)(ec)
      cachedOutputVars(input)
    }
    else {
      cache_misses = cache_misses + 1
      logInfo(s"cache miss for input '${formatInputForLogging(input)}''")
      val resultVariable: Var[Option[O]] = Var(None)
      cachedOutputVars.put(input, resultVariable)
      requestExecution(input, resultVariable)(ec)
      resultVariable
    }
  }

  private def requestExecution(input: I, updateVar: Var[Option[O]])(implicit ec: ExecutionContext): Unit = {
    execution_requested = execution_requested + 1
    executeLoading(input)(ec).onComplete {
      case Success(outputData) => {
        execution_succeeded = execution_succeeded + 1
        updateVar.set(Some(outputData))
        logInfo(s"Successfully calculated output: '${formatInputForLogging(input)}' -> '${formatOutputForLogging(outputData)}'")
      }
      case Failure(error) => logError(s"Failed to load output for input '${formatInputForLogging(input)}", error)
    }(ec)
  }

  def startLoading(input: I)(implicit ec: ExecutionContext): Unit = loadIntoVariable(input)(ec)


}

object DataStorage {

  val urlDataStore: DataStorage[URL, String] = new DataStorage[URL, String]("UrlDataStore", false) {
    override protected def executeLoading(url: URL)(ec: ExecutionContext): Future[String] = {
      dom.fetch(url.toString)
        .toFuture
        .flatMap { response =>
          if (!response.ok)
            Future.failed(new RuntimeException(
              s"HTTP ${response.status} ${response.statusText}"
            ))
          else
            response.text().toFuture
        }(ec)
    }

    override protected def initialValueWhileLoading(in: URL): Option[String] = None

    override protected def formatInputForLogging(in: URL): String = in.toString

    override protected def formatOutputForLogging(out: String): String = out.toString
  }


}

