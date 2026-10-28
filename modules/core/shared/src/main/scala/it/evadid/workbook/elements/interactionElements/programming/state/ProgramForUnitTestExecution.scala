package it.evadid.workbook.elements.interactionElements.programming.state

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.io.stringPrinter.python.JavaTurtlePythonExport
import it.evadid.vm.parsing.java.turtle.JavaTurtleVmPrograms
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{JavaTurtleEditingBridge, SnapTurtlePythonBridge}
import scala.util.control.NonFatal

private[programming] final case class UnitTestConversionError(message: String)

private[evadid] final case class ProgramForUnitTestExecution(pythonSource: String,
    commandEntryPoint: Option[ProgramForUnitTestExecution.CommandEntryPoint] = None)

private[evadid] object ProgramForUnitTestExecution {
  final case class CommandEntryPoint(functionName: String, methodIdsByName: Map[String, Int])

  private[programming] def fromState(state: ProgrammingState): Either[UnitTestConversionError, ProgramForUnitTestExecution] =
    convertedFromState(state).left.map(UnitTestConversionError.apply)

  private def convertedFromState(state: ProgrammingState): Either[String, ProgramForUnitTestExecution] =
    try state match {
      case value: ProgrammingStateJavaString =>
        value.toJavaVmProgram.left.map(_.message).flatMap(program => fromExpression(program.root))
      case ProgrammingStatePythonString(code) =>
        if JavaTurtleEditingBridge.hasPythonMetadata(code) then
          JavaTurtleEditingBridge.fromPython(code).flatMap(convertedFromState)
        else Right(ProgramForUnitTestExecution(code))
      case value: ProgrammingStateSnapXml =>
        if value.hasLegacyFloatingObjects then Left("Legacy extra blocks cannot be included in unit-test execution.")
        else if JavaTurtleEditingBridge.hasSnapMetadata(value.snapXml) then
          JavaTurtleEditingBridge.fromSnap(value).flatMap(convertedFromState)
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
    case _ => SnapTurtlePythonBridge.validateSubset(expression)
      .map(_ => ProgramForUnitTestExecution(SnapTurtlePythonBridge.printedPython(expression)))
  }
}
