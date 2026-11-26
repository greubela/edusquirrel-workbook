package it.evadid.vm.parsing.source

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.vm.code.errors.ProgrammingConversionException
import it.evadid.vm.compilation.java.turtle.JavaTurtleInputLimits
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import it.evadid.vm.conversion.parsed.ParsedProgram
import it.evadid.vm.conversion.javaexpression.JavaToExpression

final class JavaSource private[source] (val source: String) extends ParsedSource {
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

  lazy val neutral: Either[ProgrammingConversionException, ParsedProgram] = syntax.left.map(_.unparsable(JavaSource.unreadable))
    .flatMap(tree => scala.util.Try(JavaToExpression.neutral(tree)).toEither.left.map {
      case error: ProgrammingConversionException => error
      case other => ProgrammingConversionException.unparsable(Option(other.getMessage).getOrElse(other.toString))
    })

  /** The syntax as a conversion result: an unreadable source fails at its position. */
  lazy val readable: Either[ProgrammingConversionException, JavaSyntax] =
    syntax.left.map(_.unparsable(JavaSource.unreadable)).flatMap(_.readable)

  /** The expression tree in `environment`. */
  def parsed(environment: ProgrammingEnvironment = ProgrammingEnvironment.all): Either[ProgrammingConversionException, JavaProgramParsed] =
    byEnvironment(environment)

  private val byEnvironment = PerEnvironment[Either[ProgrammingConversionException, JavaProgramParsed]](environment =>
    for {
      tree <- syntax.left.map(_.unparsable(JavaSource.unreadable))
      program <- neutral
      bound <- scala.util.Try(JavaToExpression.convert(tree, program, environment)).toEither.left.map {
        case conversion: ProgrammingConversionException => conversion
        case other => ProgrammingConversionException.unparsable(Option(other.getMessage).getOrElse(other.toString))
      }
    } yield {
      val functions = tree.functions.map { function =>
        val span = SourceLines(source).span(function.node.range.start, function.node.range.end)
        JavaFunctionParsed(function.structured, function.node, bound._2.sourceFunctions.get(span))
      }
      JavaProgramParsed(tree, program, bound._1, functions, bound._2.sourceOrigins)
    })
}

object JavaSource {
  private val unreadable = "This Java source cannot be read as a complete program or fragment."
}
