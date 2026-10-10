package it.evadid.vm.parsing.python.clean.model

import it.evadid.vm.parsing.generic.abstractions.GenericAST
import it.evadid.vm.parsing.generic.abstractions.GenericAST.{GenericAstLiteral, NamedElement}
import it.evadid.vm.parsing.python.clean.PythonAstParserSimple

sealed trait PyAST extends GenericAST

object PyAST {
  private case class StoredAst(kind: String, payload: ujson.Value) derives upickle.default.ReadWriter
  private given upickle.default.ReadWriter[PythonLiteral[Any]] = upickle.default.macroRW

  given upickle.default.ReadWriter[PyAST] = upickle.default.readwriter[StoredAst].bimap(storeAst, restoreAst)
  given upickle.default.ReadWriter[PyStatement] = narrowedAst {
    case value: PyStatement => Some(value)
    case _ => None
  }
  given upickle.default.ReadWriter[PyExpression] = narrowedAst {
    case value: PyExpression => Some(value)
    case _ => None
  }
  given upickle.default.ReadWriter[PyAtomar] = narrowedAst {
    case value: PyAtomar => Some(value)
    case _ => None
  }

  private def narrowedAst[T <: PyAST](accept: PyAST => Option[T]): upickle.default.ReadWriter[T] =
    upickle.default.readwriter[PyAST].bimap(value => value, value => accept(value)
      .getOrElse(throw new IllegalArgumentException("Unexpected Python AST node type")))

  private def storeAst(value: PyAST): StoredAst = value match {
    case node: PyProgram => StoredAst("PyProgram", upickle.default.writeJs(node))
    case node: StatementWithLineNumber => StoredAst("StatementWithLineNumber", upickle.default.writeJs(node))
    case node: PyExecutionBlock => StoredAst("PyExecutionBlock", upickle.default.writeJs(node))
    case node: PyUnparsableStatement => StoredAst("PyUnparsableStatement", upickle.default.writeJs(node))
    case node: PyPlainImportStatement => StoredAst("PyPlainImportStatement", upickle.default.writeJs(node))
    case node: PyImportFromStatement => StoredAst("PyImportFromStatement", upickle.default.writeJs(node))
    case node: PyRaiseStatement => StoredAst("PyRaiseStatement", upickle.default.writeJs(node))
    case node: PyReturnStatement => StoredAst("PyReturnStatement", upickle.default.writeJs(node))
    case node: PyIfStatement => StoredAst("PyIfStatement", upickle.default.writeJs(node))
    case node: PyWhileStatement => StoredAst("PyWhileStatement", upickle.default.writeJs(node))
    case node: PyForStatement => StoredAst("PyForStatement", upickle.default.writeJs(node))
    case node: PyTryStatement => StoredAst("PyTryStatement", upickle.default.writeJs(node))
    case node: PyExceptClauseBasic => StoredAst("PyExceptClauseBasic", upickle.default.writeJs(node))
    case node: PyExceptClauseStar => StoredAst("PyExceptClauseStar", upickle.default.writeJs(node))
    case node: PyExceptClauseFinally => StoredAst("PyExceptClauseFinally", upickle.default.writeJs(node))
    case node: NamedExpression => StoredAst("NamedExpression", upickle.default.writeJs(node))
    case node: PyTypeDefExpression => StoredAst("PyTypeDefExpression", upickle.default.writeJs(node))
    case node: PySimpleAssignment => StoredAst("PySimpleAssignment", upickle.default.writeJs(node))
    case node: PyAugAssignment => StoredAst("PyAugAssignment", upickle.default.writeJs(node))
    case node: PyFunctionCall => StoredAst("PyFunctionCall", upickle.default.writeJs(node))
    case node: PyAttributeAccess => StoredAst("PyAttributeAccess", upickle.default.writeJs(node))
    case node: PySubscript => StoredAst("PySubscript", upickle.default.writeJs(node))
    case node: PyCallExpression => StoredAst("PyCallExpression", upickle.default.writeJs(node))
    case node: PyFunctionDef => StoredAst("PyFunctionDef", upickle.default.writeJs(node))
    case node: PyClassDef => StoredAst("PyClassDef", upickle.default.writeJs(node))
    case node: PyOperationBinary => StoredAst("PyOperationBinary", upickle.default.writeJs(node))
    case node: PyOperationUnary => StoredAst("PyOperationUnary", upickle.default.writeJs(node))
    case node: PyTarget => StoredAst("PyTarget", upickle.default.writeJs(node))
    case node: PythonLiteral[?] => StoredAst("PythonLiteral", upickle.default.writeJs(node.asInstanceOf[PythonLiteral[Any]]))
    case node: PythonType[?] => StoredAst("PythonType", upickle.default.writeJs(node))
    case PyPassStatement => StoredAst("PyPassStatement", ujson.Null)
    case PyBreakStatement => StoredAst("PyBreakStatement", ujson.Null)
    case PyContinueStatement => StoredAst("PyContinueStatement", ujson.Null)
    case PyEmptyStatement => StoredAst("PyEmptyStatement", ujson.Null)
    case other => throw new IllegalArgumentException(s"Unsupported Python AST node: ${other.getClass.getName}")
  }

