package it.evadid.vm.parsing.python.clean

import it.evadid.vm.parsing.generic.abstractions.GenericAST
import it.evadid.vm.parsing.generic.abstractions.GenericAST.{GenericAstLiteral, NamedElement}
import it.evadid.vm.parsing.python.clean.PythonType.*

sealed trait PyAST extends GenericAST

object PyAST {

  /** Parser helper state, e.g. `currentLevel = 4` while parsing an indented block. */
  case class IndentState(var currentLevel: Int = 0)

  // ==========================================
  // PROGRAM
  // ==========================================
  /** A complete Python module, e.g. `x = 1\ny = 2`. */
  case class PyProgram(statements: Seq[StatementWithLineNumber]) extends PyAST {
    override def getChildren(): Seq[GenericAST] = statements
  }

  /** A parsed statement annotated with its source line, e.g. line 0 for `x = 1`. */
  case class StatementWithLineNumber(statement: PyStatement, lineNumber: Int) extends PyAST {
    override def getChildren(): Seq[GenericAST] = Seq(statement)
  }

  /** A block of statements, e.g. the indented body after `if ready:`. */
  case class PyExecutionBlock(statements: Seq[PyStatement]) extends PyAST {
    def withAdded(other: Seq[PyStatement]): PyExecutionBlock = PyExecutionBlock(statements ++ other)

    override def getChildren(): Seq[GenericAST] = statements
  }

  // ==========================================
  // STATEMENTS
  // ==========================================
  sealed trait PyStatement extends PyAST

  /** A fallback for unsupported syntax, e.g. `match value:` before match parsing exists. */
  case class PyUnparsableStatement(str: String) extends PyStatement

  val passBlock: PyExecutionBlock = PyExecutionBlock(List())

  /** The no-op statement `pass`. */
  case object PyPassStatement extends PyStatement

  /** A loop exit statement, e.g. `break`. */
  case object PyBreakStatement extends PyStatement

  /** A loop continuation statement, e.g. `continue`. */
  case object PyContinueStatement extends PyStatement

  /** A blank source line with no semantic statement. */
  case object PyEmptyStatement extends PyStatement

  /** A direct import statement, e.g. `import turtle`. */
  case class PyImportStatement(moduleName: String) extends PyStatement with NamedElement {
    override def name: String = moduleName
  }

  /** A from-import statement, e.g. `from math import sin` or `from math import *`. */
  case class PyImportFromStatement(moduleName: String, imports: List[PyTarget], importAll: Boolean) extends PyStatement with NamedElement {
    override def name: String = moduleName

    override def getChildren(): Seq[GenericAST] = imports
  }

