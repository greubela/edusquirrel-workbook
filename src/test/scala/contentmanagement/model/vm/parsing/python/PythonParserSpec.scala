package contentmanagement.model.vm.parsing.python

import contentmanagement.model.language.AppLanguage.{English, JavaScript, Python}
import contentmanagement.model.language.LanguageMap
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.controlStructures.{BeIfElse, BeSequence, BeWhile}
import contentmanagement.model.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import contentmanagement.model.vm.code.usage.BeAssignVariable
import contentmanagement.model.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import contentmanagement.model.vm.code.others.BeReturn
import contentmanagement.model.vm.parsing.python.PythonParser.KnownStructure
import contentmanagement.model.vm.types.BeDataType
import interactionPlugins.blockEnvironment.programming.BeProgram
import munit.FunSuite

class PythonParserSpec extends FunSuite {

  private val normalizer = new PythonNormalizer()

  private case class RoundTripCase(
      name: String,
      python: String,
      expectedNormalized: Option[String] = None,
      assertions: PythonParser.CodeParsingResult => Unit = _ => ()
  )

  private val roundTripCases = List(
    RoundTripCase(
      name = "main app example",
      python =
        """x = 3
          |def greeting(name: str) -> str:
          |    return 'Hello ' + name
          |
          |def increase(nr):
          |   nr = nr + 3
          |   x = 5
          |
          |greeting('hi')
          |increase(5)
          |""".stripMargin,
      assertions = result =>
        assert(
          result.definedFunctions.exists(_.functionTypeInfo.funcType.isInstanceOf[BeDefineFunction.Operator]),
          "expected operator functions to be recorded"
        )
    ),
    RoundTripCase(
      name = "string literals with different quotes",
      python =
        """message = \"Hello\"
          |reply = 'Hi there'
          |combined = message + \" & \\" + reply
          |""".stripMargin
    ),
    RoundTripCase(
      name = "single line comments",
      python =
        """# module header
          |value = 10
          |# keep the result accessible
          |value
          |""".stripMargin,
      assertions = result => {
        val sequence = result.codeExpression.asInstanceOf[BeSequence]
        val comments = sequence.body.collect { case comment: BeSingleLineComment => comment }
        assertEquals(comments.length, 2)
      }
    ),
    RoundTripCase(
      name = "inline comments after statements",
      python =
        """value = 10  # initial load
          |value = value + 1  # increment
          |""".stripMargin,
      expectedNormalized = Some(
        """value = 10
          |# initial load
          |value = value + 1
          |# increment""".stripMargin.trim
      ),
      assertions = result => {
        val sequence = result.codeExpression.asInstanceOf[BeSequence]
        val comments = sequence.body.collect { case comment: BeSingleLineComment => comment }
        assertEquals(comments.length, 2)
      }
    ),
    RoundTripCase(
      name = "while loop control flow",
      python =
        """count = 0
          |while count < 3:
          |    count = count + 1
          |count
          |""".stripMargin,
      assertions = result => {
        val sequence = result.codeExpression.asInstanceOf[BeSequence]
        val whileExpressions = sequence.body.collect { case loop: BeWhile => loop }
        assertEquals(whileExpressions.length, 1)
      }
    ),
    RoundTripCase(
      name = "if else branching",
      python =
        """value = 5
          |if value > 3:
          |    result = \"big\"
          |else:
          |    result = \"small\"
          |result
          |""".stripMargin,
      assertions = result => {
        val sequence = result.codeExpression.asInstanceOf[BeSequence]
        val conditional = sequence.body.collect { case branch: BeIfElse => branch }
        assertEquals(conditional.length, 1)
      }
    ),
    RoundTripCase(
      name = "if without else branch",
      python =
        """flag = True
          |if flag:
          |    value = 1
          |result = value
          |""".stripMargin,
      expectedNormalized = Some(
        """flag = True
          |if flag:
          |    value = 1
          |result = value""".stripMargin.trim
      ),
      assertions = result => {
        val sequence = result.codeExpression.asInstanceOf[BeSequence]
        val conditional = sequence.body.collect { case branch: BeIfElse => branch }
        assertEquals(conditional.length, 1)
        assertEquals(conditional.head.elseBody.body.length, 0)
        val rendered = normalizer.normalizePython(conditional.head.getInLanguage(Python, English))
        assert(!rendered.contains("else:"))
      }
    ),
    RoundTripCase(
      name = "if-elif-else normalization and augmented assignment",
      python =
        """score = 0
          |if score > 10:
          |    result = \"high\"
          |elif score == 10:
          |    score += 1
          |elif score == 0:
          |    result = \"empty\"
          |else:
          |    result = \"low\"
          |""".stripMargin,
      expectedNormalized = Some(
        """score = 0
          |if score > 10:
          |    result = \"high\"
          |else:
          |    if score == 10:
          |        score = score + 1
          |    else:
          |        if score == 0:
          |            result = \"empty\"
          |        else:
          |            result = \"low\"
          |""".stripMargin.trim
      )
    ),
    RoundTripCase(
      name = "operator precedence normalization",
      python =
        """a = 1 + 2 * 3
          |b = 1 * (2 + 3)
          |c = (1*2) + 3
          |x = 0
          |x += 1
          |""".stripMargin,
      expectedNormalized = Some(
        """a = 1 + 2 * 3
          |b = 1 * (2 + 3)
          |c = 1 * 2 + 3
          |x = 0
          |x = x + 1
          |""".stripMargin.trim
      )
    ),
    RoundTripCase(
      name = "combined control structures",
      python =
        """total = 0
          |def accumulate(limit: float) -> float:
          |    steps = 0
          |    while steps < limit:
          |        # increment step count
          |        steps = steps + 1
          |        total = total + steps
          |    return total
          |result = accumulate(3)
          |""".stripMargin,
      assertions = result => {
        val maybeFunction = result.definedFunctions.find(_.functionTypeInfo.displayName.getInLanguage(English) == "accumulate")
        assert(maybeFunction.nonEmpty, "expected accumulate function to be defined")
        val functionBody = maybeFunction.get.body match {
          case seq: BeSequence => seq.body
          case other => fail(s"Expected sequence body, found ${other.getClass.getSimpleName}")
        }
        val whileExpressions = functionBody.collect { case loop: BeWhile => loop }
        assertEquals(whileExpressions.length, 1)
        val whileBodyComments = whileExpressions.head.body.body.collect { case comment: BeSingleLineComment => comment }
        assertEquals(whileBodyComments.length, 1)
      }
    )
  )

