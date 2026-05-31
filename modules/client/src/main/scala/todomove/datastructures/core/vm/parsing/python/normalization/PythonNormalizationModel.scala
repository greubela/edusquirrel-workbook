package todomove.datastructures.core.vm.parsing.python.normalization

object PythonNormalizationModel {
  final case class RawLine(indent: Int, text: String)
  final case class Line(level: Int, text: String)

  sealed trait Statement
  final case class SimpleStatement(text: String) extends Statement
  final case class CompoundStatement(header: String, body: List[Statement]) extends Statement
  final case class IfStatement(condition: String, thenBranch: List[Statement], elseBranch: Option[List[Statement]])
      extends Statement

  final case class ParsedStatementTree(statements: List[Statement], indentStep: Int)
}
