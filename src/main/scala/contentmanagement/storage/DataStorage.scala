package contentmanagement.storage

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.Signal
import contentmanagement.model.FileInformation
import contentmanagement.model.image.ImageDescription.{ServerImageDescription, SvgImageDescription, UploadImageDescription}
import contentmanagement.model.image.{FullImage, ImageDescription}
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.html.Image
import util.TypeConversion

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.util.{Failure, Success}

abstract class DataStorage[I, O](storageName: String, debug: Boolean) {

  protected def executeLoading(in: I)(ec: ExecutionContext): Future[O]

  protected def initialValueWhileLoading(in: I): O

  private val cachedOutputVars: mutable.HashMap[I, Var[Option[O]]] = new mutable.HashMap(50, 0.25)

  private var cache_hits: Long = 0
  private var cache_misses: Long = 0

  private var execution_requested: Long = 0
  private var execution_succeeded: Long = 0

  private def logInfo(str: String): Unit = if (debug) {
    println(s"[INFO] for data storage '$storageName': " + str
      + s"\n    cache performance (${cachedOutputVars.size} elements): $cache_hits  hits + $cache_misses +  misses"
      + s"\n    calculation history: $execution_succeeded/$execution_requested succeeded so far (${execution_succeeded * 1.0 / execution_requested}%")
  }

  private def logError(str: String, throwable: Throwable): Unit = {
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
      logInfo(s"cache hit for input '${input.toString}''")
      if (forceReloading) requestExecution(input, resultVar)(ec)
      cachedOutputVars(input)
    }
    else {
      cache_misses = cache_misses + 1
      logInfo(s"cache miss for input '${input.toString}''")
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
        logInfo(s"Successfully calculated output for input '${input.toString}''")
        updateVar.set(Some(outputData))
      }
      case Failure(error) => logError(s"Failed to load output for input '${input.toString}", error)
    }(ec)
  }

  def startLoading(input: I)(implicit ec: ExecutionContext): Unit = loadIntoVariable(input)(ec)


}

