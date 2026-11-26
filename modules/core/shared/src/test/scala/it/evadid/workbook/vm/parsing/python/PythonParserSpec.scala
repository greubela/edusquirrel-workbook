package it.evadid.workbook.vm.parsing.python

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage

import it.evadid.core.datastructures.language.AppLanguage.{English, German, JavaScript, Python}
import it.evadid.vm.conversion.parsed.SymbolTable
import it.evadid.vm.conversion.parsed.SymbolTable.KnownStructure
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeIfElse, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.{BeEntityName, NamingStyle}
import it.evadid.vm.conversion.pythonexpression.PythonToExpression
import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import it.evadid.vm.parsing.python.clean.PythonAstParserSimple
import it.evadid.vm.conversion.parsed.DefaultDefinitions
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import munit.FunSuite

class PythonParserSpec extends FunSuite {

  /** Canonical Python text: the program as the converter reads and prints it. */
  private object normalizer {
    def normalizePython(source: String): String =
      ExpressionToPython.printedPython(SourceParsing.parse(source, SourceLanguage.Python).parsed().program.fullProgram)
    def normalizeLineEndings(source: String): String = PythonAstParserSimple.normalizeLineEndingsAndDetab(source)
  }

  private def parsedPython(source: String): PythonToExpression.Details =
    SourceParsing.parse(source, SourceLanguage.Python).parsed().details

  private def renderedPython(expression: BeExpression): String = expression.structureInfo.toStringInLanguage(Python, English)

  private def snakeName(name: BeEntityName): String = name.getNameIn(English, NamingStyle.SnakeCase)

  private def functionNamed(details: PythonToExpression.Details, name: String): Option[BeDefineFunction] =
    details.definedFunctions.find(function => snakeName(function.functionTypeInfo.displayName) == name)

  private def topLevelComments(details: PythonToExpression.Details): Seq[BeSingleLineComment] =
    details.codeExpression.body.collect { case comment: BeSingleLineComment => comment }

  private def miniProgramExpression(): BeExpression = {
    val forwardName: BeEntityName = BeEntityName.fromMapInCodeNotation(Map(
      German -> "vorwärts",
      English -> "forward"
    ))

    val parameter = BeDefineVariable(
      BeEntityName.fromMapInCodeNotation(Map(
        German -> "distanz",
        English -> "distance"
      )),
      BeDataType.Numeric
    )

    val forwardFunc = BeDefineFunction(
      List(parameter),
      None,
      BeExpression.pass,
      BeDefineFunction.functionInfo(forwardName)
    )

    BeStartProgram(
      BeSequence.optionalBody(
        List(
          BeFunctionCall.fromDefinition(forwardFunc, Map(parameter -> BeUseValue(BeDataValueLiteral("100"), Some(parameter)))),
          BeFunctionCall.fromDefinition(forwardFunc, Map(parameter -> BeUseValue(BeDataValueLiteral("100"), Some(parameter))))
        )
      )
    )
  }

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

  /** Type hints by declaration; a later declaration of the same key replaces an earlier one. */
  private def collectTypeHints(code: String): Map[TypeHintKey, String] =
    code.split("\n", -1).toList.flatMap {
      case FunctionHeaderPattern(_, name, params, returnType) =>
        val returnHint = Option(returnType).map(_.trim).filter(_.nonEmpty)
          .map(raw => TypeHintKey("return", name) -> stripDefaultValue(raw))
        val paramEntries = if (params.trim.isEmpty) Nil else params.split(",").toList
        val paramHints = paramEntries.flatMap { entry =>
          entry.trim.split(":", 2) match {
            case Array(paramName, typePart) if paramName.trim.nonEmpty && stripDefaultValue(typePart).nonEmpty =>
              Some(TypeHintKey(s"param:$name", paramName.trim) -> stripDefaultValue(typePart))
            case _ => None
          }
        }
        returnHint.toList ++ paramHints
      case AssignmentWithTypePattern(_, identifier, typeHint, _) =>
        List(TypeHintKey("assign", identifier) -> typeHint.trim)
      case _ => Nil
    }.toMap

