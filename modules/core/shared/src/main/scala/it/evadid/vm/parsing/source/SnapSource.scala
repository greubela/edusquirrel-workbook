package it.evadid.vm.parsing.source

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.vm.code.errors.ProgrammingConversionException
import it.evadid.vm.conversion.snapexpression.{SnapExpressionSupport, SnapToExpression}
import it.evadid.vm.conversion.turtlestitchexpression.{TurtleStitchModelMapper, TurtleStitchProgramModel, TurtleStitchToBeExpressionParser}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel

/** Snap's source stages; a typed document can enter the converters directly. */
final class SnapSource private[source] (val source: String) extends ParsedSource[TurtleStitchToBeExpressionParser.ParseWithLayout] {
  override def language: AppLanguage.ProgrammingLanguage = AppLanguage.SnapLanguage

  lazy val structured: SnapSourceStructured = SnapSourceStructured.lex(source)
  private lazy val nodes: Either[String, Vector[SnapScalaModel.Node]] =
    if source == null || source.length > 64 * 1024 * 1024 then Left("Snap XML exceeds the 64 MiB input limit.")
    else SnapDocumentStep(structured)

  lazy val syntax: Either[String, SnapScalaModel.Document] = nodes.flatMap(values =>
    scala.util.Try(SnapScalaModel.Document(values)).toEither.left.map(_.getMessage))

  // Snap copies a sequence of blocks without a script wrapper.
  private lazy val documentForProject: SnapScalaModel.Document = {
    val text = source.trim
    val document = if (text.startsWith("<block") && !text.startsWith("<blocks")) || text.startsWith("<custom-block") then
      nodes.map(values => SnapScalaModel.Document(Vector(
        SnapScalaModel.classify(SnapScalaModel.Element("script", children = values)))))
    else syntax
    document.fold(message => throw ProgrammingConversionException.unparsable(message), identity)
  }

  lazy val project: TurtleStitchProgramModel.Project =
    if source.trim.isEmpty then TurtleStitchModelMapper.EmptyProject else TurtleStitchModelMapper.toProject(documentForProject)

  lazy val unsupportedSelectors: List[String] =
    if source.trim.isEmpty then Nil else SnapExpressionSupport.unsupportedSnapSelectors(documentForProject, project)

  lazy val parsed: TurtleStitchToBeExpressionParser.ParseWithLayout =
    SnapToExpression.convert(project, unsupportedSelectors)
}
