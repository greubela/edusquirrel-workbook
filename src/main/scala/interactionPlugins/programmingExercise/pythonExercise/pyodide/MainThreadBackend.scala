package interactionPlugins.programmingExercise.pythonExercise.pyodide

import com.raquo.laminar.api.L.Var
import interactionPlugins.programmingExercise.pythonExercise.data.PythonExecutionResult.PythonExecutionRunningState
import interactionPlugins.programmingExercise.pythonExercise.data.PythonUnitTestResult.{GradingStatus, PythonUnitTestGradingResult}
import interactionPlugins.programmingExercise.pythonExercise.data.*
import util.web.JsHelpers.*
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment.Backend

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js

final class MainThreadBackend() extends Backend {

  private var pyOpt: Option[PyodideEnvironment.Pyodide] = None
  private var envGlobals: js.Any = _

  private val helperCode =
    """
      |import json
      |import traceback
      |import unittest
      |import io
      |import sys
      |import contextlib
      |
      |HELPER_EXCLUDE = {
      |    "json", "traceback", "unittest", "io", "sys", "contextlib",
      |    "ExecutionLineLimitExceeded",
      |    "_snapshot_namespace", "_run_with_optional_line_limit",
      |    "_execute_code_impl", "_execute_unit_test_impl",
      |    "HELPER_EXCLUDE", "USER_NS"
      |}
      |
      |# Dedicated execution namespace for user code.
      |# Keeping this separate from helper globals prevents helper internals from
      |# leaking into user variable snapshots and keeps environment state isolated.
      |USER_NS = {"__name__": "__main__"}
      |
      |class ExecutionLineLimitExceeded(Exception):
      |    pass
      |
      |
      |def _safe_repr(x):
      |    try:
      |        return repr(x)
      |    except Exception as e:
      |        return f"<unreprable {type(x).__name__}: {e}>"
      |
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
      |
      |def _execute_code_impl(code, max_lines):
      |    stream_out = io.StringIO()
      |    stream_err = io.StringIO()
      |    user_ns = USER_NS
      |
      |    def body():
      |        with contextlib.redirect_stdout(stream_out):
      |            with contextlib.redirect_stderr(stream_err):
      |                exec(compile(code, "<user_code>", "exec"), user_ns, user_ns)
      |
      |    run = _run_with_optional_line_limit(body, ["<user_code>"], max_lines)
      |    result = {
      |        "success": run["ok"],
      |        "stdout": stream_out.getvalue(),
      |        "stderr": stream_err.getvalue(),
      |        "exception": run["exception"],
      |        "globals": _snapshot_namespace(user_ns),
      |        "locals": _snapshot_namespace(user_ns),
      |        "linesExecuted": run["linesExecuted"],
      |        "maxExecutedLines": max_lines,
      |        "lineLimitHit": run["lineLimitHit"]
      |    }
      |    return json.dumps(result)
      |
      |
      |def _execute_unit_test_impl(code, test_code, test_name, max_lines):
      |    stream_out = io.StringIO()
      |    stream_err = io.StringIO()
      |    test_stream = io.StringIO()
      |    user_ns = USER_NS
      |
      |    def body():
      |        with contextlib.redirect_stdout(stream_out):
      |            with contextlib.redirect_stderr(stream_err):
      |                exec(compile(code, "<user_code>", "exec"), user_ns, user_ns)
      |                exec(compile(test_code, "<unit_tests>", "exec"), user_ns, user_ns)
      |        suite = unittest.defaultTestLoader.loadTestsFromName(test_name, module=None)
      |        runner = unittest.TextTestRunner(stream=test_stream, verbosity=2)
      |        user_ns["__last_test_result"] = runner.run(suite)
      |
      |    run = _run_with_optional_line_limit(body, ["<user_code>", "<unit_tests>"], max_lines)
      |    test_result = user_ns.get("__last_test_result", None)
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
      |        "stdout": stream_out.getvalue() + test_stream.getvalue(),
      |        "stderr": stream_err.getvalue(),
      |        "failures": failures,
      |        "errors": errors,
      |        "globals": _snapshot_namespace(user_ns),
      |        "locals": _snapshot_namespace(user_ns),
      |        "exception": run["exception"],
      |        "linesExecuted": run["linesExecuted"],
      |        "maxExecutedLines": max_lines,
      |        "lineLimitHit": run["lineLimitHit"]
      |    }
      |    return json.dumps(result)
      |""".stripMargin

  private def initPy(): Future[PyodideEnvironment.Pyodide] =
    pyOpt match {
      case Some(py) => Future.successful(py)
      case None =>
        promiseToFuture(PyodideEnvironment.loadPyodide()).flatMap { py =>
          pyOpt = Some(py)
          envGlobals = py.runPython("dict(__name__='__main__')")
          promiseToFuture(py.runPythonAsync(helperCode, js.Dynamic.literal(
            globals = envGlobals,
            locals = envGlobals,
            filename = "<bootstrap>"
          ))).map(_ => py)
        }
    }

  private def wrapCallback(fn: Seq[js.Any] => js.Any): js.Function =
    js.Any.fromFunction12(
      (
        a1: js.UndefOr[js.Any],
        a2: js.UndefOr[js.Any],
        a3: js.UndefOr[js.Any],
        a4: js.UndefOr[js.Any],
        a5: js.UndefOr[js.Any],
        a6: js.UndefOr[js.Any],
        a7: js.UndefOr[js.Any],
        a8: js.UndefOr[js.Any],
        a9: js.UndefOr[js.Any],
        a10: js.UndefOr[js.Any],
        a11: js.UndefOr[js.Any],
        a12: js.UndefOr[js.Any]
      ) => {
        val args = Seq(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12)
          .takeWhile(_.isDefined)
          .map(_.get)
        fn(args)
      }
    )

