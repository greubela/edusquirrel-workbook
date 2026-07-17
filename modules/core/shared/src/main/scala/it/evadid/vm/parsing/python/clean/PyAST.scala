package it.evadid.vm.parsing.python.clean

import upickle.default.{ReadWriter, macroRW}

sealed trait PyAST

object PyAST {
  implicit val rw: ReadWriter[PyAST] = macroRW

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
  case class BinOp(left: PyAST, op: String, right: PyAST) extends PyAST
  case class UnaryOp(op: String, operand: PyAST) extends PyAST
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

  // Macro Engine Generation Schema mappings
  implicit val modRW: ReadWriter[Module] = macroRW
  implicit val funcRW: ReadWriter[FunctionDef] = macroRW
  implicit val clsRW: ReadWriter[ClassDef] = macroRW
  implicit val ifRW: ReadWriter[If] = macroRW
  implicit val whileRW: ReadWriter[While] = macroRW
  implicit val forRW: ReadWriter[For] = macroRW
  implicit val withRW: ReadWriter[With] = macroRW
  implicit val withItemRW: ReadWriter[WithItem] = macroRW
  implicit val tryRW: ReadWriter[Try] = macroRW
  implicit val handlerRW: ReadWriter[ExceptHandler] = macroRW
  implicit val matchRW: ReadWriter[MatchStatement] = macroRW
  implicit val caseRW: ReadWriter[MatchCase] = macroRW
  implicit val pParamRW: ReadWriter[PyParam] = macroRW
  implicit val aliasRW: ReadWriter[TypeAlias] = macroRW
  implicit val assignRW: ReadWriter[Assign] = macroRW
  implicit val nameRW: ReadWriter[Name] = macroRW
  implicit val constRW: ReadWriter[Constant] = macroRW
  implicit val binOpRW: ReadWriter[BinOp] = macroRW
  implicit val unOpRW: ReadWriter[UnaryOp] = macroRW
  implicit val boolOpRW: ReadWriter[BoolOp] = macroRW
  implicit val compRW: ReadWriter[Compare] = macroRW
  implicit val namedExprRW: ReadWriter[NamedExpr] = macroRW
  implicit val callRW: ReadWriter[Call] = macroRW
  implicit val kwArgRW: ReadWriter[KeywordArg] = macroRW
  implicit val attrRW: ReadWriter[Attribute] = macroRW
  implicit val subRW: ReadWriter[Subscript] = macroRW
  implicit val sliceRW: ReadWriter[Slice] = macroRW
  implicit val listRW: ReadWriter[PyList] = macroRW
  implicit val tupleRW: ReadWriter[PyTuple] = macroRW
  implicit val setRW: ReadWriter[PySet] = macroRW
  implicit val dictRW: ReadWriter[PyDict] = macroRW
  implicit val lCompRW: ReadWriter[ListComp] = macroRW
  implicit val dCompRW: ReadWriter[DictComp] = macroRW
  implicit val sCompRW: ReadWriter[SetComp] = macroRW
  implicit val genExpRW: ReadWriter[GeneratorExp] = macroRW
  implicit val compDefRW: ReadWriter[Comprehension] = macroRW
  implicit val retRW: ReadWriter[Return] = macroRW
  implicit val raiseRW: ReadWriter[Raise] = macroRW
  implicit val delRW: ReadWriter[Delete] = macroRW
  implicit val assertRW: ReadWriter[Assert] = macroRW
  implicit val exprRW: ReadWriter[ExprStmt] = macroRW
  implicit val passRW: ReadWriter[Pass] = macroRW
  implicit val breakRW: ReadWriter[Break] = macroRW
  implicit val contRW: ReadWriter[Continue] = macroRW
  implicit val impRW: ReadWriter[Import] = macroRW
  implicit val impFromRW: ReadWriter[ImportFrom] = macroRW
}
