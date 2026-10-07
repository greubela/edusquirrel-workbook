package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.{GoalLabel, HintLabel, TaskLabel}
import it.evadid.workbook.elements.interactionElements.Turtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.programming.TurtleGraphic.{Line, TurtleLineBasedProgram}
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}

case class CreateTestWorkbook(fullInfo: FullInfo) extends WorkbookFactory {

  override def createWorkbook: Workbook = {
    workbook(
      "TestWorkbook/WorkbookTitle",
      List(section1, section2),
      User.AndreGreubel
    )
  }

  val exp = TurtleLineBasedProgram(List(
    Line[Double](Point(0, 0), Point(100, 0)),
    Line[Double](Point(100, 0), Point(100, 100)),
    Line[Double](Point(100, 100), Point(200, 100))
  ))

  lazy val section1: WorkbookSection = {
    section("sec1Id", "TestWorkbook/Sec1", List[WorkbookElement](
      container("TestWorkbook/Sec1Cont1", List(
        TurtleRecreateShapeInteraction(
          "Turtle-Recreate-1",
          ProgrammingStateSnapXml(SnapProjectXml.mini).toBeExpressionState,
          exp,
          ProgrammingEditorPalette.Default,
          Map("forward" -> 5)
        ),
        ProgrammingExercise("prog-1", editorPalette = ProgrammingEditorPalette.PythonCompatibleSnap),
        ProgrammingExerciseFullJava("prog-full-java")
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
