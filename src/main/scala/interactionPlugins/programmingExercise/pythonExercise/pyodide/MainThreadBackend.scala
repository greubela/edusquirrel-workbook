package interactionPlugins.programmingExercise.pythonExercise.pyodide

import com.raquo.laminar.api.L.Var

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js

private[pyodide] final class MainThreadBackend(stdoutVar: Var[String], stderrVar: Var[String]) extends Backend {
  import Helpers.*

  private var pyOpt: Option[Pyodide] = None
  private var envGlobals: js.Any = _

  private val helperCode =
    """
      |import json
      |import traceback
      |import unittest
      |import io
      |import sys
      |
      |HELPER_EXCLUDE = {
      |    "json", "traceback", "unittest", "io", "sys",
      |    "ExecutionLineLimitExceeded",
      |    "_snapshot_namespace", "_run_with_optional_line_limit",
      |    "_execute_code_impl", "_execute_unit_test_impl",
      |    "HELPER_EXCLUDE"
      |}
      |
      |class ExecutionLineLimitExceeded(Exception):
      |    pass
      |
      |def _safe_repr(x):
      |    try:
      |        return repr(x)
      |    except Exception as e:
      |        return f"<unreprable {type(x).__name__}: {e}>"
      |
      |def _snapshot_namespace(ns):
      |    out = {}
      |    for k, v in ns.items():
      |        if k.startswith("__"):
      |            continue
      |        if k in HELPER_EXCLUDE:
      |            continue
      |        kind = type(v).__name__
      |        if callable(v):
      |            tag = "callable"
      |        else:
      |            tag = "value"
      |        out[k] = f"[{tag}:{kind}] {_safe_repr(v)}"
      |    return out
      |
      |def _run_with_optional_line_limit(fn, filenames, max_lines):
      |    lines_executed = 0
      |    old_trace = sys.gettrace()
      |    file_set = set(filenames)
      |
      |    def tracer(frame, event, arg):
      |        nonlocal lines_executed
      |        if event == "line" and frame.f_code.co_filename in file_set:
      |            lines_executed += 1
      |            if max_lines is not None and lines_executed > max_lines:
      |                raise ExecutionLineLimitExceeded(f"maximum executed line count exceeded: {max_lines}")
      |        return tracer
      |
      |    try:
      |        if max_lines is not None:
      |            sys.settrace(tracer)
      |        fn()
      |        return {
      |            "ok": True,
      |            "exception": None,
      |            "linesExecuted": lines_executed,
      |            "lineLimitHit": False
      |        }
      |    except ExecutionLineLimitExceeded as e:
      |        return {
      |            "ok": False,
      |            "exception": str(e),
      |            "linesExecuted": lines_executed,
      |            "lineLimitHit": True
      |        }
      |    except Exception:
      |        return {
      |            "ok": False,
      |            "exception": traceback.format_exc(),
      |            "linesExecuted": lines_executed,
      |            "lineLimitHit": False
      |        }
      |    finally:
      |        sys.settrace(old_trace)
      |
      |def _execute_code_impl(code, max_lines):
      |    def body():
      |        exec(compile(code, "<user_code>", "exec"), globals(), locals())
      |
      |    run = _run_with_optional_line_limit(body, ["<user_code>"], max_lines)
      |    result = {
      |        "success": run["ok"],
      |        "exception": run["exception"],
      |        "globals": _snapshot_namespace(globals()),
      |        "locals": _snapshot_namespace(locals()),
      |        "linesExecuted": run["linesExecuted"],
      |        "maxExecutedLines": max_lines,
      |        "lineLimitHit": run["lineLimitHit"]
      |    }
      |    return json.dumps(result)
      |
      |def _execute_unit_test_impl(code, test_code, test_name, max_lines):
      |    stream = io.StringIO()
      |
      |    def body():
      |        exec(compile(code, "<user_code>", "exec"), globals(), locals())
      |        exec(compile(test_code, "<unit_tests>", "exec"), globals(), locals())
      |        suite = unittest.defaultTestLoader.loadTestsFromName(test_name, module=None)
      |        runner = unittest.TextTestRunner(stream=stream, verbosity=2)
      |        globals()["__last_test_result"] = runner.run(suite)
      |
      |    run = _run_with_optional_line_limit(body, ["<user_code>", "<unit_tests>"], max_lines)
      |    test_result = globals().get("__last_test_result", None)
      |    if test_result is not None and run["ok"]:
      |        success = test_result.wasSuccessful()
      |        failures = [f[1] for f in test_result.failures]
      |        errors = [e[1] for e in test_result.errors]
      |    else:
      |        success = False
      |        failures = []
      |        errors = []
      |
      |    result = {
      |        "success": success,
      |        "stdout": stream.getvalue(),
      |        "failures": failures,
      |        "errors": errors,
      |        "globals": _snapshot_namespace(globals()),
      |        "locals": _snapshot_namespace(locals()),
      |        "exception": run["exception"],
      |        "linesExecuted": run["linesExecuted"],
      |        "maxExecutedLines": max_lines,
      |        "lineLimitHit": run["lineLimitHit"]
      |    }
      |    return json.dumps(result)
      |""".stripMargin

  private def initPy(): Future[Pyodide] =
    pyOpt match {
      case Some(py) => Future.successful(py)
      case None =>
        promiseToFuture(loadPyodide()).flatMap { py =>
          pyOpt = Some(py)
          py.setStdout(js.Dynamic.literal(
            batched = ((msg: String) => appendVar(stdoutVar, msg + "\n")): js.Function1[String, Unit]
          ))
          py.setStderr(js.Dynamic.literal(
            batched = ((msg: String) => appendVar(stderrVar, msg + "\n")): js.Function1[String, Unit]
          ))
          envGlobals = py.runPython("dict(__name__='__main__')")
          promiseToFuture(py.runPythonAsync(helperCode, js.Dynamic.literal(
            globals = envGlobals,
            locals = envGlobals,
            filename = "<bootstrap>"
          ))).map(_ => py)
        }
    }

  private def wrapCallback(fn: Seq[js.Any] => Unit): js.Function =
    js.Dynamic.global
      .eval("(function(f){ return function(){ return f(Array.prototype.slice.call(arguments)); }; })")
      .asInstanceOf[js.Function1[js.Function1[js.Array[js.Any], Unit], js.Function]](
        js.Any.fromFunction1((args: js.Array[js.Any]) => fn(args.toSeq))
      )

  override def registerModule(binding: ModuleBinding): Future[Unit] =
    initPy().map { py =>
      val dict = js.Dynamic.literal()
      binding.callbacks.foreach { case (name, fn) =>
        dict.updateDynamic(name)(wrapCallback(fn))
      }
      py.registerJsModule(binding.moduleName, dict.asInstanceOf[js.Object])
    }

  override def executeCode(pythonCode: String, maxExecutedLines: Option[Int]): Future[ExecutionResult] = {
    clearVar(stdoutVar)
    clearVar(stderrVar)
    initPy().flatMap { py =>
      val maxExpr = maxExecutedLines.fold("None")(_.toString)
      promiseToFuture(py.runPythonAsync(
        s"_execute_code_impl(${js.JSON.stringify(pythonCode)}, $maxExpr)",
        js.Dynamic.literal(globals = envGlobals, locals = envGlobals, filename = "<bridge>")
      )).map { raw =>
        val parsed = decodeJsonResult(raw)
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
    initPy().flatMap { py =>
      val maxExpr = maxExecutedLines.fold("None")(_.toString)
      promiseToFuture(py.runPythonAsync(
        s"_execute_unit_test_impl(${js.JSON.stringify(pythonCode)}, ${js.JSON.stringify(pyUnitTest.testCode)}, ${js.JSON.stringify(pyUnitTest.testName)}, $maxExpr)",
        js.Dynamic.literal(globals = envGlobals, locals = envGlobals, filename = "<bridge>")
      )).map { raw =>
        val parsed = decodeJsonResult(raw)
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

  override def destroy(): Unit = ()
}
