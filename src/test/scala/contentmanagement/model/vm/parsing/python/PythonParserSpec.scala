package contentmanagement.model.vm.parsing.python

import contentmanagement.model.language.AppLanguage.{English, Python as PythonLanguage}
import contentmanagement.model.vm.code.*
import contentmanagement.model.vm.code.controlStructures.*
import contentmanagement.model.vm.code.defining.*
import contentmanagement.model.vm.code.errors.*
import contentmanagement.model.vm.code.others.*
import contentmanagement.model.vm.code.usage.*
import munit.FunSuite

class PythonParserSpec extends FunSuite {

  private val programmingLanguage = PythonLanguage
  private val humanLanguage = English

  private case class ProgramScenario(name: String, code: String, assertion: BeSequence => Unit = _ => ())

  private val baselinePrograms = List(
    "x = 3",
    """x = 3
      |y = x
      |print(y)
      |""".stripMargin,
    "pass",
    """if flag:
      |    pass
      |else:
      |    pass
      |""".stripMargin,
    """while count:
      |    count = count
      |""".stripMargin,
    """for value in items:
      |    process(value)
      |""".stripMargin,
    """for _ in range(3):
      |    print(_)
      |""".stripMargin,
    """def greet(name):
      |    return name
      |""".stripMargin,
    """def compute(value):
      |    if value > 0:
      |        while value:
      |            value = value - 1
      |    return value
      |""".stripMargin,
    """class Person:
      |    def greet(self):
      |        return self
      |""".stripMargin,
    """def greeting(name):
      |    return 'Hello ' + name
      |
      |def run():
      |    return greeting('World')
      |""".stripMargin,
    """try:
      |    risky()
      |except error:
      |    handle(error)
      |finally:
      |    cleanup()
      |""".stripMargin,
    """raise issue()
      |""".stripMargin
  )

  baselinePrograms.zipWithIndex.foreach { case (program, index) =>
    registerScenario(
      ProgramScenario(
        s"round trip python <-> beexpression program ${index + 1}",
        program
      )
    )
  }

  // Keyword-heavy coverage explores:
  // 1. Deeply branched control flow mixing `if`/`elif`/`else`/`return`/`None`.
  // 2. Exception pipelines chaining `try`/`except`/`raise`/`finally` statements.
  // 3. Loop-oriented flows relying on `for`/`is`/`return` keywords alongside unsupported comparisons.
  private val keywordHeavyPrograms = List(
    ProgramScenario(
      "keyword heavy - branching decisions",
      """def choose(flag, fallback):
        |    if flag:
        |        return True
        |    elif fallback:
        |        return False
        |    else:
        |        return None
        |""".stripMargin,
      seq => {
        val function = seq.body.collectFirst { case fn: BeDefineFunction => fn }
          .getOrElse(fail("Expected a function definition"))
        val decision = function.body.body.collectFirst { case branch: BeIfElse => branch }
          .getOrElse(fail("Expected an if/elif/else branch"))
        assert(decision.condition.isInstanceOf[BeUseValueReferencing])
        val elifBranch = decision.elseBody.body.collectFirst { case nested: BeIfElse => nested }
          .getOrElse(fail("Expected elif branch"))
        assert(elifBranch.condition.isInstanceOf[BeUseValueReferencing])
      }
    ),
    ProgramScenario(
      "keyword heavy - exception pipeline",
      """try:
        |    risky()
        |except ValueError:
        |    raise
        |finally:
        |    pass
        |""".stripMargin,
      seq => {
        val tryExcept = seq.body.collectFirst { case block: BeTryExcept => block }
          .getOrElse(fail("Expected a try/except structure"))
        assertEquals(tryExcept.exceptBlocks.size, 1)
        val exceptBlock = tryExcept.exceptBlocks.head
        assert(exceptBlock.condition.exists(_.isInstanceOf[BeUseValueReferencing]))
        assert(tryExcept.finallyBody.exists(_.body.contains(BeExpression.pass)))
      }
    ),
    ProgramScenario(
      "keyword heavy - loop with identity checks",
      """def keyword_loop(data):
        |    for value in data:
        |        if value is None:
        |            return None
        |    return True
        |""".stripMargin,
      seq => {
        val function = seq.body.collectFirst { case fn: BeDefineFunction => fn }
          .getOrElse(fail("Expected a function definition"))
        val loop = function.body.body.collectFirst { case each: BeForEach => each }
          .getOrElse(fail("Expected a for-each loop"))
        val nestedIf = loop.body.body.collectFirst { case branch: BeIfElse => branch }
          .getOrElse(fail("Expected a nested if"))
        assert(nestedIf.condition.isInstanceOf[BeExpressionUnsupported])
      }
    )
  )

  keywordHeavyPrograms.foreach(registerScenario)

