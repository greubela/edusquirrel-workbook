package it.evadid.homepage.workbook.content

import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExerciseFullJava
import it.evadid.workbook.elements.interactionElements.emailSimulator.{MailFolder, MailInteraction}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.elements.interactionElements.sql.SqlCommandExercise
import munit.FunSuite

class CreateTestWorkbookSpec extends FunSuite {
  test("complete test workbook round-trips in JSON and constructor format") {
    val workbook = CreateTestWorkbook(null).createWorkbook
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike)) {
      assertEquals(serializer.deserialize(serializer.serialize(workbook)), workbook)
    }
  }

  test("test workbook showcases a populated, editable mail simulator that survives serialization") {
    val mailboxes = CreateTestWorkbook(null).createWorkbook.allChildrenFullSubtree.collect {
      case mail: MailInteraction => mail
    }
    assertEquals(mailboxes.size, 1)
    val mailbox = mailboxes.head
    assertEquals(mailbox.defaultValue.inboxState.getMailsInFolder(MailFolder.Inbox).size, 15)
    assert(mailbox.allowCompose)
    assert(!mailbox.isPassed)
    val serializer = WorkbookElementFactory.serializerRefBasedJson
    assertEquals(serializer.deserialize(serializer.serialize(mailbox)), mailbox)
  }

  test("test workbook includes a QR exercise with a meaningful byte requirement") {
    val exercises = CreateTestWorkbook(null).createWorkbook.allChildrenFullSubtree.collect {
      case q: it.evadid.workbook.elements.interactionElements.qr.CreateQrCodeInteraction => q
    }
    assertEquals(exercises.size, 1)
    assertEquals(exercises.head.requirements.minBytes, 32)
    assert(!exercises.head.isPassed)
  }

  test("test/design workbook includes the configured school SQL sample") {
    val workbook = CreateTestWorkbook(null).createWorkbook
    val exercises = workbook.allChildrenFullSubtree.collect { case sql: SqlCommandExercise => sql }
    assertEquals(exercises.size, 1)
    val exercise = exercises.head
    assertEquals(exercise.elementId, "sql-students-demo")
    assertEquals(exercise.databaseConfig.databaseName, "school_exercises")
    assert(exercise.initialSql.contains("JOIN teachers ON students.teacher_id = teachers.id"))
    assert(exercise.initialSql.contains("WHERE students.grade >= 10"))
    assertEquals(exercise.defaultValue, exercise.initialSql)
    assert(workbook.sections.exists(_.elementId == "sql-editor-demo"))
  }

  test("test workbook includes the full Java exercise") {
    val workbook = CreateTestWorkbook(null).createWorkbook

    assert(
      workbook.allChildrenFullSubtree.exists {
        case exercise: ProgrammingExerciseFullJava => exercise.elementId == "prog-full-java"
        case _ => false
      }
    )
  }
}
