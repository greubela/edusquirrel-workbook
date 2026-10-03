package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.{GoalLabel, HintLabel, TaskLabel}
import it.evadid.workbook.elements.interactionElements.programming.{
  ProgrammingEditorKind,
  ProgrammingEditorPalette,
  ProgrammingExercise
}
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}

case class CreateTestWorkbook(fullInfo: FullInfo) extends WorkbookFactory {

  override def createWorkbook: Workbook = {
    workbook(
      "TestWorkbook/WorkbookTitle",
      List(section1, section2),
      User.AndreGreubel
    )
  }

  lazy val section1: WorkbookSection = {
    section("sec1Id", "TestWorkbook/Sec1", List[WorkbookElement](
      container("TestWorkbook/Sec1Cont1", List(
        ProgrammingExercise(
          "prog-1",
          editorPalette = ProgrammingEditorPalette.PythonCompatibleSnap,
          allowedEditors = List(ProgrammingEditorKind.Snap, ProgrammingEditorKind.Python)
        )
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
            ProgrammingExercise(
              "prog-circle",
              editorPalette = ProgrammingEditorPalette.BeginnerTurtle,
              allowedEditors = List(ProgrammingEditorKind.Snap, ProgrammingEditorKind.Python),
              // Beginner palette has turn (right), not turnLeft: approximate a circle clockwise.
              referencePython = Some(
                """for i in range(36):
                  |    forward(10)
                  |    turn(10)
                  |""".stripMargin
              )
            )
          )
        )
      )
    )
  }

  override def workbookId: String = "workbookTest"
}
