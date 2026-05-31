package datastructures.core.vm.code

import todomove.datastructures.core.vm.code.defining.BeDefineFunction.functionInfo
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMap
import munit.FunSuite
import todomove.datastructures.core.vm.code.BeExpression
import todomove.datastructures.core.vm.code.controlStructures.{BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import todomove.datastructures.core.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}
import todomove.datastructures.core.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import todomove.datastructures.core.vm.code.others.{BeReturn, BeStartProgram}
import todomove.datastructures.core.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import todomove.datastructures.core.vm.types.{BeDataType, BeDataValueLiteral}

class BeExpressionLanguageSupportTest extends FunSuite {

  private val targetLanguages = List(Python, Java, Lisp, Cpp)
  private val humanLanguage = English

  private val xVar = BeDefineVariable(LanguageMap.universalMap("x"), BeDataType.Int)
  private val yVar = BeDefineVariable(LanguageMap.universalMap("y"), BeDataType.Int)
  private val boolVar = BeDefineVariable(LanguageMap.universalMap("ok"), BeDataType.Boolean)

  private val literalOne = BeUseValue(BeDataValueLiteral("1"), Some(xVar))
  private val literalTwo = BeUseValue(BeDataValueLiteral("2"), Some(yVar))
  private val literalTrue = BeUseValue(BeDataValueLiteral("true"), Some(boolVar))

  private val assignX = BeAssignVariable(xVar, literalOne)
  private val returnX = BeReturn(Some(BeUseValue(BeDataValueLiteral("x"), Some(xVar))))
  private val sequence = BeSequence.optionalBody(List(assignX, returnX))

  private val function = BeDefineFunction(
    inputs = List(xVar, yVar),
    outputs = Some(BeDefineVariable(LanguageMap.universalMap("result"), BeDataType.Int)),
    body = BeSequence.optionalBody(List(returnX)),
    functionTypeInfo = functionInfo(LanguageMap.universalMap("add"))
  )

  private val functionCall = BeFunctionCall(function, Map(xVar -> literalOne, yVar -> literalTwo))

  private val allExpressions: List[BeExpression] = List(
    xVar,
    function,
    BeDefineClass(LanguageMap.universalMap("Counter"), attributes = List(xVar), methods = List(function)),
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

  test("all BeExpression subclasses render for Python, Java, Lisp, and C++".ignore) {
    allExpressions.foreach { expr =>
      targetLanguages.foreach { language =>
        val rendered = expr.getInLanguage(language, humanLanguage)
        assert(rendered.trim.nonEmpty, s"${expr.getClass.getSimpleName} should render non-empty for ${language.name}")
      }
    }
  }

  test("Python rendering includes type hints for variable and function signatures") {
    val renderedVariable = xVar.getInLanguage(Python, humanLanguage)
    val renderedFunction = function.getInLanguage(Python, humanLanguage)

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

    val rendered = scripted.getInLanguage(Python, humanLanguage)

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

  test("Java sequence rendering matches expected string exactly".ignore) {
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

    assertEquals(scripted.getInLanguage(Java, humanLanguage), expected)
  }

  test("Lisp sequence rendering matches expected string exactly".ignore) {
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
        |  (setf x: int 1)
        |  (setf y: int 2)
        |  (if true
        |    (progn
        |      (setf x: int 2)
        |    )
        |    (progn
        |      (setf y: int 1)
        |    )
        |  )
        |)""".stripMargin

    assertEquals(scripted.getInLanguage(Lisp, humanLanguage), expected)
  }

  test("C++ sequence rendering matches expected string exactly".ignore) {
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

    assertEquals(scripted.getInLanguage(Cpp, humanLanguage), expected)
  }
}
