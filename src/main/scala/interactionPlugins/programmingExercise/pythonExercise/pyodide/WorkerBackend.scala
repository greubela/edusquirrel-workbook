package interactionPlugins.programmingExercise.pythonExercise.pyodide





import com.raquo.laminar.api.L.Var
import interactionPlugins.programmingExercise.pythonExercise.data.*
import interactionPlugins.programmingExercise.pythonExercise.pyodide.*

import org.scalajs.dom

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

import interactionPlugins.programmingExercise.pythonExercise.data.*
import interactionPlugins.programmingExercise.pythonExercise.pyodide.*
import interactionPlugins.programmingExercise.pythonExercise.data.PythonExecutionResult.*
import interactionPlugins.programmingExercise.pythonExercise.data.PythonUnitTestResult.*
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment.*

private[pyodide] final class WorkerBackend() extends Backend {

/*
  private val worker =
    new dom.Worker(
      "./js/PyodideWorker.js",
      js.Dynamic.literal(`type` = "module").asInstanceOf[dom.WorkerOptions]
    )

  private val pending = mutable.Map.empty[String, Promise[js.Dynamic]]
  private val moduleCallbacks = mutable.Map.empty[(String, String), Seq[js.Any] => Unit]
  private var initialized = false
  private var requestCounter = 0

  worker.onmessage = { (e: dom.MessageEvent) =>
    val data = e.data.asInstanceOf[js.Dynamic]
    data.`type`.asInstanceOf[String] match {
      case "stdout" => appendVar(stdoutVar, data.text.asInstanceOf[String])
      case "stderr" => appendVar(stderrVar, data.text.asInstanceOf[String])
      case "module-callback" =>
        val moduleName = data.moduleName.asInstanceOf[String]
        val functionName = data.functionName.asInstanceOf[String]
        val args = data.args.asInstanceOf[js.Array[js.Any]].toSeq
        moduleCallbacks.get((moduleName, functionName)).foreach(cb => cb(args))
      case "ready" | "registered" | "result" | "error" =>
        val requestId = data.requestId.asInstanceOf[String]
        pending.remove(requestId).foreach { p =>
          if data.`type`.asInstanceOf[String] == "error" then p.failure(new RuntimeException(data.error.asInstanceOf[String]))
          else p.success(data)
        }
      case other => dom.console.warn(s"Unhandled worker message type: $other")
    }
  }: js.Function1[dom.MessageEvent, Any]

  private def nextRequestId(): String = {
    requestCounter += 1
    s"req-$requestCounter"
  }

  private def send(msgType: String, payload: js.Dictionary[js.Any] = js.Dictionary.empty): Future[js.Dynamic] = {
    val requestId = nextRequestId()
    val promise = Promise[js.Dynamic]()
    pending.update(requestId, promise)
    val body = js.Dynamic.literal(`type` = msgType, requestId = requestId)
    payload.foreach { case (k, v) => body.updateDynamic(k)(v) }
    worker.postMessage(body)
    promise.future
  }

  private def init(): Future[Unit] =
    if initialized then Future.successful(())
    else send("init").map(_ => initialized = true)

  override def registerModule(binding: ModuleBinding): Future[Unit] = {
    binding.callbacks.foreach { case (name, fn) => moduleCallbacks.update((binding.moduleName, name), fn) }
    init().flatMap { _ =>
      send(
        "registerModule",
        js.Dictionary(
          "moduleName" -> binding.moduleName,
          "functionNames" -> binding.callbacks.keys.toJSArray
        )
      ).map(_ => ())
    }
  }

  override def executeCode(pythonCode: String, maxExecutedLines: Option[Int]): Future[ExecutionResult] = {
    clearVar(stdoutVar)
    clearVar(stderrVar)
    init().flatMap { _ =>
      val payload = js.Dictionary[js.Any]("code" -> pythonCode)
      maxExecutedLines.foreach(limit => payload.update("maxExecutedLines", limit))
      send("executeCode", payload).map { msg =>
        val parsed = msg.payload.asInstanceOf[js.Dynamic]
        parseExecutionResult(pythonCode, stdoutVar.now(), stderrVar.now(), parsed)
      }
    }
  }

  override def executeUnitTest(
      pythonCode: String,
      pyUnitTest: PythonUnitTest,
      maxExecutedLines: Option[Int]
  ): Future[PythonUnitTestResult] = {
    clearVar(stdoutVar)
    clearVar(stderrVar)
    init().flatMap { _ =>
      val payload = js.Dictionary[js.Any](
        "code" -> pythonCode,
        "testCode" -> pyUnitTest.testCode,
        "testName" -> pyUnitTest.testName
      )
      maxExecutedLines.foreach(limit => payload.update("maxExecutedLines", limit))
      send("executeUnitTest", payload).map { msg =>
        val parsed = msg.payload.asInstanceOf[js.Dynamic]
        val execution = parseExecutionResult(
          pythonCode,
          stdoutVar.now() + optString(parsed.stdout).getOrElse(""),
          stderrVar.now(),
          parsed
        )
        PythonUnitTestResult(
          test = pyUnitTest,
          execution = execution,
          success = boolValue(parsed.success),
          testRunnerOutput = optString(parsed.stdout).getOrElse(""),
          failures = jsonArrayToList(parsed.failures),
          errors = jsonArrayToList(parsed.errors)
        )
      }
    }
  }

  override def destroy(): Unit = {
    worker.postMessage(js.Dynamic.literal(`type` = "destroy"))
    worker.terminate()
  }
  
 */


  def registerModule(
                      moduleName: String,
                      callbacks: Map[String, Seq[js.Any] => Unit]
                    ): Future[Unit] = ???

  def executeCodeFull(
                       request: PythonExecutionRequest
                     ): Future[PythonExecutionResult] = ???

  def executeCodeLinewise(
                           request: PythonExecutionRequest,
                           updateAtLeastEveryNLines: Int = 1
                         ): Var[PythonExecutionResult] = ???

  def executeUnitTestsFull(
                            pythonCode: PythonExecutionRequest,
                            unitTests: List[PythonUnitTest],
                          ): Future[PythonUnitTestResult] = ???

  def executeUnitTestLinewise(
                               pythonCode: PythonExecutionRequest,
                               unitTests: List[PythonUnitTest],
                               updateAtLeastEveryNLines: Int = 100,
                             ): Var[PythonUnitTestResult] = ???

  def destroy(): Unit = ???
}
