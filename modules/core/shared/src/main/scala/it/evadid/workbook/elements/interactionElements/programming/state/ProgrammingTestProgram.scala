package it.evadid.workbook.elements.interactionElements.programming.state

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.io.stringPrinter.python.JavaTurtlePythonExport
import it.evadid.vm.parsing.java.turtle.JavaTurtleVmPrograms
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapTurtlePythonBridge
import scala.util.control.NonFatal
import upickle.default.ReadWriter

final case class ProgrammingTestProgram(source: String,
    commandEntryPoint: Option[ProgrammingTestProgram.CommandEntryPoint] = None)

object ProgrammingTestProgram {
  final case class CommandEntryPoint(functionName: String, methods: Map[String, Int]) derives ReadWriter

  private[state] def converted(result: => Either[String, ProgrammingTestProgram]): Either[String, ProgrammingTestProgram] =
    try result catch {
      case NonFatal(_) => Left("This program cannot be prepared for unit-test execution.")
    }

  private[state] def fromExpression(expression: BeExpression): Either[String, ProgrammingTestProgram] = expression match {
    case root: JavaTurtleVmPrograms.Root =>
      val exported = JavaTurtlePythonExport.render(JavaTurtleVmPrograms.fromRoot(root))
      Right(ProgrammingTestProgram(exported.source, Some(CommandEntryPoint(JavaTurtlePythonExport.EntryPoint,
        exported.methods.view.mapValues(_.index).toMap))))
    case _ => SnapTurtlePythonBridge.validateSubset(expression)
      .map(_ => ProgrammingTestProgram(SnapTurtlePythonBridge.printedPython(expression)))
  }
}