  private def toExecutionState(parsed: js.Dynamic): PythonExecutionResult.PythonExecutionState = {
    val runningState =
      if asBoolean(parsed.success) then PythonExecutionRunningState.FINISHED_SUCCESS
      else if asBoolean(parsed.lineLimitHit) then PythonExecutionRunningState.FINISHED_LINE_LIMIT
      else PythonExecutionRunningState.FINISHED_ERROR

    PythonExecutionResult.PythonExecutionState(
      stdout = asStringOption(parsed.stdout).getOrElse(""),
      stderr = List(asStringOption(parsed.stderr), asStringOption(parsed.exception))
        .flatten
        .filter(_.nonEmpty)
        .mkString("\n"),
      globals = asStringMap(parsed.globals),
      locals = asStringMap(parsed.locals),
      linesExecuted = asInt(parsed.linesExecuted),
      runningState = runningState
    )
  }

  private def runningExecutionResult(request: PythonExecutionRequest): PythonExecutionResult =
    PythonExecutionResult(
      request = request,
      state = PythonExecutionResult.PythonExecutionState("", "", Map.empty, Map.empty, 0, PythonExecutionRunningState.RUNNING)
    )

  override def registerSyncModule(
      moduleName: String,
      callbacks: Map[String, Seq[js.Any] => js.Any]
  ): Future[Unit] =
    initPy().map { py =>
      val dict = js.Dynamic.literal()
      callbacks.foreach { case (name, fn) => dict.updateDynamic(name)(wrapCallback(fn)) }
      py.registerJsModule(moduleName, dict.asInstanceOf[js.Object])
    }

  override def registerAsyncModule(
      moduleName: String,
      callbacks: Map[String, Seq[js.Any] => Unit]
  ): Future[Unit] =
    registerSyncModule(moduleName, callbacks.map { case (name, cb) =>
      name -> ((args: Seq[js.Any]) => { cb(args); ().asInstanceOf[js.Any] })
    })

  override def registerJsModule(moduleName: String, module: js.Object): Future[Unit] =
    initPy().map { py =>
      py.registerJsModule(moduleName, module)
    }

  override def executeCodeFull(request: PythonExecutionRequest): Future[PythonExecutionResult] =
    initPy().flatMap { py =>
      val maxExpr = request.maxLinesToExecute.fold("None")(_.toString)
      promiseToFuture(py.runPythonAsync(
        s"_execute_code_impl(${js.JSON.stringify(request.pythonCode)}, $maxExpr)",
        js.Dynamic.literal(globals = envGlobals, locals = envGlobals, filename = "<bridge>")
      )).map { raw =>
        val parsed = js.JSON.parse(raw.asInstanceOf[String]).asInstanceOf[js.Dynamic]
        PythonExecutionResult(request, toExecutionState(parsed))
      }
    }

  override def executeCodeLinewise(
      request: PythonExecutionRequest,
      updateAtLeastEveryNLines: Int
  ): Var[PythonExecutionResult] = {
    val resultVar = Var(runningExecutionResult(request))
    executeCodeFull(request).foreach(resultVar.set)
    resultVar
  }

  override def executeUnitTestsFull(
      pythonCode: PythonExecutionRequest,
      unitTests: List[PythonUnitTest]
  ): Future[PythonUnitTestResult] = {
    def runOne(test: PythonUnitTest): Future[PythonUnitTestGradingResult] =
      initPy().flatMap { py =>
        val maxExpr = pythonCode.maxLinesToExecute.fold("None")(_.toString)
        promiseToFuture(py.runPythonAsync(
          s"_execute_unit_test_impl(${js.JSON.stringify(pythonCode.pythonCode)}, ${js.JSON.stringify(test.testCode)}, ${js.JSON.stringify(test.testName)}, $maxExpr)",
          js.Dynamic.literal(globals = envGlobals, locals = envGlobals, filename = "<bridge>")
        )).map { raw =>
          val parsed = js.JSON.parse(raw.asInstanceOf[String]).asInstanceOf[js.Dynamic]
          val result = PythonExecutionResult(pythonCode, toExecutionState(parsed))
          val gradingStatus = if asBoolean(parsed.success) then GradingStatus.SUCCESS else GradingStatus.FAILED
          PythonUnitTestGradingResult(test = test, result = result, gradingStatus = gradingStatus)
        }
      }

    Future.sequence(unitTests.map(runOne)).map { testResults =>
      PythonUnitTestResult(userCode = pythonCode, tests = testResults.toSet)
    }
  }

  override def executeUnitTestLinewise(
      pythonCode: PythonExecutionRequest,
      unitTests: List[PythonUnitTest],
      updateAtLeastEveryNLines: Int
  ): Var[PythonUnitTestResult] = {
    val initResults = unitTests.map { test =>
      PythonUnitTestGradingResult(test, runningExecutionResult(pythonCode), GradingStatus.UNFINISHED)
    }
    val resultVar = Var(PythonUnitTestResult(pythonCode, initResults.toSet))
    executeUnitTestsFull(pythonCode, unitTests).foreach(resultVar.set)
    resultVar
  }

  override def destroy(): Unit = {
    pyOpt = None
    envGlobals = null
  }
}
