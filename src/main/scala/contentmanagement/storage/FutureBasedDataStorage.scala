package contentmanagement.storage
/*
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.Signal

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

abstract class FutureBasedDataStorage[I, O](override val storageName: String, override val debug: Boolean, val ec: ExecutionContext) extends DataStorage[I, O] {

  protected def executeLoading(in: I): Future[O]

  protected def initialValueWhileLoading(in: I): Option[O]

  /*def createSignalDependendVar(inputSignal: Signal[I])(implicit ec: ExecutionContext): Var[Option[O]] = {
    val resultVar: Var[Option[O]] = Var(None)
    inputSignal.foreach(newValue => {
      val actualVar = loadIntoVariable(newValue)(ec)
      resultVar.set(actualVar.now())
      actualVar.signal.foreach(newValue => {
        resultVar.set(newValue)
      })(unsafeWindowOwner)

    })(unsafeWindowOwner)
    resultVar
  }*/

  protected def startCalculatingOutput(input: I, callbackOnFinished: Try[O] => Any): Unit = {
    executeLoading(input).onComplete(resTry => onFinished(resTry))(ec)
  }

  protected override def calculateAndUpdateOutputVar(input: I, outputVariable: Var[Option[O]]): Unit = {
    .onComplete {

    }(ec)
  }

  /*
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
*/
}
*/