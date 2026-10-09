package it.evadid.workbook.elements.interactionElements.sql

import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.distribution.commandTypes.SqlEditorCommands.*
import upickle.default.*

class SqlCommandExerciseSpec extends munit.FunSuite {
  test("exercise factory registration preserves configuration and initial SQL") {
    val exercise = SqlCommandExercise("sql-example", SqlDatabaseConfig("school"), "SELECT name FROM students;")
    assertEquals(WorkbookElementFactory.parse(exercise.toSerialized), exercise)
    assertEquals(exercise.serializerInteractionContent.deserialize("SELECT 2;"), "SELECT 2;")
    assertEquals(exercise.defaultValue, "SELECT name FROM students;")
  }
  test("wire results preserve SQL NULL, labels and metadata") {
    val response = Response(Status.Ready, results = List(Result(List("value", "value"), List(List(None, Some("NULL"))), truncated = true)),
      schema = List(Table("student", List(Column("id", "INT", false, true)), List(ForeignKey("teacher_id", "teacher", "id")))))
    assertEquals(read[Response](write(response)), response)
  }
  test("probe request contains only a database reference") {
    val request = Request(SqlDatabaseConfig("school"))
    assertEquals(read[Request](write(request)), request)
    assert(!write(request).contains("password"))
  }
}
