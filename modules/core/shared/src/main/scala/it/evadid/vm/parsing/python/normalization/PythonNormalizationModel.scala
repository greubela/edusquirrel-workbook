package it.evadid.vm.parsing.python.normalization

import upickle.default.*

object PythonNormalizationModel {
  final case class RawLine(indent: Int, text: String) derives ReadWriter
  final case class Line(level: Int, text: String) derives ReadWriter

  sealed trait Statement derives ReadWriter
  final case class SimpleStatement(text: String) extends Statement derives ReadWriter
  final case class CompoundStatement(header: String, body: List[Statement]) extends Statement derives ReadWriter
  final case class IfStatement(condition: String, thenBranch: List[Statement], elseBranch: Option[List[Statement]])
      extends Statement derives ReadWriter

  final case class ParsedStatementTree(statements: List[Statement], indentStep: Int) derives ReadWriter
}
