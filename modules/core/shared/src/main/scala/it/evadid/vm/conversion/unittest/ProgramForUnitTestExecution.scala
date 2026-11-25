package it.evadid.vm.conversion.unittest

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateBeExpression
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateSnapScalaModel
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateRaw
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateJavaStructured, ProgrammingStatePythonStructured}

import it.evadid.vm.conversion.expressionpython.ExpressionToPython
import it.evadid.vm.conversion.snapexpression.SnapExpressionSupport
import it.evadid.vm.conversion.snapexpression.SnapStateConversion

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.conversion.expressionpython.JavaTurtlePythonExport
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
      case value: ProgrammingStateJavaStructured => convertedFromState(value.toJava)
      case value: ProgrammingStatePythonStructured => Right(ProgramForUnitTestExecution(value.toString))
      case value @ ProgrammingStateRaw(Java, _) =>
        value.compileForExecution.left.map(_.message).map { program =>
          val exported = JavaTurtlePythonExport.render(program)
          ProgramForUnitTestExecution(exported.source, Some(CommandEntryPoint(JavaTurtlePythonExport.EntryPoint,
            exported.methods.view.mapValues(_.index).toMap)))
        }
      case ProgrammingStateRaw(Python, code) => Right(ProgramForUnitTestExecution(code))
      case value @ ProgrammingStateRaw(SnapLanguage, _) => it.evadid.vm.parsing.source.SourceParsing.parse(value.code, it.evadid.vm.parsing.source.SourceParsing.SourceLanguage.Snap)
        .syntax.left.map(_.message).flatMap(SnapStateConversion.expressionForExecution).flatMap(fromExpression)
      case value: ProgrammingStateSnapScalaModel => SnapStateConversion.expressionForExecution(value.model).flatMap(fromExpression)
      case ProgrammingStateBeExpression(expression) => fromExpression(expression)
      case ProgrammingStateRaw(language, _) => Left(s"Unit-test execution does not support $language source.")
    } catch {
      case NonFatal(_) => Left("This program cannot be converted for unit-test execution.")
    }

  private def fromExpression(expression: BeExpression): Either[String, ProgramForUnitTestExecution] =
    SnapExpressionSupport.validateSubset(expression)
      .map(_ => ProgramForUnitTestExecution(ExpressionToPython.printedPython(expression)))
}
