package interactionPlugins.pythonExercises

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.Thenable.Implicits.*
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.JSConverters.*

private[interactionPlugins] final case class PythonRunRequest(
    code: String,
    visibleTests: Seq[PythonUnitTest],
    hiddenTests: Seq[PythonUnitTest],
    fixtures: Seq[PythonFixture],
    packages: Seq[String],
    timeoutMs: Int
)

private[interactionPlugins] object PythonRuntimeService {

  @js.native
  trait Pyodide extends js.Object {
    def runPythonAsync(code: String): js.Promise[js.Any] = js.native
    def loadPackage(pkg: String | js.Array[String]): js.Promise[Unit] = js.native
  }

  @js.native
  @JSGlobal("loadPyodide")
  object LoadPyodide extends js.Object {
    def apply(options: js.UndefOr[js.Object] = js.undefined): js.Promise[Pyodide] = js.native
  }

  private given ExecutionContext = queue

  private var cachedPyodide: Option[Future[Pyodide]] = None
  private var installedPackages: Set[String] = Set.empty

  private def ensurePyodideLoaded(): Future[Pyodide] = cachedPyodide match {
    case Some(value) => value
    case None =>
      val future =
        if js.typeOf(js.Dynamic.global.selectDynamic("loadPyodide")) == "function" then LoadPyodide().toFuture
        else Future.failed(new IllegalStateException("Pyodide runtime is not available on the global scope."))
      cachedPyodide = Some(future)
      future
  }

  private def ensurePackages(pyodide: Pyodide, packages: Seq[String]): Future[Unit] = {
    val missing = packages.filterNot(installedPackages.contains)
    if missing.isEmpty then Future.successful(())
    else
      pyodide
        .loadPackage(missing.toJSArray)
        .toFuture
        .map(_ => installedPackages = installedPackages ++ missing)
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
import json
import sys
import traceback
import time
from io import StringIO

_tests = json.loads(${JSON.stringify(testsLiteral)})
_code_source = json.loads($codeLiteral)
_fixtures = json.loads(${JSON.stringify(fixturesLiteral)})

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
      exec(test.get("code", ""), namespace, namespace)
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
    ensurePyodideLoaded().flatMap { pyodide =>
      ensurePackages(pyodide, request.packages).flatMap { _ =>
        val script = buildExecutionScript(request)
        pyodide
          .runPythonAsync(script)
          .toFuture
          .map(parseResult)
          .recover { case error =>
            PythonRunResult(
              PythonRunStatus.RuntimeError,
              Seq.empty,
              stdout = "",
              stderr = "",
              error = Option(error.getMessage()).filter(_.nonEmpty),
              score = 0.0
            )
          }
      }
    }.recover { case error =>
      PythonRunResult(PythonRunStatus.RuntimeError, Seq.empty, "", "", Option(error.getMessage()).filter(_.nonEmpty), 0.0)
    }
  }
}
