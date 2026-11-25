package it.evadid.vm.parsing.source

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.vm.code.errors.ProgrammingConversionException
import it.evadid.vm.parsing.python.clean.PythonAstParserSimple
import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import it.evadid.vm.conversion.parsed.ParsedProgram
import it.evadid.vm.conversion.pythonexpression.PythonToExpression

final class PythonSource private[source] (val source: String) extends ParsedSource {
  override def language: ProgrammingLanguage = AppLanguage.Python

  lazy val structured: PythonSourceStructured = PythonStructureStep(source, SourceTokens.lex(source, python = true))

  lazy val syntax: Either[SyntaxFailure, PythonSyntax] =
    PythonAstParserSimple.parse(structured.masked).fold(error => {
      val (line, column) = error match {
        case located: ProgrammingConversionException => (located.error.line.getOrElse(1), located.error.column.getOrElse(1))
        case _ => (1, 1)
      }
      Left(SyntaxFailure(Option(error.getMessage).getOrElse(error.toString), SourceLines(source).offset(line, column), line, column))
    }, program => Right(PythonSyntax(structured, program)))

  /** An unreadable source is a program of one unparsable statement. */
  lazy val neutral: ParsedProgram = PythonToExpression.neutral(structured, syntax)

  /** The program in `environment`. */
  def parsed(environment: ProgrammingEnvironment = ProgrammingEnvironment.all): PythonProgramParsed = byEnvironment(environment)

  private val byEnvironment = PerEnvironment[PythonProgramParsed] { environment =>
    val (program, details) = PythonToExpression.convert(neutral, environment)
    PythonProgramParsed(structured, syntax, neutral, program, details, structured.functions.map(function =>
      PythonFunctionParsed(function, syntax.toOption.flatMap(_.functionOrigins.get(function.span)).flatMap(details.sourceFunctions.get))))
  }
}