  // Chained comparisons & boolean expressions vary by:
  // 1. Long mixed comparison/boolean chain without parentheses to stress precedence.
  // 2. Grouped boolean logic relying on parentheses to prioritize evaluation order.
  // 3. Negation-heavy expressions combining chained equality checks and grouped clauses.
  private val chainedComparisonPrograms = List(
    ProgramScenario(
      "chained comparisons - mix of comparisons and keywords",
      """def classify(value, limit, alarm):
        |    if 0 < value < limit and not alarm:
        |        return 'ok'
        |    return 'fail'
        |""".stripMargin,
      seq => {
        val function = seq.body.collectFirst { case fn: BeDefineFunction => fn }
          .getOrElse(fail("Expected a function"))
        val branch = function.body.body.collectFirst { case cond: BeIfElse => cond }
          .getOrElse(fail("Expected an if branch"))
        val unsupported = branch.condition match {
          case expr: BeExpressionUnsupported => expr
          case other => fail(s"Expected unsupported expression, but got ${other.getClass.getSimpleName}")
        }
        assertEquals(unsupported.originalSource, "0 < value < limit and not alarm")
      }
    ),
    ProgramScenario(
      "chained comparisons - grouped boolean loop",
      """def wait(left, right, center, override_flag):
        |    while (left and right) or (center and not override_flag):
        |        return None
        |    return True
        |""".stripMargin,
      seq => {
        val function = seq.body.collectFirst { case fn: BeDefineFunction => fn }
          .getOrElse(fail("Expected a function"))
        val loop = function.body.body.collectFirst { case w: BeWhile => w }
          .getOrElse(fail("Expected while loop"))
        val unsupported = loop.condition match {
          case expr: BeExpressionUnsupported => expr
          case other => fail(s"Expected unsupported expression, but got ${other.getClass.getSimpleName}")
        }
        assertEquals(unsupported.originalSource, "(left and right) or (center and not override_flag)")
      }
    ),
    ProgramScenario(
      "chained comparisons - negations and equality",
      """if not (a or b and c) or (d == e == f):
        |    pass
        |""".stripMargin,
      seq => {
        val branch = seq.body.collectFirst { case cond: BeIfElse => cond }
          .getOrElse(fail("Expected an if statement"))
        val unsupported = branch.condition match {
          case expr: BeExpressionUnsupported => expr
          case other => fail(s"Expected unsupported expression, but got ${other.getClass.getSimpleName}")
        }
        assertEquals(unsupported.originalSource, "not (a or b and c) or (d == e == f)")
      }
    )
  )

  chainedComparisonPrograms.foreach(registerScenario)

  // Assignment expression coverage explores:
  // 1. Walrus usage inside while conditions controlling loop execution.
  // 2. Walrus usage within if conditions to capture intermediate values.
  // 3. Walrus usage nested inside standard assignments.
  private val assignmentExpressionPrograms = List(
    ProgramScenario(
      "assignment expression - loop control",
      """def accumulate(stream):
        |    total = 0
        |    while (item := stream()):
        |        total = item
        |    return total
        |""".stripMargin,
      seq => {
        val function = seq.body.collectFirst { case fn: BeDefineFunction => fn }
          .getOrElse(fail("Expected a function"))
        val loop = function.body.body.collectFirst { case w: BeWhile => w }
          .getOrElse(fail("Expected while loop"))
        val unsupported = loop.condition match {
          case expr: BeExpressionUnsupported => expr
          case other => fail(s"Expected unsupported expression, but got ${other.getClass.getSimpleName}")
        }
        assertEquals(unsupported.originalSource, "(item := stream())")
      }
    ),
    ProgramScenario(
      "assignment expression - conditional capture",
      """def find(pattern, text):
        |    if match := pattern(text):
        |        return match
        |    return None
        |""".stripMargin,
      seq => {
        val function = seq.body.collectFirst { case fn: BeDefineFunction => fn }
          .getOrElse(fail("Expected a function"))
        val branch = function.body.body.collectFirst { case cond: BeIfElse => cond }
          .getOrElse(fail("Expected an if statement"))
        val unsupported = branch.condition match {
          case expr: BeExpressionUnsupported => expr
          case other => fail(s"Expected unsupported expression, but got ${other.getClass.getSimpleName}")
        }
        assertEquals(unsupported.originalSource, "match := pattern(text)")
      }
    ),
    ProgramScenario(
      "assignment expression - nested inside assignment",
      """result = (value := compute())
        |""".stripMargin,
      seq => {
        val assign = seq.body.collectFirst { case a: BeAssignVariable => a }
          .getOrElse(fail("Expected a top-level assignment"))
        val unsupported = assign.value match {
          case expr: BeExpressionUnsupported => expr
          case other => fail(s"Expected unsupported expression, but got ${other.getClass.getSimpleName}")
        }
        assertEquals(unsupported.originalSource, "(value := compute())")
      }
    )
  )

  assignmentExpressionPrograms.foreach(registerScenario)

