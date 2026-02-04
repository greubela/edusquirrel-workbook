package interactionPlugins.blockEnvironment.feedback.ml

import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.others.BeStartProgram
import contentmanagement.model.vm.parsing.python.PythonParser
import interactionPlugins.blockEnvironment.feedback.{
  BlockFeedbackConfig,
  BlockFeedbackMeta,
  BlockFeedbackRequest,
  BlockFeedbackExerciseRegistry,
  PythonRuntimeOutcome,
  PythonTestResult
}
import interactionPlugins.blockEnvironment.feedback.rules.{PythonStaticRules, VmStaticRules}
import interactionPlugins.pythonExercises.PythonRunStatus
import munit.FunSuite

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.JSConverters.*

/**
 * Dev-only generator: uses the proxy LLM to synthesize student solutions (good/bad),
 * executes unit tests via local python, extracts features, logs to /api/ml/log-example,
 * and optionally triggers /api/ml/train.
 *
 * Enable by setting env SYNTH_GENERATE=1.
 */
final class SyntheticMlDatasetGeneratorSpec extends FunSuite {

  private given ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private def readEnv(name: String): Option[String] =
    try {
      val process = js.Dynamic.global.selectDynamic("process")
      if (js.isUndefined(process) || process == null) None
      else {
        val env = process.selectDynamic("env")
        if (js.isUndefined(env) || env == null) None
        else {
          val v = env.selectDynamic(name)
          if (js.isUndefined(v) || v == null) None else Some(v.toString)
        }
      }
    } catch {
      case _: Throwable => None
    }

  private def boolEnv(name: String): Boolean =
    readEnv(name).exists(v => v == "1" || v.equalsIgnoreCase("true"))

  private def exprFromPython(source: String): BeExpression =
    BeStartProgram(PythonParser.parsePython(source))

  private def dummyExerciseText(text: String): LanguageMap[HumanLanguage] =
    LanguageMap.mapBasedLanguageMap(
      Map[HumanLanguage, String](
        AppLanguage.English -> text
      )
    )

  private def jsPromiseToFuture[A](promise: js.Promise[A]): Future[A] = {
    val p = Promise[A]()
    promise.`then`[Unit](
      (value: A) => { p.success(value); () },
      (err: Any) => { p.failure(new Exception(err.toString)); () }
    )
    p.future
  }

  private def fetchJson(url: String, payload: js.Any): Future[js.Dynamic] = {
    val fetch = js.Dynamic.global.selectDynamic("fetch")
    if (js.isUndefined(fetch) || fetch == null)
      Future.failed(new Exception("global fetch() not available (Node 18+ required)"))
    else {
      val opts = js.Dynamic.literal(
        method = "POST",
        headers = js.Dictionary("content-type" -> "application/json"),
        body = JSON.stringify(payload)
      )
      jsPromiseToFuture(fetch(url, opts).asInstanceOf[js.Promise[js.Dynamic]]).flatMap { resp =>
        val ok = resp.selectDynamic("ok").asInstanceOf[Boolean]
        val status = resp.selectDynamic("status").asInstanceOf[Int]
        jsPromiseToFuture(resp.text().asInstanceOf[js.Promise[String]]).flatMap { txt =>
          if (!ok) Future.failed(new Exception(s"HTTP $status: $txt"))
          else Future.successful(JSON.parse(txt).asInstanceOf[js.Dynamic])
        }
      }
    }
  }

  private val pythonRunnerCode: String =
    """
import sys, json, traceback
from io import StringIO

payload = json.load(sys.stdin)
code = payload.get('code', '')
tests = payload.get('tests', [])

result = {
  'status': 'success',
  'tests': [],
  'stdout': '',
  'stderr': '',
  'error': None,
  'score': 0.0
}

stdout_capture = StringIO()
stderr_capture = StringIO()
old_stdout, old_stderr = sys.stdout, sys.stderr

try:
  sys.stdout = stdout_capture
  sys.stderr = stderr_capture

  namespace = {}
  exec(code, namespace, namespace)

  total_weight = 0.0
  earned = 0.0

  for t in tests:
    name = t.get('name', 'test')
    weight = float(t.get('weight', 1.0) or 1.0)
    total_weight += weight
    entry = {'name': name, 'status': 'passed', 'message': None, 'hint': t.get('hint')}
    try:
      exec(t.get('code', ''), namespace, namespace)
      earned += weight
    except AssertionError as ae:
      entry['status'] = 'failed'
      entry['message'] = str(ae)
    except Exception:
      entry['status'] = 'errored'
      entry['message'] = traceback.format_exc()
      result['status'] = 'runtime-error'
    finally:
      result['tests'].append(entry)

  if total_weight <= 0.0:
    total_weight = 1.0
  result['score'] = max(0.0, min(1.0, earned / total_weight))
  if result['status'] == 'success' and result['score'] < 1.0:
    result['status'] = 'failed'

except Exception:
  result['status'] = 'runtime-error'
  result['error'] = traceback.format_exc()

finally:
  result['stdout'] = stdout_capture.getvalue()
  result['stderr'] = stderr_capture.getvalue()
  sys.stdout = old_stdout
  sys.stderr = old_stderr

sys.__stdout__.write(json.dumps(result))
""".trim