  /** A raise statement, e.g. `raise ValueError()` or `raise err from cause`. */
  case class PyRaiseStatement(raiseDirectly: Option[PyExpression], raiseFrom: Option[PyExpression]) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = raiseDirectly.toList ++ raiseFrom.toList
  }

  /** A return statement, e.g. `return total` or bare `return`. */
  case class PyReturnStatement(expr: Option[PyExpression]) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = expr.toList
  }

  // Basic Control Structures
  /** An if/elif/else statement, e.g. `if x > 0: ... else: ...`. */
  case class PyIfStatement(condition: PyExpression, thenBlock: PyExecutionBlock, elseBlock: PyExecutionBlock) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = List(condition, thenBlock, elseBlock)
  }

  /** A while loop, e.g. `while running: ...`. */
  case class PyWhileStatement(condition: PyExpression, bodyBlock: PyExecutionBlock, elseBlock: Option[PyExecutionBlock]) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = List(condition, bodyBlock) ++ elseBlock.toList
  }

  /** A for loop, e.g. `for item in items: ...` or `async for item in stream: ...`. */
  case class PyForStatement(elementExpression: PyExpression, inExpression: PyExpression, bodyBlock: PyExecutionBlock, elseBlock: Option[PyExecutionBlock], isAsync: Boolean) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = List(elementExpression, inExpression, bodyBlock) ++ elseBlock.toList
  }

  // Exception Handling
  /** A try statement with handlers, e.g. `try: ... except ValueError: ...`. */
  case class PyTryStatement(body: PyExecutionBlock, exceptStatements: List[PyExceptClause], elseBlock: Option[PyExecutionBlock]) extends PyStatement {
    override def getChildren(): Seq[GenericAST] = List(body) ++ exceptStatements ++ elseBlock.toList
  }

  sealed trait PyExceptClause extends PyStatement {
    def body: PyExecutionBlock
  }

  /** A regular except clause, e.g. `except ValueError as err:`. */
  case class PyExceptClauseBasic(expression: Option[PyExpression], name: Option[String], body: PyExecutionBlock) extends PyExceptClause {
    override def getChildren(): Seq[GenericAST] = expression.toList ++ List(body)
  }

  /** An exception-group handler, e.g. `except* ValueError as err:`. */
  case class PyExceptClauseStar(expression: PyExpression, name: Option[String], body: PyExecutionBlock) extends PyExceptClause {
    override def getChildren(): Seq[GenericAST] = List(expression, body)
  }

  /** A finally clause, e.g. `finally: cleanup()`. */
  case class PyExceptClauseFinally(body: PyExecutionBlock) extends PyExceptClause {
    override def getChildren(): Seq[GenericAST] = List(body)
  }

  // Expressions

  trait PyExpression extends PyStatement

  /** A walrus expression, e.g. `count := len(items)`. */
  case class NamedExpression(name: String, expression: PyExpression) extends PyExpression with NamedElement {
    override def getChildren(): Seq[GenericAST] = List(expression)
  }

  /** A placeholder for type-definition expressions, e.g. future support for `type Alias = int`. */
  case class PyTypeDefExpression() extends PyExpression

  /** A simple assignment, e.g. `x = 1` or `user.name = "Ada"`. */
  sealed trait PyAssignment extends PyStatement {
    def target: PyTarget
    def value: Option[PyExpression] = None
  }

  /** A normal assignment statement, e.g. `total = subtotal + tax`. */
  case class PySimpleAssignment(target: PyTarget, override val value: Option[PyExpression]) extends PyAssignment {
    override def getChildren(): Seq[GenericAST] = value.toList ++ List(target)
  }

  /** An augmented assignment statement, e.g. `total += 1`. */
  case class PyAugAssignment(target: PyTarget, augOperator: String, expression: PyExpression) extends PyAssignment {
    override def getChildren(): Seq[GenericAST] = List(expression) ++ List(target)
  }

  // Targets


  // Defs

  /** A compatibility node for simple target calls, e.g. `print("hello")` or `math.sin(x)`. */
  case class PyFunctionCall(target: PyTarget, parameterValues: List[PyExpression]) extends PyExpression with NamedElement {
    override def getChildren(): Seq[GenericAST] = parameterValues ++ List(target)

    override def name: String = target.name
  }

  /** Attribute access on any primary expression, e.g. `obj.field` in `obj.field + 1`. */
  case class PyAttributeAccess(receiver: PyExpression, name: String) extends PyExpression with NamedElement {
    override def getChildren(): Seq[GenericAST] = List(receiver)
  }

  /** Subscript access on any primary expression, e.g. `items[0]` or `matrix[i, j]`. */
  case class PySubscript(receiver: PyExpression, indices: List[PyExpression]) extends PyExpression {
    override def getChildren(): Seq[GenericAST] = receiver +: indices
  }

  /** A call whose callee is any expression, e.g. `func()(x)` or `obj.method(1)`. */
  case class PyCallExpression(callee: PyExpression, args: List[PyExpression]) extends PyExpression {
    override def getChildren(): Seq[GenericAST] = callee +: args
  }

  /** A function definition, e.g. `def add(a, b): return a + b`. */
  case class PyFunctionDef(name: String, parameters: List[PyAssignment], block: PyExecutionBlock, isAsync: Boolean) extends PyStatement with NamedElement {
    override def getChildren(): Seq[GenericAST] = parameters ++ List(block)
  }

  /** A class definition, e.g. `class Turtle:` or `class Shape(Base):`. */
  case class PyClassDef(name: String, parameters: List[PyAssignment], block: PyExecutionBlock, isAsync: Boolean) extends PyStatement with NamedElement {
    override def getChildren(): Seq[GenericAST] = parameters ++ List(block)
  }

  /** A binary operation, e.g. `left + right` or `x and y`. */
  case class PyOperationBinary(left: PyExpression, op: String, right: PyExpression) extends PyExpression with NamedElement {
    override def getChildren(): Seq[GenericAST] = List(left, right)

    override def name: String = op
  }

  /** A unary operation, e.g. `-x` or `not ready`. */
  case class PyOperationUnary(op: String, operand: PyExpression) extends PyExpression with NamedElement {
    override def getChildren(): Seq[GenericAST] = List(operand)

    override def name: String = op
  }

  // Atomar

  sealed trait PyAtomar extends PyExpression

  /** An identifier-like target, e.g. `x`, `obj.field`, or `items[0]` in assignments. */
  case class PyTarget(identifier: String, locationString: List[String] = List(), sliceExpr: Option[PyExpression] = None, typeHint: Option[PythonType[?]] = None) extends PyAtomar with NamedElement {
    override def getChildren(): Seq[GenericAST] = sliceExpr.toList

    override def name: String = (locationString :+ identifier).mkString(".") // without slice for now
  }


  /** A literal expression with an explicit Python type, e.g. `123`, `"Ada"`, `[1, 2]`, or `{"name": "Ada"}`. */
  case class PythonLiteral[ScalaType](val literalValue: String, literalType: PythonType[ScalaType]) extends GenericAstLiteral[ScalaType, PythonType[ScalaType], PythonLiteral[ScalaType]] with PyAtomar {

  }


  def main(args: Array[String]): Unit = {
    println("hai!")
    val example1: String = {
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
        |turtle.forward(100   + 50 )
        |turtle.left(120)
        |turtle.forward(x - int(y) )
        |turtle.right(50)
        |
        |""".stripMargin
    }

    val example2: String =
      """
        |turtle.forward( int(y) + 1 )
        |func( int (2) )
        |
        |""".stripMargin

    val res = PythonAstParserSimple.parse(example2)

    println(res)

    println(res.right.get.statements.mkString("\n"))

  }

}
