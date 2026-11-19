package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.conversion.expressionjava.ExpressionToJava
import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import it.evadid.vm.conversion.expressionsnap.ExpressionToSnap
import it.evadid.vm.conversion.javaexpression.JavaToExpression
import it.evadid.vm.conversion.pythonexpression.PythonToExpression
import it.evadid.vm.conversion.snapexpression.SnapToExpression
import it.evadid.vm.conversion.snapmodelxml.SnapModelToXml.*
import it.evadid.vm.conversion.snapxmlmodel.SnapScalaXmlCodec
import it.evadid.workbook.elements.interactionElements.programming.state.*

/** Owned by one immutable state. Views and graders share its parsing and projections. */
final class ProgrammingStateDerivations private[evadid](source: ProgrammingState) {
  private lazy val preparedSource = source match {
    case ProgrammingStateRaw(Java, code) => javaFrontend.preparedSource
    case value: ProgrammingStateJavaStructured => javaFrontend.preparedSource
    case ProgrammingStateRaw(Python, code) => it.evadid.vm.conversion.structured.PreparedProgramSource(code, Python)
    case value: ProgrammingStatePythonStructured => it.evadid.vm.conversion.structured.PreparedProgramSource(value.toString, Python)
    case _ => it.evadid.vm.conversion.structured.PreparedProgramSource("", Seq.empty)
  }
  private lazy val javaFrontend = JavaToExpression(source match {
    case ProgrammingStateRaw(Java, code) => code
    case value: ProgrammingStateJavaStructured => value.toString
    case _ => java.code
  })

  lazy val expression: ProgrammingStateBeExpression = source match {
    case value: ProgrammingStateBeExpression => value
    case ProgrammingStateRaw(Java, _) | _: ProgrammingStateJavaStructured => javaFrontend.convert
    case ProgrammingStateRaw(Python, code) => ProgrammingStateBeExpression(PythonToExpression.program(preparedSource.code).fullProgram)
    case value: ProgrammingStatePythonStructured => ProgrammingStateBeExpression(PythonToExpression.program(preparedSource.code).fullProgram)
    case ProgrammingStateRaw(SnapLanguage, xml) => ProgrammingStateBeExpression(SnapToExpression.parse(xml))
    case ProgrammingStateSnapScalaModel(model) => ProgrammingStateBeExpression(SnapToExpression.parse(model.toXml))
    case ProgrammingStateRaw(language, _) => throw UnsupportedOperationException(s"Parsing $language source into expressions is not supported.")
  }
  lazy val python: ProgrammingStateRaw = source match {
    case value @ ProgrammingStateRaw(Python, _) => value
    case value: ProgrammingStatePythonStructured => ProgrammingStateRaw(Python, value.toString)
    case _ => ExpressionToPython.convert(expression.expression)
  }
  lazy val java: ProgrammingStateRaw = source match {
    case value @ ProgrammingStateRaw(Java, _) => value
    case value: ProgrammingStateJavaStructured => ProgrammingStateRaw(Java, value.toString)
    case _ => ExpressionToJava.convert(expression.expression)
  }
  lazy val snap: ProgrammingStateRaw = source match {
    case value @ ProgrammingStateRaw(SnapLanguage, xml) if xml.trim.nonEmpty => value
    case ProgrammingStateSnapScalaModel(model) => ProgrammingStateRaw(SnapLanguage, model.toXml)
    case _ => ExpressionToSnap.convert(expression.expression)
  }
  lazy val javaStructured: ProgrammingStateJavaStructured = source match {
    case value: ProgrammingStateJavaStructured => value
    case ProgrammingStateRaw(Java, _) => ProgrammingStateJavaStructured(preparedSource.code)
    case _ => ProgrammingStateJavaStructured(java.code)
  }
  lazy val pythonStructured: ProgrammingStatePythonStructured = source match {
    case value: ProgrammingStatePythonStructured => value
    case ProgrammingStateRaw(Python, _) => ProgrammingStatePythonStructured(preparedSource.code)
    case _ => ProgrammingStatePythonStructured(python.code)
  }
  lazy val snapModel: Either[String, ProgrammingStateSnapScalaModel] = source match {
    case value: ProgrammingStateSnapScalaModel => Right(value)
    case _ => SnapScalaXmlCodec.parse(snap.code).map(ProgrammingStateSnapScalaModel(_))
  }
  lazy val isJavaClass: Boolean = javaFrontend.isClassProgram
  lazy val javaExecutionProgram = javaFrontend.compileForExecution

  def convertToLanguage(language: ProgrammingLanguage): ConversionResult = {
    try {
      val target = inLanguage(language)
      val numericWarnings = Option.when(source.associatedLanguage == Java && language != Java &&
          "\\b(?:byte|short|int|long)\\b".r.findFirstIn(
            it.evadid.vm.conversion.structured.SourceStructure.mask(java.code, false)).nonEmpty) {
        ConversionInfo("Java integer types were widened to shared numeric values; integer overflow and truncating division are not preserved.",
          it.evadid.util.logging.LoggingLevel.WARN, true)
      }.toSeq
      val droppedComments = Option.when(language == SnapLanguage && source.associatedLanguage != SnapLanguage &&
          expression.expression.recToTree(false, it.evadid.vm.types.BeChildInfo(it.evadid.vm.types.BeChildRole.NoRole,
            it.evadid.vm.types.BeScope.GlobalScope())).values.exists {
            case it.evadid.vm.code.tree.BeExpressionReference(_, _: it.evadid.vm.code.errors.BeSingleLineComment) => true
            case _ => false
          }) {
        ConversionInfo("Snap scripts have no comment statements; comments were not converted.", it.evadid.util.logging.LoggingLevel.INFO, false)
      }.toSeq
      ConversionResult(source, Some(target), preparedSource.diagnostics ++ numericWarnings ++ droppedComments)
    }
    catch { case scala.util.control.NonFatal(error) => ConversionResult.failed(source, error) }
  }

  private def inLanguage(language: ProgrammingLanguage): ProgrammingState = language match {
    case Python => pythonStructured
    case Java => javaStructured
    case SnapLanguage => snapModel.fold(message => throw IllegalArgumentException(message), _.persistent)
    case _ => throw UnsupportedOperationException(s"Programming language '$language' cannot be converted.")
  }
}
