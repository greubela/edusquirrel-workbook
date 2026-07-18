package it.evadid.vm.parsing.python.clean

import it.evadid.core.util.io.Serializer
import it.evadid.vm.parsing.abstractions.GenericAST

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

  case class PyImportStatement(moduleName: String) extends PyStatement

  case class PyImportFromStatement(moduleName: String, imports: List[PyTarget], importAll: Boolean) extends PyStatement

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

  sealed trait PyExpression extends PyStatement

  case class NamedExpression(name: String, expression: PyExpression) extends PyExpression {
    override def getChildren(): Seq[GenericAST] = List(expression)
  }

  case class PyTypeDefExpression() extends PyExpression

  case class PyAssignment(target: PyTarget, value: Option[PyExpression]) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = value.toList
  }

  case class PyAugAssignment(target: PyTarget, augOperator: String, expression: PyExpression) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = List(expression)
  }

  // Targets


  // Defs

  case class PyFunctionCall(name: PyTarget, parameterValues: List[PyExpression]) extends PyExpression {
    override def getChildren(): Seq[GenericAST] = parameterValues
  }

  case class PyFunctionDef(name: String, parameters: List[PyAssignment], block: PyExecutionBlock, isAsync: Boolean) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = parameters ++ List(block)
  }

  case class PyClassDef(name: String, parameters: List[PyAssignment], block: PyExecutionBlock, isAsync: Boolean) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = parameters ++ List(block)
  }

  case class PyOperationBinary(left: PyExpression, op: String, right: PyExpression) extends PyExpression {
    override def getChildren(): Seq[GenericAST] = List(left, right)
  }

  case class PyOperationUnary(op: String, operand: PyExpression) extends PyExpression{
    override def getChildren(): Seq[GenericAST] = List(operand)}

  // Atomar

  sealed trait PyAtomar extends PyExpression

  case class PyTarget(name: String, locationString: List[String] = List(), sliceExpr: Option[PyExpression] = None, typeHint: Option[PythonType] = None) extends PyAtomar {
    override def getChildren(): Seq[GenericAST] = sliceExpr.toList
  }

  case class PyLiteral[T](literalAsString: String, pythonTyp: PythonType, scalaValue: T, serializer: Serializer[T]) extends PyAtomar

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
        |func (hai)
        |
        |x = 2
        |
        |""".stripMargin

    val res =  Python313Parser.parse(example2)

    println(res)

    println(res.right.get.statements.mkString("\n"))

  }


  /*

  // Core Program Structure
  case class Module(statements: Seq[PyAST]) extends PyAST

  // Structural Blocks & Control Flow
  case class FunctionDef(name: String, params: Seq[PyParam], returns: Option[PyAST], body: Seq[PyAST], isAsync: Boolean, decorators: Seq[PyAST]) extends PyAST
  case class ClassDef(name: String, bases: Seq[PyAST], body: Seq[PyAST], decorators: Seq[PyAST]) extends PyAST
  case class If(condition: PyAST, body: Seq[PyAST], orelse: Seq[PyAST]) extends PyAST
  case class While(condition: PyAST, body: Seq[PyAST], orelse: Seq[PyAST]) extends PyAST
  case class For(target: PyAST, iter: PyAST, body: Seq[PyAST], orelse: Seq[PyAST], isAsync: Boolean) extends PyAST

  // Context Management & Error Handling
  case class With(items: Seq[WithItem], body: Seq[PyAST], isAsync: Boolean) extends PyAST
  case class WithItem(contextExpr: PyAST, optionalVars: Option[PyAST]) extends PyAST
  case class Try(body: Seq[PyAST], handlers: Seq[ExceptHandler], orelse: Seq[PyAST], finalbody: Seq[PyAST]) extends PyAST
  case class ExceptHandler(typeExpr: Option[PyAST], nameTarget: Option[String], body: Seq[PyAST], isStar: Boolean) extends PyAST

  // Pattern Matching (PEP 634)
  case class MatchStatement(subject: PyAST, cases: Seq[MatchCase]) extends PyAST
  case class MatchCase(pattern: PyAST, guard: Option[PyAST], body: Seq[PyAST]) extends PyAST

  // Inline Parameters & Variables
  case class PyParam(name: String, annotation: Option[PyAST], default: Option[PyAST]) extends PyAST
  case class TypeAlias(name: String, value: PyAST) extends PyAST
  case class Assign(targets: Seq[PyAST], value: PyAST, augOp: Option[String] = None) extends PyAST

  // Operators & Precedence Expressions Tree
  case class Name(id: String) extends PyAST
  case class Constant(value: String, kind: String) extends PyAST // kind: "int", "float", "str", "bool", "none"

  case class BoolOp(op: String, values: Seq[PyAST]) extends PyAST
  case class Compare(left: PyAST, ops: Seq[String], comparators: Seq[PyAST]) extends PyAST
  case class NamedExpr(target: PyAST, value: PyAST) extends PyAST
  case class Call(func: PyAST, args: Seq[PyAST], keywords: Seq[KeywordArg]) extends PyAST
  case class KeywordArg(arg: Option[String], value: PyAST) extends PyAST
  case class Attribute(value: PyAST, attr: String) extends PyAST
  case class Subscript(value: PyAST, slice: PyAST) extends PyAST
  case class Slice(lower: Option[PyAST], upper: Option[PyAST], step: Option[PyAST]) extends PyAST

  // Collection Literals & Comprehensions
  case class PyList(elts: Seq[PyAST]) extends PyAST
  case class PyTuple(elts: Seq[PyAST]) extends PyAST
  case class PySet(elts: Seq[PyAST]) extends PyAST
  case class PyDict(keys: Seq[Option[PyAST]], values: Seq[PyAST]) extends PyAST
  case class ListComp(elt: PyAST, generators: Seq[Comprehension]) extends PyAST
  case class DictComp(key: PyAST, value: PyAST, generators: Seq[Comprehension]) extends PyAST
  case class SetComp(elt: PyAST, generators: Seq[Comprehension]) extends PyAST
  case class GeneratorExp(elt: PyAST, generators: Seq[Comprehension]) extends PyAST
  case class Comprehension(target: PyAST, iter: PyAST, ifs: Seq[PyAST], isAsync: Boolean) extends PyAST

  // Atomic Core Statements
  case class Return(expr: Option[PyAST]) extends PyAST
  case class Raise(expr: Option[PyAST], fromExpr: Option[PyAST]) extends PyAST
  case class Delete(targets: Seq[PyAST]) extends PyAST
  case class Assert(condition: PyAST, msg: Option[PyAST]) extends PyAST
  case class ExprStmt(expr: PyAST) extends PyAST
  case class Pass() extends PyAST
  case class Break() extends PyAST
  case class Continue() extends PyAST

  // Modules Handling
  case class Import(names: Seq[String]) extends PyAST
  case class ImportFrom(module: String, names: Seq[String]) extends PyAST
 */

}
