package interactionPlugins.programmingExercise.pythonExercisesUnsorted

import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.JSON

private[interactionPlugins] final case class PythonRunRequest(
    code: String,
    visibleTests: Seq[PythonUnitTest],
    hiddenTests: Seq[PythonUnitTest],
    fixtures: Seq[PythonFixture],
    packages: Seq[String],
    timeoutMs: Int
)

private[interactionPlugins] object PythonRuntimeService {

  private given ExecutionContext = queue

  // **Web Worker management**
  //
  // Pyodide runs inside a dedicated Web Worker so that long-running / hung
  // Python code cannot block the main JS event loop.
  // When a timeout fires we call worker.terminate(), which immediately kills
  // the worker thread regardless of what Python is doing.  The next call to
  // run() lazily creates a fresh worker (which will reload Pyodide).
  //
  // Worker script URL: configurable via the global JS variable
  //   window.PYTHON_WORKER_URL
  // falling back to the default relative path "js/pythonWorker.js".

  private var currentWorker: Option[dom.Worker] = None
  private var msgIdCounter: Int = 0

  // Promise that resolves when the worker's Pyodide runtime is fully loaded.
  // Reset whenever the worker is terminated so a fresh worker gets a fresh promise.
  private var pyodideReadyPromise: Promise[Unit] = Promise()

  // Pending run promises keyed by message id (stored as Double; JS Numbers are
  // doubles, and using Double avoids any Int/Double boxing mismatch in the Map).
  private val pendingRuns = scala.collection.mutable.Map.empty[Double, Promise[PythonRunResult]]

  /** URL of the worker script; can be overridden via a page-level JS global. */
  private def workerUrl: String =
    // selectDynamic throws a ReferenceError in strict mode when the variable
    // was never declared at all, so we wrap the lookup in a try/catch.
    try
      val g = js.Dynamic.global.selectDynamic("PYTHON_WORKER_URL")
      if !js.isUndefined(g) && g != null then g.asInstanceOf[String]
      else "js/pythonWorker.js"
    catch case _: Throwable => "js/pythonWorker.js"

  private def getOrCreateWorker(): dom.Worker =
    currentWorker match {
      case Some(w) => w
      case None =>
        val w = new dom.Worker(workerUrl)
        currentWorker = Some(w)

        // Single dispatching handler: handles the "ready" signal from warmup
        // AND routes run results to the matching pending-run promise.
        w.onmessage = { (event: dom.MessageEvent) =>
          val data = event.data.asInstanceOf[js.Dynamic]
          val msgType = data.selectDynamic("type")
          if !js.isUndefined(msgType) && msgType != null && msgType.asInstanceOf[String] == "ready" then
            pyodideReadyPromise.trySuccess(())
          else
            // JS numbers are doubles; use Double key to avoid Int-boxing mismatch.
            val id = data.selectDynamic("id").asInstanceOf[Double]
            pendingRuns.remove(id).foreach { p =>
              val errorVal = data.selectDynamic("error")
              val result =
                if !js.isUndefined(errorVal) && errorVal != null then
                  PythonRunResult(
                    PythonRunStatus.RuntimeError, Seq.empty,
                    stdout = "", stderr = "",
                    error = Some(errorVal.asInstanceOf[String]).filter(_.nonEmpty),
                    score = 0.0
                  )
                else
                  parseResult(data.selectDynamic("result"))
              p.trySuccess(result)
            }
        }

        w.onerror = { (e: dom.ErrorEvent) =>
          val msg = Option(e.message).filter(_.nonEmpty).getOrElse("Web Worker error")
          // Reject the ready promise so waitUntilReady() doesn't hang forever.
          pyodideReadyPromise.tryFailure(new RuntimeException(s"Worker failed: $msg"))
          pendingRuns.values.foreach(_.trySuccess(PythonRunResult(
            PythonRunStatus.RuntimeError, Seq.empty,
            stdout = "", stderr = "",
            error = Some(s"Web Worker error: $msg"),
            score = 0.0
          )))
          pendingRuns.clear()
        }

        // Kick off Pyodide loading; worker replies with {type:"ready"} when done.
        w.postMessage(js.Dynamic.literal("type" -> "warmup"))
        w
    }

  /** Create the worker and start loading Pyodide in the background.
   *  Call this as early as possible (when the UI element is mounted)
   *  so Pyodide is ready before the user submits their first code.
   */
  private[interactionPlugins] def warmup(): Unit =
    try getOrCreateWorker()
    catch case _: Throwable => ()

  /** True once the worker has signalled that Pyodide is fully loaded.
   *  Used by PythonFeedbackRuntime to choose the right timeout for each run:
   *  short (user-configured ms) on warm runs, long on the first cold-start run.
   */
  private[interactionPlugins] def isReady: Boolean =
    pyodideReadyPromise.isCompleted &&
      pyodideReadyPromise.future.value.exists(_.isSuccess)

  /** Terminate the current worker (if any) and reset state.
   *  Called by PythonFeedbackRuntime when a timeout fires so that infinite
   *  loops are killed immediately.
   */
  private[interactionPlugins] def terminateWorker(): Unit = {
    js.Dynamic.global.console.warn("[PythonWorker] terminateWorker() called — worker killed (timeout or explicit)")
    currentWorker.foreach(_.terminate())
    currentWorker = None
    // Fail any run that was waiting for a response from the killed worker.
    pendingRuns.values.foreach(_.tryFailure(
      new java.util.concurrent.TimeoutException("Worker terminated")
    ))
    pendingRuns.clear()
    // Fresh promise so the next worker's ready signal will be picked up.
    pyodideReadyPromise = Promise()
  }

  private def nextMsgId(): Int = {
    msgIdCounter += 1
    msgIdCounter
  }

  private def buildExecutionScript(request: PythonRunRequest): String = {
    val testsJsArray = new js.Array[js.Any]
    request.visibleTests.foreach { test =>
      testsJsArray.push(
        js.Dynamic.literal(
          "name" -> test.name,
          "code" -> test.code,
          "weight" -> test.weight,
          "hidden" -> false,
          "hint" -> test.hint.orUndefined
        )
      )
    }
    request.hiddenTests.foreach { test =>
      testsJsArray.push(
        js.Dynamic.literal(
          "name" -> test.name,
          "code" -> test.code,
          "weight" -> test.weight,
          "hidden" -> true,
          "hint" -> js.undefined
        )
      )
    }

    val fixturesJsArray = new js.Array[js.Any]
    request.fixtures.foreach { fixture =>
      fixturesJsArray.push(
        js.Dynamic.literal(
          "path" -> fixture.path,
          "content" -> fixture.content,
          "isBinary" -> fixture.isBinary
        )
      )
    }

    val testsLiteral = JSON.stringify(testsJsArray)
    val fixturesLiteral = JSON.stringify(fixturesJsArray)
    val codeLiteral = JSON.stringify(request.code)

    s"""
import ast
import json
import sys
import traceback
import time
from io import StringIO

_tests = json.loads(r'''$testsLiteral''')
_fixtures = json.loads(r'''$fixturesLiteral''')
_code_source = $codeLiteral

result = {
  "status": "success",
  "tests": [],
  "stdout": "",
  "stderr": "",
  "error": None,
  "score": 0.0
}

stdout_capture = StringIO()
stderr_capture = StringIO()
old_stdout, old_stderr = sys.stdout, sys.stderr

try:
  sys.stdout = stdout_capture
  sys.stderr = stderr_capture
  namespace = {}
  namespace['_student_source'] = _code_source

  import pathlib
  import base64

  for fixture in _fixtures:
    path = fixture["path"]
    data = fixture["content"]
    is_binary = fixture["isBinary"]
    path_obj = pathlib.Path(path)
    if not path_obj.parent.exists():
      path_obj.parent.mkdir(parents=True, exist_ok=True)
    mode = "wb" if is_binary else "w"
    with open(path, mode) as handle:
      if is_binary:
        handle.write(base64.b64decode(data))
      else:
        handle.write(data)

  exec(_code_source, namespace, namespace)

  total_weight = sum(test.get("weight", 1.0) for test in _tests) or 1.0
  earned = 0.0

  def _eval_simple_assert(code, ns):
    try:
      tree = ast.parse(code)
      if len(tree.body) != 1 or not isinstance(tree.body[0], ast.Assert):
        return None
      test_node = tree.body[0].test
      if isinstance(test_node, ast.Compare) and len(test_node.ops) == 1 and len(test_node.comparators) == 1:
        op = test_node.ops[0]
        # Only handle == and 'in' in the fast path; other operators (>, >=, !=, …)
        # fall through to exec() so Python itself evaluates them correctly.
        if not isinstance(op, (ast.Eq, ast.In)):
          return None
        left_expr = ast.Expression(test_node.left)
        right_expr = ast.Expression(test_node.comparators[0])
        left_val = eval(compile(left_expr, "<assert>", "eval"), ns, ns)
        right_val = eval(compile(right_expr, "<assert>", "eval"), ns, ns)
        return (op, left_val, right_val)
      return None
    except Exception:
      return None

  for test in _tests:
    start = time.perf_counter()
    entry = {
      "name": test.get("name", "Unnamed test"),
      "hidden": bool(test.get("hidden", False)),
      "hint": test.get("hint"),
      "status": "passed",
      "message": None,
      "durationMs": 0.0,
      "weight": test.get("weight", 1.0)
    }
    try:
      test_code = test.get("code", "")
      maybe_eval = _eval_simple_assert(test_code, namespace)
      if maybe_eval is not None:
        op, left_val, right_val = maybe_eval
        if isinstance(op, ast.In):
          ok = left_val in right_val
        else:
          ok = left_val == right_val
        if ok:
          earned += entry["weight"]
        else:
          entry["status"] = "failed"
          entry["message"] = f"expected={right_val} actual={left_val}"
      else:
        exec(test_code, namespace, namespace)
        earned += entry["weight"]
    except AssertionError as assertion_error:
      entry["status"] = "failed"
      entry["message"] = str(assertion_error)
    except Exception:
      entry["status"] = "errored"
      entry["message"] = traceback.format_exc()
      result["status"] = "runtime-error"
    finally:
      entry["durationMs"] = (time.perf_counter() - start) * 1000.0
      result["tests"].append(entry)

  result["score"] = max(0.0, min(1.0, earned / total_weight))
  if result["status"] == "success" and result["score"] < 1.0:
    result["status"] = "failed"
except Exception:
  result["status"] = "runtime-error"
  result["error"] = traceback.format_exc()
finally:
  result["stdout"] = stdout_capture.getvalue()
  result["stderr"] = stderr_capture.getvalue()
  sys.stdout = old_stdout
  sys.stderr = old_stderr

json.dumps(result)
    """
  }

  private def toOptionString(value: js.Any): Option[String] =
    if js.isUndefined(value) || value == null then None
    else Option(value.asInstanceOf[String]).filter(_.nonEmpty)

  private def parseResult(raw: js.Any): PythonRunResult = {
    val jsonString = raw.asInstanceOf[String]
    val dynamic = JSON.parse(jsonString)
    val status = dynamic.selectDynamic("status").asInstanceOf[String] match {
      case "success"       => PythonRunStatus.Success
      case "failed"        => PythonRunStatus.Failed
      case "runtime-error" => PythonRunStatus.RuntimeError
      case _                => PythonRunStatus.RuntimeError
    }
    val tests = dynamic
      .selectDynamic("tests")
      .asInstanceOf[js.Array[js.Dynamic]]
      .map { entry =>
        val statusString = entry.selectDynamic("status").asInstanceOf[String]
        val mappedStatus = statusString match {
          case "passed"  => PythonTestStatus.Passed
          case "failed"  => PythonTestStatus.Failed
          case "errored" => PythonTestStatus.Errored
          case _          => PythonTestStatus.Errored
        }
        PythonTestResult(
          name = entry.selectDynamic("name").asInstanceOf[String],
          status = mappedStatus,
          isHidden = entry.selectDynamic("hidden").asInstanceOf[Boolean],
          message = toOptionString(entry.selectDynamic("message")),
          durationMs = entry.selectDynamic("durationMs").asInstanceOf[Double],
          hint = toOptionString(entry.selectDynamic("hint"))
        )
      }
      .toSeq
    val stdout = dynamic.selectDynamic("stdout").asInstanceOf[String]
    val stderr = dynamic.selectDynamic("stderr").asInstanceOf[String]
    val error = toOptionString(dynamic.selectDynamic("error"))
    val score = dynamic.selectDynamic("score").asInstanceOf[Double]

    PythonRunResult(status, tests, stdout, stderr, error, score)
  }

  def run(request: PythonRunRequest): Future[PythonRunResult] = {
    val p = Promise[PythonRunResult]()
    val msgId = nextMsgId()

    val worker =
      try getOrCreateWorker()
      catch {
        case ex: Throwable =>
          return Future.successful(PythonRunResult(
            PythonRunStatus.RuntimeError,
            Seq.empty,
            stdout = "",
            stderr = "",
            error = Some(s"Python worker unavailable: ${Option(ex.getMessage).getOrElse(ex.toString)}"),
            score = 0.0
          ))
      }

    // Register this run (keyed as Double to match JS Number type from response).
    pendingRuns(msgId.toDouble) = p

    worker.postMessage(js.Dynamic.literal(
      "id"       -> msgId,
      "script"   -> buildExecutionScript(request),
      "packages" -> request.packages.toJSArray
    ))

    p.future
  }
}
