package interactionPlugins.programmingExercise.pythonExercise.pyodide

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import interactionPlugins.programmingExercise.pythonExercise.data.*
import interactionPlugins.programmingExercise.pythonExercise.data.PythonUnitTestResult.{GradingStatus, PythonUnitTestGradingResult}
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment.{AsyncModuleBackend, JsDataVariable, SyncModuleBackend}
import org.scalajs.dom
import util.web.JsHelpers.{asBoolean, asStringSeq}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

class PyodideWorkerEnvironment extends PyodideEnvironment {

  private val worker =
    new dom.Worker(
      "./js/PyodideLoader.js",
      js.Dynamic.literal(`type` = "module").asInstanceOf[dom.WorkerOptions]
    )

  private val pending: mutable.Map[String, Promise[js.Dynamic]] = mutable.Map.empty
  private val registeredBackends: mutable.Map[String, AsyncModuleBackend] = mutable.Map.empty
  private var requestCounter: Long = 0
  private var destroyed = false
  private var initFuture: Option[Future[Unit]] = None

  worker.onmessage = { (e: dom.MessageEvent) =>
    val data = e.data.asInstanceOf[js.Dynamic]
    data.`type`.asInstanceOf[String] match {
      case "module-callback" =>
        val moduleName = data.moduleName.asInstanceOf[String]
        val functionName = data.functionName.asInstanceOf[String]
        val args = data.args.asInstanceOf[js.Array[js.Any]].toSeq.zipWithIndex.map { case (value, idx) =>
          JsDataVariable(
            varName = s"arg$idx",
            jsTypeOf = js.typeOf(value),
            stringRepresentation = value.toString
          )
        }
        registeredBackends.get(moduleName).foreach(_.handleModuleCall(functionName, args))
      case "ready" | "registered" | "result" | "reset-done" =>
        val requestId = data.requestId.asInstanceOf[String]
        pending.remove(requestId).foreach(_.success(data))
      case "error" =>
        val requestId = data.requestId.asInstanceOf[String]
        val error = Option(data.error).map(_.toString).getOrElse("Unknown worker error")
        pending.remove(requestId).foreach(_.failure(new RuntimeException(error)))
      case _ =>
        ()
    }
  }: js.Function1[dom.MessageEvent, Any]

  private def nextRequestId(): String = {
    requestCounter += 1
    s"req-$requestCounter"
  }

  private def send(messageType: String, payload: js.Dictionary[js.Any] = js.Dictionary.empty): Future[js.Dynamic] = {
    if destroyed then Future.failed(new IllegalStateException("Worker environment already destroyed"))
    else {
      val requestId = nextRequestId()
      val promise = Promise[js.Dynamic]()
      pending.update(requestId, promise)
      val msg = js.Dynamic.literal(`type` = messageType, requestId = requestId)
      payload.foreach((k, v) => msg.updateDynamic(k)(v))
      worker.postMessage(msg)
      promise.future
    }
  }

  private def init(): Future[Unit] =
    initFuture.getOrElse {
      val future = send("init").map(_ => ())
      initFuture = Some(future)
      future
    }

  private def parseExecutionResult(request: PythonExecutionRequest, message: js.Dynamic): PythonExecutionResult = {
    val parsed = message.payload.asInstanceOf[js.Dynamic]
    PythonExecutionResult(request, PyodideHelper.toExecutionState(parsed))
  }

  override def register(asyncBackend: AsyncModuleBackend): Unit = {
    if destroyed then throw new IllegalStateException("Worker environment already destroyed")
    registeredBackends.update(asyncBackend.moduleName, asyncBackend)
    init().flatMap(_ => send("registerModule", js.Dictionary("moduleName" -> asyncBackend.moduleName))).failed.foreach(_ => ())
  }

  override def register(syncBackend: SyncModuleBackend): Unit =
    throw new UnsupportedOperationException("Synchronous modules are not supported in worker environments")

