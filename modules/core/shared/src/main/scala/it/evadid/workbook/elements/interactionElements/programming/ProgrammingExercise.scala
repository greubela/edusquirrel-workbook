package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.core.util.io.Serializer
import it.evadid.vm.BeProgram
import it.evadid.vm.test.BeTestSuite
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}

import scala.util.Try

case class ProgrammingExercise(override val id: String, testSuite: Option[BeTestSuite] = None)
    extends WorkbookInteractionElement[ProgrammingExerciseState] {

  override val defaultValue: ProgrammingExerciseState = ProgrammingExerciseState.mini

  override val serializer: Serializer[ProgrammingExerciseState] = ProgrammingExercise.StateSerializer

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()
}

object ProgrammingExercise {

  private val LayoutHeader = "SNAP_LAYOUT_V1"
  private val Separator = "---"

  /** Composite persist format: layout JSON + Python body. Legacy = pure Python. */
  object StateSerializer extends Serializer[ProgrammingExerciseState] {
    override def serialize(obj: ProgrammingExerciseState): String = {
      val python = obj.program.fullProgram.expressionIO.toStringInLanguage(Python, English, false)
      if obj.canvasLayout.isEmpty then python
      else s"$LayoutHeader\n${obj.canvasLayout.toJson}\n$Separator\n$python"
    }

    override def deserialize(str: String): ProgrammingExerciseState = {
      if Option(str).forall(_.trim.isEmpty) then ProgrammingExerciseState.mini
      else parseComposite(str).getOrElse(ProgrammingExerciseState.mini)
    }

    private def parseComposite(str: String): Option[ProgrammingExerciseState] = {
      val trimmed = str.trim
      if trimmed.startsWith(LayoutHeader) then
        val rest = trimmed.drop(LayoutHeader.length).stripLeading
        val sepIdx = rest.indexOf(s"\n$Separator\n")
        if sepIdx < 0 then None
        else
          val layoutJson = rest.substring(0, sepIdx).trim
          val python = rest.substring(sepIdx + Separator.length + 2).stripLeading
          Some(
            ProgrammingExerciseState(
              program = Try(BeProgram.fromPythonString(python)).getOrElse(BeProgram.miniProgram()),
              canvasLayout = SnapCanvasLayout.fromJson(layoutJson)
            )
          )
      else
        Some(
          ProgrammingExerciseState(
            program = Try(BeProgram.fromPythonString(trimmed)).getOrElse(BeProgram.miniProgram()),
            canvasLayout = SnapCanvasLayout.empty
          )
        )
    }
  }
}