  roundTripCases.foreach { testCase =>
    test(s"round trip - ${testCase.name}") {
      val normalizedInput = normalizer.normalizePython(testCase.python)
      val parsingResult = PythonParser.parsePythonWithDetails(testCase.python)
      val expression = parsingResult.codeExpression
      assert(
        !expression.isInstanceOf[BeExpressionUnparsable],
        s"parsing produced an unparsable expression for ${testCase.name}"
      )

      val generated = expression.getInLanguage(Python, English)
      val normalizedGenerated = normalizer.normalizePython(generated)
      assertEquals(normalizedGenerated, normalizedInput)

      val reparsed = PythonParser.parsePythonWithDetails(normalizedGenerated)
      val regenerated = reparsed.codeExpression.getInLanguage(Python, English)
      val normalizedRegenerated = normalizer.normalizePython(regenerated)
      assertEquals(normalizedRegenerated, normalizedGenerated)

      testCase.expectedNormalized.foreach { expected =>
        assertEquals(
          normalizer.normalizeLineEndings(normalizedInput),
          normalizer.normalizeLineEndings(expected.stripMargin)
        )
      }
      testCase.assertions(parsingResult)
    }
  }

  test("boolean operators are parsed into expressions") {
    val python =
      """flag1 = True
        |flag2 = False
        |flag3 = True
        |result = flag1 and not flag2 or flag3
        |""".stripMargin

    val result = PythonParser.parsePythonWithDetails(python)
    val sequence = result.codeExpression.asInstanceOf[BeSequence]
    val assignments = sequence.body.collect { case assign: BeAssignVariable => assign }
    assertEquals(assignments.length, 4)

    val unsupported = sequence.body.collect { case unsupported: BeExpressionUnsupported => unsupported }
    assertEquals(unsupported.length, 0)

    val booleanExpression = assignments.last.value
    val normalizedProgram = normalizer.normalizePython(result.codeExpression.getInLanguage(Python, English))
    assertEquals(normalizedProgram, normalizer.normalizePython(python))
    assertEquals(booleanExpression.canEvaluateTo, BeDataType.Boolean)
  }

