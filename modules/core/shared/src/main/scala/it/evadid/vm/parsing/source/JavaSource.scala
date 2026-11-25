package it.evadid.vm.parsing.source

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.vm.code.errors.ProgrammingConversionException
import it.evadid.vm.compilation.java.turtle.JavaTurtleInputLimits
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import it.evadid.vm.conversion.parsed.ParsedProgram
import it.evadid.vm.conversion.javaexpression.JavaToExpression

final class JavaSource private[source] (val source: String) extends ParsedSource[Either[ProgrammingConversionException, JavaProgramParsed]] {
  override def language: ProgrammingLanguage = AppLanguage.Java

  lazy val structured: JavaSourceStructured = JavaStructureStep(source, SourceTokens.lex(source, python = false))

  /** The grammar reads the masked source within the input limits that keep parsing bounded. */
  lazy val syntax: Either[SyntaxFailure, JavaSyntax] = {
    val lines = SourceLines(source)
    def failure(message: String, offset: Int) = SyntaxFailure(message, offset, lines.line(offset), lines.column(offset))
    JavaTurtleInputLimits.checkParserInput(source) match {
      case Left(diagnostic) => Left(failure(diagnostic.message, diagnostic.range.fold(0)(_.start)))
      case Right(_) => JavaParser.parse(structured.masked).fold(
        stopped => Left(failure(s"Java Parsing Error: ${stopped.trace().longAggregateMsg}", stopped.index)),
        sourceMap => Right(JavaSyntax(structured, sourceMap)))
    }
  }

  lazy val neutral: Either[SyntaxFailure, ParsedProgram] = syntax.map(JavaToExpression.neutral)

  /** The expression tree in `environment`; an unreadable source is a conversion failure at its position. */
  def parsed(environment: ProgrammingEnvironment = ProgrammingEnvironment.all): Either[ProgrammingConversionException, JavaProgramParsed] =
    inEnvironment(environment) {
      syntax.left.map(failure => ProgrammingConversionException.unparsable(
        "This Java source cannot be read as a complete program or fragment.", Some(failure.line), Some(failure.column)))
        .flatMap(tree => scala.util.Try {
          val program = neutral.toOption.get
          program -> JavaToExpression.convert(tree, program, environment)
        }.toEither.left.map {
          case conversion: ProgrammingConversionException => conversion
          case other => ProgrammingConversionException.unparsable(Option(other.getMessage).getOrElse(other.toString))
        }.map((neutral, expression) => JavaProgramParsed(tree, neutral, expression, tree.functions.map(function =>
          JavaFunctionParsed(function.structured, function.node, ParsedFunctions.find(expression.expression,
            Some(function.structured.className), function.structured.name, function.structured.parameters.size))))))
    }
}
