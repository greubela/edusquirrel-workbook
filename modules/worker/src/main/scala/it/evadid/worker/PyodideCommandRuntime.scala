package it.evadid.worker

import it.evadid.core.datastructures.state.State
import it.evadid.distribution.commandTypes.WorkerCommands.*
import it.evadid.util.logging.Logger
import it.evadid.facades.{Pyodide, PyodideRuntime}
import it.evadid.vm.simulation.python.{PythonConsoleProgram, PythonTurtleCalls}
import it.evadid.worker.PyodideRuntimeState.{Interpreter, JobOutput}
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.util.Try

/** One lazy interpreter per worker; only Python jobs are serialized, including initialization. */
object PyodideCommandRuntime {
  private given ExecutionContext = ExecutionContext.global
  private val state = State(PyodideRuntimeState.initial)
  // Jobs are serialized. Retained Python imports must keep writing into the current job’s output.
  private val output = State(JobOutput.empty)

  /** The worker is single-threaded, so reading and replacing the job chain cannot interleave. */
  def run(request: RunPythonRequest, logger: Logger): Future[RunPythonResult] = {
    val (next, result) = state.now().enqueue(() => execute(request, logger))
    state.set(next)
    result
  }

  private def load(): Future[PyodideRuntime] = state.now().interpreter match {
    case Interpreter.Ready(runtime, _) => Future.successful(runtime)
    case Interpreter.Loading(runtime) => runtime
    case Interpreter.NotStarted =>
      val created = Pyodide.createInstance()
      state.update(_.startLoading(created))
      created.transform { loaded =>
        state.update(_.finishLoading(loaded))
        loaded
      }
  }

  private def json(value: js.Any): ujson.Value = {
    if (!isDefined(value)) ujson.Null
    else {
      val encoded = js.JSON.stringify(value, ((_: String, item: js.Any) => {
        // Pyodide turns Python None into undefined. Keep it as JSON null so object keys survive.
        if (js.isUndefined(item)) null
        else {
          if (js.typeOf(item) == "number")
            require(item.asInstanceOf[Double].isFinite, "Python result contains a non-finite number")
          if (item != null && js.typeOf(item) == "object") {
            val tag = js.Dynamic.global.Object.prototype.selectDynamic("toString").call(item).asInstanceOf[String]
            require(tag != "[object Set]" && tag != "[object Map]", "Python result must use JSON-compatible objects and lists")
          }
          item
        }
      }): js.Function2[String, js.Any, js.Any])
      require(!js.isUndefined(encoded), "Python result is not JSON serializable")
      ujson.read(encoded)
    }
  }

  // Pyodide needs undefined for None and Map for arbitrary dictionary keys.
  private def pythonContextValue(value: ujson.Value): js.Any = value match {
    case ujson.Null => js.undefined
    case ujson.Str(text) => text
    case ujson.Num(number) =>
      require(number.isFinite, "Python context contains a non-finite number")
      number
    case ujson.Bool(flag) => flag
    case ujson.Arr(values) => values.map(pythonContextValue).toJSArray
    case ujson.Obj(fields) =>
      new js.Map[String, js.Any](fields.toList.map { (key, value) =>
        js.Tuple2(key, pythonContextValue(value))
      }.toJSArray)
  }

  private val identifier = "[A-Za-z_][A-Za-z0-9_]*"

  private def isDefined(value: js.Any): Boolean = value != null && !js.isUndefined(value)

  /** A Pyodide proxy for a Python object; it must be converted to plain JavaScript and destroyed. */
  private def isPyProxy(value: js.Any): Boolean =
    isDefined(value) && js.typeOf(value.asInstanceOf[js.Dynamic].toJs) == "function"

  private def proxyToJs(value: js.Any): js.Any = value.asInstanceOf[js.Dynamic].toJs(
    js.Dynamic.literal(dict_converter = js.Dynamic.global.Object.fromEntries, create_pyproxies = false))

  private def newDecoder(): js.Dynamic = js.Dynamic.newInstance(js.Dynamic.global.TextDecoder)("utf-8")

  /** Pyodide output stream that streams decoded text to `append` only when `capture` is set. */
  private def outputStream(capture: Boolean, decoder: js.Dynamic)(append: String => Unit): js.Object =
    js.Dynamic.literal(write = ((bytes: js.typedarray.Uint8Array) => {
      if (capture) append(decoder.decode(bytes, js.Dynamic.literal(stream = true)).asInstanceOf[String])
      bytes.length
    }): js.Function1[js.typedarray.Uint8Array, Int])

  private def program(request: RunPythonRequest): String = request.turtleCalls match {
    case PythonTurtleCalls.Native => request.code
    case PythonTurtleCalls.Logged => PythonConsoleProgram.wrap(request.code)
    case PythonTurtleCalls.Ignored => PythonConsoleProgram.wrap(request.code, log = false)
  }

