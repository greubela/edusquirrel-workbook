package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.vm.BeProgram
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExerciseState
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExerciseState.{PythonSource, SnapXml}

import scala.util.Try

/** BeProgram and Python views of a stored programming state. Never written back. */
object ProgrammingDerivation {

  def program(state: ProgrammingExerciseState): BeProgram = state match
    case SnapXml(xml) =>
      SnapProgramDerivation.fromXml(xml).program
    case PythonSource(source, _) =>
      Try(BeProgram.fromPythonString(source)).getOrElse(BeProgram.empty)

  def pythonFor(state: ProgrammingExerciseState): String = state match
    case SnapXml(xml) =>
      SnapProgramDerivation.fromXml(xml).python
    case PythonSource(source, _) =>
      source
}