  private def runPython(
    pythonBin: String,
    code: String,
    tests: Seq[(String, String, Double, Option[String])],
    timeoutMs: Int
  ): (PythonRuntimeOutcome, Option[String], Option[String]) = {
    val childProcess = js.Dynamic.global.require("child_process")

    val testsJson = js.Array[js.Any]()
    tests.foreach { case (name, tcode, weight, hint) =>
      testsJson.push(
        js.Dynamic.literal(
          name = name,
          code = tcode,
          weight = weight,
          hint = hint.orNull
        )
      )
    }

    val payload = js.Dynamic.literal(
      code = code,
      tests = testsJson
    )

    val args = js.Array("-c", pythonRunnerCode)
    val opts = js.Dynamic.literal(
      input = JSON.stringify(payload),
      encoding = "utf8",
      timeout = timeoutMs
    )

    val res = childProcess.spawnSync(pythonBin, args, opts)

    val err =
      if (!js.isUndefined(res.selectDynamic("error")) && res.selectDynamic("error") != null)
        Some(res.selectDynamic("error").toString)
      else None

    val stdoutRaw = Option(res.selectDynamic("stdout").asInstanceOf[String]).getOrElse("")
    val stderrRaw = Option(res.selectDynamic("stderr").asInstanceOf[String]).getOrElse("")

    if (err.nonEmpty) {
      val outcome = PythonRuntimeOutcome(
        tests = Seq(
          PythonTestResult(
            name = "python-runner",
            passed = false,
            expected = "Python must run",
            actual = err.getOrElse("error"),
            message = err
          )
        ),
        runStatus = Some(PythonRunStatus.RuntimeError),
        normalizedScore = Some(0.0),
        runtimeError = err,
        stdout = Option(stdoutRaw).filter(_.nonEmpty),
        stderr = Option(stderrRaw).filter(_.nonEmpty)
      )
      return (outcome, Option(stdoutRaw).filter(_.nonEmpty), Option(stderrRaw).filter(_.nonEmpty))
    }

    val parsed = JSON.parse(stdoutRaw).asInstanceOf[js.Dynamic]
    val status = parsed.selectDynamic("status").asInstanceOf[String]
    val score = parsed.selectDynamic("score").asInstanceOf[Double]

    val testsArr = parsed.selectDynamic("tests").asInstanceOf[js.Array[js.Dynamic]]
    val mapped = testsArr.toSeq.map { t =>
      val name = t.selectDynamic("name").asInstanceOf[String]
      val st = t.selectDynamic("status").asInstanceOf[String]
      val hint =
        if (js.isUndefined(t.selectDynamic("hint")) || t.selectDynamic("hint") == null) None
        else Some(t.selectDynamic("hint").toString)
      val msgOpt =
        if (js.isUndefined(t.selectDynamic("message")) || t.selectDynamic("message") == null) None
        else Some(t.selectDynamic("message").toString)

      val passed = st == "passed"
      PythonTestResult(
        name = name,
        passed = passed,
        expected = hint.getOrElse("Test should pass"),
        actual = if (passed) "OK" else msgOpt.getOrElse("Failed"),
        message = msgOpt.orElse(hint)
      )
    }

    val runStatus = status match {
      case "success" => PythonRunStatus.Success
      case "failed" => PythonRunStatus.Failed
      case _          => PythonRunStatus.RuntimeError
    }

    val runtimeError =
      if (js.isUndefined(parsed.selectDynamic("error")) || parsed.selectDynamic("error") == null) None
      else Some(parsed.selectDynamic("error").toString).filter(_.nonEmpty)

    val outStdout =
      if (js.isUndefined(parsed.selectDynamic("stdout")) || parsed.selectDynamic("stdout") == null) ""
      else parsed.selectDynamic("stdout").toString

    val outStderr =
      if (js.isUndefined(parsed.selectDynamic("stderr")) || parsed.selectDynamic("stderr") == null) ""
      else parsed.selectDynamic("stderr").toString

    val outcome = PythonRuntimeOutcome(
      tests = mapped,
      runStatus = Some(runStatus),
      normalizedScore = Some(score),
      runtimeError = runtimeError,
      stdout = Option(outStdout).filter(_.nonEmpty),
      stderr = Option(outStderr).filter(_.nonEmpty)
    )

    (outcome, Option(outStdout).filter(_.nonEmpty), Option(outStderr).filter(_.nonEmpty))
  }

