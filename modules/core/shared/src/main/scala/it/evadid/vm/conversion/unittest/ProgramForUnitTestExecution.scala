package it.evadid.vm.conversion.unittest

import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateBeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaString
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonString
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateSnapScalaModel
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateSnapXml

import it.evadid.vm.conversion.ProgrammingStateConversions.*
import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import it.evadid.vm.conversion.javaediting.JavaEditingMetadata
import it.evadid.vm.conversion.pythonjava.PythonToJava
import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport
import it.evadid.vm.conversion.snapexpression.SnapStateConversion
import it.evadid.vm.conversion.snapjava.SnapToJava

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.conversion.expressionpython.JavaTurtlePythonExport
import it.evadid.vm.conversion.javaexpression.checked.JavaTurtleVmPrograms
import scala.util.control.NonFatal

private[evadid] final case class UnitTestConversionError(message: String)

private[evadid] final case class ProgramForUnitTestExecution(pythonSource: String,
    commandEntryPoint: Option[ProgramForUnitTestExecution.CommandEntryPoint] = None)

private[evadid] object ProgramForUnitTestExecution {
  final case class CommandEntryPoint(functionName: String, methodIdsByName: Map[String, Int])

  private[evadid] def fromState(state: ProgrammingState): Either[UnitTestConversionError, ProgramForUnitTestExecution] =
    convertedFromState(state).left.map(UnitTestConversionError.apply)

  private def convertedFromState(state: ProgrammingState): Either[String, ProgramForUnitTestExecution] =
    try state match {
      case value: ProgrammingStateJavaString =>
        value.toJavaVmProgram.left.map(_.message).flatMap(program => fromExpression(program.root))
      case ProgrammingStatePythonString(code) =>
        if JavaEditingMetadata.hasPythonMetadata(code) then
          PythonToJava.fromPython(code).flatMap(convertedFromState)
        else Right(ProgramForUnitTestExecution(code))
      case value: ProgrammingStateSnapXml =>
        if JavaEditingMetadata.hasSnapMetadata(value.snapXml) then
          SnapToJava.fromSnap(value).flatMap(convertedFromState)
        else SnapStateConversion.expressionForExecution(value.snapXml).flatMap(fromExpression)
      case value: ProgrammingStateSnapScalaModel => convertedFromState(value.toSnapXml)
      case ProgrammingStateBeExpression(expression) => fromExpression(expression)
    } catch {
      case NonFatal(_) => Left("This program cannot be converted for unit-test execution.")
    }

  private def fromExpression(expression: BeExpression): Either[String, ProgramForUnitTestExecution] = expression match {
    case root: JavaTurtleVmPrograms.Root =>
      val exported = JavaTurtlePythonExport.render(JavaTurtleVmPrograms.fromRoot(root))
      Right(ProgramForUnitTestExecution(exported.source, Some(CommandEntryPoint(JavaTurtlePythonExport.EntryPoint,
        exported.methods.view.mapValues(_.index).toMap))))
    case _ => SnapExpressionSupport.validateSubset(expression)
      .map(_ => ProgramForUnitTestExecution(ExpressionToPython.printedPython(expression)))
  }
}
