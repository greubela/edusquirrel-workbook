package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.conversion.structured.StructuredProgramConversions
import it.evadid.vm.conversion.javaexpression.JavaToExpression
import it.evadid.vm.conversion.expressionjava.ExpressionToJava
import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import it.evadid.vm.conversion.expressionsnap.ExpressionToSnap
import it.evadid.vm.conversion.pythonexpression.PythonToExpression
import it.evadid.vm.conversion.snapxmlmodel.SnapScalaXmlCodec
import it.evadid.vm.conversion.snapmodelxml.SnapModelToXml.*
import it.evadid.workbook.elements.interactionElements.programming.state.*

/** Conversion API for programming states. Models contain data; conversion dispatch lives in the VM. */
object ProgrammingStateConversions {
  extension (state: ProgrammingState) {
    def toLanguage(language: ProgrammingLanguage): ProgrammingState = language match {
      case SnapLanguage => state.toSnapXml
      case Java => state.toJava
      case Python => state.toPython
      case _ => throw new UnsupportedOperationException(s"Programming language '$language' cannot be converted.")
    }

    def toJavaStructured: ProgrammingStateJavaStructured = state match {
      case value: ProgrammingStateJavaStructured => value
      case ProgrammingStateJavaString(code) => ProgrammingStateJavaStructured(code)
      case _ => ProgrammingStateJavaStructured(state.toBeExpression.toJava.code)
    }

    def toPythonStructured: ProgrammingStatePythonStructured = state match {
      case value: ProgrammingStatePythonStructured => value
      case ProgrammingStatePythonString(code) => ProgrammingStatePythonStructured(code)
      case _ => StructuredProgramConversions.fromExpression(state.toBeExpression)
    }

    def toBeExpression: ProgrammingStateBeExpression = state match {
      case value: ProgrammingStateBeExpression => value
      case ProgrammingStateJavaString(code) => JavaToExpression(code).convert
      case value: ProgrammingStateJavaStructured => JavaToExpression(value.toString).convert
      case ProgrammingStatePythonString(code) => ProgrammingStateBeExpression(PythonToExpression.program(code).fullProgram)
      case value: ProgrammingStatePythonStructured => StructuredProgramConversions.toExpression(value)
      case ProgrammingStateSnapXml(xml) => ProgrammingStateBeExpression(
        it.evadid.vm.conversion.snapexpression.SnapToExpression.parse(xml))
      case value: ProgrammingStateSnapScalaModel => value.toSnapXml.toBeExpression
    }

    def toSnapXml: ProgrammingStateSnapXml = state match {
      case value: ProgrammingStateSnapXml => value
      case ProgrammingStateSnapScalaModel(model) => ProgrammingStateSnapXml(model.toXml)
      case _ => ExpressionToSnap.convert(state.toBeExpression.expression)
    }

    def toPython: ProgrammingStatePythonString = state match {
      case value: ProgrammingStatePythonString => value
      case _ => ExpressionToPython.convert(state.toBeExpression.expression)
    }

    def toJava: ProgrammingStateJavaString = state match {
      case value: ProgrammingStateJavaString => value
      case _ => ExpressionToJava.convert(state.toBeExpression.expression)
    }
  }

  extension (state: ProgrammingStateSnapXml)
    /** Lossless full-project conversion, independent of the executable Turtle subset. */
    def toSnapScalaModel: Either[String, ProgrammingStateSnapScalaModel] =
      SnapScalaXmlCodec.parse(state.snapXml).map(ProgrammingStateSnapScalaModel(_))

  extension (state: ProgrammingStateJavaString) {
    def isClassProgram: Boolean = JavaToExpression(state.code).isClassProgram
    def toJavaVmProgram = JavaToExpression(state.code).toJavaVmProgram
    def toLegacyTurtleCommands = JavaToExpression(state.code).toLegacyTurtleCommands
  }
}