  override def executeCodeFull(request: PythonExecutionRequest): Future[PythonExecutionResult] =
    init().flatMap { _ =>
      val payload = js.Dictionary[js.Any]("code" -> request.pythonCode)
      request.maxLinesToExecute.foreach(limit => payload.update("maxExecutedLines", limit))
      send("executeCode", payload).map(message => parseExecutionResult(request, message))
    }

  override def executeCodeLinewise(request: PythonExecutionRequest, updateAtLeastEveryNLines: Int): L.Var[PythonExecutionResult] = {
    val resultVar = Var(PyodideHelper.runningExecutionResult(request))
    executeCodeFull(request).foreach(resultVar.set)
    resultVar
  }

  private def executeSingleUnitTest(
      pythonCode: PythonExecutionRequest,
      test: PythonUnitTest
  ): Future[PythonUnitTestGradingResult] =
    init().flatMap { _ =>
      val payload = js.Dictionary[js.Any](
        "code" -> pythonCode.pythonCode,
        "testCode" -> test.testCode,
        "testName" -> test.testName
      )
      pythonCode.maxLinesToExecute.foreach(limit => payload.update("maxExecutedLines", limit))

      send("executeUnitTest", payload).map { message =>
        val parsed = message.payload.asInstanceOf[js.Dynamic]
        val executionResult = PythonExecutionResult(pythonCode, PyodideHelper.toExecutionState(parsed))
        val gradingStatus = if asBoolean(parsed.success) then GradingStatus.SUCCESS else GradingStatus.FAILED

        val failureLines = asStringSeq(parsed.failures)
        val errorLines = asStringSeq(parsed.errors)
        val details = (failureLines ++ errorLines).mkString("\n").trim
        val updatedResult =
          if details.nonEmpty then executionResult.copy(state = executionResult.state.copy(stderr = List(executionResult.state.stderr, details).filter(_.nonEmpty).mkString("\n")))
          else executionResult

        PythonUnitTestGradingResult(test = test, result = updatedResult, gradingStatus = gradingStatus)
      }
    }

  override def executeUnitTestsFull(
      pythonCode: PythonExecutionRequest,
      unitTests: List[PythonUnitTest]
  ): Future[PythonUnitTestResult] =
    Future.sequence(unitTests.map(executeSingleUnitTest(pythonCode, _))).map { tests =>
      PythonUnitTestResult(userCode = pythonCode, tests = tests.toSet)
    }

  override def executeUnitTestLinewise(
      pythonCode: PythonExecutionRequest,
      unitTests: List[PythonUnitTest],
      updateAtLeastEveryNLines: Int
  ): L.Var[PythonUnitTestResult] = {
    val initial = unitTests.map { test =>
      PythonUnitTestGradingResult(test, PyodideHelper.runningExecutionResult(pythonCode), GradingStatus.UNFINISHED)
    }
    val resultVar = Var(PythonUnitTestResult(pythonCode, initial.toSet))

    unitTests.foldLeft(Future.successful(List.empty[PythonUnitTestGradingResult])) { (accFuture, test) =>
      for {
        acc <- accFuture
        next <- executeSingleUnitTest(pythonCode, test)
      } yield {
        val updated = (acc :+ next).toSet
        val unfinished = unitTests.filterNot(t => updated.exists(_.test == t)).map { remaining =>
          PythonUnitTestGradingResult(remaining, PyodideHelper.runningExecutionResult(pythonCode), GradingStatus.UNFINISHED)
        }
        resultVar.set(PythonUnitTestResult(pythonCode, updated ++ unfinished))
        acc :+ next
      }
    }

    resultVar
  }

  override def resetImportsAndState(): Unit = {
    if destroyed then return
    init().flatMap(_ => send("resetState")).failed.foreach(_ => ())
  }

  override def destroy(): Unit = {
    if destroyed then return
    destroyed = true
    worker.postMessage(js.Dynamic.literal(`type` = "destroy"))
    worker.terminate()
    pending.values.foreach(_.tryFailure(new RuntimeException("Worker destroyed")))
    pending.clear()
    registeredBackends.clear()
    initFuture = None
  }
}