  private def assertPythonEquivalentAllowingAdditionalTypeHints(original: String, regenerated: String): Unit = {
    def hints(source: String) = collectTypeHints(canonicalizeNumericTypeHints(normalizer.normalizePython(source)))
    val regeneratedHints = hints(regenerated)
    hints(original).foreach { case (key, hint) =>
      regeneratedHints.get(key).foreach { regeneratedHint =>
        assertEquals(normalizeTypeName(regeneratedHint), normalizeTypeName(hint))
      }
    }
  }

  /** Parses `python`, asserting `count` top-level assignments and no unsupported statements. */
  private def supportedAssignments(python: String, count: Int): (PythonToExpression.Details, Seq[BeAssignVariable]) = {
    val result = parsedPython(python)
    val body = result.codeExpression.body
    val assignments = body.collect { case assign: BeAssignVariable => assign }
    assertEquals(assignments.length, count)
    assertEquals(body.collect { case unsupported: BeExpressionUnsupported => unsupported }.length, 0)
    (result, assignments)
  }

  private case class RoundTripCase(
      name: String,
      python: String,
      expectedNormalized: Option[String] = None,
      assertions: PythonToExpression.Details => Unit = _ => ()
  )

  private val nestedIfWithCommentsSource =
    """def classify_score(value):
      |    if value > 10:
      |        tier = "high"
      |        
      |        # evaluate extreme tier
      |        if value > 20:
      |            return "extreme"
      |        
      |        return tier
      |    else:
      |        return "low"
      |""".stripMargin

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
      name = "function docstring retains hash characters",
      python =
        """def describe():
          |    '''Explains # usage and return handling.'''
          |    return "done"
          |""".stripMargin,
      assertions = result => {
        val generated = normalizer.normalizePython(renderedPython(result.codeExpression))
        val expectedDocstring = "'''Explains # usage and return handling.'''"
        val docstringLines = generated
          .split("\n")
          .toList
          .filter(_.contains(expectedDocstring))
        assertEquals(
          docstringLines.length,
          1,
          s"expected docstring line containing '$expectedDocstring' to be preserved exactly, but was: $generated"
        )
        val commentLines = generated
          .split("\n")
          .count(line => line.trim.startsWith("#"))
        assertEquals(commentLines, 0, s"hash characters must remain only inside docstrings: $generated")
      }
    ),
    RoundTripCase(
      name = "string literals with different quotes",
      python =
        """message = "Hello"
          |reply = 'Hi there'
          |combined = message + " & " + reply
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
      assertions = result => assertEquals(topLevelComments(result).length, 2)
    ),
    RoundTripCase(
      name = "inline comments after statements",
      python =
        """value = 10  # initial load
          |value = value + 1  # increment
          |""".stripMargin,
      expectedNormalized = Some(
        """value: float = 10
          |# initial load
          |value = value + 1
          |# increment""".stripMargin
      ),
      assertions = result => assertEquals(topLevelComments(result).length, 2)
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
        """flag: bool = True
          |if flag:
          |    value: float = 1
          |result: float = value""".stripMargin
      ),
      assertions = result => {
        val conditional = result.codeExpression.body.collect { case branch: BeIfElse => branch }
        assertEquals(conditional.length, 1)
        assertEquals(conditional.head.elseBody.body.length, 0)
        val rendered = normalizer.normalizePython(renderedPython(conditional.head))
        assert(!rendered.contains("else:"))
      }
    ),
    RoundTripCase(
      name = "if-elif-else normalization and augmented assignment",
      python =
        """score = 0
          |if score > 10:
          |    result = "high"
          |elif score == 10:
          |    score += 1
          |elif score == 0:
          |    result = "empty"
          |else:
          |    result = "low"
          |""".stripMargin,
      expectedNormalized = Some(
        """score: float = 0
          |if score > 10:
          |    result: str = "high"
          |elif score == 10:
          |    score = score + 1
          |elif score == 0:
          |    result = "empty"
          |else:
          |    result = "low"""".stripMargin
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
        """a: float = 1 + (2 * 3)
          |b: float = 1 * (2 + 3)
          |c: float = (1 * 2) + 3
          |x: float = 0
          |x = x + 1""".stripMargin
      )
    ),
    RoundTripCase(
      name = "typed expression with optional else generation",
      python =
        """a:float    = (((3)))
          |z=(1+2)*(3+4/2)
          |if(z<3):
          |    print(z)
          |""".stripMargin,
      assertions = result => {
        val ifExpressions = result.codeExpression.body.collect { case branch: BeIfElse => branch }
        assertEquals(ifExpressions.length, 1)
        val elseBody = ifExpressions.head.elseBody.body
        assert(
          elseBody.isEmpty || elseBody.forall(_ == BeExpression.pass),
          "if an else branch is introduced it must contain only a pass expression"
        )
      }
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
        val maybeFunction = functionNamed(result, "accumulate")
        assert(maybeFunction.nonEmpty, "expected accumulate function to be defined")
        val functionBody = maybeFunction.get.body.body
        val whileExpressions = functionBody.collect { case loop: BeWhile => loop }
        assertEquals(whileExpressions.length, 1)
        val whileBodyComments = whileExpressions.head.body.body.collect { case comment: BeSingleLineComment => comment }
        assertEquals(whileBodyComments.length, 1)
      }
    ),
    RoundTripCase(
      name = "class with methods survives round trip",
      python =
        """# create a class
          |class TestClass:
          |
          |    # some method
          |    def test_method(self):
          |        print("Area of Room =", self.length * self.breadth)
          |
          |    def test_with_int_result(self, a) -> float:
          |        return a + 4
          |""".stripMargin,
      assertions = result => {
        val clazz = result.definedClasses
          .collectFirst { case c if snakeName(c.name).equalsIgnoreCase("test_class") => c }
          .getOrElse(fail("expected test_class to be parsed as a class"))

        val methodNames = clazz.methods.map(method => snakeName(method.functionTypeInfo.displayName)).toSet
        assertEquals(methodNames, Set("test_method", "test_with_int_result"))
        assert(clazz.methods.forall(_.isInstanceOf[BeDefineFunction]))
        assert(clazz.methods.forall(_.functionTypeInfo.funcType.isInstanceOf[BeDefineFunction.Method.type]))
      }
    ),
    RoundTripCase(
      name = "lambda assignment round trip respects typing rules",
      python =
        """halfed = lambda x: (x / 2)
          |nr = 4
          |res = halfed(nr)
          |""".stripMargin,
      assertions = result => {
        val generated = renderedPython(result.codeExpression)
        val normalizedGenerated = normalizer.normalizePython(generated)
        val lines = normalizedGenerated.split("\n").toList
        val maybeLambdaLine = lines.find(_.contains("lambda"))
        maybeLambdaLine match {
          case Some(lambdaLine) =>
            val equalsIndex = lambdaLine.indexOf('=')
            assert(equalsIndex >= 0, "expected lambda line to be an assignment")
            val leftHandSide = lambdaLine.substring(0, equalsIndex)
            assert(
              !leftHandSide.contains(':'),
              s"lambda assignments must not introduce type hints, found: '$lambdaLine'"
            )
          case None =>
            assert(
              normalizedGenerated.contains("def halfed"),
              "expected halfed definition to remain either a lambda or become a def"
            )
        }
      }
    ),
    RoundTripCase(
      name = "typed addition uses operator definition",
      python =
        """def doSomething(par1: int, par2: str) -> str:
          |    res = par1 + par2
          |""".stripMargin,
      assertions = result => {
        val additionFunctions = result.definedFunctions.filter { function =>
          snakeName(function.functionTypeInfo.displayName) == "+"
        }
        assert(additionFunctions.nonEmpty, "expected '+' operator definition to be present")
        assert(
          additionFunctions.forall(_.functionTypeInfo.funcType.isInstanceOf[BeDefineFunction.Operator]),
          "expected '+' operator functions to use the operator function type"
        )
      }
    ),
    RoundTripCase(
      name = "nested if keeps blank lines and comments",
      python = nestedIfWithCommentsSource,
      assertions = result => {
        val regenerated = renderedPython(result.codeExpression)
        val normalizedRegenerated = normalizer.normalizePython(regenerated)

        assert(
          !normalizedRegenerated.linesIterator.exists(_.trim == "pass"),
          s"no additional pass statements expected, but got: $normalizedRegenerated"
        )

        val classifyFunction = functionNamed(result, "classify_score")
          .getOrElse(fail("expected classify_score function to be defined"))

        val functionBody = classifyFunction.body.body

        val topLevelIfs = functionBody.collect { case branch: BeIfElse => branch }
        assertEquals(topLevelIfs.length, 1)

        val nestedIfs = topLevelIfs.head.thenBody.body.collect { case nested: BeIfElse => nested }
        assertEquals(nestedIfs.length, 1, "expected nested if inside classify_score")
      }
    ),
    RoundTripCase(
      name = "bitshift comparisons keep tight spacing",
      python =
        """if((i<<2 ==   0)):
          |    i = i < 3
          |""".stripMargin,
      expectedNormalized = Some(
        """from typing import Any
          |
          |if (i << 2) == 0:
          |    i: Any = i < 3""".stripMargin
      ),
      assertions = result => {
        val generated = renderedPython(result.codeExpression)
        val normalizedGenerated = normalizer.normalizePython(generated)
        assert(
          normalizedGenerated.contains("<<"),
          s"expected bitshift comparison to stay compact, but was: $normalizedGenerated"
        )
        assert(!normalizedGenerated.contains("i < <"), "bitshift operator must not be split")
      }
    )
  )

  roundTripCases.foreach { testCase =>
    test(s"round trip - ${testCase.name}") {
      val normalizedInput = normalizer.normalizePython(testCase.python)
      val parsingResult = parsedPython(testCase.python)
      val expression = parsingResult.codeExpression
      assert(
        expression.body.forall(!_.isInstanceOf[BeExpressionUnparsable]),
        s"parsing produced an unparsable expression for ${testCase.name}"
      )

      val generated = renderedPython(expression)
      assertPythonEquivalentAllowingAdditionalTypeHints(testCase.python, generated)

      val normalizedGenerated = normalizer.normalizePython(generated)
      val reparsed = parsedPython(normalizedGenerated)
      val regenerated = renderedPython(reparsed.codeExpression)
      assertPythonEquivalentAllowingAdditionalTypeHints(normalizedGenerated, regenerated)

      testCase.expectedNormalized.foreach { expected =>
        assertEquals(
          normalizer.normalizeLineEndings(normalizedInput).trim,
          normalizer.normalizeLineEndings(expected.stripMargin).trim
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

    val (result, assignments) = supportedAssignments(python, 4)
    val booleanExpression = assignments.last.value
    val rendered = renderedPython(result.codeExpression)
    assertPythonEquivalentAllowingAdditionalTypeHints(python, rendered)
    assertEquals(booleanExpression.staticInformationExpression.staticType, BeDataType.Boolean)
  }

  test("unary operators are parsed as functions") {
    val python =
      """count = 5
        |negative = -count
        |positive = +count
        |bitwise = ~count
        |flag = not False
        |""".stripMargin

    val (result, assignments) = supportedAssignments(python, 5)
    val rendered = renderedPython(result.codeExpression)
    assertPythonEquivalentAllowingAdditionalTypeHints(python, rendered)
    assertEquals(assignments(1).value.staticInformationExpression.staticType, BeDataType.Numeric)
    assertEquals(assignments(2).value.staticInformationExpression.staticType, BeDataType.Numeric)
    assertEquals(assignments(3).value.staticInformationExpression.staticType, BeDataType.Numeric)
    assertEquals(assignments(4).value.staticInformationExpression.staticType, BeDataType.Boolean)
  }

  test("typed circle_area function preserves annotations and types") {
    val python =
      """def circle_area(radius: double) -> double:
        |    area: double = 3.14 * radius * radius
        |testWithNr: double = 3.0
        |result = circle_area(testWithNr)
        |""".stripMargin

    val parsingResult = parsedPython(python)
    val regenerated = renderedPython(parsingResult.codeExpression)
    assertPythonEquivalentAllowingAdditionalTypeHints(python, regenerated)

    val circleFunction = functionNamed(parsingResult, "circle_area").getOrElse(fail("expected to find circle_area function"))

    assertEquals(circleFunction.inputs.length, 1)
    val radiusParam = circleFunction.inputs.head
    assertEquals(snakeName(radiusParam.name), "radius")
    assertEquals(radiusParam.variableType, BeDataType.Numeric)

    val returnVariable = circleFunction.outputs.getOrElse(fail("expected return variable"))
    assertEquals(returnVariable.variableType, BeDataType.Numeric)

    val bodySequence = circleFunction.body
    val areaAssignment = bodySequence.body.collectFirst {
      case assign: BeAssignVariable if snakeName(assign.target.name) == "area" => assign
    }.getOrElse(fail("expected assignment to area"))
    assertEquals(areaAssignment.target.variableType, BeDataType.Numeric)
    assertEquals(areaAssignment.value.staticInformationExpression.staticType, BeDataType.Numeric)

    val variablesByName = parsingResult.definedVariables.map { variable =>
      snakeName(variable.name) -> variable
    }.toMap
    val testWithNrVar = variablesByName.getOrElse("test_with_nr", fail("expected test_with_nr variable"))
    assertEquals(testWithNrVar.variableType, BeDataType.Numeric)
    val resultVar = variablesByName.getOrElse("result", fail("expected result variable"))
    assertEquals(resultVar.variableType, BeDataType.Numeric)

    val topAssignments = parsingResult.codeExpression.body.collect { case assign: BeAssignVariable => assign }
    val testAssignment = topAssignments
      .find(assign => snakeName(assign.target.name).equalsIgnoreCase("test_with_nr"))
      .getOrElse(fail("expected assignment to test_with_nr"))
    assertEquals(testAssignment.value.staticInformationExpression.staticType, BeDataType.Numeric)

    val resultAssignment = topAssignments
      .find(assign => snakeName(assign.target.name) == "result")
      .getOrElse(fail("expected assignment to result"))
    assertEquals(resultAssignment.value.staticInformationExpression.staticType, BeDataType.Numeric)
  }

  test("round trip from mini program expression") {
    val sourceExpression = miniProgramExpression()
    val generated = renderedPython(sourceExpression)

    val parsed = parsedPython(generated)
    assertEquals(parsed.codeExpression.body.length, 2)

    val regenerated = renderedPython(parsed.codeExpression)
    val canonical = renderedPython(parsedPython(regenerated).codeExpression)
    assertEquals(normalizer.normalizePython(canonical), normalizer.normalizePython(regenerated))
  }

  test("distinguish unsupported and unparsable inputs") {
    val unsupportedSource = "foo[0]"
    val unsupportedExpressions = parsedPython(unsupportedSource).codeExpression.body
    assert(unsupportedExpressions.exists(_.isInstanceOf[BeExpressionUnsupported]))

    val namedForSource =
      """for i in range(3):
        |    forward(i)
        |""".stripMargin
    val namedForExpressions = parsedPython(namedForSource).codeExpression.body
    assert(namedForExpressions.exists(_.isInstanceOf[it.evadid.vm.code.controlStructures.BeFor]))

    val supportedIfSource =
      """if True:
        |    pass
        |""".stripMargin
    val supportedIfExpressions = parsedPython(supportedIfSource).codeExpression.body
    val parsedIf = supportedIfExpressions.collect { case branch: BeIfElse => branch }
    assertEquals(parsedIf.length, 1)
    assertEquals(parsedIf.head.elseBody.body.length, 0)

    val unparsableSource =
      """while True
        |    pass
        |""".stripMargin
    val unparsableExpressions = parsedPython(unparsableSource).codeExpression.body
    assert(unparsableExpressions.exists(_.isInstanceOf[BeExpressionUnparsable]))
  }

  test("parse python segment with while expression and convert to javascript") {
    val segment =
      """    while remaining > 0:
        |        remaining = remaining - 1
        |    return remaining
        |""".stripMargin
    val body = parsedPython(segment).codeExpression.body
    val whileExpressions = body.collect { case loop: BeWhile => loop }
    assertEquals(whileExpressions.length, 1)
    val whileRendered = whileExpressions.head.structureInfo.toStringInLanguage(JavaScript, English)
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
    assertEquals(returnExpressions.head.structureInfo.toStringInLanguage(JavaScript, English), "return remaining;")
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
    val maybeFunction = functionNamed(parsedPython(pythonSource), "sum_until")
    assert(maybeFunction.nonEmpty, "expected to find sum_until function definition")
    val renderedJavaScript = maybeFunction.get.structureInfo.toStringInLanguage(JavaScript, English)
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

    val outerFunction = functionNamed(parsedPython(pythonSource), "outer")
      .getOrElse(fail("expected to find outer function definition"))

    val innerFunction = outerFunction.body.body.collectFirst { case function: BeDefineFunction => function }
      .getOrElse(fail("expected nested inner function definition"))

    val renderedInner = renderedPython(innerFunction)
    val expectedInner =
      """def inner():
        |    value = 1
        |    return value""".stripMargin

    assertPythonEquivalentAllowingAdditionalTypeHints(expectedInner, renderedInner)
  }

  test("merge the environment's known structures with parsed definitions") {
    val parsingResult = parsedPython("""value = left > right""")
    val known = parsingResult.currentlyKnownStructures
    val greater = SymbolTable.defaultKnownStructures.collect { case KnownStructure.Operator(">", function) => function }
    assertEquals(known.operators.get(">" -> 2).map(_.toSet), Some(greater.toSet))
    assert(known.variables.contains("value"))
    assert(greater.forall(function => parsingResult.definedFunctions.contains(function)))
  }

  test("parser initializes with default builtin function definitions") {
    val result = parsedPython("")
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
    val result = parsedPython(source)
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
      snakeName(func.functionTypeInfo.displayName) == ">"
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

    val result = parsedPython(python)
    val maybeClass = result.definedClasses.collectFirst { case clazz if snakeName(clazz.name).equalsIgnoreCase("player") => clazz }
    assert(maybeClass.nonEmpty, "expected Player class to be parsed")
    val clazz = maybeClass.get

    val attributeNames = clazz.attributes.map(attribute => snakeName(attribute.name)).toSet
    assertEquals(attributeNames, Set("level", "role", "name", "age"))

    val attributeTypes = clazz.attributes.map(attr => snakeName(attr.name) -> attr.variableType).toMap
    assertEquals(attributeTypes("level"), BeDataType.Numeric)
    assert(attributeTypes("role").formatTypeForDisplay.getInLanguage(Python).contains("str"))
    assert(attributeTypes("name").formatTypeForDisplay.getInLanguage(Python).contains("str"))
    assertEquals(attributeTypes("age"), BeDataType.AnyType)

    val methodNames = clazz.methods.map(method => snakeName(method.functionTypeInfo.displayName))
    assertEquals(methodNames, List("λ", "greet"))
    assertEquals(clazz.methods.map(_.functionTypeInfo.funcType), List(BeDefineFunction.Constructor, BeDefineFunction.Method))
    assert(clazz.methods.forall(_.functionTypeInfo.isMethodInClass.nonEmpty))

    assert(functionNamed(result, "greet").isEmpty)
    assert(!result.definedVariables.exists(v => attributeNames.contains(snakeName(v.name))))
  }

  test("retain attribute assignments outside init and constructor attributes") {
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

    val result = parsedPython(python)
    val clazz = result.definedClasses match {
      case List(single: BeDefineClass) => single
      case other => fail(s"expected a single class definition, found ${other.length}")
    }

    val attributeMap = clazz.attributes.map(attr => snakeName(attr.name) -> attr.variableType).toMap
    assertEquals(attributeMap.keySet, Set("value", "ready", "hidden"))
    assertEquals(attributeMap("value"), BeDataType.Numeric)
    assert(attributeMap("ready").formatTypeForDisplay.getInLanguage(Python).contains("bool"))
    assertEquals(attributeMap("hidden"), BeDataType.Numeric)

    assert(!result.definedVariables.exists(variable => snakeName(variable.name) == "temp"))
    assert(clazz.methods.forall(_.functionTypeInfo.isMethodInClass.nonEmpty))
  }
}
