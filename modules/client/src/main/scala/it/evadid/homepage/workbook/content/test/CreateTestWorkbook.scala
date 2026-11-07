package it.evadid.homepage.workbook.content.test

import it.evadid.core.datastructures.geometry.{Line, Point}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.content.WorkbookFactory
import it.evadid.homepage.workbook.content.wip.PhishingMailboxData
import it.evadid.workbook.abstractions.{LangMapContentIdType, RoleInWorkbook, TypeOfTextDisplay, WorkbookElement}
import it.evadid.workbook.elements.displayElements.DisplayLangMapContent
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.{GoalLabel, HintLabel, TaskLabel}
import it.evadid.workbook.elements.interactionElements.emailSimulator.MailInteraction
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading.ContentForLlmInteraction
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.{ProgrammingExercise, ProgrammingExerciseFullJava}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.{ProgrammingEditorPalette, SnapProjectXml}
import it.evadid.workbook.elements.interactionElements.qr.{CreateQrCodeInteraction, QrCodeRequirements}
import it.evadid.workbook.elements.interactionElements.sql.{SqlCommandExercise, SqlDatabaseConfig}
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}

case class CreateTestWorkbook(fullInfo: FullInfo) extends WorkbookFactory {

  override def createWorkbook: Workbook = {
    workbook(
      "TestWorkbook/WorkbookTitle",
      List(section1, section2, section3, section4, section5, section6, section7),
      User.AndreGreubel
    )
  }

  lazy val section7: WorkbookSection = {

    val instr = LanguageMapContentId("TestWorkbook/sec7ex1Instr")
    val solut = LanguageMapContentId("TestWorkbook/sec7ex1Solution")
    val scaff = LanguageMapContentId("TestWorkbook/sec7ex1scaff1instr")
    val grade = LanguageMapContentId("TestWorkbook/sec7ex1grade1Instr")

    val scaffData = ContentForLlmInteraction(
      scaff, Map(
        LanguageMapContentId("TestWorkbook/sec7ex1scaff1stud1") -> LanguageMapContentId("TestWorkbook/sec7ex1scaff1resp1")
      ))
    val gradeData1 = ContentForLlmInteraction(
      grade, Map(
        LanguageMapContentId("TestWorkbook/sec7ex1grade1stud1") -> LanguageMapContentId("TestWorkbook/sec7ex1grade1resp1"),
        LanguageMapContentId("TestWorkbook/sec7ex1grade1stud2") -> LanguageMapContentId("TestWorkbook/sec7ex1grade1resp2")
      )
    )

    section("text-feedback-demo", "TestWorkbook/sec7", List(
      container("TestWorkbook/sec7cont", List(
        TextInputWithScaffoldingAndGrading(
          "demo-feedback-1",
          instr,
          Some(solut),
          Some(scaffData),
          List(gradeData1)
        ))
      )))
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

    val startingProgramJava: String =
      """public class Drawing {
        |  static void square(int side) {
        |
        |  }
        |
        |  public static void main(String[] args) {
        |    square(25);
        |  }
        |}
        |""".stripMargin

    val javaSquareInstructions = LanguageMapContentId("TestWorkbook/javaSquareInstructions")

    val cont1 = container("TestWorkbook/Sec1Cont1", List(
      // 1
      DisplayLangMapContent("instr-test-01",
        LanguageMapContentId("TestWorkbook/Sec1Instr1"),
        LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN)),
      TurtleRecreateShapeInteraction(
        "turtle-recreate-1",
        ProgrammingStateSnapXml(SnapProjectXml.mini).toBeExpression,
        exp1,
        ProgrammingEditorPalette.BeginnerTurtle,
        Map("forward" -> 5)
      ),
      // 2
      DisplayLangMapContent("instr-test-02",
        LanguageMapContentId("TestWorkbook/Sec1Instr2"),
        LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN)),
      TurtleRecreateShapeInteraction(
        "Turtle-Recreate-2",
        ProgrammingStateSnapXml(SnapProjectXml.mini).toBeExpression,
        exp2,
        ProgrammingEditorPalette.BeginnerTurtle,
        Map("forward" -> 2)
      )
    ))
    val cont2 = container("TestWorkbook/Sec1Cont1", List(
      // 3
      DisplayLangMapContent("instr-test-03",
        LanguageMapContentId("TestWorkbook/Sec1Instr3"),
        LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN)),
      ProgrammingExercise("prog-1", editorPalette = ProgrammingEditorPalette.BeginnerTurtle),

      // 4
      DisplayLangMapContent("instr-test-04",
        LanguageMapContentId("TestWorkbook/Sec1Instr4"),
        LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN)),

      DisplayLangMapContent("java-square-pilot-instructions", javaSquareInstructions,
        LangMapContentIdType(RoleInWorkbook.EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN)),
      ProgrammingExerciseFullJava("java-square-pilot", startingProgram =
        startingProgramJava, hintTask = Some(javaSquareInstructions))
    ))

    section("sec1Id", "TestWorkbook/Sec1", List[WorkbookElement](
      cont1, cont2
    ))

  }

  lazy val section2: WorkbookSection = {
    section(
      "sec2Id",
      "TestWorkbook/sec2",
      List(
        container(
          "TestWorkbook/sec2cont1",
          List(
            instructionLabeledPair("TestWorkbook/sec2cont1goalTitle", "TestWorkbook/sec2cont1goalText", GoalLabel),
            instructionLabeledPair("TestWorkbook/sec2cont1instTitle", "TestWorkbook/sec2cont1instText", TaskLabel),
            instructionLabeledPair("TestWorkbook/sec2cont1hintTitle", "TestWorkbook/sec2cont1hintText", HintLabel)
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
        initialSql =
          """SELECT students.name, students.grade, teachers.name AS teacher
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