  private def restoreAst(value: StoredAst): PyAST = value.kind match {
    case "PyProgram" => upickle.default.read[PyProgram](value.payload)
    case "StatementWithLineNumber" => upickle.default.read[StatementWithLineNumber](value.payload)
    case "PyExecutionBlock" => upickle.default.read[PyExecutionBlock](value.payload)
    case "PyUnparsableStatement" => upickle.default.read[PyUnparsableStatement](value.payload)
    case "PyPlainImportStatement" => upickle.default.read[PyPlainImportStatement](value.payload)
    case "PyImportFromStatement" => upickle.default.read[PyImportFromStatement](value.payload)
    case "PyRaiseStatement" => upickle.default.read[PyRaiseStatement](value.payload)
    case "PyReturnStatement" => upickle.default.read[PyReturnStatement](value.payload)
    case "PyIfStatement" => upickle.default.read[PyIfStatement](value.payload)
    case "PyWhileStatement" => upickle.default.read[PyWhileStatement](value.payload)
    case "PyForStatement" => upickle.default.read[PyForStatement](value.payload)
    case "PyTryStatement" => upickle.default.read[PyTryStatement](value.payload)
    case "PyExceptClauseBasic" => upickle.default.read[PyExceptClauseBasic](value.payload)
    case "PyExceptClauseStar" => upickle.default.read[PyExceptClauseStar](value.payload)
    case "PyExceptClauseFinally" => upickle.default.read[PyExceptClauseFinally](value.payload)
    case "NamedExpression" => upickle.default.read[NamedExpression](value.payload)
    case "PyTypeDefExpression" => upickle.default.read[PyTypeDefExpression](value.payload)
    case "PySimpleAssignment" => upickle.default.read[PySimpleAssignment](value.payload)
    case "PyAugAssignment" => upickle.default.read[PyAugAssignment](value.payload)
    case "PyFunctionCall" => upickle.default.read[PyFunctionCall](value.payload)
    case "PyAttributeAccess" => upickle.default.read[PyAttributeAccess](value.payload)
    case "PySubscript" => upickle.default.read[PySubscript](value.payload)
    case "PyCallExpression" => upickle.default.read[PyCallExpression](value.payload)
    case "PyFunctionDef" => upickle.default.read[PyFunctionDef](value.payload)
    case "PyClassDef" => upickle.default.read[PyClassDef](value.payload)
    case "PyOperationBinary" => upickle.default.read[PyOperationBinary](value.payload)
    case "PyOperationUnary" => upickle.default.read[PyOperationUnary](value.payload)
    case "PyTarget" => upickle.default.read[PyTarget](value.payload)
    case "PythonLiteral" => upickle.default.read[PythonLiteral[Any]](value.payload)
    case "PythonType" => upickle.default.read[PythonType[Any]](value.payload)
    case "PyPassStatement" => PyPassStatement
    case "PyBreakStatement" => PyBreakStatement
    case "PyContinueStatement" => PyContinueStatement
    case "PyEmptyStatement" => PyEmptyStatement
    case other => throw new IllegalArgumentException(s"Unknown Python AST node: $other")
  }


  /** Parser helper state, e.g. `currentLevel = 4` while parsing an indented block. */
  case class IndentState(var currentLevel: Int = 0) derives upickle.default.ReadWriter

