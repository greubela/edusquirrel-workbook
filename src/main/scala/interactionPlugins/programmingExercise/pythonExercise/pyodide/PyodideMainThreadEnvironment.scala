package interactionPlugins.programmingExercise.pythonExercise.pyodide

import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideBackends.*

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.JSGlobal

object PyodideMainThreadEnvironment {
  @js.native
  trait Pyodide extends js.Object {
    val globals: js.Dynamic = js.native

    def registerJsModule(name: String, module: js.Object): Unit = js.native

    def setStdout(options: js.Object): Unit = js.native

    def setStderr(options: js.Object): Unit = js.native

    def runPythonAsync(code: String): js.Promise[js.Any] = js.native
  }

  @js.native
  @JSGlobal("loadPyodide")
  private def loadPyodide(): js.Promise[Pyodide] = js.native
}

final class PyodideMainThreadEnvironment {
  import PyodideMainThreadEnvironment.*

  private var pyodidePromise: Option[Future[Pyodide]] = None
  private var pyodideInstance: Option[Pyodide] = None

  private var callbackOps = Vector.empty[CallbackOp]
  private val stdoutBuffer = mutable.ArrayBuffer.empty[String]
  private val stderrBuffer = mutable.ArrayBuffer.empty[String]

  private val backends = mutable.Map.empty[String, SyncModuleBackend]
  private val callbackMethodsByModule = mutable.Map.empty[String, Vector[String]]

  private val preheated: Future[Unit] = ensurePyodide().map(_ => ())

  def register(syncBackend: SyncModuleBackend): Unit = {
    backends.update(syncBackend.moduleName, syncBackend)
    pyodideInstance.foreach(_.registerJsModule(syncBackend.moduleName, createModuleProxy(syncBackend.moduleName)))
  }

  def addCallbacks(moduleName: String, methodNames: Seq[String]): Future[Unit] =
    afterPreheat {
      callbackMethodsByModule.update(moduleName, methodNames.toVector)
      pyodideInstance.foreach(_.registerJsModule(moduleName, createModuleProxy(moduleName)))
      Future.successful(())
    }

  def run(code: String, config: PythonRunConfig = PythonRunConfig()): Future[PythonRunReport] =
    afterPreheat {
      val readyPyodide = if config.resetGlobals then resetInternal() else ensurePyodide()

      readyPyodide.flatMap { pyodide =>
        clearBuffers()
        setStreams(pyodide, config.captureStdout, config.captureStderr)
        applyContext(pyodide, config.context)

        val p = Promise[PythonRunReport]()
        pyodide.runPythonAsync(code).`then`[Unit]({ (_: js.Any) =>
          p.success(PythonRunReport(callbackOps, stdoutBuffer.mkString, stderrBuffer.mkString))
          ()
        }, { (err: Any) =>
          p.failure(PythonWorkerFailure(
            message = Option(err).map(_.toString).getOrElse("Pyodide run failed"),
            stdout = stdoutBuffer.mkString,
            stderr = stderrBuffer.mkString
          ))
          ()
        })
        p.future
      }
    }

  def snapshotGlobals(): Future[js.Dictionary[js.Any]] =
    afterPreheat {
      ensurePyodide().map { pyodide =>
        pyodide.globals
          .toJs(js.Dynamic.literal(dict_converter = js.Dynamic.global.Object.fromEntries))
          .asInstanceOf[js.Dictionary[js.Any]]
      }
    }

  def reset(): Future[Unit] =
    afterPreheat {
      resetInternal().map(_ => ())
    }

  def terminate(): Unit = {
    pyodideInstance = None
    pyodidePromise = None
    callbackOps = Vector.empty
    stdoutBuffer.clear()
    stderrBuffer.clear()
  }

  private def ensurePyodide(): Future[Pyodide] =
    pyodideInstance match {
      case Some(instance) => Future.successful(instance)
      case None =>
        pyodidePromise.getOrElse {
          val created = fromPromise(loadPyodide()).map { pyodide =>
            pyodideInstance = Some(pyodide)
            reinstallModules(pyodide)
            pyodide
          }
          pyodidePromise = Some(created)
          created
        }
    }

  private def resetInternal(): Future[Pyodide] = {
    pyodideInstance = None
    pyodidePromise = None
    ensurePyodide()
  }

  private def reinstallModules(pyodide: Pyodide): Unit = {
    val allModules = (backends.keySet ++ callbackMethodsByModule.keySet).toSeq
    allModules.foreach { moduleName =>
      pyodide.registerJsModule(moduleName, createModuleProxy(moduleName))
    }
  }

  private def createModuleProxy(moduleName: String): js.Object = {
    val handler = js.Dynamic.literal(
      get = { (_: js.Any, prop: js.Any) =>
        val callbackName = prop.toString
        callbackName match {
          case "__name__" => moduleName
          case "__package__" => ""
          case "__doc__" => s"Proxy module for $moduleName"
          case "__all__" => callbackMethodsByModule.getOrElse(moduleName, Vector.empty).toJSArray
          case name if name.startsWith("__") => js.undefined
          case _ =>
            ((rawArgs: js.Any) => {
              val args = toSeq(rawArgs)
              callbackOps = callbackOps :+ CallbackOp(moduleName, callbackName, args.toVector)
              backends.get(moduleName).foreach(_.handleModuleCall(callbackName, toJsDataVariables(args)))
              js.undefined
            }): js.Function1[js.Any, js.Any]
        }
      }: js.Function2[js.Any, js.Any, js.Any]
    )

    js.Dynamic
      .newInstance(js.Dynamic.global.Proxy)(js.Dynamic.literal(), handler)
      .asInstanceOf[js.Object]
  }

  private def clearBuffers(): Unit = {
    callbackOps = Vector.empty
    stdoutBuffer.clear()
    stderrBuffer.clear()
  }

  private def setStreams(pyodide: Pyodide, captureStdout: Boolean, captureStderr: Boolean): Unit = {
    pyodide.setStdout(js.Dynamic.literal(
      batched = ((text: String) => if captureStdout then stdoutBuffer += text else ())
    ))
    pyodide.setStderr(js.Dynamic.literal(
      batched = ((text: String) => if captureStderr then stderrBuffer += text else ())
    ))
  }

  private def applyContext(pyodide: Pyodide, context: js.Dictionary[js.Any]): Unit = {
    context.foreach { case (key, value) =>
      pyodide.globals.set(key, value)
    }
  }

  private def afterPreheat[A](fa: => Future[A]): Future[A] =
    preheated.flatMap(_ => fa)

  private def fromPromise[A](promise: js.Promise[A]): Future[A] = {
    val p = Promise[A]()
    promise.`then`[Unit]({ (value: A) => p.success(value); () }, { (err: Any) => p.failure(js.JavaScriptException(err)); () })
    p.future
  }

  private def toSeq(rawArgs: js.Any): Seq[js.Any] =
    if js.isUndefined(rawArgs) || rawArgs == null then Seq.empty
    else if js.Array.isArray(rawArgs) then rawArgs.asInstanceOf[js.Array[js.Any]].toSeq
    else Seq(rawArgs)

  private def toJsDataVariables(args: Seq[js.Any]): Seq[JsDataVariable] =
    args.zipWithIndex.map { case (value, idx) =>
      JsDataVariable(
        varName = s"arg$idx",
        jsTypeOf = js.typeOf(value),
        stringRepresentation = value.toString
      )
    }
}
