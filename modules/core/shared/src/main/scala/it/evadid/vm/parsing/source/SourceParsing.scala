package it.evadid.vm.parsing.source

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.vm.code.errors.ProgrammingConversionException
import it.evadid.vm.compilation.java.turtle.JavaTurtleInputLimits
import it.evadid.vm.parsing.java.clean.JavaParser
import it.evadid.vm.parsing.python.clean.PythonAstParserSimple

import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import it.evadid.vm.conversion.parsed.ParsedProgram
import it.evadid.vm.conversion.javaexpression.JavaToExpression
import it.evadid.vm.conversion.pythonexpression.PythonToExpression

import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable.VectorMap

/** The single entry to parsing Java and Python source. Parsing runs in steps, each computed on first use and kept
  * with the returned source:
  *
  *  1. `structured` — a fastparse lexer reads any text, unfinished or invalid code included, into declarations
  *     ([[JavaSourceStructured]], [[PythonSourceStructured]]) with spans, lines and library definitions masked.
  *  1. `syntax` — the language's fastparse grammar reads the masked source into a syntax tree, or a [[SyntaxFailure]].
  *  1. `neutral` — the language-neutral program ([[it.evadid.vm.conversion.parsed.ParsedProgram]]) of the syntax tree.
  *  1. `parsed(environment)` — the shared expression tree bound in `environment`, with each structured function and
  *     its definition.
  *
  * Nothing else invokes a lexer or grammar; consumers read the steps they need. Recently parsed sources are reused.
  */
object SourceParsing {
  /** A language this pipeline reads, typed by the source it produces. */
  sealed abstract class SourceLanguage[S <: ParsedSource](val language: ProgrammingLanguage, private[source] val create: String => S)
  object SourceLanguage {
    case object Java extends SourceLanguage[JavaSource](AppLanguage.Java, JavaSource(_))
    case object Python extends SourceLanguage[PythonSource](AppLanguage.Python, PythonSource(_))
  }

  private val capacity = 32
  private val recent = AtomicReference(VectorMap.empty[(SourceLanguage[?], String), ParsedSource])

  def parse[S <: ParsedSource](source: String, language: SourceLanguage[S]): S = {
    val key = (language, source)
    recent.get().get(key).getOrElse(recent.synchronized {
      recent.get().getOrElse(key, {
        val created = language.create(source)
        recent.set((recent.get() + (key -> created)).takeRight(capacity))
        created
      })
    }).asInstanceOf[S]
  }
}

/** One source and the pipeline's steps for it ([[SourceParsing]]). */
sealed trait ParsedSource {
  def source: String
  def language: ProgrammingLanguage

  private val byEnvironment = AtomicReference(Map.empty[ProgrammingEnvironment, Any])

  /** A step-3 result per environment, computed once. */
  protected def inEnvironment[R](environment: ProgrammingEnvironment)(create: => R): R =
    byEnvironment.get().get(environment).getOrElse(byEnvironment.synchronized {
      byEnvironment.get().getOrElse(environment, {
        val created = create
        byEnvironment.set(byEnvironment.get().updated(environment, created))
        created
      })
    }).asInstanceOf[R]
}

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
  def parsed(environment: ProgrammingEnvironment = ProgrammingEnvironment.all): PythonProgramParsed =
    inEnvironment(environment) {
      val (program, details) = PythonToExpression.convert(neutral, environment)
      PythonProgramParsed(structured, syntax, neutral, program, details, structured.functions.map(function =>
        PythonFunctionParsed(function, ParsedFunctions.find(program.fullProgram, function.owner, function.name,
          function.parameters.count(parameter => parameter.name != "self")))))
    }
}
