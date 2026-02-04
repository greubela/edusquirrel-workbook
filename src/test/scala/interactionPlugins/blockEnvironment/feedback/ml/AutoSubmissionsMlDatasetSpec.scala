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

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.JSConverters.*
import scala.util.hashing.MurmurHash3

/**
 * Dev-only generator: creates many plausible (hand-written) student submissions for the existing
 * BlockFeedback exercises, executes their unit tests via local python, extracts features, and
 * logs to /api/ml/log-example.
 *
 * This is meant to be repeatable whenever you add new exercises and want to re-train.
 *
 * Enable by setting env AUTO_SUBMISSIONS=1.
 */
final class AutoSubmissionsMlDatasetSpec extends FunSuite {

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

  private def intEnv(name: String, default: Int): Int =
    readEnv(name).flatMap(_.toIntOption).getOrElse(default)

  private def clamp(v: Int, lo: Int, hi: Int): Int =
    math.max(lo, math.min(hi, v))

  private def saveAcceptedSubmissionIfEnabled(
    enabled: Boolean,
    exerciseId: String,
    label: String,
    submissionNr: Int,
    pythonCode: String
  ): Option[String] = {
    if (!enabled) return None

    try {
      val process = js.Dynamic.global.selectDynamic("process")
      val cwd =
        if (js.isUndefined(process) || process == null) "."
        else process.selectDynamic("cwd").asInstanceOf[js.Function0[String]]()

      val fs = js.Dynamic.global.require("fs")
      val path = js.Dynamic.global.require("path")

      val baseDir = path.join(cwd, "tools", "openai-proxy", "ml-logs", "submissions")
      fs.mkdirSync(baseDir, js.Dynamic.literal(recursive = true))

      val safeExercise = exerciseId.replaceAll("[^a-zA-Z0-9._-]", "_")
      val exerciseDir = path.join(baseDir, safeExercise)
      fs.mkdirSync(exerciseDir, js.Dynamic.literal(recursive = true))

      val safeLabel = label.replaceAll("[^a-zA-Z0-9._-]", "_")
      val fileName = f"$submissionNr%07d_$safeLabel.py"
      val filePath = path.join(exerciseDir, fileName)

      fs.writeFileSync(filePath, pythonCode, "utf8")
      Some(filePath.toString)
    } catch {
      case _: Throwable => None
    }
  }

  private def exprFromPython(source: String): BeExpression =
    BeStartProgram(PythonParser.parsePython(source))

  private def dummyExerciseText(text: String): LanguageMap[HumanLanguage] =
    LanguageMap.mapBasedLanguageMap(
      Map[HumanLanguage, String](
        AppLanguage.English -> text
      )
    )

  private def tryParseExpr(source: String): Option[BeExpression] =
    try Some(exprFromPython(source))
    catch case _: Throwable => None