  // ==========================================
  // PROGRAM
  // ==========================================
  /** A complete Python module, e.g. `x = 1\ny = 2`. */
  case class PyProgram(statements: Seq[StatementWithLineNumber]) extends PyAST derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = statements
  }

  /** A parsed statement annotated with its source line, e.g. line 0 for `x = 1`. */
  case class StatementWithLineNumber(statement: PyStatement, lineNumber: Int) extends PyAST derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = Seq(statement)
  }

  /** A block of statements, e.g. the indented body after `if ready:`. */
  case class PyExecutionBlock(statements: Seq[PyStatement]) extends PyAST derives upickle.default.ReadWriter {
    def withAdded(other: Seq[PyStatement]): PyExecutionBlock = PyExecutionBlock(statements ++ other)

    override def getChildren(): Seq[GenericAST] = statements
  }

  // ==========================================
  // STATEMENTS
  // ==========================================
  sealed trait PyStatement extends PyAST

  /** A fallback for unsupported syntax, e.g. `match value:` before match parsing exists. */
  case class PyUnparsableStatement(str: String) extends PyStatement derives upickle.default.ReadWriter

  val passBlock: PyExecutionBlock = PyExecutionBlock(List())

  /** The no-op statement `pass`. */
  case object PyPassStatement extends PyStatement

  /** A loop exit statement, e.g. `break`. */
  case object PyBreakStatement extends PyStatement

  /** A loop continuation statement, e.g. `continue`. */
  case object PyContinueStatement extends PyStatement

  /** A blank source line with no semantic statement. */
  case object PyEmptyStatement extends PyStatement



  sealed trait PythonImportStatement extends PyStatement with NamedElement derives upickle.default.ReadWriter {
    def moduleName: String
    override def name: String = moduleName
  }

  /** A direct import statement, e.g. `import turtle`. */
  case class PyPlainImportStatement(moduleName: String) extends PythonImportStatement derives upickle.default.ReadWriter {
  }

  /** A from-import statement, e.g. `from math import sin` or `from math import *`. */
  case class PyImportFromStatement(moduleName: String, imports: List[PyTarget], importAll: Boolean) extends PythonImportStatement derives upickle.default.ReadWriter {
    override def name: String = moduleName

    override def getChildren(): Seq[GenericAST] = imports
  }

  /** A raise statement, e.g. `raise ValueError()` or `raise err from cause`. */
  case class PyRaiseStatement(raiseDirectly: Option[PyExpression], raiseFrom: Option[PyExpression]) extends PyStatement derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = raiseDirectly.toList ++ raiseFrom.toList
  }

  /** A return statement, e.g. `return total` or bare `return`. */
  case class PyReturnStatement(expr: Option[PyExpression]) extends PyStatement derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = expr.toList
  }

  // Basic Control Structures
  /** An if/elif/else statement, e.g. `if x > 0: ... else: ...`. */
  case class PyIfStatement(condition: PyExpression, thenBlock: PyExecutionBlock, elseBlock: PyExecutionBlock) extends PyStatement derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = List(condition, thenBlock, elseBlock)
  }

  /** A while loop, e.g. `while running: ...`. */
  case class PyWhileStatement(condition: PyExpression, bodyBlock: PyExecutionBlock, elseBlock: Option[PyExecutionBlock]) extends PyStatement derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = List(condition, bodyBlock) ++ elseBlock.toList
  }

  /** A for loop, e.g. `for item in items: ...` or `async for item in stream: ...`. */
  case class PyForStatement(elementExpression: PyExpression, inExpression: PyExpression, bodyBlock: PyExecutionBlock, elseBlock: Option[PyExecutionBlock], isAsync: Boolean) extends PyStatement derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = List(elementExpression, inExpression, bodyBlock) ++ elseBlock.toList
  }

  // Exception Handling
  /** A try statement with handlers, e.g. `try: ... except ValueError: ...`. */
  case class PyTryStatement(body: PyExecutionBlock, exceptStatements: List[PyExceptClause], elseBlock: Option[PyExecutionBlock]) extends PyStatement derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = List(body) ++ exceptStatements ++ elseBlock.toList
  }

  sealed trait PyExceptClause extends PyStatement derives upickle.default.ReadWriter {
    def body: PyExecutionBlock
  }

  /** A regular except clause, e.g. `except ValueError as err:`. */
  case class PyExceptClauseBasic(expression: Option[PyExpression], name: Option[String], body: PyExecutionBlock) extends PyExceptClause derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = expression.toList ++ List(body)
  }

  /** An exception-group handler, e.g. `except* ValueError as err:`. */
  case class PyExceptClauseStar(expression: PyExpression, name: Option[String], body: PyExecutionBlock) extends PyExceptClause derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = List(expression, body)
  }

  /** A finally clause, e.g. `finally: cleanup()`. */
  case class PyExceptClauseFinally(body: PyExecutionBlock) extends PyExceptClause derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = List(body)
  }

  // Expressions

  trait PyExpression extends PyStatement

  /** A walrus expression, e.g. `count := len(items)`. */
  case class NamedExpression(name: String, expression: PyExpression) extends PyExpression with NamedElement derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = List(expression)
  }




  /** A placeholder for type-definition expressions, e.g. future support for `type Alias = int`. */
  case class PyTypeDefExpression() extends PyExpression derives upickle.default.ReadWriter

  /** A simple assignment, e.g. `x = 1` or `user.name = "Ada"`. */
  sealed trait PyAssignment extends PyStatement derives upickle.default.ReadWriter {
    def target: PyTarget
    def value: Option[PyExpression] = None
  }

  /** A normal assignment statement, e.g. `total = subtotal + tax`. */
  case class PySimpleAssignment(target: PyTarget, override val value: Option[PyExpression]) extends PyAssignment derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = value.toList ++ List(target)
  }

  /** An augmented assignment statement, e.g. `total += 1`. */
  case class PyAugAssignment(target: PyTarget, augOperator: String, expression: PyExpression) extends PyAssignment derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = List(expression) ++ List(target)
  }

  // Targets


  // Defs

  /** A compatibility node for simple target calls, e.g. `print("hello")` or `math.sin(x)`. */
  case class PyFunctionCall(target: PyTarget, parameterValues: List[PyExpression]) extends PyExpression with NamedElement derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = parameterValues ++ List(target)

    override def name: String = target.name
  }

  /** Attribute access on any primary expression, e.g. `obj.field` in `obj.field + 1`. */
  case class PyAttributeAccess(receiver: PyExpression, name: String) extends PyExpression with NamedElement derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = List(receiver)
  }

  /** Subscript access on any primary expression, e.g. `items[0]` or `matrix[i, j]`. */
  case class PySubscript(receiver: PyExpression, indices: List[PyExpression]) extends PyExpression derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = receiver +: indices
  }

  /** A call whose callee is any expression, e.g. `func()(x)` or `obj.method(1)`. */
  case class PyCallExpression(callee: PyExpression, args: List[PyExpression]) extends PyExpression derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = callee +: args
  }

  /** A function definition, e.g. `def add(a, b): return a + b`. */
  case class PyFunctionDef(name: String, parameters: List[PyAssignment], block: PyExecutionBlock, isAsync: Boolean) extends PyStatement with NamedElement derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = parameters ++ List(block)
  }

  /** A class definition, e.g. `class Turtle:` or `class Shape(Base):`. */
  case class PyClassDef(name: String, parameters: List[PyAssignment], block: PyExecutionBlock, isAsync: Boolean) extends PyStatement with NamedElement derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = parameters ++ List(block)
  }

  /** A binary operation, e.g. `left + right` or `x and y`. */
  case class PyOperationBinary(left: PyExpression, op: String, right: PyExpression) extends PyExpression with NamedElement derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = List(left, right)

    override def name: String = op
  }

  /** A unary operation, e.g. `-x` or `not ready`. */
  case class PyOperationUnary(op: String, operand: PyExpression) extends PyExpression with NamedElement derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = List(operand)

    override def name: String = op
  }

  // Atomar

  sealed trait PyAtomar extends PyExpression


  /** An identifier-like target, e.g. `x`, `obj.field`, or `items[0]` in assignments. */
  case class PyTarget(identifier: String, locationString: List[String] = List(), sliceExpr: Option[PyExpression] = None, typeHint: Option[PythonType[?]] = None) extends PyAtomar with NamedElement derives upickle.default.ReadWriter {
    override def getChildren(): Seq[GenericAST] = sliceExpr.toList

    override def name: String = (locationString :+ identifier).mkString(".") // without slice for now
  }


  /** A literal expression with an explicit Python type, e.g. `123`, `"Ada"`, `[1, 2]`, or `{"name": "Ada"}`. */
  case class PythonLiteral[ScalaType](val literalValue: String, literalType: PythonType[ScalaType]) extends GenericAstLiteral[ScalaType, PythonType[ScalaType], PythonLiteral[ScalaType]] with PyAtomar derives upickle.default.ReadWriter {

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

    val res = PythonAstParserSimple.parse(example1)

    println(res)

    println(res.right.get.statements.mkString("\n"))

  }

}
