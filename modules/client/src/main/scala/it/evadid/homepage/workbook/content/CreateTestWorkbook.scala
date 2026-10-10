package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.geometry.{Line, Point}
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.elements.interactionElements.emailSimulator.MailInteraction
import it.evadid.workbook.elements.interactionElements.qr.{CreateQrCodeInteraction, QrCodeRequirements}
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.{GoalLabel, HintLabel, TaskLabel}
import it.evadid.workbook.elements.interactionElements.Turtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic.*
import it.evadid.workbook.elements.interactionElements.programming.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.{ProgrammingExercise, ProgrammingExerciseFullJava}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStateSnapXml
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{ProgrammingEditorPalette, SnapProjectXml}
import it.evadid.workbook.elements.interactionElements.sql.{SqlCommandExercise, SqlDatabaseConfig}
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}

case class CreateTestWorkbook(fullInfo: FullInfo) extends WorkbookFactory {

  override def createWorkbook: Workbook = {
    workbook(
      "TestWorkbook/WorkbookTitle",
      List(section1, section2, section3, section4, section5, section6),
      User.AndreGreubel
    )
  }

  val exp1 = TurtleLineBasedProgram(List(
    Line[Double](Point(0, 0), Point(100, 0)),
    Line[Double](Point(100, 0), Point(100, 100)),
    Line[Double](Point(100, 100), Point(200, 100))
  ))
  val exp2 = TurtleLineBasedProgram(List(
    Line[Double](Point(0, 0), Point(100, 0)),
    Line[Double](Point(100, 0), Point(100, 100)),
    Line[Double](Point(100, 100), Point(200, 100)),
    Line[Double](Point(100, 100), Point(100, 200)),
  ))

  lazy val section1: WorkbookSection = {
    section("sec1Id", "TestWorkbook/Sec1", List[WorkbookElement](
      container("TestWorkbook/Sec1Cont1", List(
        TurtleRecreateShapeInteraction(
          "Turtle-Recreate-1",
          ProgrammingStateSnapXml(SnapProjectXml.mini).toBeExpressionState,
          exp1,
          ProgrammingEditorPalette.BeginnerTurtle,
          Map("forward" -> 5)
        ),
        TurtleRecreateShapeInteraction(
          "Turtle-Recreate-2",
          ProgrammingStateSnapXml(SnapProjectXml.mini).toBeExpressionState,
          exp2,
          ProgrammingEditorPalette.BeginnerTurtle,
          Map("forward" -> 2)
        ),
        ProgrammingExercise("prog-1", editorPalette = ProgrammingEditorPalette.BeginnerTurtle),
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

  lazy val section3: WorkbookSection = section("qr-summary", "basic/qrTitle", List(
    container("basic/qrTitle", List(
      instructionLabeledPair("basic/qrTitle", "basic/qrTask", TaskLabel),
      CreateQrCodeInteraction("qr-summary-create", QrCodeRequirements(minBytes = 32))
    ))
  ))

  lazy val section4: WorkbookSection = section("mail-simulator", "emailSimulator/title", List(
    container("emailSimulator/exerciseTitle", List(
      instructionHtml("emailSimulator/instructions"),
      instructionPlaintext("emailSimulator/practiceInstructions"),
      MailInteraction("mail-simulator-demo", PhishingMailboxData.initialInbox, allowCompose = true)
    ))
  ))

  lazy val section6: WorkbookSection = section("sql-editor-demo", "TestWorkbook/sqlTitle", List(
    container("TestWorkbook/sqlExerciseTitle", List(
      instructionPlaintext("TestWorkbook/sqlInstructions"),
      SqlCommandExercise(
        elementId = "sql-students-demo",
        databaseConfig = SqlDatabaseConfig("school_exercises"),
        initialSql = """SELECT students.name, students.grade, teachers.name AS teacher
          |FROM students
          |JOIN teachers ON students.teacher_id = teachers.id
          |WHERE students.grade >= 10
          |ORDER BY students.name;""".stripMargin
      )
    ))
  ))

  override def workbookId: String = "workbookTest"
  lazy val section5: WorkbookSection = section("evacuation-construction", "digitalWorkbooks/evacuationTitle", List(
    container("digitalWorkbooks/evacuationTitle", List(
      instructionPlaintext("digitalWorkbooks/evacuationExampleTask"),
      it.evadid.workbook.elements.interactionElements.evacuation.EvacuationConstructFloorInteraction("evacuation-construct-floor")
    ))
  ))

}
