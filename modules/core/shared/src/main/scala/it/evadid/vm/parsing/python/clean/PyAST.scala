package it.evadid.vm.parsing.python.clean

import it.evadid.vm.parsing.generic.abstractions.GenericAST
import it.evadid.vm.parsing.generic.abstractions.GenericAST.{GenericAstLiteral, NamedElement}

sealed trait PyAST extends GenericAST

object PyAST {

  case class IndentState(var currentLevel: Int = 0)

  // ==========================================
  // PROGRAM
  // ==========================================
  case class PyProgram(statements: Seq[StatementWithLineNumber]) extends PyAST {
    override def getChildren(): Seq[GenericAST] = statements
  }

  case class StatementWithLineNumber(statement: PyStatement, lineNumber: Int) extends PyAST {
    override def getChildren(): Seq[GenericAST] = Seq(statement)
  }

  case class PyExecutionBlock(statements: Seq[PyStatement]) extends PyAST {
    def withAdded(other: Seq[PyStatement]): PyExecutionBlock = PyExecutionBlock(statements ++ other)

    override def getChildren(): Seq[GenericAST] = statements
  }

  // ==========================================
  // STATEMENTS
  // ==========================================
  sealed trait PyStatement extends PyAST

  case class PyUnparsableStatement(str: String) extends PyStatement

  val passBlock: PyExecutionBlock = PyExecutionBlock(List())

  case object PyPassStatement extends PyStatement

  case object PyBreakStatement extends PyStatement

  case object PyContinueStatement extends PyStatement

  case object PyEmptyStatement extends PyStatement

  case class PyImportStatement(moduleName: String) extends PyStatement with NamedElement {
    override def name: String = moduleName
  }

  case class PyImportFromStatement(moduleName: String, imports: List[PyTarget], importAll: Boolean) extends PyStatement with NamedElement {
    override def name: String = moduleName

    override def getChildren(): Seq[GenericAST] = imports
  }

