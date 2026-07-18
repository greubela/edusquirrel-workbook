package it.evadid.vm.parsing.python.normalization

import PythonNormalizationModel._
import scala.collection.mutable

object PythonNormalizationRenderer {

  def renderNormalizedOutput(statements: List[Statement], indentStep: Int): String =
    renderStatements(statements, 0, indentStep).mkString("\n")

  private def renderStatements(statements: List[Statement], indentLevel: Int, indentStep: Int): List[String] = {
    val rendered = mutable.ListBuffer[String]()
    statements.foreach {
      case SimpleStatement(text) =>
        rendered += formatLine(indentLevel, indentStep, text)
      case CompoundStatement(header, body) =>
        rendered += formatLine(indentLevel, indentStep, header)
        rendered ++= renderStatements(body, indentLevel + 1, indentStep)
      case IfStatement(condition, thenBranch, elseBranch) =>
        rendered += formatLine(indentLevel, indentStep, s"if $condition:")
        rendered ++= renderStatements(ensureNonEmpty(thenBranch), indentLevel + 1, indentStep)
        elseBranch.foreach { branch =>
          rendered += formatLine(indentLevel, indentStep, "else:")
          rendered ++= renderStatements(ensureNonEmpty(branch), indentLevel + 1, indentStep)
        }
    }
    rendered.toList
  }

  private def ensureNonEmpty(body: List[Statement]): List[Statement] =
    if (body.nonEmpty) body else List(SimpleStatement("pass"))

  private def formatLine(indentLevel: Int, indentStep: Int, text: String): String = {
    val indent = " " * (indentLevel * indentStep)
    indent + text
  }
}
