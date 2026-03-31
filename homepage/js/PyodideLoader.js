const PYODIDE_MJS_URL = "https://cdn.jsdelivr.net/pyodide/v0.29.3/full/pyodide.mjs";

async function ensureMainThreadLoader() {
  if (typeof globalThis.loadPyodide === "function") return;
  const pyodideModule = await import(PYODIDE_MJS_URL);
  globalThis.loadPyodide = pyodideModule.loadPyodide;
}

const isWorkerContext =
  typeof WorkerGlobalScope !== "undefined" && globalThis instanceof WorkerGlobalScope;

if (!isWorkerContext) {
  void ensureMainThreadLoader();
} else {
  const { loadPyodide } = await import(PYODIDE_MJS_URL);

  let pyodide = null;
  let envGlobals = null;
  let envLocals = null;
  let initialized = false;

  function installStdStreams() {
    pyodide.setStdout({
      batched: (msg) => self.postMessage({ type: "stdout", text: msg + "\n" })
    });
    pyodide.setStderr({
      batched: (msg) => self.postMessage({ type: "stderr", text: msg + "\n" })
    });
  }

  function helpersPython() {
    return `
import json
import traceback
import unittest
import io
import sys
import contextlib

HELPER_EXCLUDE = {
    "json", "traceback", "unittest", "io", "sys", "contextlib",
    "ExecutionLineLimitExceeded",
    "_snapshot_namespace", "_run_with_optional_line_limit",
    "_execute_code_impl", "_execute_unit_test_impl",
    "HELPER_EXCLUDE", "USER_NS"
}

# Dedicated execution namespace for user code.
# Keeping this separate from helper globals prevents helper internals from
# leaking into user variable snapshots and keeps environment state isolated.
USER_NS = {"__name__": "__main__"}

class ExecutionLineLimitExceeded(Exception):
    pass


def _safe_repr(x):
    try:
        return repr(x)
    except Exception as e:
        return f"<unreprable {type(x).__name__}: {e}>"


def _snapshot_namespace(ns):
    out = {}
    for k, v in ns.items():
        if k.startswith("__"):
            continue
        if k in HELPER_EXCLUDE:
            continue
        kind = type(v).__name__
        if callable(v):
            tag = "callable"
        else:
            tag = "value"
        out[k] = f"[{tag}:{kind}] {_safe_repr(v)}"
    return out


def _run_with_optional_line_limit(fn, filenames, max_lines):
    lines_executed = 0
    old_trace = sys.gettrace()
    file_set = set(filenames)

    def tracer(frame, event, arg):
        nonlocal lines_executed
        if event == "line" and frame.f_code.co_filename in file_set:
            lines_executed += 1
            if max_lines is not None and lines_executed > max_lines:
                raise ExecutionLineLimitExceeded(f"maximum executed line count exceeded: {max_lines}")
        return tracer

    try:
        if max_lines is not None:
            sys.settrace(tracer)
        fn()
        return {
            "ok": True,
            "exception": None,
            "linesExecuted": lines_executed,
            "lineLimitHit": False
        }
    except ExecutionLineLimitExceeded as e:
        return {
            "ok": False,
            "exception": str(e),
            "linesExecuted": lines_executed,
            "lineLimitHit": True
        }
    except Exception:
        return {
            "ok": False,
            "exception": traceback.format_exc(),
            "linesExecuted": lines_executed,
            "lineLimitHit": False
        }
    finally:
        sys.settrace(old_trace)


def _execute_code_impl(code, max_lines):
    stream_out = io.StringIO()
    stream_err = io.StringIO()
    user_ns = USER_NS

    def body():
        with contextlib.redirect_stdout(stream_out):
            with contextlib.redirect_stderr(stream_err):
                exec(compile(code, "<user_code>", "exec"), user_ns, user_ns)

    run = _run_with_optional_line_limit(body, ["<user_code>"], max_lines)
    result = {
        "success": run["ok"],
        "stdout": stream_out.getvalue(),
        "stderr": stream_err.getvalue(),
        "exception": run["exception"],
        "globals": _snapshot_namespace(user_ns),
        "locals": _snapshot_namespace(user_ns),
        "linesExecuted": run["linesExecuted"],
        "maxExecutedLines": max_lines,
        "lineLimitHit": run["lineLimitHit"]
    }
    return json.dumps(result)


def _execute_unit_test_impl(code, test_code, test_name, max_lines):
    stream_out = io.StringIO()
    stream_err = io.StringIO()
    test_stream = io.StringIO()
    user_ns = USER_NS

    def body():
        with contextlib.redirect_stdout(stream_out):
            with contextlib.redirect_stderr(stream_err):
                exec(compile(code, "<user_code>", "exec"), user_ns, user_ns)
                exec(compile(test_code, "<unit_tests>", "exec"), user_ns, user_ns)
        suite = unittest.defaultTestLoader.loadTestsFromName(test_name, module=None)
        runner = unittest.TextTestRunner(stream=test_stream, verbosity=2)
        user_ns["__last_test_result"] = runner.run(suite)

    run = _run_with_optional_line_limit(body, ["<user_code>", "<unit_tests>"], max_lines)
    test_result = user_ns.get("__last_test_result", None)
    if test_result is not None and run["ok"]:
        success = test_result.wasSuccessful()
        failures = [f[1] for f in test_result.failures]
        errors = [e[1] for e in test_result.errors]
    else:
        success = False
        failures = []
        errors = []

    result = {
        "success": success,
        "stdout": stream_out.getvalue() + test_stream.getvalue(),
        "stderr": stream_err.getvalue(),
        "failures": failures,
        "errors": errors,
        "globals": _snapshot_namespace(user_ns),
        "locals": _snapshot_namespace(user_ns),
        "exception": run["exception"],
        "linesExecuted": run["linesExecuted"],
        "maxExecutedLines": max_lines,
        "lineLimitHit": run["lineLimitHit"]
    }
    return json.dumps(result)
`;
  }

  async function init() {
    if (initialized) return;
    pyodide = await loadPyodide();
    installStdStreams();
    envGlobals = pyodide.runPython("dict(__name__='__main__')");
    envLocals = envGlobals;
    await pyodide.runPythonAsync(helpersPython(), {
      globals: envGlobals,
      locals: envLocals,
      filename: "<bootstrap>"
    });
    initialized = true;
  }

  async function registerModule(moduleName, functionNames) {
    const members = {};
    for (const fnName of functionNames) {
      members[fnName] = (...args) => {
        self.postMessage({
          type: "module-callback",
          moduleName,
          functionName: fnName,
          args
        });
        return undefined;
      };
    }
    pyodide.registerJsModule(moduleName, members);
  }

  function parseMaybeLimit(msg) {
    return typeof msg.maxExecutedLines === "number" ? msg.maxExecutedLines : null;
  }

  self.onmessage = async (event) => {
    const msg = event.data;
    try {
      switch (msg.type) {
        case "init": {
          await init();
          self.postMessage({ type: "ready", requestId: msg.requestId });
          break;
        }
        case "registerModule": {
          await init();
          await registerModule(msg.moduleName, msg.functionNames);
          self.postMessage({ type: "registered", requestId: msg.requestId, moduleName: msg.moduleName });
          break;
        }
        case "executeCode": {
          await init();
          const resultText = await pyodide.runPythonAsync(
            `_execute_code_impl(${JSON.stringify(msg.code)}, ${JSON.stringify(parseMaybeLimit(msg))})`,
            { globals: envGlobals, locals: envLocals, filename: "<bridge>" }
          );
          self.postMessage({ type: "result", requestId: msg.requestId, payload: JSON.parse(resultText) });
          break;
        }
        case "executeUnitTest": {
          await init();
          const resultText = await pyodide.runPythonAsync(
            `_execute_unit_test_impl(${JSON.stringify(msg.code)}, ${JSON.stringify(msg.testCode)}, ${JSON.stringify(msg.testName)}, ${JSON.stringify(parseMaybeLimit(msg))})`,
            { globals: envGlobals, locals: envLocals, filename: "<bridge>" }
          );
          self.postMessage({ type: "result", requestId: msg.requestId, payload: JSON.parse(resultText) });
          break;
        }
        case "destroy": {
          self.close();
          break;
        }
        default: {
          self.postMessage({ type: "error", requestId: msg.requestId, error: `Unknown message type: ${msg.type}` });
        }
      }
    } catch (err) {
      self.postMessage({
        type: "error",
        requestId: msg.requestId,
        error: err && err.stack ? String(err.stack) : String(err)
      });
    }
  };
}
