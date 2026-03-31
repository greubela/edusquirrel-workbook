package interactionPlugins.programmingExercise.pythonExercise.pyodide

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import interactionPlugins.programmingExercise.pythonExercise.data.*
import interactionPlugins.programmingExercise.pythonExercise.data.PythonUnitTestResult.{GradingStatus, PythonUnitTestGradingResult}
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment.{AsyncModuleBackend, JsDataVariable}
import org.scalajs.dom
import util.web.JsHelpers.{asBoolean, asStringOption, asStringSeq}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

class PyodideWorkerEnvironment extends PyodideEnvironment {

  private val worker = new dom.Worker(
    "./js/PyodideLoader.js",
    js.Dynamic.literal(`type` = "module").asInstanceOf[dom.WorkerOptions]
  )

  private val pending: mutable.Map[String, Promise[js.Dynamic]] = mutable.Map.empty
  private val registeredBackends: mutable.Map[String, AsyncModuleBackend] = mutable.Map.empty
  private var requestCounter: Long = 0
  private var destroyed = false
  private var initFuture: Option[Future[Unit]] = None


  private def failAllPending(cause: Throwable): Unit = {
    pending.values.foreach(_.tryFailure(cause))
    pending.clear()
  }

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

  worker.onerror = { (e: dom.ErrorEvent) =>
    val message = Option(e.message).filter(_.nonEmpty).getOrElse("Pyodide worker error")
    failAllPending(new RuntimeException(message))
  }: js.Function1[dom.ErrorEvent, Any]


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

  private def executeUnitTestsBatch(
                                     pythonCode: PythonExecutionRequest,
                                     unitTests: List[PythonUnitTest]
                                   ): Future[List[PythonUnitTestGradingResult]] =
    init().flatMap { _ =>
      val payload = js.Dictionary[js.Any](
        "code" -> pythonCode.pythonCode,
        "tests" -> unitTests.zipWithIndex.map { case (test, idx) =>
          js.Dynamic.literal(index = idx, testCode = test.testCode, testName = test.testName)
        }.toJSArray
      )
      pythonCode.maxLinesToExecute.foreach(limit => payload.update("maxExecutedLines", limit))

      send("executeUnitTestsBatch", payload).map { message =>
        val batch = message.payload.asInstanceOf[js.Dynamic]
        val lineLimitHit = asBoolean(batch.lineLimitHit)
        val sharedStdout = asStringOption(batch.stdout).getOrElse("")
        val sharedStderr = asStringOption(batch.stderr).getOrElse("")
        val batchException = asStringOption(batch.exception).orNull

        batch.tests.asInstanceOf[js.Array[js.Dynamic]].toSeq.map { testPayload =>
          val index = testPayload.index.asInstanceOf[Int]
          val test = unitTests(index)
          val details = (asStringSeq(testPayload.failures) ++ asStringSeq(testPayload.errors)).mkString("\n").trim
          val combinedStderr = List(sharedStderr, asStringOption(testPayload.stderr).getOrElse(""), details).filter(_.nonEmpty).mkString("\n")
          val parsedExecutionPayload = js.Dynamic.literal(
            success = asBoolean(testPayload.success) && !lineLimitHit,
            stdout = sharedStdout + asStringOption(testPayload.stdout).getOrElse(""),
            stderr = combinedStderr,
            exception = if lineLimitHit then batchException else asStringOption(testPayload.exception).orNull,
            globals = batch.globals,
            locals = batch.locals,
            linesExecuted = batch.linesExecuted,
            lineLimitHit = batch.lineLimitHit
          )
          val executionResult = PythonExecutionResult(pythonCode, PyodideHelper.toExecutionState(parsedExecutionPayload))
          val gradingStatus = if executionResult.state.runningState == PythonExecutionResult.PythonExecutionRunningState.FINISHED_SUCCESS then GradingStatus.SUCCESS else GradingStatus.FAILED
          PythonUnitTestGradingResult(test = test, result = executionResult, gradingStatus = gradingStatus)
        }.toList
      }
    }

  override def executeUnitTestsFull(
                                     pythonCode: PythonExecutionRequest,
                                     unitTests: List[PythonUnitTest]
                                   ): Future[PythonUnitTestResult] =
    executeUnitTestsBatch(pythonCode, unitTests).map { tests =>
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

    executeUnitTestsBatch(pythonCode, unitTests).foreach { completed =>
      resultVar.set(PythonUnitTestResult(pythonCode, completed.toSet))
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
    failAllPending(new RuntimeException("Worker destroyed"))
    registeredBackends.clear()
    initFuture = None
  }
}
