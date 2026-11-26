package it.evadid.worker

import it.evadid.distribution.commandTypes.WorkerCommands.*
import it.evadid.util.logging.Logger
import it.evadid.facades.{Pyodide, PyodideRuntime}
import it.evadid.vm.simulation.python.{PythonConsoleProgram, PythonTurtleCalls}
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.util.Try

/** One lazy interpreter per worker; only Python jobs are serialized, including initialization. */
object PyodideCommandRuntime {
  private given ExecutionContext = ExecutionContext.global
  private var interpreter = Option.empty[Future[PyodideRuntime]]
  private var namespace = Option.empty[js.Dynamic]
  private var registeredModules = Set.empty[String]
  private var callbackOps = Vector.empty[PythonCallback]
  private var pending: Future[Unit] = Future.successful(())

  private def load(): Future[PyodideRuntime] = interpreter.getOrElse {
    val created = Pyodide.createInstance()
    interpreter = Some(created)
    created.failed.foreach(_ => interpreter = None)
    created
  }

  def run(request: RunPythonRequest, logger: Logger): Future[RunPythonResult] = {
    val result = pending.flatMap(_ => execute(request, logger))
    pending = result.transform(_ => scala.util.Success(()))
    result
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
    if (request.resetGlobals || namespace.isEmpty) {
      namespace.foreach(_.destroy())
      namespace = Some(pyodide.runPython("dict()").asInstanceOf[js.Dynamic])
    }
    val globals = namespace.get
    request.context.foreach { (key, value) =>
      val converted = pyodide.toPy(pythonContextValue(value))
      try globals.set(key, converted)
      finally if (isDefined(converted) && js.typeOf(converted.destroy) == "function") converted.destroy()
    }
    globals
  }

  /** Replaces the previous job's callback modules; each call is recorded in [[callbackOps]]. */
  private def registerCallbacks(pyodide: PyodideRuntime, callbacks: Map[String, Seq[String]]): Unit = {
    registeredModules.foreach { module =>
      pyodide.unregisterJsModule(module)
      pyodide.runPython(s"import sys; sys.modules.pop('$module', None)")
    }
    registeredModules = Set.empty
    callbacks.foreach { (module, methods) =>
      require(module.matches(identifier), "Invalid Python callback module")
      val functions = js.Dictionary.empty[js.Any]
      methods.foreach { method =>
        require(method.matches(identifier), "Invalid Python callback method")
        val handler = js.Dynamic.literal(apply = ((_: js.Any, _: js.Any, args: js.Array[js.Any]) => {
          callbackOps :+= PythonCallback(module, method, args.toList.map(arg => json(if (isPyProxy(arg)) proxyToJs(arg) else arg)))
          js.undefined
        }): js.Function3[js.Any, js.Any, js.Array[js.Any], js.Any])
        functions(method) = js.Dynamic.newInstance(js.Dynamic.global.Proxy)((() => ()): js.Function0[Unit], handler)
      }
      pyodide.registerJsModule(module, functions)
      registeredModules += module
    }
  }

  private def execute(request: RunPythonRequest, logger: Logger): Future[RunPythonResult] = {
    load().flatMap { pyodide =>
      var stdout = Vector.empty[String]
      var stderr = Vector.empty[String]
      callbackOps = Vector.empty
      val stdoutDecoder = newDecoder()
      val stderrDecoder = newDecoder()
      pyodide.setStdout(outputStream(request.captureStdout, stdoutDecoder)(text => stdout :+= text))
      pyodide.setStderr(outputStream(request.captureStderr, stderrDecoder)(text => stderr :+= text))
      def flushOutput(): Unit = {
        Try(pyodide.runPython("import sys; sys.stdout.flush(); sys.stderr.flush()"))
        stdout :+= stdoutDecoder.decode().asInstanceOf[String]
        stderr :+= stderrDecoder.decode().asInstanceOf[String]
      }
      val globals = globalsFor(pyodide, request)
      registerCallbacks(pyodide, request.callbacks)
      val prepared = if (request.packages.isEmpty) Future.successful(())
        else pyodide.loadPackage(request.packages.toJSArray).asInstanceOf[js.Promise[js.Any]].toFuture.map(_ => ())
      prepared.flatMap(_ => pyodide.loadPackagesFromImports(request.code).asInstanceOf[js.Promise[js.Any]].toFuture)
        .flatMap(_ => pyodide.runPythonAsync(program(request), js.Dynamic.literal(globals = globals))
          .asInstanceOf[js.Promise[js.Any]].toFuture)
        .map { value =>
          flushOutput()
          val isProxy = isPyProxy(value)
          try {
            val converted = if (isProxy) proxyToJs(value) else value
            RunPythonResult(json(converted), stdout.mkString, stderr.mkString, callbackOps.toList)
          } finally if (isProxy) value.asInstanceOf[js.Dynamic].destroy()
        }.recoverWith { case error =>
          flushOutput()
          stdout.foreach(logger.logInfo(_))
          stderr.foreach(logger.logError(_))
          Future.failed(error)
        }
    }
  }
}
