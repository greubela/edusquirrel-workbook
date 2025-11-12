package contentmanagement.model.vm.parsing.python

import contentmanagement.model.language.AppLanguage.{English, JavaScript, Python}
import contentmanagement.model.language.LanguageMap
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.controlStructures.{BeIfElse, BeSequence, BeWhile}
import contentmanagement.model.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import contentmanagement.model.vm.code.usage.BeAssignVariable
import contentmanagement.model.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import contentmanagement.model.vm.code.others.BeReturn
import contentmanagement.model.vm.parsing.python.PythonParser.KnownStructure
import contentmanagement.model.vm.types.BeDataType
import interactionPlugins.blockEnvironment.programming.BeProgram
import munit.FunSuite
import scala.collection.mutable

class PythonParserSpec extends FunSuite {

  private val normalizer = new PythonNormalizer()

  private case class TypeHintKey(kind: String, identifier: String)

  private val FunctionHeaderPattern =
    """^(\s*)def\s+([A-Za-z_][A-Za-z0-9_]*)\s*\((.*)\)\s*(?:->\s*([^:]+))?:$""".r
  private val AssignmentWithTypePattern =
    """^(\s*)([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([A-Za-z0-9_\.]+)\s*=(?!=)\s*(.+)$""".r
  private val NumericTypeNames = Set("int", "float", "double", "number")

  private def normalizeTypeName(raw: String): String = {
    val lowered = raw.trim.toLowerCase
    if (NumericTypeNames.contains(lowered)) "numeric" else raw.trim
  }

  private def canonicalizeNumericTypeHints(code: String): String = {
    val numericPattern = """(?i)(:\s*)(int|float|double|number)\b""".r
    val returnPattern = """(?i)(->\s*)(int|float|double|number)\b""".r
    val step1 = numericPattern.replaceAllIn(code, m => s"${m.group(1)}numeric")
    returnPattern.replaceAllIn(step1, m => s"${m.group(1)}numeric")
  }

  private def stripDefaultValue(typeHint: String): String = {
    val eqIndex = typeHint.indexOf('=')
    if (eqIndex >= 0) typeHint.substring(0, eqIndex).trim else typeHint.trim
  }

  private def collectTypeHints(code: String): Map[TypeHintKey, String] = {
    val hints = mutable.LinkedHashMap.empty[TypeHintKey, String]
    val lines = code.split("\n", -1)
    lines.foreach { line =>
      line match {
        case FunctionHeaderPattern(_, name, params, returnType) =>
          Option(returnType).map(_.trim).filter(_.nonEmpty).foreach { raw =>
            hints.update(TypeHintKey("return", name), stripDefaultValue(raw))
          }
          val paramEntries =
            if (params.trim.isEmpty) Nil else params.split(",").toList
          paramEntries.foreach { entry =>
            val cleaned = entry.trim
            if (cleaned.nonEmpty && cleaned.contains(":")) {
              val parts = cleaned.split(":", 2)
              if (parts.length == 2) {
                val paramName = parts.head.trim
                val typePart = stripDefaultValue(parts(1))
                if (paramName.nonEmpty && typePart.nonEmpty) {
                  hints.update(TypeHintKey(s"param:$name", paramName), typePart)
                }
              }
            }
          }
        case AssignmentWithTypePattern(_, identifier, typeHint, _) =>
          hints.update(TypeHintKey("assign", identifier), typeHint.trim)
        case _ =>
      }
    }
    hints.toMap
  }

  private def stripTypeHints(code: String): String = {
    val lines = code.split("\n", -1).toVector
    val processed = lines.map {
      case FunctionHeaderPattern(indent, name, params, _) =>
        val strippedParams = params
          .split(",")
          .toList
          .map(_.trim)
          .filter(_.nonEmpty)
          .map { param =>
            val colonIndex = param.indexOf(':')
            if (colonIndex >= 0) {
              val before = param.substring(0, colonIndex).trim
              val after = param.substring(colonIndex + 1).trim
              val defaultIdx = after.indexOf('=')
              if (defaultIdx >= 0) {
                val defaultPart = after.substring(defaultIdx).trim
                if (defaultPart.nonEmpty) s"$before $defaultPart" else before
              } else {
                val eqIdx = before.indexOf('=')
                if (eqIdx >= 0) {
                  val nameOnly = before.substring(0, eqIdx).trim
                  val defaultPart = before.substring(eqIdx).trim
                  if (defaultPart.nonEmpty) s"$nameOnly $defaultPart" else nameOnly
                } else before
              }
            } else param
          }
        val paramsJoined = strippedParams.filter(_.nonEmpty).mkString(", ")
        s"${indent}def $name($paramsJoined):"
      case AssignmentWithTypePattern(indent, identifier, _, value) =>
        s"$indent$identifier = $value"
      case other => other
    }
    processed.mkString("\n")
  }