  // Pattern-matching scenarios differ by:
  // 1. Literal cases with wildcard fallbacks.
  // 2. Structured destructuring patterns.
  // 3. Guarded cases with conditionals.
  private val patternMatchingPrograms = List(
    ProgramScenario(
      "pattern matching - literals and wildcard",
      """match status:
        |    case 'start':
        |        handle_start()
        |    case _:
        |        pass
        |""".stripMargin,
      seq => {
        val unsupportedLines = seq.body.collect { case unsupported: BeExpressionUnsupported => unsupported.originalSource }
        assert(unsupportedLines.exists(_.startsWith("match status")))
        assert(unsupportedLines.exists(_.startsWith("    case 'start':")))
        assert(unsupportedLines.exists(_.startsWith("    case _:")))
      }
    ),
    ProgramScenario(
      "pattern matching - mapping destructuring",
      """def interpret(data):
        |    match data:
        |        case {'kind': 'point', 'value': value}:
        |            return value
        |        case _:
        |            raise
        |""".stripMargin,
      seq => {
        val function = seq.body.collectFirst { case fn: BeDefineFunction => fn }
          .getOrElse(fail("Expected a function"))
        val unsupportedLines = function.body.body.collect { case unsupported: BeExpressionUnsupported => unsupported.originalSource }
        assert(unsupportedLines.exists(_.startsWith("    match data")))
        assert(unsupportedLines.exists(_.contains("{'kind': 'point', 'value': value}")))
        assert(function.body.body.exists(_.isInstanceOf[BeReturn]))
        assert(function.body.body.exists(_.isInstanceOf[BeExpressionThrowError]))
      }
    ),
    ProgramScenario(
      "pattern matching - guarded cases",
      """match command:
        |    case action if action == 'quit':
        |        return
        |    case _:
        |        pass
        |""".stripMargin,
      seq => {
        val unsupportedLines = seq.body.collect { case unsupported: BeExpressionUnsupported => unsupported.originalSource }
        assert(unsupportedLines.exists(_.contains("case action if action == 'quit':")))
      }
    )
  )

  patternMatchingPrograms.foreach(registerScenario)

  // Edge-case whitespace coverage touches:
  // 1. Leading/trailing blank lines with trailing spaces inside a function body.
  // 2. Windows-style CRLF newlines.
  // 3. Interleaved blank lines with indentation preserved inside control flow.
  private val edgeWhitespacePrograms = List(
    ProgramScenario(
      "edge whitespace - trailing spaces and blanks",
      Seq(
        "",
        "def spaced():",
        "    ",
        "    value = 1    ",
        "    ",
        "    return value    ",
        "    "
      ).mkString("\n"),
      seq => {
        val function = seq.body.collectFirst { case fn: BeDefineFunction => fn }
          .getOrElse(fail("Expected a function"))
        val generated = function.body.getInLanguage(programmingLanguage, humanLanguage)
        assert(generated.contains("value = 1"))
        assert(generated.contains("return value"))
      }
    ),
    ProgramScenario(
      "edge whitespace - windows newlines",
      Seq(
        "def windows():",
        "    value = 1",
        "    return value",
        ""
      ).mkString("\r\n"),
      seq => {
        val function = seq.body.collectFirst { case fn: BeDefineFunction => fn }
          .getOrElse(fail("Expected a function"))
        assertEquals(function.parameters.length, 0)
      }
    ),
    ProgramScenario(
      "edge whitespace - spaced control flow",
      Seq(
        "def spaced_control(flag):",
        "",
        "    if flag:",
        "        ",
        "        pass",
        "        ",
        "    return flag"
      ).mkString("\n"),
      seq => {
        val function = seq.body.collectFirst { case fn: BeDefineFunction => fn }
          .getOrElse(fail("Expected a function"))
        val branch = function.body.body.collectFirst { case cond: BeIfElse => cond }
          .getOrElse(fail("Expected an if"))
        assert(branch.body.body.contains(BeExpression.pass))
        assert(function.body.body.lastOption.exists(_.isInstanceOf[BeReturn]))
      }
    )
  )

  edgeWhitespacePrograms.foreach(registerScenario)

  private def registerScenario(scenario: ProgramScenario): Unit =
    addRoundTripTest(scenario.name, scenario.code)(scenario.assertion)

  private def addRoundTripTest(name: String, program: String)(assertions: BeSequence => Unit = _ => ()): Unit = {
    test(name) {
      val normalizedSource = normalize(program)
      val parsed = PythonParser.parsePython(program)
      assert(!parsed.isInstanceOf[BeExpressionUnparsable], s"Parser failed for $name")

      val sequence = parsed match {
        case seq: BeSequence => seq
        case other => fail(s"Expected BeSequence, but received ${other.getClass.getSimpleName}")
      }

      assertions(sequence)

      val generated = sequence.getInLanguage(programmingLanguage, humanLanguage)
      val normalizedGenerated = normalize(generated)
      assertEquals(normalizedGenerated, normalizedSource, s"Round trip mismatch for $name")

      val reparsed = PythonParser.parsePython(generated)
      assertEquals(reparsed, sequence)
    }
  }

  private def normalize(code: String): String = {
    val unifiedNewlines = code.replace("\r\n", "\n").replace('\r', '\n')
    val lines = unifiedNewlines.split("\n", -1).toList
    val withoutLeading = lines.dropWhile(_.trim.isEmpty)
    val withoutTrailing = withoutLeading.reverse.dropWhile(_.trim.isEmpty).reverse
    val cleaned = withoutTrailing.map(_.replaceAll("\\s+$", ""))
    cleaned.mkString("\n")
  }
}
