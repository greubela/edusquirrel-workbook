package interactionPlugins.programmingExercise.pythonExercise.pyodide

import interactionPlugins.programmingExercise.pythonExercise.data.PythonExecutionResult.PythonExecutionRunningState
import interactionPlugins.programmingExercise.pythonExercise.data.{PythonExecutionRequest, PythonExecutionResult}
import util.web.JsHelpers.{asBoolean, asInt, asStringMap, asStringOption}

import scala.scalajs.js

object PyodideHelper {

  def toExecutionState(parsed: js.Dynamic): PythonExecutionResult.PythonExecutionState = {
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

  def runningExecutionResult(request: PythonExecutionRequest): PythonExecutionResult =
    PythonExecutionResult(
      request = request,
      state = PythonExecutionResult.PythonExecutionState("", "", Map.empty, Map.empty, 0, PythonExecutionRunningState.RUNNING)
    )

  val helperCode: String =
    """
      |import json
      |import traceback
      |import unittest
      |import io
      |import sys
      |import contextlib
      |import types
      |
      |HELPER_EXCLUDE = {
      |    "json", "traceback", "unittest", "io", "sys", "contextlib", "types",
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
      |            # Keep user-defined callables (functions / classes declared in user
      |            # code), but hide technical helper callables such as imported proxy
      |            # functions from runtime support modules.
      |            if getattr(v, "__module__", None) != "__main__":
      |                continue
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
      |def _execute_code_impl(code, max_lines, include_snapshots):
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
      |    snapshots = _snapshot_namespace(user_ns) if include_snapshots else {}
      |
      |    result = {
      |        "success": run["ok"],
      |        "stdout": stream_out.getvalue(),
      |        "stderr": stream_err.getvalue(),
      |        "exception": run["exception"],
      |        "globals": snapshots,
      |        "locals": snapshots,
      |        "linesExecuted": run["linesExecuted"],
      |        "maxExecutedLines": max_lines,
      |        "lineLimitHit": run["lineLimitHit"]
      |    }
      |    return json.dumps(result)
      |
      |
      |def _execute_unit_test_impl(code, test_code, test_name, max_lines, include_snapshots):
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
      |    snapshots = _snapshot_namespace(user_ns) if include_snapshots else {}
      |
      |    result = {
      |        "success": success,
      |        "stdout": stream_out.getvalue() + test_stream.getvalue(),
      |        "stderr": stream_err.getvalue(),
      |        "failures": failures,
      |        "errors": errors,
      |        "globals": snapshots,
      |        "locals": snapshots,
      |        "exception": run["exception"],
      |        "linesExecuted": run["linesExecuted"],
      |        "maxExecutedLines": max_lines,
      |        "lineLimitHit": run["lineLimitHit"]
      |    }
      |    return json.dumps(result)
      |""".stripMargin
}