  private def assertPythonEquivalentAllowingAdditionalTypeHints(
      original: String,
      regenerated: String
  ): Unit = {
    val normalizedOriginal = normalizer.normalizePython(original)
    val normalizedRegenerated = normalizer.normalizePython(regenerated)

    val canonicalOriginal = canonicalizeNumericTypeHints(normalizedOriginal)
    val canonicalRegenerated = canonicalizeNumericTypeHints(normalizedRegenerated)

    val originalHints = collectTypeHints(canonicalOriginal)
    val regeneratedHints = collectTypeHints(canonicalRegenerated)

    originalHints.foreach { case (key, hint) =>
      val regeneratedHint = regeneratedHints.getOrElse(
        key,
        fail(s"Missing type hint for ${key.kind} ${key.identifier}")
      )
      assertEquals(normalizeTypeName(regeneratedHint), normalizeTypeName(hint))
    }

    if (canonicalOriginal != canonicalRegenerated) {
      val strippedOriginal = stripTypeHints(canonicalOriginal)
      val strippedRegenerated = stripTypeHints(canonicalRegenerated)
      assertEquals(strippedRegenerated, strippedOriginal)
    }
  }

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

  test("typed circle_area function preserves annotations and types") {
    val python =
      """def circle_area(radius: double) -> double:
        |    area: double = 3.14 * radius * radius
        |testWithNr: double = 3.0
        |result = circle_area(testWithNr)
        |""".stripMargin

    val parsingResult = PythonParser.parsePythonWithDetails(python)
    val regenerated = parsingResult.codeExpression.getInLanguage(Python, English)
    assertPythonEquivalentAllowingAdditionalTypeHints(python, regenerated)

    val circleFunction = parsingResult.definedFunctions
      .find(_.functionTypeInfo.displayName.getInLanguage(English) == "circle_area")
      .getOrElse(fail("expected to find circle_area function"))

    assertEquals(circleFunction.inputs.length, 1)
    val radiusParam = circleFunction.inputs.head
    assertEquals(radiusParam.name.getInLanguage(English), "radius")
    assertEquals(radiusParam.variableType, BeDataType.Numeric)

    val returnVariable = circleFunction.outputs.getOrElse(fail("expected return variable"))
    assertEquals(returnVariable.variableType, BeDataType.Numeric)

    val bodySequence = circleFunction.body match {
      case seq: BeSequence => seq
      case other => fail(s"expected function body to be a sequence but was ${other.getClass.getSimpleName}")
    }
    val areaAssignment = bodySequence.body.collectFirst {
      case assign: BeAssignVariable if assign.target.name.getInLanguage(English) == "area" => assign
    }.getOrElse(fail("expected assignment to area"))
    assertEquals(areaAssignment.target.variableType, BeDataType.Numeric)
    assertEquals(areaAssignment.value.canEvaluateTo, BeDataType.Numeric)

    val variablesByName = parsingResult.definedVariables.map { variable =>
      variable.name.getInLanguage(English) -> variable
    }.toMap
    val testWithNrVar = variablesByName.getOrElse("testWithNr", fail("expected testWithNr variable"))
    assertEquals(testWithNrVar.variableType, BeDataType.Numeric)
    val resultVar = variablesByName.getOrElse("result", fail("expected result variable"))
    assertEquals(resultVar.variableType, BeDataType.Numeric)

    val topAssignments = parsingResult.codeExpression.body.collect { case assign: BeAssignVariable => assign }
    val testAssignment = topAssignments
      .find(_.target.name.getInLanguage(English) == "testWithNr")
      .getOrElse(fail("expected assignment to testWithNr"))
    assertEquals(testAssignment.value.canEvaluateTo, BeDataType.Numeric)

    val resultAssignment = topAssignments
      .find(_.target.name.getInLanguage(English) == "result")
      .getOrElse(fail("expected assignment to result"))
    assertEquals(resultAssignment.value.canEvaluateTo, BeDataType.Numeric)
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

  test("nested function renders body with relative indentation") {
    val pythonSource =
      """def outer():
        |    def inner():
        |        value = 1
        |        return value
        |    return inner()
        |""".stripMargin

    val parsingResult = PythonParser.parsePythonWithDetails(pythonSource)
    val outerFunction = parsingResult.definedFunctions.find { function =>
      function.functionTypeInfo.displayName.getInLanguage(English) == "outer"
    }.getOrElse(fail("expected to find outer function definition"))

    val innerFunction = outerFunction.body match {
      case seq: BeSequence =>
        seq.body.collectFirst { case function: BeDefineFunction => function }
          .getOrElse(fail("expected nested inner function definition"))
      case other => fail(s"Expected sequence body, found ${other.getClass.getSimpleName}")
    }

    val renderedInner = innerFunction.getInLanguage(Python, English)
    val expectedInner =
      """def inner():
        |    value = 1
        |    return value""".stripMargin

    assertEquals(renderedInner, expectedInner)
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
    assertEquals(known.operators.get(">" -> 2), Some(List(greaterOperator)))
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
    val defaultGreaterOverloads =
      DefaultDefinitions.operatorDefinitionsWithSymbols.collect {
        case (symbol, function) if symbol == ">" && function.inputs.length == 2 => function
      }.toSet

    val currentGreater = result.currentlyKnownStructures.operators.get(">" -> 2)
    assertEquals(
      currentGreater.map(_.toSet),
      Some(defaultGreaterOverloads),
      "expected parser to reuse default > operator overloads"
    )

    val greaterFunctions = result.definedFunctions.filter { func =>
      func.functionTypeInfo.displayName.getInLanguage(English) == ">"
    }.toSet
    assertEquals(
      greaterFunctions,
      defaultGreaterOverloads,
      "expected only the default > operator overloads to be present"
    )
  }


  test("parse class with body attributes and constructor assignments") {
    val python =
      """class Player:
        |    level: int = 1
        |    role = 'mage'
        |
        |    def __init__(self, name: str, age):
        |        self.name: str = name
        |        self.age = age
        |
        |    def greet(self) -> str:
        |        return "Welcome " + self.name
        |""".stripMargin

    val result = PythonParser.parsePythonWithDetails(python)
    val maybeClass = result.definedClasses.collectFirst { case clazz if clazz.name.getInLanguage(English) == "Player" => clazz }
    assert(maybeClass.nonEmpty, "expected Player class to be parsed")
    val clazz = maybeClass.get

    val attributeNames = clazz.attributes.map(_.name.getInLanguage(English)).toSet
    assertEquals(attributeNames, Set("level", "role", "name", "age"))

    val attributeTypes = clazz.attributes.map(attr => attr.name.getInLanguage(English) -> attr.variableType).toMap
    assertEquals(attributeTypes("level"), BeDataType.Numeric)
    assert(attributeTypes("role").formatTypeForDisplay.getInLanguage(Python).contains("str"))
    assert(attributeTypes("name").formatTypeForDisplay.getInLanguage(Python).contains("str"))
    assertEquals(attributeTypes("age"), BeDataType.AnyType)

    val methodNames = clazz.methods.map(_.functionTypeInfo.displayName.getInLanguage(English))
    assertEquals(methodNames, List("__init__", "greet"))
    assert(clazz.methods.forall(_.functionTypeInfo.funcType == BeDefineFunction.Method()))
    assert(clazz.methods.forall(_.functionTypeInfo.isMethodInClass.nonEmpty))

    assert(!result.definedFunctions.exists(_.functionTypeInfo.displayName.getInLanguage(English) == "greet"))
    assert(!result.definedVariables.exists(v => attributeNames.contains(v.name.getInLanguage(English))))
  }

  test("ignore attribute assignments outside init while keeping constructor attributes") {
    val python =
      """class Helper:
        |    value: float
        |
        |    def __init__(self):
        |        self.ready = True
        |
        |    def prepare(self):
        |        temp = 5
        |        self.hidden = 10
        |""".stripMargin

    val result = PythonParser.parsePythonWithDetails(python)
    val clazz = result.definedClasses match {
      case List(single: BeDefineClass) => single
      case other => fail(s"expected a single class definition, found ${other.length}")
    }

    val attributeMap = clazz.attributes.map(attr => attr.name.getInLanguage(English) -> attr.variableType).toMap
    assertEquals(attributeMap.keySet, Set("value", "ready"))
    assertEquals(attributeMap("value"), BeDataType.Numeric)
    assert(attributeMap("ready").formatTypeForDisplay.getInLanguage(Python).contains("bool"))
    assert(!attributeMap.contains("hidden"))

    assert(!result.definedVariables.exists(_.name.getInLanguage(English) == "temp"))
    assert(clazz.methods.forall(_.functionTypeInfo.isMethodInClass.nonEmpty))
  }
}
