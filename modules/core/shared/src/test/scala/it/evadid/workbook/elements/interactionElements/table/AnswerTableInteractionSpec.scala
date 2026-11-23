package it.evadid.workbook.elements.interactionElements.table

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite
import upickle.default.*
import it.evadid.workbook.support.TestExecutionContext
import scala.concurrent.{ExecutionContext, Future}

class AnswerTableInteractionSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private def correctCells(answer: TableAnswer): Future[Int] = Future.traverse(table.gradingElements)(
    _.gradeStateSafe(answer, false, TestExecutionContext())).map(_.count(_.gradingStatus.isCorrect))
  private def id(s: String) = LanguageMapContentId(s"test/$s")
  private val binary = EditableTableCell(Some(List("1")), List("0", "1"))
  private val table = AnswerTableInteraction("table", id("caption"), List(id("row1"), id("row2")),
    List(id("col1"), id("col2")), List(
      List(FixedTableCell(id("given")), binary),
      List(EditableTableCell(Some(List("hello", "hi"))), EditableTableCell())))

  test("editable indices skip fixed cells and updates preserve unrelated answers") {
    assertEquals(table.editableIndex(0, 0), None)
    assertEquals(table.editableIndex(0, 1), Some(0))
    assertEquals(table.editableIndex(1, 0), Some(1))
    assertEquals(table.editableIndex(1, 1), Some(2))
    val updated = table.update(table.defaultValue, 1, 0, "hello")
    assertEquals(updated.values, List("", "hello", ""))
    assertEquals(table.defaultValue.values, List("", "", ""))
    intercept[IllegalArgumentException](table.update(updated, 0, 0, "changed"))
    for ((row, col) <- List((-1, 0), (2, 0), (0, -1), (0, 2)))
      intercept[IllegalArgumentException](table.editableIndex(row, col))
  }
  test("choices accept only configured values and may be cleared; free input preserves text") {
    intercept[IllegalArgumentException](table.update(table.defaultValue, 0, 1, "2"))
    val updated = table.update(table.defaultValue, 0, 1, "1")
    assertEquals(table.update(updated, 0, 1, "").values.head, "")
    val text = " Quotes: \"Grüße\"\n\\ "
    assertEquals(table.update(updated, 1, 1, text).values.last, text)
  }
  test("grading counts only expected cells and accepts trimmed alternatives case sensitively") {
    assert(!table.isAnswered(TableAnswer(List("1", "hello", "   "))))
    assert(table.isAnswered(TableAnswer(List("1", "hello", "reflection"))))
    assert(!table.isAnswered(TableAnswer(List("2", "hello", "reflection"))))
    assertEquals(table.gradingElements.size, 2)
    Future.traverse(Seq(table.defaultValue, TableAnswer(Seq("1", " hi ", "")), TableAnswer(Seq("1", "hello", "")),
      TableAnswer(Seq("1", "HELLO", "reflection"))))(correctCells).map(counts => assertEquals(counts, Seq(0, 2, 2, 1)))
  }
  test("ungraded tables require all cells but do not invent correctness") {
    val reflection = table.copy(rows = List(
      List(FixedTableCell(id("given")), EditableTableCell()),
      List(EditableTableCell(), EditableTableCell())))
    assert(reflection.gradingElements.isEmpty)
    assert(!reflection.isAnswered(reflection.defaultValue))
    assert(reflection.isAnswered(TableAnswer(List("a", "b", "c"))))
  }
  test("invalid dimensions, empty tables and invalid cell definitions fail early") {
    intercept[IllegalArgumentException](table.copy(rowLabels = Nil))
    intercept[IllegalArgumentException](table.copy(columnLabels = Nil))
    intercept[IllegalArgumentException](table.copy(rows = table.rows.take(1)))
    intercept[IllegalArgumentException](table.copy(rows = List(List(binary), List(binary))))
    intercept[IllegalArgumentException](table.copy(rows = List.fill(2)(List.fill(2)(FixedTableCell(id("given"))))))
    for (choices <- List(List(""), List(" 1"), List("1", "1")))
      intercept[IllegalArgumentException](EditableTableCell(choices = choices))
    for (expected <- List(Nil, List(" "), List("2")))
      intercept[IllegalArgumentException](EditableTableCell(Some(expected), List("0", "1")))
  }
  test("wrong stored dimensions cannot be updated, graded, saved or restored") {
    val invalid = TableAnswer(List("1"))
    intercept[IllegalArgumentException](table.update(invalid, 0, 1, "1"))
    intercept[IllegalArgumentException](table.isAnswered(invalid))
    intercept[IllegalArgumentException](table.validateState(invalid))
    intercept[IllegalArgumentException](table.validateState(table.interactionFormat.stateSerializer.deserialize(write(invalid))))
  }
  test("both definition formats preserve fixed cells, choices and alternative answers") {
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike))
      assertEquals(serializer.deserialize(serializer.serialize(table)), table)
  }
  test("learner values round-trip without changing whitespace or special characters") {
    val answer = TableAnswer(List("1", " hi ", "Quotes: \"Grüße\"\n\\"))
    assertEquals(table.interactionFormat.stateSerializer.deserialize(table.interactionFormat.stateSerializer.serialize(answer)), answer)
    assertEquals(table.interactionFormat.stateSerializer.deserialize(table.interactionFormat.stateSerializer.serialize(table.defaultValue)), table.defaultValue)
  }

  test("per-cell results are persisted with their registered table graders") {
    val answer = TableAnswer(Seq("1", "hello", "reflection"))
    val context = TestExecutionContext()
    table.interactionVariable.setStateFromUserInteraction(answer, it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR, context = context)
    table.interactionVariable.control.getOrCalculateGrading(context).map { result =>
      assertEquals(result.testsWithResults.size, 2)
      assert(result.testsWithResults.forall(_.gradingStatus.isCorrect))
      val history = table.interactionVariable.currentHistory
      val serializer = table.interactionFormat.historySerializer
      assertEquals(serializer.deserialize(serializer.serialize(history)), history)
    }
  }
}
