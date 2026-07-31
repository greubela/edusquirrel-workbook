package it.evadid.workbook.vm.code

import it.evadid.vm.code.defining.BeDefineFunction.functionInfo
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import munit.FunSuite

class BeExpressionLanguageSupportTest extends FunSuite {

  private val targetLanguages = List(Python, Java, Lisp, Cpp)
  private val humanLanguage = English

  private val xVar = BeDefineVariable(BeEntityName.fromUniversalNameInParts("x"), BeDataType.Int)
  private val yVar = BeDefineVariable(BeEntityName.fromUniversalNameInParts("y"), BeDataType.Int)
  private val boolVar = BeDefineVariable(BeEntityName.fromUniversalNameInParts("ok"), BeDataType.Boolean)

  private val literalOne = BeUseValue(BeDataValueLiteral("1"), Some(xVar))
  private val literalTwo = BeUseValue(BeDataValueLiteral("2"), Some(yVar))
  private val literalTrue = BeUseValue(BeDataValueLiteral("true"), Some(boolVar))

  private val assignX = BeAssignVariable(xVar, literalOne)
  private val returnX = BeReturn(Some(BeUseValue(BeDataValueLiteral("x"), Some(xVar))))
  private val sequence = BeSequence.optionalBody(List(assignX, returnX))

  private val function = BeDefineFunction(
    inputs = List(xVar, yVar),
    outputs = Some(BeDefineVariable(BeEntityName.fromUniversalNameInParts("result"), BeDataType.Int)),
    body = BeSequence.optionalBody(List(returnX)),
    functionTypeInfo = functionInfo(BeEntityName.fromUniversalNameInParts("add"))
  )

  private val functionCall = BeFunctionCall(function, Map(xVar -> literalOne, yVar -> literalTwo))

  private val allExpressions: List[BeExpression] = List(
    xVar,
    function,
    BeDefineClass(BeEntityName.fromUniversalNameInParts("Counter"), attributes = List(xVar), methods = List(function)),
    assignX,
    BeUseValue(BeDataValueLiteral("7"), Some(xVar)),
    functionCall,
    BeReturn(Some(literalOne)),
    BeStartProgram(Some(sequence)),
    sequence,
    BeIfElse(BeSequence.conditionalBody(List(literalTrue)), BeSequence.optionalBody(List(assignX)), BeSequence.optionalBody(List(returnX))),
    BeWhile(BeSequence.conditionalBody(List(literalTrue)), BeSequence.optionalBody(List(assignX))),
    BeRepeatNr(3, BeSequence.optionalBody(List(assignX))),
    BeExpressionUnsupported("unsupported"),
    BeExpressionUnparsable("raw", "bad syntax"),
    BeSingleLineComment(LanguageMap.universalMap("note"))
  )

  test("all BeExpression subclasses render for Python, Java, Lisp, and C++") {
    allExpressions.foreach { expr =>
      targetLanguages.foreach { language =>
        val rendered = expr.structureInfo.toStringInLanguage(language, humanLanguage)
        assert(rendered.trim.nonEmpty, s"${expr.getClass.getSimpleName} should render non-empty for ${language.name}")
      }
    }
  }

  test("Python rendering includes type hints for variable and function signatures") {
    val renderedVariable = xVar.structureInfo.toStringInLanguage(Python, humanLanguage)
    val renderedFunction = function.structureInfo.toStringInLanguage(Python, humanLanguage)

    assert(renderedVariable.contains(":"), s"Expected Python variable hint in: $renderedVariable")
    assert(renderedFunction.contains("def add("), clues(renderedFunction))
    assert(renderedFunction.contains("x: int"), clues(renderedFunction))
    assert(renderedFunction.contains("y: int"), clues(renderedFunction))
    assert(renderedFunction.contains("-> int"), clues(renderedFunction))
  }

  test("Python sequence rendering matches expected string exactly") {
    val scripted = BeSequence.optionalBody(List(
      BeAssignVariable(xVar, literalOne),
      BeAssignVariable(yVar, literalTwo),
      BeIfElse(
        BeSequence.conditionalBody(List(literalTrue)),
        BeSequence.optionalBody(List(BeAssignVariable(xVar, literalTwo))),
        BeSequence.optionalBody(List(BeAssignVariable(yVar, literalOne)))
      )
    ))

    val rendered = scripted.structureInfo.toStringInLanguage(Python, humanLanguage)

    val expected =
      """x: int = 1
        |y: int = 2
        |if true:
        |    x: int = 2
        |else:
        |    y: int = 1
        |""".stripMargin

    assertEquals(rendered, expected)
  }

  test("Java sequence rendering matches expected string ignoring surrounding whitespace") {
    val scripted = BeSequence.optionalBody(List(
      BeAssignVariable(xVar, literalOne),
      BeAssignVariable(yVar, literalTwo),
      BeIfElse(
        BeSequence.conditionalBody(List(literalTrue)),
        BeSequence.optionalBody(List(BeAssignVariable(xVar, literalTwo))),
        BeSequence.optionalBody(List(BeAssignVariable(yVar, literalOne)))
      )
    ))

    val expected =
      """int x = 1;
        |int y = 2;
        |if(true){
        |    int x = 2;
        |} else {
        |    int y = 1;
        |}
        |""".stripMargin

    assertEquals(scripted.structureInfo.toStringInLanguage(Java, humanLanguage).trim, expected.trim)
  }

  test("Lisp sequence rendering matches expected string exactly") {
    val scripted = BeSequence.optionalBody(List(
      BeAssignVariable(xVar, literalOne),
      BeAssignVariable(yVar, literalTwo),
      BeIfElse(
        BeSequence.conditionalBody(List(literalTrue)),
        BeSequence.optionalBody(List(BeAssignVariable(xVar, literalTwo))),
        BeSequence.optionalBody(List(BeAssignVariable(yVar, literalOne)))
      )
    ))

    val expected =
      """(progn
        |    (setf x 1)
        |    (setf y 2)
        |    (if (progn    true)
        |        (progn
        |            (progn
        |                (setf x 2)
        |            )
        |        )
        |        (progn
        |            (progn
        |                (setf y 1)
        |            )
        |        )
        |    )
        |)""".stripMargin

    assertEquals(scripted.structureInfo.toStringInLanguage(Lisp, humanLanguage), expected)
  }

  test("C++ sequence rendering matches expected string ignoring surrounding whitespace") {
    val scripted = BeSequence.optionalBody(List(
      BeAssignVariable(xVar, literalOne),
      BeAssignVariable(yVar, literalTwo),
      BeIfElse(
        BeSequence.conditionalBody(List(literalTrue)),
        BeSequence.optionalBody(List(BeAssignVariable(xVar, literalTwo))),
        BeSequence.optionalBody(List(BeAssignVariable(yVar, literalOne)))
      )
    ))

    val expected =
      """int x = 1;
        |int y = 2;
        |if(true){
        |    int x = 2;
        |} else {
        |    int y = 1;
        |}
        |""".stripMargin

    assertEquals(scripted.structureInfo.toStringInLanguage(Cpp, humanLanguage).trim, expected.trim)
  }
}