  case class PyRaiseStatement(raiseDirectly: Option[PyExpression], raiseFrom: Option[PyExpression]) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = raiseDirectly.toList ++ raiseFrom.toList
  }

  case class PyReturnStatement(expr: Option[PyExpression]) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = expr.toList
  }

  // Basic Control Structures
  case class PyIfStatement(condition: PyExpression, thenBlock: PyExecutionBlock, elseBlock: PyExecutionBlock) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = List(condition, thenBlock, elseBlock)
  }

  case class PyWhileStatement(condition: PyExpression, bodyBlock: PyExecutionBlock, elseBlock: Option[PyExecutionBlock]) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = List(condition, bodyBlock) ++ elseBlock.toList
  }

  case class PyForStatement(elementExpression: PyExpression, inExpression: PyExpression, bodyBlock: PyExecutionBlock, elseBlock: Option[PyExecutionBlock], isAsync: Boolean) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = List(elementExpression, inExpression, bodyBlock) ++ elseBlock.toList
  }

  // Exception Handling
  case class PyTryStatement(body: PyExecutionBlock, exceptStatements: List[PyExceptClause], elseBlock: Option[PyExecutionBlock]) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = List(body) ++ exceptStatements ++ elseBlock.toList
  }

  sealed trait PyExceptClause extends PyStatement {
    def body: PyExecutionBlock
  }

  case class PyExceptClauseBasic(expression: Option[PyExpression], name: Option[String], body: PyExecutionBlock) extends PyExceptClause {
    override def getChildren(): Seq[GenericAST] = expression.toList ++ List(body)
  }

  case class PyExceptClauseStar(expression: PyExpression, name: Option[String], body: PyExecutionBlock) extends PyExceptClause {
    override def getChildren(): Seq[GenericAST] = List(expression, body)
  }

  case class PyExceptClauseFinally(body: PyExecutionBlock) extends PyExceptClause {
    override def getChildren(): Seq[GenericAST] = List(body)
  }

  // Expressions

  trait PyExpression extends PyStatement

  case class NamedExpression(name: String, expression: PyExpression) extends PyExpression with NamedElement {
    override def getChildren(): Seq[GenericAST] = List(expression)
  }

  case class PyTypeDefExpression() extends PyExpression

  sealed trait PyAssignment extends PyStatement {
    def target: PyTarget
  }

  case class PySimpleAssignment(target: PyTarget, value: Option[PyExpression]) extends PyAssignment {
    override def getChildren(): Seq[GenericAST] = value.toList ++ List(target)
  }

  case class PyAugAssignment(target: PyTarget, augOperator: String, expression: PyExpression) extends PyAssignment {
    override def getChildren(): Seq[GenericAST] = List(expression) ++ List(target)
  }

  // Targets


  // Defs

  case class PyFunctionCall(target: PyTarget, parameterValues: List[PyExpression]) extends PyExpression with NamedElement {
    override def getChildren(): Seq[GenericAST] = parameterValues ++ List(target)

    override def name: String = target.name
  }

  case class PyFunctionDef(name: String, parameters: List[PyAssignment], block: PyExecutionBlock, isAsync: Boolean) extends PyStatement with NamedElement {
    override def getChildren(): Seq[GenericAST] = parameters ++ List(block)
  }

  case class PyClassDef(name: String, parameters: List[PyAssignment], block: PyExecutionBlock, isAsync: Boolean) extends PyStatement with NamedElement {
    override def getChildren(): Seq[GenericAST] = parameters ++ List(block)
  }

  case class PyOperationBinary(left: PyExpression, op: String, right: PyExpression) extends PyExpression with NamedElement {
    override def getChildren(): Seq[GenericAST] = List(left, right)

    override def name: String = op
  }

  case class PyOperationUnary(op: String, operand: PyExpression) extends PyExpression with NamedElement {
    override def getChildren(): Seq[GenericAST] = List(operand)

    override def name: String = op
  }

  // Atomar

  sealed trait PyAtomar extends PyExpression

  case class PyTarget(identifier: String, locationString: List[String] = List(), sliceExpr: Option[PyExpression] = None, typeHint: Option[PythonType[?]] = None) extends PyAtomar with NamedElement {
    override def getChildren(): Seq[GenericAST] = sliceExpr.toList

    override def name: String = locationString.mkString("", ".", ".") + identifier // without slice for now
  }


  case class PythonLiteral[ScalaType](val literalValue: String, literalType: PythonType[ScalaType]) extends GenericAstLiteral[ScalaType, PythonType[ScalaType], PythonLiteral[ScalaType]] with PyExpression {

  }


  //type PyLiteral[T] = GenericAstLiteral[T, TargetType] forSome { type TargetType <: PythonType[T, TargetType] }

  /*case class PyLiteral[T](literalAsString: String, pythonTyp: PythonType[T]) extends PyAtomar with GenericAstLiteral[T, PythonType[T]] {
    override def literalValue: String = literalAsString

    override def literalType: PythonType[T] = pythonTyp
  }*/

  /*
  Todo: Adjust to PyLiteral
  case class PyListLiteral(elements: List[PyExpression]) extends PyAtomar {
    override def getChildren(): Seq[GenericAST] = elements
  }

  case class PyTupleLiteral(elements: List[PyExpression]) extends PyAtomar {
    override def getChildren(): Seq[GenericAST] = elements
  }*/

  def main(args: Array[String]): Unit = {
    println("hai!")
    val exampleToScan: String = {
      """
        |import turtle
        |import turtle2
        |from aturtle3 import *
        |from bturtle4 import turtle1
        |from cturtle5 import turtle2, turtle3,t4,t5     , t7
        |
        |a1 = 1
        |a2:int=1
        |a3 :int=1
        |a4: int=1
        |a5 : int=1
        |a6 : int =1
        |a7 : int = 1
        |x:int=1
        |x[0] = 1
        |x[0]
        |a.x = 1
        |a.x : int = 2
        |a.x[3] = 3
        |a.x[4]: int = 5
        |x = 50
        |y: str = "30"
        |x: int = 20
        |
        |x += 1
        |x+=1
        |x=+1
        |x=-1
        |
        |(abc: int) = 3
        |
        |a.func(b)
        |
        |func(hai)
        |
        |str(3)
        |int("4")
        |
        |
        |turtle.forward(100+50)
        |turtle.left(120)
        |turtle.forward(x - int(y))
        |turtle.right(50)
        |
        |""".stripMargin
    }

    val example2: String =
      """
        |
        |d = 3
        |
        |my.func (  hai  , x := 3)
        |
        |x+=2
        |
        |""".stripMargin

    val res = PythonAstParserSimple.parse(example2)

    println(res)

    println(res.right.get.statements.mkString("\n"))

  }

}
