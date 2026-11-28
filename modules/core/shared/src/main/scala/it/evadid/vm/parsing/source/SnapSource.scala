package it.evadid.vm.parsing.source

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.vm.code.errors.ProgrammingConversionException
import it.evadid.vm.conversion.snapexpression.{SnapExpressionSupport, SnapToExpression}
import it.evadid.vm.conversion.turtlestitchexpression.{TurtleStitchModelMapper, TurtleStitchProgramModel, TurtleStitchToBeExpressionParser}
import it.evadid.workbook.interactions.programming.state.snap.SnapScalaModel
import scala.util.Try

/** Snap's steps: lexical tokens, the XML document, the TurtleStitch project, and the expression tree. */
final class SnapSource private[source] (val source: String) extends ParsedSource {
  override def language: AppLanguage.ProgrammingLanguage = AppLanguage.SnapLanguage

  lazy val structured: SnapSourceStructured = SnapSourceStructured.lex(source)

  private lazy val nodes: Either[SyntaxFailure, Vector[SnapScalaModel.Node]] =
    if source.length > SnapSource.maxCharacters then Left(SyntaxFailure("Snap XML exceeds the 64 MiB input limit.", 0, 1, 1))
    else SnapDocumentStep(structured)

  /** The document; it requires exactly one root element. */
  lazy val syntax: Either[SyntaxFailure, SnapScalaModel.Document] = nodes.flatMap(values =>
    Try(SnapScalaModel.Document(values)).toEither.left.map(error =>
      SyntaxFailure(Option(error.getMessage).getOrElse("Invalid Snap document."), 0, 1, 1)))

  // Snap copies a sequence of blocks without a script wrapper.
  private lazy val documentForProject: Either[SyntaxFailure, SnapScalaModel.Document] = {
    val text = source.trim
    if (text.startsWith("<block") && !text.startsWith("<blocks")) || text.startsWith("<custom-block") then
      nodes.map(values => SnapScalaModel.Document(Vector(
        SnapScalaModel.classify(SnapScalaModel.Element("script", children = values)))))
    else syntax
  }

  private lazy val readable: Either[ProgrammingConversionException, Option[SnapScalaModel.Document]] =
    if source.trim.isEmpty then Right(None) else documentForProject.map(Some(_)).left.map(_.unparsable())

  lazy val project: Either[ProgrammingConversionException, TurtleStitchProgramModel.Project] =
    readable.map(_.fold(TurtleStitchModelMapper.EmptyProject)(TurtleStitchModelMapper.toProject))

  /** Blocks without a shared (Python) form; their presence makes [[parsed]] fail. */
  lazy val unsupportedSelectors: Either[ProgrammingConversionException, List[String]] =
    for { document <- readable; loaded <- project } yield document.fold(Nil)(SnapExpressionSupport.unsupportedSnapSelectors(_, loaded))

  lazy val parsed: Either[ProgrammingConversionException, TurtleStitchToBeExpressionParser.ParseWithLayout] =
    for {
      loaded <- project
      unsupported <- unsupportedSelectors
      result <- Try(SnapToExpression.convert(loaded, unsupported)).toEither.left.map {
        case conversion: ProgrammingConversionException => conversion
        case other => ProgrammingConversionException.unparsable(Option(other.getMessage).getOrElse(other.toString))
      }
    } yield result
}

object SnapSource {
  private val maxCharacters = 64 * 1024 * 1024
}
