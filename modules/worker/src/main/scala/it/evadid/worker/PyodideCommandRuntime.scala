package it.evadid.worker

import it.evadid.distribution.commandTypes.WorkerCommands.*
import it.evadid.util.logging.Logger
import it.evadid.facades.{Pyodide, PyodideRuntime}
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
    if (value == null || js.isUndefined(value)) ujson.Null
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

  private def execute(request: RunPythonRequest, logger: Logger): Future[RunPythonResult] = {
    load().flatMap { pyodide =>
      var stdout = Vector.empty[String]
      var stderr = Vector.empty[String]
      callbackOps = Vector.empty
      val stdoutDecoder = js.Dynamic.newInstance(js.Dynamic.global.TextDecoder)("utf-8")
      val stderrDecoder = js.Dynamic.newInstance(js.Dynamic.global.TextDecoder)("utf-8")
      pyodide.setStdout(js.Dynamic.literal(write = ((bytes: js.typedarray.Uint8Array) => {
        if (request.captureStdout) stdout :+= stdoutDecoder.decode(bytes, js.Dynamic.literal(stream = true)).asInstanceOf[String]
        bytes.length
      }): js.Function1[js.typedarray.Uint8Array, Int]))
      pyodide.setStderr(js.Dynamic.literal(write = ((bytes: js.typedarray.Uint8Array) => {
        if (request.captureStderr) stderr :+= stderrDecoder.decode(bytes, js.Dynamic.literal(stream = true)).asInstanceOf[String]
        bytes.length
      }): js.Function1[js.typedarray.Uint8Array, Int]))
      def flushOutput(): Unit = {
        Try(pyodide.runPython("import sys; sys.stdout.flush(); sys.stderr.flush()"))
        stdout :+= stdoutDecoder.decode().asInstanceOf[String]
        stderr :+= stderrDecoder.decode().asInstanceOf[String]
      }
      if (request.resetGlobals || namespace.isEmpty) {
        namespace.foreach(_.destroy())
        namespace = Some(pyodide.runPython("dict()").asInstanceOf[js.Dynamic])
      }
      val globals = namespace.get
      request.context.foreach { (key, value) =>
        // toPy converts nested JSON arrays and objects into Python lists/dictionaries.
        val converted = pyodide.toPy(js.JSON.parse(ujson.write(value)))
        globals.set(key, converted)
        if (converted != null && !js.isUndefined(converted) && js.typeOf(converted.destroy) == "function") converted.destroy()
      }
      registeredModules.foreach { module =>
        pyodide.unregisterJsModule(module)
        pyodide.runPython(s"import sys; sys.modules.pop('$module', None)")
      }
      registeredModules = Set.empty
      request.callbacks.foreach { (module, methods) =>
        require(module.matches("[A-Za-z_][A-Za-z0-9_]*"), "Invalid Python callback module")
        val functions = js.Dictionary.empty[js.Any]
        methods.foreach { method =>
          require(method.matches("[A-Za-z_][A-Za-z0-9_]*"), "Invalid Python callback method")
          val handler = js.Dynamic.literal(apply = ((_: js.Any, _: js.Any, args: js.Array[js.Any]) => {
            callbackOps :+= PythonCallback(module, method, args.toList.map { arg =>
              val dynamic = arg.asInstanceOf[js.Dynamic]
              json(if (arg != null && !js.isUndefined(arg) && js.typeOf(dynamic.toJs) == "function") dynamic.toJs(
                js.Dynamic.literal(dict_converter = js.Dynamic.global.Object.fromEntries, create_pyproxies = false)) else arg)
            })
            js.undefined
          }): js.Function3[js.Any, js.Any, js.Array[js.Any], js.Any])
          functions(method) = js.Dynamic.newInstance(js.Dynamic.global.Proxy)((() => ()): js.Function0[Unit], handler)
        }
        pyodide.registerJsModule(module, functions)
        registeredModules += module
      }
      val prepared = if (request.packages.isEmpty) Future.successful(())
        else pyodide.loadPackage(request.packages.toJSArray).asInstanceOf[js.Promise[js.Any]].toFuture.map(_ => ())
      prepared.flatMap(_ => pyodide.loadPackagesFromImports(request.code).asInstanceOf[js.Promise[js.Any]].toFuture)
        .flatMap(_ => pyodide.runPythonAsync(request.code, js.Dynamic.literal(globals = globals))
          .asInstanceOf[js.Promise[js.Any]].toFuture)
        .map { value =>
          flushOutput()
          val dynamic = value.asInstanceOf[js.Dynamic]
          val isProxy = value != null && !js.isUndefined(value) && js.typeOf(dynamic.toJs) == "function"
          try {
            val converted = if (isProxy) dynamic.toJs(js.Dynamic.literal(
              dict_converter = js.Dynamic.global.Object.fromEntries, create_pyproxies = false)) else value
            RunPythonResult(json(converted), stdout.mkString, stderr.mkString, callbackOps.toList)
          } finally if (isProxy) dynamic.destroy()
        }.recoverWith { case error =>
          flushOutput()
          stdout.foreach(logger.logInfo(_))
          stderr.foreach(logger.logError(_))
          Future.failed(error)
        }
    }
  }
}
