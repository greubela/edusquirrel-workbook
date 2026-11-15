package it.evadid.vm.conversion

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.vm.conversion.ConversionResult.converted
import it.evadid.vm.conversion.structured.StructuredProgramConversions
import it.evadid.vm.conversion.javaexpression.JavaToExpression
import it.evadid.vm.conversion.javaediting.JavaEditingMetadata
import it.evadid.vm.conversion.javasnap.JavaToSnap
import it.evadid.vm.conversion.pythonjava.PythonToJava
import it.evadid.vm.conversion.snapjava.SnapToJava
import it.evadid.vm.conversion.expressionjava.ExpressionToJava
import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import it.evadid.vm.conversion.expressionsnap.ExpressionToSnap
import it.evadid.vm.conversion.pythonexpression.PythonToExpression
import it.evadid.vm.conversion.pythonsnap.PythonToSnap
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
      case ProgrammingStateBeExpression(root: it.evadid.vm.conversion.javaexpression.checked.JavaTurtleVmPrograms.Root) =>
        ProgrammingStateJavaStructured(root.bindings.source.source)
      case other => StructuredProgramConversions.toJava(other.toPythonStructured)
    }

    def toPythonStructured: ProgrammingStatePythonStructured = state match {
      case value: ProgrammingStatePythonStructured => value
      case ProgrammingStatePythonString(code) => ProgrammingStatePythonStructured(code)
      case value: ProgrammingStateJavaStructured => StructuredProgramConversions.toPython(value)
      case ProgrammingStateJavaString(code) => StructuredProgramConversions.toPython(ProgrammingStateJavaStructured(code))
      case ProgrammingStateSnapXml(xml) => StructuredProgramConversions.fromSnap(xml)
      case value: ProgrammingStateSnapScalaModel => value.toSnapXml.toPythonStructured
      case ProgrammingStateBeExpression(root: it.evadid.vm.conversion.javaexpression.checked.JavaTurtleVmPrograms.Root) =>
        StructuredProgramConversions.toPython(ProgrammingStateJavaStructured(root.bindings.source.source))
      case value: ProgrammingStateBeExpression => StructuredProgramConversions.fromExpression(value)
    }

    def toBeExpression: ProgrammingStateBeExpression = state match {
      case value: ProgrammingStatePythonStructured => StructuredProgramConversions.toExpression(value)
      case value: ProgrammingStateJavaStructured => StructuredProgramConversions.toExpression(value.toPythonStructured)
      case value: ProgrammingStateBeExpression => value
      case ProgrammingStateJavaString(code) => JavaToExpression(code).convert
      case ProgrammingStatePythonString(code) =>
        if JavaEditingMetadata.hasPythonMetadata(code) then state.toJava.toBeExpression
        else ProgrammingStateBeExpression(PythonToExpression.program(code).fullProgram)
      case ProgrammingStateSnapXml(xml) =>
        if JavaEditingMetadata.hasSnapMetadata(xml) then state.toJava.toBeExpression
        else StructuredProgramConversions.toExpression(StructuredProgramConversions.fromSnap(xml))
      case value: ProgrammingStateSnapScalaModel => value.toSnapXml.toBeExpression
    }

    def toSnapXml: ProgrammingStateSnapXml = state match {
      case value: ProgrammingStatePythonStructured => StructuredProgramConversions.toSnap(value)
      case value: ProgrammingStateJavaStructured => StructuredProgramConversions.toSnap(value.toPythonStructured)
      case value: ProgrammingStateSnapXml => value
      case ProgrammingStateSnapScalaModel(model) => ProgrammingStateSnapXml(model.toXml)
      case ProgrammingStateBeExpression(expression) => ExpressionToSnap.convert(expression)
      case ProgrammingStatePythonString(code) =>
        if JavaEditingMetadata.hasPythonMetadata(code) then state.toJava.toSnapXml
        else converted(PythonToSnap.applyPython(code))
      case ProgrammingStateJavaString(code) =>
        val source = JavaToExpression(code)
        if source.isClassProgram then converted(source.toJavaVmProgram.left.map(_.message).flatMap(JavaToSnap.toSnap))
        else source.convert.toSnapXml
    }

    def toPython: ProgrammingStatePythonString = state match {
      case value: ProgrammingStatePythonStructured => ProgrammingStatePythonString(value.toString)
      case value: ProgrammingStateJavaStructured => ProgrammingStatePythonString(value.toPythonStructured.toString)
      case value: ProgrammingStatePythonString => value
      case ProgrammingStateBeExpression(expression) => ExpressionToPython.convert(expression)
      case _ => state.toBeExpression.toPython
    }

    def toJava: ProgrammingStateJavaString = state match {
      case value: ProgrammingStateJavaStructured => ProgrammingStateJavaString(value.toString)
      case value: ProgrammingStatePythonStructured => ProgrammingStateJavaString(value.toJavaStructured.toString)
      case value: ProgrammingStateJavaString => value
      case ProgrammingStateBeExpression(expression) => ExpressionToJava.convert(expression)
      case value: ProgrammingStateSnapXml if JavaEditingMetadata.hasSnapMetadata(value.snapXml) =>
        converted(SnapToJava.fromSnap(value))
      case ProgrammingStatePythonString(code) if JavaEditingMetadata.hasPythonMetadata(code) =>
        converted(PythonToJava.fromPython(code))
      case value: ProgrammingStateSnapScalaModel => value.toSnapXml.toJava
      case _ => state.toBeExpression.toJava
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