  private def dummyExpr: BeExpression =
    exprFromPython("""def __dummy__():\n    return 0\n""")

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

except SyntaxError as se:
  result['status'] = 'compile-error'
  result['error'] = traceback.format_exc()

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
  ): (PythonRuntimeOutcome, String) = {
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

    val errOpt =
      if (!js.isUndefined(res.selectDynamic("error")) && res.selectDynamic("error") != null)
        Some(res.selectDynamic("error").toString)
      else None

    val stdoutRaw = Option(res.selectDynamic("stdout").asInstanceOf[String]).getOrElse("")
    val stderrRaw = Option(res.selectDynamic("stderr").asInstanceOf[String]).getOrElse("")

    if (errOpt.nonEmpty) {
      val out = PythonRuntimeOutcome(
        tests = Seq(
          PythonTestResult(
            name = "python-runner",
            passed = false,
            expected = "Python must run",
            actual = errOpt.getOrElse("error"),
            message = errOpt
          )
        ),
        runStatus = Some(PythonRunStatus.RuntimeError),
        normalizedScore = Some(0.0),
        runtimeError = errOpt,
        stdout = Option(stdoutRaw).filter(_.nonEmpty),
        stderr = Option(stderrRaw).filter(_.nonEmpty)
      )
      return (out, "spawn-error")
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

    val (runStatus, runtimeError) = status match {
      case "success" => (PythonRunStatus.Success, None)
      case "failed" => (PythonRunStatus.Failed, None)
      case "compile-error" =>
        val err =
          if (js.isUndefined(parsed.selectDynamic("error")) || parsed.selectDynamic("error") == null) None
          else Some(parsed.selectDynamic("error").toString).filter(_.nonEmpty)
        (PythonRunStatus.RuntimeError, err)
      case _ =>
        val err =
          if (js.isUndefined(parsed.selectDynamic("error")) || parsed.selectDynamic("error") == null) None
          else Some(parsed.selectDynamic("error").toString).filter(_.nonEmpty)
        (PythonRunStatus.RuntimeError, err)
    }

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

    (outcome, status)
  }

  private enum VariantType:
    case Correct
    case CompileError
    case Incomplete
    case LogicWrong
    case BoundaryWrong
    case ExceptionType
    case NonDeterminism
    case IoContract
    case FormatOutput

  private final case class Variant(kind: VariantType, python: String, note: String)

  private object AutoSolutions {

    // Maintain this map: add new exercises here when you want them included.
    val baseCorrect: Map[String, String] = Map(
      BlockFeedbackExerciseRegistry.addTwoNumbersExerciseId ->
        """def add(a, b):
          |    return a + b
          |""".stripMargin,
      BlockFeedbackExerciseRegistry.maxInListExerciseId ->
        """def max_in_list(xs):
          |    if not xs:
          |        return None
          |    m = xs[0]
          |    for x in xs[1:]:
          |        if x > m:
          |            m = x
          |    return m
          |""".stripMargin,
      BlockFeedbackExerciseRegistry.balancedBracketsExerciseId ->
        """def balanced_brackets(s):
          |    stack = []
          |    pairs = {')': '(', ']': '[', '}': '{'}
          |    for ch in s:
          |        if ch in '([{':
          |            stack.append(ch)
          |        elif ch in ')]}':
          |            if not stack or stack[-1] != pairs[ch]:
          |                return False
          |            stack.pop()
          |    return len(stack) == 0
          |""".stripMargin,
      BlockFeedbackExerciseRegistry.twoSumIndicesExerciseId ->
        """def two_sum_indices(nums, target):
          |    seen = {}
          |    for i, x in enumerate(nums):
          |        need = target - x
          |        if need in seen:
          |            return (seen[need], i)
          |        seen[x] = i
          |    return (-1, -1)
          |""".stripMargin,
      BlockFeedbackExerciseRegistry.palindromeExerciseId ->
        """def is_palindrome(s):
          |    cleaned = []
          |    for ch in s:
          |        if ch.isalnum():
          |            cleaned.append(ch.lower())
          |    cleaned = ''.join(cleaned)
          |    return cleaned == cleaned[::-1]
          |""".stripMargin,
      BlockFeedbackExerciseRegistry.gcdExerciseId ->
        """def gcd(a, b):
          |    a = abs(a)
          |    b = abs(b)
          |    while b != 0:
          |        a, b = b, a % b
          |    return a
          |""".stripMargin,
      BlockFeedbackExerciseRegistry.countVowelsExerciseId ->
        """def count_vowels(s):
          |    vowels = set('aeiou')
          |    c = 0
          |    for ch in s.lower():
          |        if ch in vowels:
          |            c += 1
          |    return c
          |""".stripMargin,
      BlockFeedbackExerciseRegistry.runLengthEncodeExerciseId ->
        """def rle_encode(s):
          |    if s == '':
          |        return []
          |    out = []
          |    cur = s[0]
          |    cnt = 1
          |    for ch in s[1:]:
          |        if ch == cur:
          |            cnt += 1
          |        else:
          |            out.append((cur, cnt))
          |            cur = ch
          |            cnt = 1
          |    out.append((cur, cnt))
          |    return out
          |""".stripMargin,
      BlockFeedbackExerciseRegistry.mergeSortedExerciseId ->
        """def merge_sorted(a, b):
          |    i = 0
          |    j = 0
          |    out = []
          |    while i < len(a) and j < len(b):
          |        if a[i] <= b[j]:
          |            out.append(a[i])
          |            i += 1
          |        else:
          |            out.append(b[j])
          |            j += 1
          |    out.extend(a[i:])
          |    out.extend(b[j:])
          |    return out
          |""".stripMargin,
      BlockFeedbackExerciseRegistry.uniquePreserveOrderExerciseId ->
        """def unique(xs):
          |    seen = set()
          |    out = []
          |    for x in xs:
          |        if x not in seen:
          |            out.append(x)
          |            seen.add(x)
          |    return out
          |""".stripMargin,
      BlockFeedbackExerciseRegistry.romanToIntExerciseId ->
        """def roman_to_int(s):
          |    values = {'I': 1, 'V': 5, 'X': 10, 'L': 50, 'C': 100, 'D': 500, 'M': 1000}
          |    total = 0
          |    prev = 0
          |    for ch in reversed(s):
          |        v = values[ch]
          |        if v < prev:
          |            total -= v
          |        else:
          |            total += v
          |            prev = v
          |    return total
          |""".stripMargin,
      BlockFeedbackExerciseRegistry.intToRomanExerciseId ->
        """def int_to_roman(n):
          |    vals = [1000,900,500,400,100,90,50,40,10,9,5,4,1]
          |    syms = ['M','CM','D','CD','C','XC','L','XL','X','IX','V','IV','I']
          |    out = []
          |    i = 0
          |    while n > 0:
          |        while n >= vals[i]:
          |            out.append(syms[i])
          |            n -= vals[i]
          |        i += 1
          |    return ''.join(out)
          |""".stripMargin,
      BlockFeedbackExerciseRegistry.normalizeWhitespaceExerciseId ->
        """def normalize_whitespace(s):
          |    parts = s.split()
          |    return ' '.join(parts)
          |""".stripMargin,
      BlockFeedbackExerciseRegistry.rotateListExerciseId ->
        """def rotate(xs, k):
          |    if not xs:
          |        return []
          |    k = k % len(xs)
          |    if k == 0:
          |        return list(xs)
          |    return xs[-k:] + xs[:-k]
          |""".stripMargin
    )

    private def injectCompileError(code: String): String = {
      val lines = code.split("\n", -1).toVector
      val idx = lines.indexWhere(_.trim.startsWith("def "))
      if (idx < 0) code + "\nthis is not python\n"
      else {
        // remove colon from def line
        val broken = lines(idx).replace(":", "")
        (lines.updated(idx, broken)).mkString("\n")
      }
    }

    private def makeIncomplete(code: String): String = {
      val lines = code.split("\n", -1).toVector
      val idx = lines.indexWhere(_.trim.startsWith("def "))
      if (idx < 0) "pass\n"
      else {
        val defLine = lines(idx)
        val indent = "    "
        val injected = Vector(defLine, indent + "pass")
        injected.mkString("\n") + "\n"
      }
    }

    private def injectAfterFirstDefLine(code: String, injectedLines: Seq[String]): String = {
      val lines = code.split("\n", -1).toVector
      val idx = lines.indexWhere(_.trim.startsWith("def "))
      if (idx < 0) code
      else {
        val out = lines.take(idx + 1) ++ injectedLines ++ lines.drop(idx + 1)
        out.mkString("\n")
      }
    }

    private def exceptionTypeFor(base: String): String = {
      // Triggers DecisionLayer.EXCEPTION_TYPE via "ZeroDivisionError".
      injectAfterFirstDefLine(base, Seq("    _boom = 1 / 0"))
    }

    private def nonDeterminismFor(base: String): String = {
      // Triggers DecisionLayer.NONDETERMINISM (random word present) when tests fail.
      injectAfterFirstDefLine(base, Seq("    import random", "    _x = random.random()"))
    }

    private def ioContractFor(base: String): String = {
      // Counts as input() usage but will not block because it's unreachable.
      injectAfterFirstDefLine(base, Seq("    if False:", "        _x = input()"))
    }

    private def formatOutputFor(base: String): String = {
      // Counts as print() and will produce stdout when the function is called.
      injectAfterFirstDefLine(base, Seq("    print(\"debug\")", "    print(\"debug\")", "    print(\"debug\")"))
    }

    private def decorateWithNoise(code: String, seed: Int): String = {
      val rnd = new scala.util.Random(seed)
      val blankPrefix = (0 until rnd.nextInt(3)).map(_ => "").mkString("\n")
      val commentCount = 1 + rnd.nextInt(3)
      val commentPrefix = (0 until commentCount).map(i => s"# auto-gen note ${seed}_${i}").mkString("\n")
      val extraBlankBetween = if rnd.nextBoolean() then "\n" else "\n\n"
      val core = code.trim + "\n"
      s"$commentPrefix$extraBlankBetween$blankPrefix$core"
    }

    private def applySmallMutations(code: String, seed: Int): (String, String) = {
      val rnd = new scala.util.Random(seed)
      val rules: Vector[(String, String, String)] = Vector(
        ("<=", "<", "relop <= -> <"),
        (">=", ">", "relop >= -> >"),
        ("==", "!=", "relop == -> !="),
        ("+", "-", "op + -> -"),
        ("-", "+", "op - -> +"),
        ("%", "//", "op % -> //")
      )
      val pick = rules(rnd.nextInt(rules.size))
      val (from, to, note) = pick
      if code.contains(from) then (code.replaceFirst(java.util.regex.Pattern.quote(from), java.util.regex.Matcher.quoteReplacement(to)), note)
      else (code, "no-op")
    }

    private def logicWrongFor(exerciseId: String, base: String): String =
      exerciseId match {
        case BlockFeedbackExerciseRegistry.addTwoNumbersExerciseId => base.replace("a + b", "a - b")
        case BlockFeedbackExerciseRegistry.maxInListExerciseId => base.replace("> m", "< m")
        case BlockFeedbackExerciseRegistry.twoSumIndicesExerciseId => base.replace("return (seen[need], i)", "return (i, seen[need])")
        case BlockFeedbackExerciseRegistry.palindromeExerciseId => base.replace("if ch.isalnum():", "if ch.isalpha():")
        case BlockFeedbackExerciseRegistry.gcdExerciseId => base.replace("a % b", "a // b")
        case BlockFeedbackExerciseRegistry.countVowelsExerciseId => base.replace("set('aeiou')", "set('aeio')")
        case BlockFeedbackExerciseRegistry.runLengthEncodeExerciseId => base.replace("out.append((cur, cnt))", "out.append((cur, cnt+1))")
        case BlockFeedbackExerciseRegistry.mergeSortedExerciseId => base.replace("<=", "<")
        case BlockFeedbackExerciseRegistry.uniquePreserveOrderExerciseId => base.replace("seen = set()", "seen = []")
        case BlockFeedbackExerciseRegistry.romanToIntExerciseId => base.replace("if v < prev", "if v <= prev")
        case BlockFeedbackExerciseRegistry.intToRomanExerciseId => base.replace("while n >= vals[i]", "if n >= vals[i]")
        case BlockFeedbackExerciseRegistry.normalizeWhitespaceExerciseId => base.replace("' '.join(parts)", "''.join(parts)")
        case BlockFeedbackExerciseRegistry.rotateListExerciseId => base.replace("k = k % len(xs)", "k = k")
        case _ => base
      }

    private def boundaryWrongFor(exerciseId: String, base: String): String =
      exerciseId match {
        case BlockFeedbackExerciseRegistry.maxInListExerciseId => base.replace("if not xs:\n        return None", "if not xs:\n        return 0")
        case BlockFeedbackExerciseRegistry.rotateListExerciseId => base.replace("if not xs:\n        return []", "# BUG: empty list not handled\n")
        case BlockFeedbackExerciseRegistry.normalizeWhitespaceExerciseId =>
          // keep leading/trailing spaces
          """def normalize_whitespace(s):
            |    return s.replace("\t", " ")
            |""".stripMargin
        case _ => base
      }

    def variantsFor(exerciseId: String, base: String): Seq[Variant] = {
      val correct = Variant(VariantType.Correct, base, "base")
      val compileErr = Variant(VariantType.CompileError, injectCompileError(base), "def-line colon removed")
      val incomplete = Variant(VariantType.Incomplete, makeIncomplete(base), "contains pass")
      val logicWrong = Variant(VariantType.LogicWrong, logicWrongFor(exerciseId, base), "likely fails at least one test")
      val boundaryWrong = Variant(VariantType.BoundaryWrong, boundaryWrongFor(exerciseId, base), "boundary handling bug")

      // Additional, intentionally separated issue families (more label diversity)
      val excType = Variant(VariantType.ExceptionType, exceptionTypeFor(base), "ZeroDivisionError inside function")
      val nondet = Variant(VariantType.NonDeterminism, nonDeterminismFor(logicWrongFor(exerciseId, base)), "random() used + wrong result")
      val io = Variant(VariantType.IoContract, ioContractFor(logicWrongFor(exerciseId, base)), "unreachable input() + wrong result")
      val fmt = Variant(VariantType.FormatOutput, formatOutputFor(logicWrongFor(exerciseId, base)), "prints + wrong result")

      Seq(correct, compileErr, incomplete, logicWrong, boundaryWrong, excType, nondet, io, fmt)
    }

    def materializeVariant(exerciseId: String, variant: Variant, rep: Int): (String, String) = {
      // Make each repetition actually different, while staying deterministic.
      val seedBase = MurmurHash3.stringHash(s"$exerciseId|${variant.kind}|$rep")
      val (mutated, mutNote) =
        if variant.kind == VariantType.Correct then (variant.python, "")
        else applySmallMutations(variant.python, seedBase)

      val noisy = decorateWithNoise(mutated, seedBase)
      val note = (Seq(variant.note, mutNote).filter(_.nonEmpty)).mkString("; ")
      (noisy, note)
    }

    private val desiredLabelOrder: Vector[String] = Vector(
      // prefer specific issues first
      DecisionLayer.IssueType.COMPILE_ERROR.toString,
      DecisionLayer.IssueType.INCOMPLETE_IMPLEMENTATION.toString,
      DecisionLayer.IssueType.EXCEPTION_TYPE.toString,
      DecisionLayer.IssueType.NONDETERMINISM.toString,
      DecisionLayer.IssueType.IO_CONTRACT.toString,
      DecisionLayer.IssueType.FORMAT_OUTPUT.toString,
      DecisionLayer.IssueType.BOUNDARY_CONDITION.toString,
      DecisionLayer.IssueType.API_SIGNATURE.toString,
      DecisionLayer.IssueType.PERFORMANCE.toString,
      // last resort / bucket label
      DecisionLayer.IssueType.LOGIC_EDGE_CASE.toString
    )

    def shouldAcceptLabel(labelCounts: collection.Map[String, Int], label: String, targetMax: Int, acceptedSoFar: Int): Boolean = {
      // Hard cap for LOGIC_EDGE_CASE so the dataset doesn't collapse to that.
      val logicCap = math.max(6, (targetMax * 0.35).toInt)
      if (label == DecisionLayer.IssueType.LOGIC_EDGE_CASE.toString && labelCounts.getOrElse(label, 0) >= logicCap) {
        // Allow if we're still struggling to reach minimum size.
        return acceptedSoFar < math.max(10, targetMax / 3)
      }

      // Try to keep roughly balanced across the top labels.
      val activeLabels = desiredLabelOrder.take(7) // core issues we can realistically generate
      val perLabelTarget = math.max(3, targetMax / math.max(3, activeLabels.size))

      val current = labelCounts.getOrElse(label, 0)
      if (activeLabels.contains(label)) current < perLabelTarget
      else {
        // For labels we rarely hit (or unknown), accept a few.
        current < math.max(2, perLabelTarget / 2)
      }
    }

    def preferredLabelStillMissing(labelCounts: collection.Map[String, Int], targetMax: Int): Boolean = {
      val activeLabels = desiredLabelOrder.take(7)
      val perLabelTarget = math.max(3, targetMax / math.max(3, activeLabels.size))
      activeLabels.exists(l => labelCounts.getOrElse(l, 0) < perLabelTarget)
    }

    /** Generate a candidate Variant (family) given a trial number. */
    def pickFamily(exerciseId: String, base: String, trial: Int): Variant = {
      val families = variantsFor(exerciseId, base)
      val seed = MurmurHash3.stringHash(s"$exerciseId|family|$trial")
      val idx = math.abs(seed) % families.size
      families(idx)
    }
  }

  test("auto submissions ML dataset generation (dev-only)") {
    assume(boolEnv("AUTO_SUBMISSIONS"), "Set AUTO_SUBMISSIONS=1 to run")

    val proxyBase = readEnv("ML_PROXY_BASE").getOrElse("http://127.0.0.1:8000")
    val logUrl = readEnv("ML_LOG_URL").getOrElse(s"$proxyBase/api/ml/log-example")

    val pythonBin = readEnv("PYTHON_BIN").getOrElse("python3")

    val requestedExercises =
      readEnv("AUTO_EXERCISES").map(_.split(",").map(_.trim).filter(_.nonEmpty).toSeq).getOrElse(Nil)

    val allExerciseIds =
      if (requestedExercises.nonEmpty) requestedExercises
      else AutoSolutions.baseCorrect.keys.toSeq.sorted

    val targetMin = clamp(intEnv("AUTO_TARGET_MIN", 30), 5, 200)
    val targetMax = clamp(intEnv("AUTO_TARGET_MAX", 50), targetMin, 400)
    val maxTriesPerExercise = clamp(intEnv("AUTO_MUTATION_TRIES", 600), targetMax, 5000)

    val saveCode = boolEnv("AUTO_SAVE_CODE")

    val seenFeatureSigs = scala.collection.mutable.HashSet[String]()

    def featureSig(exerciseId: String, weakLabel: String, features: Map[String, Double]): String = {
      val items = features.toSeq.sortBy(_._1).map { case (k, v) =>
        // Round to reduce accidental float formatting noise.
        val vv = math.rint(v * 1000.0) / 1000.0
        s"$k=$vv"
      }.mkString("|")
      val h = MurmurHash3.stringHash(items).toHexString
      s"$exerciseId|$weakLabel|$h"
    }

    val runF = Future.sequence(
      allExerciseIds.zipWithIndex.map { case (exerciseId, idx) =>
        Future {
          val defnOpt = BlockFeedbackExerciseRegistry.byExerciseId.get(exerciseId)
          val baseOpt = AutoSolutions.baseCorrect.get(exerciseId)

          (defnOpt, baseOpt) match {
            case (Some(defn), Some(base)) =>
              val statement =
                defn.statementTranslations
                  .getOrElse(AppLanguage.English, defn.statementTranslations.values.headOption.getOrElse(""))

              val tests: Seq[(String, String, Double, Option[String])] =
                (defn.config.visibleTests ++ defn.config.hiddenTests).map(t => (t.name, t.code, t.weight, t.hint))

              val seenLocal = scala.collection.mutable.HashSet[String]()
              val labelCounts = scala.collection.mutable.HashMap[String, Int]().withDefaultValue(0)

              var accepted = 0
              var trial = 0

              def mkSubmissionNr(t: Int): Int = 1 + idx * 100_000 + t

              while (
                accepted < targetMax &&
                  trial < maxTriesPerExercise &&
                  (accepted < targetMin || AutoSolutions.preferredLabelStillMissing(labelCounts, targetMax))
              ) {
                val family = AutoSolutions.pickFamily(exerciseId, base, trial)
                val (pythonCode, materializedNote) = AutoSolutions.materializeVariant(exerciseId, family, rep = trial)

                val (outcome, runnerStatus) = runPython(
                  pythonBin = pythonBin,
                  code = pythonCode,
                  tests = tests,
                  timeoutMs = math.max(1000, defn.config.timeoutMs + 1500)
                )

                val (expr, sourceOverride) =
                  tryParseExpr(pythonCode) match
                    case Some(e) => (e, None)
                    case None    => (dummyExpr, Some(pythonCode))

                val req = BlockFeedbackRequest(
                  exerciseText = dummyExerciseText(statement),
                  studentCodePython = expr,
                  pythonSourceOverride = sourceOverride,
                  submissionNr = mkSubmissionNr(trial),
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
                val weakDecision0 = DecisionLayer.heuristicRoute(signals)
                val label = weakDecision0.primaryIssue.toString
                val features = FeatureExtractor.toMap(signals)
                val sig = featureSig(exerciseId, label, features)

                val accept =
                  !seenFeatureSigs.contains(sig) &&
                    !seenLocal.contains(sig) &&
                    AutoSolutions.shouldAcceptLabel(labelCounts, label, targetMax, accepted)

                if (accept) {
                  seenFeatureSigs.add(sig)
                  seenLocal.add(sig)
                  labelCounts.update(label, labelCounts(label) + 1)
                  accepted += 1

                  val savedPathOpt = saveAcceptedSubmissionIfEnabled(
                    enabled = saveCode,
                    exerciseId = exerciseId,
                    label = label,
                    submissionNr = req.submissionNr,
                    pythonCode = pythonCode
                  )

                  MlTrainingLogger.logIfEnabled(
                    enabled = true,
                    logUrl = Some(logUrl),
                    request = req,
                    weakDecision = weakDecision0,
                    features = features,
                    meta = Map(
                      "source" -> "auto_submissions",
                      "variant" -> family.kind.toString,
                      "label" -> label,
                      "note" -> materializedNote,
                      "pythonRunnerStatus" -> runnerStatus,
                      "savedPythonPath" -> savedPathOpt.getOrElse(""),
                      "trial" -> trial.toString,
                      "accepted" -> accepted.toString,
                      "targetMin" -> targetMin.toString,
                      "targetMax" -> targetMax.toString
                    )
                  )
                }

                trial += 1
              }

              js.Dynamic.global.console.log(
                s"[auto_submissions] $exerciseId accepted=$accepted trials=$trial labels=" + labelCounts.toSeq
                  .sortBy(_._1)
                  .mkString(",")
              )

            case _ =>
              js.Dynamic.global.console.log(s"[auto_submissions] $exerciseId skipped (missing defn/base)")
          }
        }
      }
    ).map(_ => ())

    runF.map(_ => assert(true))
  }
}