  test("unary operators are parsed as functions") {
    val python =
      """count = 5
        |negative = -count
        |positive = +count
        |bitwise = ~count
        |flag = not False
        |""".stripMargin

    val result = PythonParser.parsePythonWithDetails(python)
    val sequence = result.codeExpression.asInstanceOf[BeSequence]
    val assignments = sequence.body.collect { case assign: BeAssignVariable => assign }
    assertEquals(assignments.length, 5)

    val unsupported = sequence.body.collect { case unsupported: BeExpressionUnsupported => unsupported }
    assertEquals(unsupported.length, 0)

    val normalizedProgram = normalizer.normalizePython(result.codeExpression.getInLanguage(Python, English))
    assertEquals(normalizedProgram, normalizer.normalizePython(python))
    assertEquals(assignments(1).value.canEvaluateTo, BeDataType.Numeric)
    assertEquals(assignments(2).value.canEvaluateTo, BeDataType.Numeric)
    assertEquals(assignments(3).value.canEvaluateTo, BeDataType.Numeric)
    assertEquals(assignments(4).value.canEvaluateTo, BeDataType.Boolean)
  }
  test("round trip from mini program expression") {
    val sourceExpression = BeProgram.miniProgramExpression()
    val generated = sourceExpression.getInLanguage(Python, English)

    val parsed = PythonParser.parsePythonWithDetails(generated)
    parsed.codeExpression match {
      case seq: BeSequence => assertEquals(seq.body.length, 2)
      case other => fail(s"Expected a sequence after parsing mini program, but received ${other.getClass.getSimpleName}")
    }

    val regenerated = parsed.codeExpression.getInLanguage(Python, English)
    assertEquals(normalizer.normalizePython(regenerated), normalizer.normalizePython(generated))
  }

  test("distinguish unsupported and unparsable inputs") {
    val unsupportedSource =
      """for i in range(3):
        |    pass
        |""".stripMargin
    val unsupportedResult = PythonParser.parsePythonWithDetails(unsupportedSource)
    val unsupportedExpressions = unsupportedResult.codeExpression.asInstanceOf[BeSequence].body
    assert(unsupportedExpressions.exists(_.isInstanceOf[BeExpressionUnsupported]))

    val supportedIfSource =
      """if True:
        |    pass
        |""".stripMargin
    val supportedIfResult = PythonParser.parsePythonWithDetails(supportedIfSource)
    val supportedIfExpressions = supportedIfResult.codeExpression.asInstanceOf[BeSequence].body
    val parsedIf = supportedIfExpressions.collect { case branch: BeIfElse => branch }
    assertEquals(parsedIf.length, 1)
    assertEquals(parsedIf.head.elseBody.body.length, 0)

    val unparsableSource =
      """while True
        |    pass
        |""".stripMargin
    val unparsableResult = PythonParser.parsePythonWithDetails(unparsableSource)
    val unparsableExpressions = unparsableResult.codeExpression.asInstanceOf[BeSequence].body
    assert(unparsableExpressions.exists(_.isInstanceOf[BeExpressionUnparsable]))
  }

