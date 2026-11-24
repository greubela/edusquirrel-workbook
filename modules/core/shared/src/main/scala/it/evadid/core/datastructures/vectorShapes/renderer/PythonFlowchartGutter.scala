package it.evadid.core.datastructures.vectorShapes.renderer

import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported}
import it.evadid.vm.code.tree.BeExpressionReference
import it.evadid.vm.conversion.pythonexpression.PythonToExpression
import it.evadid.vm.conversion.structured.SourceStructure
import it.evadid.vm.parsing.python.clean.PythonAstParserSimple
import it.evadid.vm.parsing.python.clean.model.PyAST.*
import it.evadid.vm.types.{BeChildInfo, BeChildRole, BeScope}

import scala.util.control.NonFatal

/** Places Python statements on source lines for a [[FlowchartGutter]] chart. The Python conversion decides
  * support; the fastparse syntax tree supplies statements and [[SourceStructure.pythonCodeLines]] their physical rows.
  */
object PythonFlowchartGutter {
  import FlowchartGutter.{Chart, Positioned, build, empty}

  def fromPython(source: String): Chart = parseChart(source).getOrElse(empty)

  /** Parses exactly `source`. `None` means the program is incomplete or unsupported. */
  private def parseChart(source: String): Option[Chart] = try {
    val expression = PythonToExpression.program(source).fullProgram
    val expressions = expression.subtree
    if (expressions.exists {
      case _: BeExpressionUnparsable | _: BeExpressionUnsupported => true
      case _ => false
    }) None
    else {
      val program = PythonAstParserSimple.parse(source).fold(error => throw error, identity)
      // Statements consume code rows in order; comment-only and blank rows stay without nodes.
      val rows = SourceStructure.pythonCodeLines(normalized(source))
      def row(cursor: Int): SourceStructure.CodeLine = rows.lift(cursor).getOrElse(throw IllegalArgumentException("Cannot align statement to source"))
      def startsWith(cursor: Int, keyword: String): Boolean = rows.lift(cursor).exists(next => keywordOf(next.code) == keyword)
      def body(statements: Seq[PyStatement], depth: Int, cursor: Int): (List[Positioned], Int) =
        statements.foldLeft((List.empty[Positioned], cursor)) { case ((done, current), statement) =>
          val (items, next) = position(statement, depth, current)
          (done ++ items, next)
        }
      def position(statement: PyStatement, depth: Int, cursor: Int): (List[Positioned], Int) = statement match {
        case PyEmptyStatement | _: PyCommentStatement => (Nil, cursor)
        case PyBreakStatement | PyContinueStatement | _: PyTryStatement =>
          throw IllegalArgumentException("Unsupported control-flow statement")
        case PyIfStatement(_, yes, no) =>
          val (thenBody, afterThen) = body(yes.statements, depth + 1, cursor + 1)
          // An elif is the nested decision itself; only a physical else consumes a row.
          val (elseLine, otherwise, next) =
            if startsWith(afterThen, "elif") then {
              val (nested, after) = body(no.statements, depth, afterThen)
              (None, nested, after)
            } else if startsWith(afterThen, "else") then {
              val (elseBody, after) = body(no.statements, depth + 1, afterThen + 1)
              (Some(row(afterThen).row), elseBody, after)
            } else (None, Nil, afterThen)
          (List(Positioned(row(cursor).row, row(cursor).code, depth, thenBody, otherwise, "decision", elseLine)), next)
        case loop @ (_: PyWhileStatement | _: PyForStatement) =>
          val statements = loop match {
            case value: PyWhileStatement => value.bodyBlock.statements
            case value: PyForStatement => value.bodyBlock.statements
          }
          val (items, next) = body(statements, depth + 1, cursor + 1)
          (List(Positioned(row(cursor).row, row(cursor).code, depth, items, kind = "loop")), next)
        case definition: PyFunctionDef =>
          val header = cursor + definition.decorators.size
          val (items, next) = body(definition.block.statements, depth + 1, header + 1)
          (List(Positioned(row(header).row, row(header).code, depth, items, kind = "function")), next)
        case definition: PyClassDef =>
          val (items, next) = body(definition.block.statements, depth + 1, cursor + 1)
          (List(Positioned(row(cursor).row, row(cursor).code, depth, items, kind = "class")), next)
        case _ => (List(Positioned(row(cursor).row, row(cursor).code, depth)), cursor + 1)
      }
      val (positioned, consumed) = body(program.statements.map(_.statement), 0, 0)
      require(consumed == rows.size, "Cannot align all source statements")
      Some(build(positioned))
    }
  } catch {
    case NonFatal(_) => None
  }

  private def normalized(source: String): String = PythonAstParserSimple.normalizeLineEndingsAndDetab(source)

  private def keywordOf(code: String): String = code.trim.takeWhile(ch => ch.isLetterOrDigit || ch == '_')
}
