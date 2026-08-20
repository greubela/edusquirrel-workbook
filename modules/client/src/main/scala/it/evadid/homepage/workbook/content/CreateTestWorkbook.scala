package it.evadid.homepage.workbook.content

import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.{GoalLabel, HintLabel, TaskLabel}
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingEditorPalette, ProgrammingExercise}
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}

case class CreateTestWorkbook(fullInfo: FullInfo) extends WorkbookFactory {

  override def createWorkbook: Workbook = {
    workbook("TestWorkbook/WorkbookTitle", List(section1, section2))
  }

  lazy val section1: WorkbookSection = {
    section("sec1Id", "TestWorkbook/Sec1", List[WorkbookElement](
      container("TestWorkbook/Sec1Cont1", List(
        ProgrammingExercise("prog-1", editorPalette = ProgrammingEditorPalette.PythonCompatibleSnap)
      )
    )))
  }

  lazy val section2: WorkbookSection = {
    section(
      "sec2Id",
      "TestWorkbook/section2Title",
      List(
        container(
          "TestWorkbook/section2Subtitle1",
          List(
            instructionLabeledPair("TestWorkbook/goalTitle", "TestWorkbook/section2GoalText", GoalLabel),
            instructionLabeledPair("TestWorkbook/instructionTitle", "TestWorkbook/section2InstructionText", TaskLabel),
            instructionLabeledPair("TestWorkbook/hintTitle", "TestWorkbook/section2HintText", HintLabel)
          )
        ),
        container(
          "TestWorkbook/section2Subtitle2",
          List(
            ProgrammingExercise("prog-circle", editorPalette = ProgrammingEditorPalette.BeginnerTurtle)
          )
        )
      )
    )
  }

  override def workbookId: String = "workbookTest"
}
