package interactionPlugins.blockEnvironment.feedback.runtime

import `export`.workers.PyodideWorkerClient
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideBackends.{
  PythonRunConfig,
  PythonRunReport,
  PythonWorkerFailure
}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.JSON
import scala.util.Try

object PythonRuntimeService {

  private val DefaultWorkerUrl = "./js/pyodide-worker.js"
  private val ResultBegin = "__EDUSQ_RESULT_BEGIN__"
  private val ResultEnd = "__EDUSQ_RESULT_END__"

  private given ExecutionContext = queue

  private var workerOpt: Option[PyodideWorkerClient] = None
  private var readyPromise: Promise[Unit] = Promise()

  def isReady: Boolean =
    readyPromise.isCompleted &&
      readyPromise.future.value.exists(_.isSuccess)

  def warmup(): Unit = {
    val _ = getOrCreateWorker()
  }

  def terminateWorker(): Unit = {
    workerOpt.foreach(_.terminate())
    workerOpt = None
    readyPromise = Promise()
  }

  def run(request: PythonRunRequest): Future[PythonRunResult] = {
    val client = getOrCreateWorker()
    val script = buildExecutionScript(request)

    client
      .run(script, PythonRunConfig(resetGlobals = true))
      .map(report => parseReport(report))
      .recover {
        case f: PythonWorkerFailure =>
          PythonRunResult(
            status = PythonRunStatus.RuntimeError,
            tests = Seq.empty,
            stdout = f.stdout,
            stderr = f.stderr,
            error = Option(f.message).filter(_.nonEmpty),
            score = 0.0
          )
        case ex =>
          PythonRunResult(
            status = PythonRunStatus.RuntimeError,
            tests = Seq.empty,
            stdout = "",
            stderr = "",
            error = Option(ex.getMessage).filter(_.nonEmpty).orElse(Some(ex.getClass.getSimpleName)),
            score = 0.0
          )
      }
  }

  private def getOrCreateWorker(): PyodideWorkerClient = workerOpt match {
    case Some(w) => w
    case None =>
      val client = new PyodideWorkerClient(workerUrl)
      workerOpt = Some(client)
      val markReady: Try[Any] => Unit = _ => readyPromise.trySuccess(())
      client.run("pass").onComplete(markReady)
      client
  }

  private def workerUrl: String =
    try {
      val v = js.Dynamic.global.selectDynamic("PYODIDE_WORKER_URL")
      if (!js.isUndefined(v) && v != null) v.asInstanceOf[String]
      else DefaultWorkerUrl
    } catch case _: Throwable => DefaultWorkerUrl

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

print("$ResultBegin" + json.dumps(result) + "$ResultEnd")
"""
  }

  private def parseReport(report: PythonRunReport): PythonRunResult = {
    val raw = report.stdout
    val begin = raw.indexOf(ResultBegin)
    val end = raw.indexOf(ResultEnd)
    if (begin < 0 || end < 0 || end < begin) {
      val errMsg =
        if (report.stderr.trim.nonEmpty) report.stderr.trim
        else "Python runtime returned no result payload"
      PythonRunResult(
        status = PythonRunStatus.RuntimeError,
        tests = Seq.empty,
        stdout = raw,
        stderr = report.stderr,
        error = Some(errMsg),
        score = 0.0
      )
    } else {
      val jsonString = raw.substring(begin + ResultBegin.length, end)
      try decodeResult(JSON.parse(jsonString))
      catch {
        case ex: Throwable =>
          PythonRunResult(
            status = PythonRunStatus.RuntimeError,
            tests = Seq.empty,
            stdout = raw,
            stderr = report.stderr,
            error = Some(s"Failed to parse Python result: ${Option(ex.getMessage).getOrElse(ex.toString)}"),
            score = 0.0
          )
      }
    }
  }

  private def decodeResult(dynamic: js.Dynamic): PythonRunResult = {
    val status = dynamic.selectDynamic("status").asInstanceOf[String] match {
      case "success"       => PythonRunStatus.Success
      case "failed"        => PythonRunStatus.Failed
      case "runtime-error" => PythonRunStatus.RuntimeError
      case _               => PythonRunStatus.RuntimeError
    }
    val tests = dynamic
      .selectDynamic("tests")
      .asInstanceOf[js.Array[js.Dynamic]]
      .map { entry =>
        val st = entry.selectDynamic("status").asInstanceOf[String] match {
          case "passed"  => PythonTestStatus.Passed
          case "failed"  => PythonTestStatus.Failed
          case "errored" => PythonTestStatus.Errored
          case _         => PythonTestStatus.Errored
        }
        PythonTestResult(
          name = entry.selectDynamic("name").asInstanceOf[String],
          status = st,
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

  private def toOptionString(value: js.Any): Option[String] =
    if (js.isUndefined(value) || value == null) None
    else Option(value.asInstanceOf[String]).filter(_.nonEmpty)
}
