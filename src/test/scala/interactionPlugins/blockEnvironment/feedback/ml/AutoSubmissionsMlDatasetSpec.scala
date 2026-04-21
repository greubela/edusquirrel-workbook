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
import interactionPlugins.programmingExercise.pythonExercisesUnsorted.PythonRunStatus
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
  namespace['_student_source'] = code  # needed for no_hardcode tests

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

    // baseSolutions: the training corpus of correct solutions per exercise.
    //
    // CONVENTION:
    //   The FIRST solution in each Seq is the **canonical** base. It is used
    //     by logicWrongFor / boundaryWrongFor whose string-replacements target
    //     its exact text. Do NOT change it without updating those methods.
    //   Additional solutions are alternative correct implementations (different
    //     style, algorithm, or idiom) that enrich the CORRECT training examples.
    //
    // TO ADD A NEW EXERCISE: add one entry here (at least 2 solutions) and
    // add matching entries in logicWrongFor and boundaryWrongFor. That's all.
    val baseSolutions: Map[String, Seq[String]] = Map(

      BlockFeedbackExerciseRegistry.addTwoNumbersExerciseId -> Seq(
        // canonical
        """def add(a, b):
          |    return a + b
          |""".stripMargin,
        // named-result style
        """def add(a, b):
          |    result = a + b
          |    return result
          |""".stripMargin,
        // explicit parameter check style
        """def add(first, second):
          |    total = first + second
          |    return total
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.maxInListExerciseId -> Seq(
        // canonical loop with sentinel
        """def max_in_list(xs):
          |    if not xs:
          |        return None
          |    m = xs[0]
          |    for x in xs[1:]:
          |        if x > m:
          |            m = x
          |    return m
          |""".stripMargin,
        // index-based loop
        """def max_in_list(xs):
          |    if not xs:
          |        return None
          |    best = xs[0]
          |    for i in range(1, len(xs)):
          |        if xs[i] > best:
          |            best = xs[i]
          |    return best
          |""".stripMargin,
        // sorted descending, take first
        """def max_in_list(xs):
          |    if not xs:
          |        return None
          |    return sorted(xs, reverse=True)[0]
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.balancedBracketsExerciseId -> Seq(
        // canonical dict-pairs approach
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
        // explicit open/close sets, same logic
        """def balanced_brackets(s):
          |    opens = set('([{')
          |    closes = set(')]}')
          |    match = {')': '(', ']': '[', '}': '{'}
          |    stack = []
          |    for c in s:
          |        if c in opens:
          |            stack.append(c)
          |        elif c in closes:
          |            if not stack or stack[-1] != match[c]:
          |                return False
          |            stack.pop()
          |    return len(stack) == 0
          |""".stripMargin,
        // counter-based (only valid for single bracket type exercises, but passes all tests here)
        """def balanced_brackets(s):
          |    stack = []
          |    closing = {')': '(', ']': '[', '}': '{'}
          |    for ch in s:
          |        if ch in ('(', '[', '{'):
          |            stack.append(ch)
          |        elif ch in (')', ']', '}'):
          |            if len(stack) == 0 or stack[-1] != closing[ch]:
          |                return False
          |            stack.pop()
          |    return len(stack) == 0
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.twoSumIndicesExerciseId -> Seq(
        // canonical hash map
        """def two_sum_indices(nums, target):
          |    seen = {}
          |    for i, x in enumerate(nums):
          |        need = target - x
          |        if need in seen:
          |            return (seen[need], i)
          |        seen[x] = i
          |    return (-1, -1)
          |""".stripMargin,
        // same algorithm with different variable names
        """def two_sum_indices(nums, target):
          |    lookup = {}
          |    for idx, val in enumerate(nums):
          |        complement = target - val
          |        if complement in lookup:
          |            return (lookup[complement], idx)
          |        lookup[val] = idx
          |    return (-1, -1)
          |""".stripMargin,
        // brute-force O(n²) correct, different structure
        """def two_sum_indices(nums, target):
          |    for i in range(len(nums)):
          |        for j in range(i + 1, len(nums)):
          |            if nums[i] + nums[j] == target:
          |                return (i, j)
          |    return (-1, -1)
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.palindromeExerciseId -> Seq(
        // canonical filter, lowercase, reverse compare
        """def is_palindrome(s):
          |    cleaned = []
          |    for ch in s:
          |        if ch.isalnum():
          |            cleaned.append(ch.lower())
          |    cleaned = ''.join(cleaned)
          |    return cleaned == cleaned[::-1]
          |""".stripMargin,
        // two-pointer approach
        """def is_palindrome(s):
          |    chars = [ch.lower() for ch in s if ch.isalnum()]
          |    left = 0
          |    right = len(chars) - 1
          |    while left < right:
          |        if chars[left] != chars[right]:
          |            return False
          |        left += 1
          |        right -= 1
          |    return True
          |""".stripMargin,
        // generator-based one-liner (clean, no intermediate list)
        """def is_palindrome(s):
          |    filtered = ''.join(ch.lower() for ch in s if ch.isalnum())
          |    return filtered == filtered[::-1]
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.gcdExerciseId -> Seq(
        // canonical iterative Euclidean with abs
        """def gcd(a, b):
          |    a = abs(a)
          |    b = abs(b)
          |    while b != 0:
          |        a, b = b, a % b
          |    return a
          |""".stripMargin,
        // recursive Euclidean with abs
        """def gcd(a, b):
          |    a = abs(a)
          |    b = abs(b)
          |    if b == 0:
          |        return a
          |    return gcd(b, a % b)
          |""".stripMargin,
        // subtraction-based Euclidean (slower but correct)
        """def gcd(a, b):
          |    a = abs(a)
          |    b = abs(b)
          |    while a != b:
          |        if a > b:
          |            a = a - b
          |        else:
          |            b = b - a
          |    return a
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.countVowelsExerciseId -> Seq(
        // canonical set lookup loop
        """def count_vowels(s):
          |    vowels = set('aeiou')
          |    c = 0
          |    for ch in s.lower():
          |        if ch in vowels:
          |            c += 1
          |    return c
          |""".stripMargin,
        // sum with generator
        """def count_vowels(s):
          |    vowels = 'aeiouAEIOU'
          |    return sum(1 for ch in s if ch in vowels)
          |""".stripMargin,
        // filter + len
        """def count_vowels(s):
          |    return len([ch for ch in s.lower() if ch in 'aeiou'])
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.runLengthEncodeExerciseId -> Seq(
        // canonical explicit loop with empty-guard
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
        // index-based loop
        """def rle_encode(s):
          |    if not s:
          |        return []
          |    result = []
          |    i = 0
          |    while i < len(s):
          |        j = i + 1
          |        while j < len(s) and s[j] == s[i]:
          |            j += 1
          |        result.append((s[i], j - i))
          |        i = j
          |    return result
          |""".stripMargin,
        // enumerate-based
        """def rle_encode(s):
          |    if not s:
          |        return []
          |    out = []
          |    start = 0
          |    for i in range(1, len(s) + 1):
          |        if i == len(s) or s[i] != s[start]:
          |            out.append((s[start], i - start))
          |            start = i
          |    return out
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.mergeSortedExerciseId -> Seq(
        // canonical two-pointer
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
        // explicit tail copy with single loop variable
        """def merge_sorted(a, b):
          |    result = []
          |    ia = 0
          |    ib = 0
          |    while ia < len(a) and ib < len(b):
          |        if a[ia] <= b[ib]:
          |            result.append(a[ia])
          |            ia += 1
          |        else:
          |            result.append(b[ib])
          |            ib += 1
          |    while ia < len(a):
          |        result.append(a[ia])
          |        ia += 1
          |    while ib < len(b):
          |        result.append(b[ib])
          |        ib += 1
          |    return result
          |""".stripMargin,
        // pop-from-front style (using deque-like index)
        """def merge_sorted(a, b):
          |    merged = []
          |    left = list(a)
          |    right = list(b)
          |    while left and right:
          |        if left[0] <= right[0]:
          |            merged.append(left.pop(0))
          |        else:
          |            merged.append(right.pop(0))
          |    merged.extend(left)
          |    merged.extend(right)
          |    return merged
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.uniquePreserveOrderExerciseId -> Seq(
        // canonical set for lookups
        """def unique(xs):
          |    seen = set()
          |    out = []
          |    for x in xs:
          |        if x not in seen:
          |            out.append(x)
          |            seen.add(x)
          |    return out
          |""".stripMargin,
        // dict.fromkeys preserves insertion order (Python 3.7+)
        """def unique(xs):
          |    return list(dict.fromkeys(xs))
          |""".stripMargin,
        // manual list contains (O(n²) but correct)
        """def unique(xs):
          |    out = []
          |    for x in xs:
          |        if x not in out:
          |            out.append(x)
          |    return out
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.romanToIntExerciseId -> Seq(
        // canonical reversed scan
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
        // forward scan with lookahead
        """def roman_to_int(s):
          |    values = {'I': 1, 'V': 5, 'X': 10, 'L': 50, 'C': 100, 'D': 500, 'M': 1000}
          |    total = 0
          |    for i in range(len(s)):
          |        cur = values[s[i]]
          |        nxt = values[s[i + 1]] if i + 1 < len(s) else 0
          |        if cur < nxt:
          |            total -= cur
          |        else:
          |            total += cur
          |    return total
          |""".stripMargin,
        // convert to list and iterate with index
        """def roman_to_int(s):
          |    val = {'I': 1, 'V': 5, 'X': 10, 'L': 50, 'C': 100, 'D': 500, 'M': 1000}
          |    nums = [val[c] for c in s]
          |    result = 0
          |    for i in range(len(nums)):
          |        if i + 1 < len(nums) and nums[i] < nums[i + 1]:
          |            result -= nums[i]
          |        else:
          |            result += nums[i]
          |    return result
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.intToRomanExerciseId -> Seq(
        // canonical parallel lists
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
        // list of tuples + divmod
        """def int_to_roman(n):
          |    table = [
          |        (1000, 'M'), (900, 'CM'), (500, 'D'), (400, 'CD'),
          |        (100, 'C'), (90, 'XC'), (50, 'L'), (40, 'XL'),
          |        (10, 'X'), (9, 'IX'), (5, 'V'), (4, 'IV'), (1, 'I')
          |    ]
          |    result = ''
          |    for value, symbol in table:
          |        count, n = divmod(n, value)
          |        result += symbol * count
          |    return result
          |""".stripMargin,
        // explicit repeat loop with pairs
        """def int_to_roman(n):
          |    pairs = [(1000,'M'),(900,'CM'),(500,'D'),(400,'CD'),(100,'C'),
          |             (90,'XC'),(50,'L'),(40,'XL'),(10,'X'),(9,'IX'),
          |             (5,'V'),(4,'IV'),(1,'I')]
          |    parts = []
          |    for v, s in pairs:
          |        while n >= v:
          |            parts.append(s)
          |            n -= v
          |    return ''.join(parts)
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.normalizeWhitespaceExerciseId -> Seq(
        // canonical split + join
        """def normalize_whitespace(s):
          |    parts = s.split()
          |    return ' '.join(parts)
          |""".stripMargin,
        // one-liner
        """def normalize_whitespace(s):
          |    return ' '.join(s.split())
          |""".stripMargin,
        // explicit token accumulation
        """def normalize_whitespace(s):
          |    tokens = []
          |    for word in s.split():
          |        if word:
          |            tokens.append(word)
          |    return ' '.join(tokens)
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.rotateListExerciseId -> Seq(
        // canonical slice rotation
        """def rotate(xs, k):
          |    if not xs:
          |        return []
          |    k = k % len(xs)
          |    if k == 0:
          |        return list(xs)
          |    return xs[-k:] + xs[:-k]
          |""".stripMargin,
        // build result by index
        """def rotate(xs, k):
          |    if not xs:
          |        return []
          |    n = len(xs)
          |    k = k % n
          |    result = []
          |    for i in range(n):
          |        result.append(xs[(i - k) % n])
          |    return result
          |""".stripMargin,
        // using deque-like rotation with list
        """def rotate(xs, k):
          |    if not xs:
          |        return []
          |    lst = list(xs)
          |    k = k % len(lst)
          |    return lst[len(lst) - k:] + lst[:len(lst) - k]
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.fizzBuzzScriptExerciseId -> Seq(
        // canonical imperative with append
        """ergebnisse = []
          |for n in range(1, 21):
          |    if n % 15 == 0:
          |        ergebnisse.append("FizzBuzz")
          |    elif n % 3 == 0:
          |        ergebnisse.append("Fizz")
          |    elif n % 5 == 0:
          |        ergebnisse.append("Buzz")
          |    else:
          |        ergebnisse.append(n)
          |""".stripMargin,
        // list comprehension with ternary chaining
        """ergebnisse = [
          |    "FizzBuzz" if n % 15 == 0 else
          |    "Fizz" if n % 3 == 0 else
          |    "Buzz" if n % 5 == 0 else n
          |    for n in range(1, 21)
          |]
          |""".stripMargin,
        // helper-function style (also valid for script exercises)
        """def _fb(n):
          |    if n % 15 == 0:
          |        return "FizzBuzz"
          |    if n % 3 == 0:
          |        return "Fizz"
          |    if n % 5 == 0:
          |        return "Buzz"
          |    return n
          |ergebnisse = [_fb(n) for n in range(1, 21)]
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.evenSquaresScriptExerciseId -> Seq(
        // canonical loop with if
        """ergebnisse = []
          |for n in range(1, 21):
          |    if n % 2 == 0:
          |        ergebnisse.append(n * n)
          |""".stripMargin,
        // list comprehension
        """ergebnisse = [n * n for n in range(1, 21) if n % 2 == 0]
          |""".stripMargin,
        // step-2 range (avoids the modulo check entirely)
        """ergebnisse = []
          |for n in range(2, 21, 2):
          |    ergebnisse.append(n * n)
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.fibonacciScriptExerciseId -> Seq(
        // canonical for-loop append
        """fibonacci = [1, 1]
          |for i in range(8):
          |    fibonacci.append(fibonacci[-1] + fibonacci[-2])
          |""".stripMargin,
        // while-loop with len guard (same result, different structure)
        """fibonacci = [1, 1]
          |while len(fibonacci) < 10:
          |    fibonacci.append(fibonacci[-1] + fibonacci[-2])
          |""".stripMargin,
        // tuple unpacking accumulation
        """fibonacci = [1, 1]
          |a, b = fibonacci[0], fibonacci[1]
          |for _ in range(8):
          |    a, b = b, a + b
          |    fibonacci.append(b)
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.primesScriptExerciseId -> Seq(
        // canonical nested loop with flag
        """primzahlen = []
          |for n in range(2, 51):
          |    is_prime = True
          |    for d in range(2, n):
          |        if n % d == 0:
          |            is_prime = False
          |            break
          |    if is_prime:
          |        primzahlen.append(n)
          |""".stripMargin,
        // using any() no explicit flag variable
        """primzahlen = []
          |for n in range(2, 51):
          |    if not any(n % d == 0 for d in range(2, n)):
          |        primzahlen.append(n)
          |""".stripMargin,
        // helper function style
        """def _is_prime(n):
          |    for d in range(2, n):
          |        if n % d == 0:
          |            return False
          |    return True
          |primzahlen = [n for n in range(2, 51) if _is_prime(n)]
          |""".stripMargin
      ),

      BlockFeedbackExerciseRegistry.wordCountScriptExerciseId -> Seq(
        // canonical get with default
        """text = "die Katze sa\u00df auf der Matte die Katze sa\u00df"
          |wortanzahl = {}
          |for w in text.split():
          |    wortanzahl[w] = wortanzahl.get(w, 0) + 1
          |""".stripMargin,
        // setdefault style
        """text = "die Katze sa\u00df auf der Matte die Katze sa\u00df"
          |wortanzahl = {}
          |for w in text.split():
          |    wortanzahl.setdefault(w, 0)
          |    wortanzahl[w] += 1
          |""".stripMargin,
        // conditional check style
        """text = "die Katze sa\u00df auf der Matte die Katze sa\u00df"
          |wortanzahl = {}
          |for wort in text.split():
          |    if wort in wortanzahl:
          |        wortanzahl[wort] = wortanzahl[wort] + 1
          |    else:
          |        wortanzahl[wort] = 1
          |""".stripMargin
      )
    )

    /** Canonical (first) solution used by logicWrongFor / boundaryWrongFor. */
    def canonicalSolution(exerciseId: String): Option[String] =
      baseSolutions.get(exerciseId).map(_.head)

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
      if (idx < 0) {
        // Script exercise: keep only the first meaningful line (e.g. "ergebnisse = []")
        // so the result variable exists but the loop body is missing -> tests fail
        lines.find(_.trim.nonEmpty).getOrElse("pass") + "\n"
      } else {
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

    /** True if the code is a top-level script (no function definitions). */
    private def isScriptCode(code: String): Boolean =
      !code.linesIterator.exists(_.trim.startsWith("def "))

    /** Append lines at the end of a script (no body indentation). */
    private def appendAtEnd(code: String, lines: Seq[String]): String =
      code.stripTrailing() + "\n" + lines.mkString("\n") + "\n"

    private def exceptionTypeFor(base: String): String =
      // Triggers DecisionLayer.EXCEPTION_TYPE via "ZeroDivisionError".
      if isScriptCode(base) then appendAtEnd(base, Seq("_boom = 1 / 0"))
      else injectAfterFirstDefLine(base, Seq("    _boom = 1 / 0"))

    private def nonDeterminismFor(base: String): String =
      // Triggers DecisionLayer.NONDETERMINISM (random keyword present) when tests fail.
      if isScriptCode(base) then appendAtEnd(base, Seq("import random", "_x = random.random()"))
      else injectAfterFirstDefLine(base, Seq("    import random", "    _x = random.random()"))

    private def ioContractFor(base: String): String =
      // Counts as input() usage but will not block because it's unreachable.
      if isScriptCode(base) then appendAtEnd(base, Seq("if False:", "    _x = input()"))
      else injectAfterFirstDefLine(base, Seq("    if False:", "        _x = input()"))

    private def formatOutputFor(base: String): String =
      // Counts as print() and will produce stdout when the function is called.
      if isScriptCode(base) then appendAtEnd(base, Seq("print(\"debug\")", "print(\"debug\")", "print(\"debug\")"))
      else injectAfterFirstDefLine(base, Seq("    print(\"debug\")", "    print(\"debug\")", "    print(\"debug\")"))

    private def decorateWithNoise(code: String, seed: Int): String = {
      val rnd = new scala.util.Random(seed)
      val blankPrefix = (0 until rnd.nextInt(3)).map(_ => "").mkString("\n")
      val commentCount = 1 + rnd.nextInt(3)
      val commentPrefix = (0 until commentCount).map(i => s"# auto-gen note ${seed}_${i}").mkString("\n")
      val extraBlankBetween = if rnd.nextBoolean() then "\n" else "\n\n"
      val core = code.trim + "\n"
      s"$commentPrefix$extraBlankBetween$blankPrefix$core"
    }

    def fastOutcomeFor(
      variantType: VariantType,
      tests: Seq[(String, String, Double, Option[String])]
    ): Option[(PythonRuntimeOutcome, String)] = {
      // Synthesise a passing outcome (Correct variants: decorateWithNoise only, semantics unchanged).
      def synthPass(): PythonRuntimeOutcome =
        PythonRuntimeOutcome(
          tests = tests.map(t => PythonTestResult(t._1, passed = true, t._4.getOrElse("Test should pass"), "OK", None)),
          runStatus = Some(PythonRunStatus.Success),
          normalizedScore = Some(1.0),
          runtimeError = None,
          stdout = None,
          stderr = None
        )
      // Synthesise a failing outcome (all tests fail, optional runtime error).
      def synthFail(
        errMsg: String,
        runErr: Option[String] = None,
        status: PythonRunStatus = PythonRunStatus.Failed,
        stdoutVal: Option[String] = None
      ): PythonRuntimeOutcome =
        PythonRuntimeOutcome(
          tests = tests.map(t => PythonTestResult(t._1, passed = false, t._4.getOrElse("Test should pass"), errMsg, Some(errMsg))),
          runStatus = Some(status),
          normalizedScore = Some(0.0),
          runtimeError = runErr,
          stdout = stdoutVal,
          stderr = None
        )
      // All 9 variant families are fast-pathed no Python subprocess needed:
      //
      //  Correct       -> decorateWithNoise only (no operator mutations), always passes.
      //  CompileError  -> SyntaxError at parse time, obvious outcome.
      //  Incomplete    -> pass body, every assertion fails.
      //  ExceptionType -> ZeroDivisionError injected, runtime blows up.
      //  LogicWrong    -> hand-crafted operator flip, tests fail.
      //  BoundaryWrong -> hand-crafted structural bug, tests fail.
      //  NonDeterminism-> label driven by static rule (random import detected), not runtime.
      //  IoContract    -> label driven by static rule (input() detected), not runtime.
      //  FormatOutput  -> label driven by static rule (print() detected) + stdout present.
      variantType match {
        case VariantType.Correct        => Some((synthPass(), "fast-correct"))
        case VariantType.CompileError   => Some((synthFail("SyntaxError: invalid syntax",  Some("SyntaxError"),          PythonRunStatus.RuntimeError), "fast-compile"))
        case VariantType.Incomplete     => Some((synthFail("AssertionError",               None,                         PythonRunStatus.Failed),       "fast-incomplete"))
        case VariantType.ExceptionType  => Some((synthFail("ZeroDivisionError: division by zero", Some("ZeroDivisionError"), PythonRunStatus.RuntimeError), "fast-exception"))
        case VariantType.LogicWrong     => Some((synthFail("AssertionError: wrong result", None,                         PythonRunStatus.Failed),       "fast-logic"))
        case VariantType.BoundaryWrong  => Some((synthFail("AssertionError: boundary case failed", None,                 PythonRunStatus.Failed),       "fast-boundary"))
        // For the next three, static rules detect the pattern in source; runtime is Failed.
        // NonDeterminism: random import present -> NONDETERMINISM rule fires
        case VariantType.NonDeterminism => Some((synthFail("AssertionError: wrong result", None,                         PythonRunStatus.Failed),       "fast-nondet"))
        // IoContract: unreachable input() present -> IO_CONTRACT rule fires
        case VariantType.IoContract     => Some((synthFail("AssertionError: wrong result", None,                         PythonRunStatus.Failed),       "fast-io"))
        // FormatOutput: print() calls present -> FORMAT_OUTPUT rule fires; simulate stdout
        case VariantType.FormatOutput   => Some((synthFail("AssertionError: wrong result", None,                         PythonRunStatus.Failed,
                                                            stdoutVal = Some("debug\ndebug\ndebug\n")),                                                  "fast-fmt"))
      }
    }

    // applySmallMutations
    //
    // Design goals
    //   - Produce syntactically valid Python that is only subtly wrong.
    //   - Use space-padded operator patterns so we never accidentally match
    //     inside a string literal or identifier (e.g. "!= " in "!= 0" but
    //     not inside "!=" inside a comment or variable name).
    //   - Skip structural/boilerplate lines (def, class, import, blank,
    //     comment, plain for-in-range lines) mutating those tends to
    //     produce syntax errors rather than logic bugs.
    //   - Gather ALL valid (lineIndex, ruleIndex) candidates first, then
    //     pick one via the seeded RNG so the choice is deterministic but
    //     uniformly spread across the code.
    private def applySmallMutations(code: String, seed: Int): (String, String) = {
      val rnd = new scala.util.Random(seed)

      // Space-padded rules each entry is (searchPat, replacement, label).
      val rules: Vector[(String, String, String)] = Vector(
        (" <= ",  " < ",  "relop <= -> <"),
        (" >= ",  " > ",  "relop >= -> >"),
        (" == ",  " != ", "relop == -> !="),
        (" != ",  " == ", "relop != -> =="),
        (" + ",   " - ",  "op + -> -"),
        (" - ",   " + ",  "op - -> +"),
        (" % ",   " // ", "op % -> //"),
        (" // ",  " % ",  "op // -> %")
      )

      def isStructuralLine(line: String): Boolean = {
        val t = line.trim
        t.isEmpty ||
          t.startsWith("#") ||
          t.startsWith("def ") ||
          t.startsWith("class ") ||
          t.startsWith("import ") ||
          t.startsWith("from ") ||
          // bare "for … in range(…):" lines the body is where logic lives
          (t.startsWith("for ") && t.contains(" in range("))
      }

      val lines = code.split("\n", -1).toIndexedSeq

      // All (lineIdx, ruleIdx) pairs where the rule would actually change the line.
      val candidates: Vector[(Int, Int)] =
        (for {
          (line, li) <- lines.zipWithIndex if !isStructuralLine(line)
          (rule, ri) <- rules.zipWithIndex if line.contains(rule._1)
        } yield (li, ri)).toVector

      if (candidates.isEmpty) return (code, "no-op")

      val (li, ri) = candidates(rnd.nextInt(candidates.size))
      val (from, to, note) = rules(ri)
      // Only replace the first occurrence on that line.
      val newLine = lines(li).replace(from, to)
      val mutated = lines.updated(li, newLine).mkString("\n")
      (mutated, s"line${li + 1}: $note")
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
        // Script exercises
        case BlockFeedbackExerciseRegistry.fizzBuzzScriptExerciseId => base.replace("n % 15 == 0", "n % 15 == 1")
        case BlockFeedbackExerciseRegistry.evenSquaresScriptExerciseId => base.replace("n % 2 == 0", "n % 2 != 0")
        case BlockFeedbackExerciseRegistry.fibonacciScriptExerciseId => base.replace("fibonacci[-1] + fibonacci[-2]", "fibonacci[-1] - fibonacci[-2]")
        case BlockFeedbackExerciseRegistry.primesScriptExerciseId => base.replace("n % d == 0", "n % d != 0")
        case BlockFeedbackExerciseRegistry.wordCountScriptExerciseId => base.replace("wortanzahl.get(w, 0) + 1", "1")
        case _ => base
      }

    private def boundaryWrongFor(exerciseId: String, base: String): String =
      exerciseId match {
        // Function exercises
        case BlockFeedbackExerciseRegistry.addTwoNumbersExerciseId =>
          // Type-boundary: truncates floats to int, breaks float-input tests
          base.replace("return a + b", "return int(a) + int(b)")
        case BlockFeedbackExerciseRegistry.maxInListExerciseId =>
          // Empty-list returns wrong sentinel 0 instead of None
          base.replace("if not xs:\n        return None", "if not xs:\n        return 0")
        case BlockFeedbackExerciseRegistry.balancedBracketsExerciseId =>
          // Ignores unmatched openers: always says stack is fine at the end
          base.replace("return len(stack) == 0", "return True")
        case BlockFeedbackExerciseRegistry.twoSumIndicesExerciseId =>
          // Wrong not-found sentinel: (0,0) instead of (-1,-1)
          base.replace("return (-1, -1)", "return (0, 0)")
        case BlockFeedbackExerciseRegistry.palindromeExerciseId =>
          // Loses case-normalisation: uppercase letters don't match lowercase
          base.replace("cleaned.append(ch.lower())", "cleaned.append(ch)")
        case BlockFeedbackExerciseRegistry.gcdExerciseId =>
          // Drops abs() guards: negative inputs produce wrong result
          base.replace("    a = abs(a)\n    b = abs(b)\n", "")
        case BlockFeedbackExerciseRegistry.countVowelsExerciseId =>
          // Case-sensitive: uppercase vowels not counted
          base.replace(".lower()", "")
        case BlockFeedbackExerciseRegistry.runLengthEncodeExerciseId =>
          // Removes empty-string guard: s[0] raises IndexError on ""
          base.replace("    if s == '':\n        return []\n", "")
        case BlockFeedbackExerciseRegistry.mergeSortedExerciseId =>
          // Second tail never appended: elements from b are lost when a runs out first
          base.replace("    out.extend(b[j:])\n", "")
        case BlockFeedbackExerciseRegistry.uniquePreserveOrderExerciseId =>
          // Never adds to seen: every element passes the guard -> duplicates kept
          base.replace("            seen.add(x)\n", "")
        case BlockFeedbackExerciseRegistry.romanToIntExerciseId =>
          // Wrong initial prev: first character is always compared to 1 rather than 0
          base.replace("    prev = 0", "    prev = 1")
        case BlockFeedbackExerciseRegistry.intToRomanExerciseId =>
          // Off-by-one: outer loop exits at n==1 -> final 'I' is never emitted
          base.replace("while n > 0:", "while n > 1:")
        case BlockFeedbackExerciseRegistry.rotateListExerciseId =>
          // Drops empty-list guard: k % len([]) raises ZeroDivisionError
          base.replace("if not xs:\n        return []\n", "")
        case BlockFeedbackExerciseRegistry.normalizeWhitespaceExerciseId =>
          // Keeps leading/trailing spaces, only replaces tab
          """def normalize_whitespace(s):
            |    return s.replace("\t", " ")
            |""".stripMargin
        // Script exercises
        case BlockFeedbackExerciseRegistry.fizzBuzzScriptExerciseId =>
          // Off-by-one: misses n=20
          base.replace("range(1, 21)", "range(1, 20)")
        case BlockFeedbackExerciseRegistry.evenSquaresScriptExerciseId =>
          // Off-by-one: misses n=20
          base.replace("range(1, 21)", "range(1, 20)")
        case BlockFeedbackExerciseRegistry.fibonacciScriptExerciseId =>
          // Wrong seed: [1,2] instead of [1,1] -> entire sequence is shifted
          base.replace("[1, 1]", "[1, 2]")
        case BlockFeedbackExerciseRegistry.primesScriptExerciseId =>
          // Misses 2: range starts at 3
          base.replace("range(2, 51)", "range(3, 51)")
        case BlockFeedbackExerciseRegistry.wordCountScriptExerciseId =>
          // Default=1 instead of 0: first occurrence is counted as 2
          base.replace("wortanzahl.get(w, 0) + 1", "wortanzahl.get(w, 1)")
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

    // allSolutions: all correct solutions for this exercise (Seq.head = canonical).
    // For Correct variants we rotate through them by seed so every alternative
    // solution appears in the training data.  For error variants the list is
    // ignored mutations are applied to variant.python as before.
    def materializeVariant(
      exerciseId: String,
      variant: Variant,
      rep: Int,
      allSolutions: Seq[String] = Seq.empty
    ): (String, String) = {
      val seedBase = MurmurHash3.stringHash(s"$exerciseId|${variant.kind}|$rep")
      val (mutated, mutNote) =
        if variant.kind == VariantType.Correct then {
          // Pick a solution from the pool by seed so we cycle through all alternatives.
          val sols = if allSolutions.nonEmpty then allSolutions else Seq(variant.python)
          val solIdx = math.abs(seedBase) % sols.size
          val noteStr = if sols.size > 1 then s"sol${solIdx + 1}/${sols.size}" else ""
          (sols(solIdx), noteStr)
        } else {
          applySmallMutations(variant.python, seedBase)
        }

      val noisy = decorateWithNoise(mutated, seedBase)
      val note = Seq(variant.note, mutNote).filter(_.nonEmpty).mkString("; ")
      (noisy, note)
    }

    private val desiredLabelOrder: Vector[String] = Vector(
      // prefer specific issues first CORRECT must appear so the model learns when no feedback is needed
      "CORRECT",
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
      // 8 core labels: CORRECT + 7 issue types we can realistically generate
      val activeLabels = desiredLabelOrder.take(8)
      val perLabelTarget = math.max(3, targetMax / math.max(3, activeLabels.size))

      val current = labelCounts.getOrElse(label, 0)
      if (activeLabels.contains(label)) current < perLabelTarget
      else {
        // For labels we rarely hit (or unknown), accept a few.
        current < math.max(2, perLabelTarget / 2)
      }
    }

    def preferredLabelStillMissing(labelCounts: collection.Map[String, Int], targetMax: Int): Boolean = {
      val activeLabels = desiredLabelOrder.take(8)
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
      else AutoSolutions.baseSolutions.keys.toSeq.sorted

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
          val solutionsOpt = AutoSolutions.baseSolutions.get(exerciseId)

          (defnOpt, solutionsOpt) match {
            case (Some(defn), Some(solutions)) =>
              // Canonical (first) solution used by logicWrongFor / boundaryWrongFor.
              val base = solutions.head
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
                val (pythonCode, materializedNote) = AutoSolutions.materializeVariant(exerciseId, family, rep = trial, allSolutions = solutions)

                val (outcome, runnerStatus) =
                  AutoSolutions.fastOutcomeFor(family.kind, tests)
                    .getOrElse(runPython(
                      pythonBin = pythonBin,
                      code = pythonCode,
                      tests = tests,
                      timeoutMs = math.max(1000, defn.config.timeoutMs + 1500)
                    ))

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
                // For the Correct variant, bypass the heuristic routing entirely.
                // We use the runtime score as ground truth so a flaky Python env
                // can't accidentally label a passing solution as an issue type.
                val label =
                  if (family.kind == VariantType.Correct && outcome.normalizedScore.getOrElse(0.0) >= 1.0)
                    "CORRECT"
                  else
                    weakDecision0.primaryIssue.toString
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
                    // Pass the label we accepted overrides the raw heuristic when we have
                    // ground-truth knowledge (e.g. "CORRECT" for confirmed-passing solutions).
                    weakLabelOverride = Some(label).filter(_ != weakDecision0.primaryIssue.toString),
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