  /** The persistent globals, recreated when requested or before the first job. */
  private def globalsFor(pyodide: PyodideRuntime, request: RunPythonRequest): js.Dynamic = {
    val current = state.now().session.namespace
    if (request.resetGlobals || current.isEmpty) {
      val replacement = pyodide.runPython("dict()").asInstanceOf[js.Dynamic]
      // State compares sessions when publishing: the previous proxy must still be live during that comparison.
      state.update(_.withNamespace(replacement))
      current.foreach(_.destroy())
    }
    val globals = state.now().session.namespace.get
    request.context.foreach { (key, value) =>
      val converted = pyodide.toPy(pythonContextValue(value))
      try globals.set(key, converted)
      finally if (isDefined(converted) && js.typeOf(converted.destroy) == "function") converted.destroy()
    }
    globals
  }

  /** Replaces the previous job's callback modules; each call is recorded in this job's `output`. */
  private def registerCallbacks(pyodide: PyodideRuntime, callbacks: Map[String, Seq[String]], output: State[JobOutput]): Unit = {
    state.now().session.modules.foreach { module =>
      pyodide.unregisterJsModule(module)
      pyodide.runPython(s"import sys; sys.modules.pop('$module', None)")
    }
    state.update(_.withoutModules)
    callbacks.foreach { (module, methods) =>
      require(module.matches(identifier), "Invalid Python callback module")
      pyodide.registerJsModule(module, callbackModule(module, methods, output))
      state.update(_.withModule(module))
    }
  }

  private def callbackModule(module: String, methods: Seq[String], output: State[JobOutput]): js.Dictionary[js.Any] = {
    val functions = js.Dictionary.empty[js.Any]
    methods.foreach { method =>
      require(method.matches(identifier), "Invalid Python callback method")
      val handler = js.Dynamic.literal(apply = ((_: js.Any, _: js.Any, args: js.Array[js.Any]) => {
        val values = args.toList.map(arg => json(if (isPyProxy(arg)) proxyToJs(arg) else arg))
        output.update(_.withCallback(PythonCallback(module, method, values)))
        js.undefined
      }): js.Function3[js.Any, js.Any, js.Array[js.Any], js.Any])
      functions(method) = js.Dynamic.newInstance(js.Dynamic.global.Proxy)((() => ()): js.Function0[Unit], handler)
    }
    functions
  }

  /** Routes Pyodide's streams into `output`; the returned function flushes both decoders into it. */
  private def captureOutput(pyodide: PyodideRuntime, request: RunPythonRequest, output: State[JobOutput]): () => Unit = {
    val stdoutDecoder = newDecoder()
    val stderrDecoder = newDecoder()
    pyodide.setStdout(outputStream(request.captureStdout, stdoutDecoder)(text => output.update(_.withStdout(text))))
    pyodide.setStderr(outputStream(request.captureStderr, stderrDecoder)(text => output.update(_.withStderr(text))))
    () => {
      Try(pyodide.runPython("import sys; sys.stdout.flush(); sys.stderr.flush()"))
      output.update(_.withStdout(stdoutDecoder.decode().asInstanceOf[String]).withStderr(stderrDecoder.decode().asInstanceOf[String]))
    }
  }

  private def execute(request: RunPythonRequest, logger: Logger): Future[RunPythonResult] =
    load().flatMap { pyodide =>
      output.set(JobOutput.empty)
      val flushOutput = captureOutput(pyodide, request, output)
      val globals = globalsFor(pyodide, request)
      registerCallbacks(pyodide, request.callbacks, output)
      evaluate(pyodide, request, globals)
        .map { value =>
          flushOutput()
          result(value, output.now())
        }.recoverWith { case error =>
          flushOutput()
          output.now().stdout.foreach(logger.logInfo(_))
          output.now().stderr.foreach(logger.logError(_))
          Future.failed(error)
        }
    }

  private def evaluate(pyodide: PyodideRuntime, request: RunPythonRequest, globals: js.Dynamic): Future[js.Any] = {
    val prepared = if (request.packages.isEmpty) Future.successful(())
      else pyodide.loadPackage(request.packages.toJSArray).asInstanceOf[js.Promise[js.Any]].toFuture.map(_ => ())
    prepared.flatMap(_ => pyodide.loadPackagesFromImports(request.code).asInstanceOf[js.Promise[js.Any]].toFuture)
      .flatMap(_ => pyodide.runPythonAsync(program(request), js.Dynamic.literal(globals = globals))
        .asInstanceOf[js.Promise[js.Any]].toFuture)
  }

  private def result(value: js.Any, output: JobOutput): RunPythonResult = {
    val isProxy = isPyProxy(value)
    try {
      val converted = if (isProxy) proxyToJs(value) else value
      RunPythonResult(json(converted), output.stdout.mkString, output.stderr.mkString, output.callbacks.toList)
    } finally if (isProxy) value.asInstanceOf[js.Dynamic].destroy()
  }
}
