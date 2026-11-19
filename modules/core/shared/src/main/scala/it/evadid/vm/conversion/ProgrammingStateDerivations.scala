package it.evadid.vm.conversion

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
  private lazy val javaFrontend = JavaToExpression(source match {
    case ProgrammingStateJavaString(code) => code
    case value: ProgrammingStateJavaStructured => value.toString
    case _ => java.code
  })

  lazy val expression: ProgrammingStateBeExpression = source match {
    case value: ProgrammingStateBeExpression => value
    case _: ProgrammingStateJavaString | _: ProgrammingStateJavaStructured => javaFrontend.convert
    case ProgrammingStatePythonString(code) => ProgrammingStateBeExpression(PythonToExpression.program(code).fullProgram)
    case value: ProgrammingStatePythonStructured => ProgrammingStateBeExpression(PythonToExpression.program(value.toString).fullProgram)
    case ProgrammingStateSnapXml(xml) => ProgrammingStateBeExpression(SnapToExpression.parse(xml))
    case ProgrammingStateSnapScalaModel(model) => ProgrammingStateBeExpression(SnapToExpression.parse(model.toXml))
  }
  lazy val python: ProgrammingStatePythonString = source match {
    case value: ProgrammingStatePythonString => value
    case _ => ExpressionToPython.convert(expression.expression)
  }
  lazy val java: ProgrammingStateJavaString = source match {
    case value: ProgrammingStateJavaString => value
    case _ => ExpressionToJava.convert(expression.expression)
  }
  lazy val snap: ProgrammingStateSnapXml = source match {
    case value: ProgrammingStateSnapXml => value
    case ProgrammingStateSnapScalaModel(model) => ProgrammingStateSnapXml(model.toXml)
    case _ => ExpressionToSnap.convert(expression.expression)
  }
  lazy val javaStructured: ProgrammingStateJavaStructured = source match {
    case value: ProgrammingStateJavaStructured => value
    case _ => ProgrammingStateJavaStructured(java.code)
  }
  lazy val pythonStructured: ProgrammingStatePythonStructured = source match {
    case value: ProgrammingStatePythonStructured => value
    case _ => ProgrammingStatePythonStructured(python.code)
  }
  lazy val snapModel: Either[String, ProgrammingStateSnapScalaModel] = source match {
    case value: ProgrammingStateSnapScalaModel => Right(value)
    case _ => SnapScalaXmlCodec.parse(snap.snapXml).map(ProgrammingStateSnapScalaModel(_))
  }
  lazy val isJavaClass: Boolean = javaFrontend.isClassProgram
  lazy val checkedJava = javaFrontend.toJavaVmProgram

  def inLanguage(language: ProgrammingLanguage): ProgrammingState = language match {
    case Python => python
    case Java => java
    case SnapLanguage => snap
    case _ => throw UnsupportedOperationException(s"Programming language '$language' cannot be converted.")
  }
}