  test("synthetic ML dataset generation (dev-only)") {
    assume(boolEnv("SYNTH_GENERATE"), "Set SYNTH_GENERATE=1 to run")

    val proxyBase = readEnv("SYNTH_PROXY_BASE").getOrElse("http://127.0.0.1:8000")
    val synthUrl = s"$proxyBase/api/synth/generate"
    val logUrl = readEnv("ML_LOG_URL").getOrElse(s"$proxyBase/api/ml/log-example")
    val trainUrl = s"$proxyBase/api/ml/train"

    val pythonBin = readEnv("PYTHON_BIN").getOrElse("python3")

    val exerciseIds =
      readEnv("SYNTH_EXERCISES")
        .map(_.split(",").map(_.trim).filter(_.nonEmpty).toSeq)
        .getOrElse(BlockFeedbackExerciseRegistry.byExerciseId.keys.toSeq.sorted.take(8))

    val perExercise = readEnv("SYNTH_PER_EXERCISE").flatMap(s => s.toIntOption).getOrElse(6)

    val labelCounts = js.Dictionary[js.Any](
      "CORRECT" -> math.max(1, perExercise / 3),
      "COMPILE_ERROR" -> 1,
      "LOGIC_EDGE_CASE" -> math.max(1, perExercise / 3),
      "FORMAT_OUTPUT" -> math.max(1, perExercise / 6)
    )

    val doTrain = boolEnv("SYNTH_TRAIN")

    val runF = Future.sequence(
      exerciseIds.zipWithIndex.map { case (exerciseId, idx) =>
        val defnOpt = BlockFeedbackExerciseRegistry.byExerciseId.get(exerciseId)
        defnOpt match {
          case None => Future.successful(())
          case Some(defn) =>
            val statement =
              defn.statementTranslations
                .getOrElse(AppLanguage.English, defn.statementTranslations.values.headOption.getOrElse(""))

            val payload = js.Dynamic.literal(
              exerciseId = exerciseId,
              statement = statement,
              visibleTests = defn.config.visibleTests.map(_.code).toJSArray,
              hiddenTests = defn.config.hiddenTests.map(_.code).toJSArray,
              labelCounts = labelCounts
            )

            fetchJson(synthUrl, payload).flatMap { resp =>
              val ok = resp.selectDynamic("ok").asInstanceOf[Boolean]
              if (!ok) {
                val err = resp.selectDynamic("error").toString
                Future.failed(new Exception(s"synth failed: $err"))
              } else {
                val samples = resp.selectDynamic("samples").asInstanceOf[js.Array[js.Dynamic]].toSeq

                Future.sequence(
                  samples.zipWithIndex.map { case (s, j) =>
                    val label = s.selectDynamic("label").toString
                    val py = s.selectDynamic("python").toString

                    val tests: Seq[(String, String, Double, Option[String])] =
                      (defn.config.visibleTests ++ defn.config.hiddenTests).map(t => (t.name, t.code, t.weight, t.hint))

                    val (outcome, _, _) = runPython(
                      pythonBin = pythonBin,
                      code = py,
                      tests = tests,
                      timeoutMs = math.max(1000, defn.config.timeoutMs + 1500)
                    )

                    val req = BlockFeedbackRequest(
                      exerciseText = dummyExerciseText(statement),
                      studentCodePython = exprFromPython(py),
                      submissionNr = 1 + idx * 1000 + j,
                      config = BlockFeedbackConfig.default.copy(
                        enableMlLogging = true,
                        mlLogUrl = Some(logUrl),
                        enableUnitTests = false
                      ),
                      meta = BlockFeedbackMeta(exerciseId = Some(exerciseId)),
                      humanLanguage = AppLanguage.English
                    )

                    val pythonRules = PythonStaticRules.runAll(req.pythonSource, req.humanLanguage)
                    val vmRules = VmStaticRules.runAll(req.studentCodePython, req.humanLanguage)

                    val signals = BlockFeedbackSignals.from(req, pythonRules, vmRules, outcome)
                    val weakDecision = DecisionLayer.heuristicRoute(signals)

                    // Attach the intended label as evidence for later analysis.
                    val enrichedDecision =
                      weakDecision.copy(evidence = weakDecision.evidence :+ DecisionLayer.Evidence("synthLabel", label))

                    MlTrainingLogger.logIfEnabled(
                      enabled = true,
                      logUrl = Some(logUrl),
                      request = req,
                      weakDecision = enrichedDecision,
                      features = FeatureExtractor.toMap(signals)
                    )

                    Future.successful(())
                  }
                ).map(_ => ())
              }
            }
        }
      }
    ).map(_ => ())

    runF.flatMap { _ =>
      if (!doTrain) Future.successful(assert(true))
      else {
        val payload = js.Dynamic.literal(
          epochs = readEnv("SYNTH_TRAIN_EPOCHS").flatMap(_.toIntOption).getOrElse(200),
          lr = readEnv("SYNTH_TRAIN_LR").flatMap(_.toDoubleOption).getOrElse(0.15),
          l2 = readEnv("SYNTH_TRAIN_L2").flatMap(_.toDoubleOption).getOrElse(1e-3)
        )
        fetchJson(trainUrl, payload).map { resp =>
          val ok = resp.selectDynamic("ok").asInstanceOf[Boolean]
          if (!ok) fail(resp.selectDynamic("error").toString)
          else assert(true)
        }
      }
    }
  }
}