  test("parse python segment with while expression and convert to javascript") {
    val segment =
      """    while remaining > 0:
        |        remaining = remaining - 1
        |    return remaining
        |""".stripMargin
    val parsingResult = PythonParser.parsePythonWithDetails(segment)
    val sequence = parsingResult.codeExpression.asInstanceOf[BeSequence]
    val body = sequence.body
    val whileExpressions = body.collect { case loop: BeWhile => loop }
    assertEquals(whileExpressions.length, 1)
    val whileRendered = whileExpressions.head.getInLanguage(JavaScript, English)
    val expectedWhile =
      """while (remaining > 0) {
        |    remaining = remaining - 1;
        |}""".stripMargin
    assertEquals(
      normalizer.normalizeLineEndings(whileRendered),
      normalizer.normalizeLineEndings(expectedWhile)
    )
    val returnExpressions = body.collect { case ret: BeReturn => ret }
    assertEquals(returnExpressions.length, 1)
    assertEquals(returnExpressions.head.getInLanguage(JavaScript, English), "return remaining;")
  }

  test("render parsed function with while loop to javascript") {
    val pythonSource =
      """def sum_until(limit: int) -> int:
        |    total = 0
        |    while limit > 0:
        |        total = total + limit
        |        limit = limit - 1
        |    return total
        |""".stripMargin
    val parsingResult = PythonParser.parsePythonWithDetails(pythonSource)
    val maybeFunction = parsingResult.definedFunctions.find { function =>
      function.functionTypeInfo.displayName.getInLanguage(English) == "sum_until"
    }
    assert(maybeFunction.nonEmpty, "expected to find sum_until function definition")
    val renderedJavaScript = maybeFunction.get.getInLanguage(JavaScript, English)
    val expectedJavaScript =
      """function sum_until(limit) {
        |    total = 0;
        |    while (limit > 0) {
        |        total = total + limit;
        |        limit = limit - 1;
        |    }
        |    return total;
        |}""".stripMargin
    assertEquals(
      normalizer.normalizeLineEndings(renderedJavaScript),
      normalizer.normalizeLineEndings(expectedJavaScript)
    )
  }

  test("merge initial known structures with parsed definitions") {
    val leftParam = BeDefineVariable(LanguageMap.universalMap("left"), BeDataType.AnyType)
    val rightParam = BeDefineVariable(LanguageMap.universalMap("right"), BeDataType.AnyType)
    val resultParam = BeDefineVariable(LanguageMap.universalMap("result"), BeDataType.AnyType)
    val greaterOperator =
      BeDefineFunction(
        inputs = List(leftParam, rightParam),
        outputs = Some(resultParam),
        body = BeExpression.pass,
        functionTypeInfo = BeDefineFunction.operatorInfo(">", 1)
      )

    val parsingResult = PythonParser.parsePythonWithDetails(
      """value = left > right""".stripMargin,
      initialKnownStructures = Seq(KnownStructure.Operator(">", greaterOperator))
    )

    val known = parsingResult.currentlyKnownStructures
    assertEquals(known.operators.get(">" -> 2), Some(greaterOperator))
    assertEquals(known.functions.get(">"), Some(greaterOperator))
    assert(known.variables.contains("value"))
    assert(parsingResult.definedFunctions.contains(greaterOperator))
  }

  test("parser initializes with default builtin function definitions") {
    val result = PythonParser.parsePythonWithDetails("")
    val maybeStr = result.currentlyKnownStructures.functions.get("str")
    val defaultStr = DefaultDefinitions.builtinFunctionsByName("str")
    assert(maybeStr.isDefined, "expected str builtin to be available")
    assert(maybeStr.get eq defaultStr, "expected str builtin to reuse default definition")
  }

  test("> operator reuses default definition for repeated comparisons") {
    val source =
      """first = 5 > 3
        |second = 10 > 2
        |""".stripMargin
    val result = PythonParser.parsePythonWithDetails(source)
    val defaultGreater = DefaultDefinitions.operatorDefinitionsBySymbolAndArity(">" -> 2)
    val currentGreater = result.currentlyKnownStructures.operators.get(">" -> 2)
    assert(currentGreater.contains(defaultGreater), "expected parser to reuse default > operator definition")

    val greaterFunctions = result.definedFunctions.filter { func =>
      func.functionTypeInfo.displayName.getInLanguage(English) == ">"
    }
    assertEquals(greaterFunctions.length, 1, "expected only the default > operator to be present")
    assert(greaterFunctions.head eq defaultGreater, "expected greater operator definition to match default instance")
  }

}
