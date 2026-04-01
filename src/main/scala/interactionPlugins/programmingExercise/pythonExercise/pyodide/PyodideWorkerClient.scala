package interactionPlugins.programmingExercise.pythonExercise.pyodide


import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideBackends.*
import org.scalajs.dom

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

final class PyodideWorkerClient(workerUrl: String = "./js/pyodide-worker.js") {

  private val worker =
    new dom.Worker(
      workerUrl,
      js.Dynamic.literal(`type` = "module").asInstanceOf[dom.WorkerOptions]
    )

  private var nextId = 1
  private val pending = js.Dictionary[Promise[js.Any]]()

  private val preheated: Future[Unit] = requestUnit("init")

  worker.onmessage = { (event: dom.MessageEvent) =>
    val data = dyn(event.data.asInstanceOf[js.Any])
    val id = asInt(data.id).toString

    pending.get(id).foreach { promise =>
      pending -= id
      if (asBoolean(data.ok)) {
        promise.success(data.payload.asInstanceOf[js.Any])
      } else {
        promise.failure(readFailure(data.error))
      }
    }
  }

  worker.onerror = { (event: dom.ErrorEvent) =>
    val ex = js.JavaScriptException(
      s"${event.message} (${event.filename}:${event.lineno}:${event.colno})"
    )
    pending.keys.foreach { key =>
      pending(key).failure(ex)
      pending -= key
    }
  }

  def addCallbacks(moduleName: String, methodNames: Seq[String]): Future[Unit] =
    afterPreheat {
      requestUnit(
        "addCallbacks",
        obj(
          "moduleName" -> moduleName,
          "methodNames" -> methodNames.toJSArray
        )
      )
    }

  private def handleLibrary(callbacks: List[CallbackOp], library: CallbackLibrary): Unit = {
    println("handleLibrary: " + callbacks.size + " callbacks, library '" + library.moduleName + "' (" + library.methodMap.size + " methods)")
    callbacks.foreach(op => {
      println("---- EXECUTE OP: " + op.module + "." + op.method + "(" + op.args.mkString(", ") + ")")
      library.methodMap.get(op.method).foreach { method => method(op.args) }
    })
  }

  def runWithCallbackLibrary(code: String, callbackLibrary: CallbackLibrary, config: PythonRunConfig = PythonRunConfig()): Future[PythonRunReport] = {
    val res = Promise[PythonRunReport]()
    addCallbacks(callbackLibrary.moduleName, callbackLibrary.methodMap.keys.toSeq)

    val exRes = run(code, config)
    exRes.onComplete {
      case scala.util.Success(runReport: PythonRunReport) => {
        println("run fully executed, incl. all commands ")
        handleLibrary(runReport.callbackOps.toList, callbackLibrary)
        res.success(runReport)
      }
      case scala.util.Failure(exception) => {
        res.failure(exception)
      }
    }

    res.future
  }

  def run(
           code: String,
           config: PythonRunConfig = PythonRunConfig()
         ): Future[PythonRunReport] =
    afterPreheat {
      request("run",
        obj(
          "code" -> code,
          "context" -> config.context,
          "resetGlobals" -> config.resetGlobals,
          "captureStdout" -> config.captureStdout,
          "captureStderr" -> config.captureStderr
        )
      ).map(readRunReport)
    }

  def snapshotGlobals(): Future[js.Dictionary[js.Any]] =
    afterPreheat {
      request("snapshotGlobals").map(asDict)
    }

  def reset(): Future[Unit] =
    afterPreheat {
      requestUnit("reset")
    }

  def terminate(): Unit =
    worker.terminate()

  private def afterPreheat[A](fa: => Future[A]): Future[A] =
    preheated.flatMap(_ => fa)

  private def requestUnit(kind: String, payload: js.Object = emptyObj): Future[Unit] =
    request(kind, payload).map(_ => ())

  private def request(kind: String, payload: js.Object = emptyObj): Future[js.Any] = {
    val id = nextId
    nextId += 1

    val promise = Promise[js.Any]()
    pending(id.toString) = promise

    worker.postMessage(
      obj(
        "id" -> id,
        "kind" -> kind,
        "payload" -> payload
      )
    )

    promise.future
  }

  private def readRunReport(value: js.Any): PythonRunReport = {
    val payload = dyn(value)
    PythonRunReport(
      callbackOps = asArray(payload.callbackOps).iterator.map(readCallbackOp).toVector,
      stdout = asString(payload.stdout),
      stderr = asString(payload.stderr)
    )
  }

  private def readCallbackOp(value: js.Any): CallbackOp = {
    val op = dyn(value)
    CallbackOp(
      module = asString(op.module),
      method = asString(op.method),
      args = asArray(op.args).toVector
    )
  }

  private def readFailure(value: js.Any): PythonWorkerFailure = {
    val error = dyn(value)
    PythonWorkerFailure(
      message = asString(error.message),
      stdout = asString(error.stdout),
      stderr = asString(error.stderr)
    )
  }

  private def obj(fields: (String, js.Any)*): js.Object =
    js.Dynamic.literal(fields *).asInstanceOf[js.Object]

  private def dyn(value: js.Any): js.Dynamic =
    value.asInstanceOf[js.Dynamic]

  private def asString(value: js.Any): String =
    value.asInstanceOf[String]

  private def asBoolean(value: js.Any): Boolean =
    value.asInstanceOf[Boolean]

  private def asInt(value: js.Any): Int =
    value.asInstanceOf[Int]

  private def asArray(value: js.Any): js.Array[js.Any] =
    value.asInstanceOf[js.Array[js.Any]]

  private def asDict(value: js.Any): js.Dictionary[js.Any] =
    value.asInstanceOf[js.Dictionary[js.Any]]

  private val emptyObj: js.Object =
    (new js.Object).asInstanceOf[js.Object]
}
