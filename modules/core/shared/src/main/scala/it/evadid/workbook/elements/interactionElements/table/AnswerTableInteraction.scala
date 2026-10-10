package it.evadid.workbook.elements.interactionElements.table

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

sealed trait AnswerTableCell derives ReadWriter

case class FixedTableCell(content: LanguageMapContentId) extends AnswerTableCell derives ReadWriter

case class EditableTableCell(expected: Option[List[String]] = None, choices: List[String] = Nil)
  extends AnswerTableCell derives ReadWriter {
  require(choices.forall(s => s.nonEmpty && s == s.trim) && choices.distinct == choices, "Choices must be distinct nonempty values")
  require(expected.forall(xs => xs.nonEmpty && xs.forall(s => s.trim.nonEmpty && (choices.isEmpty || choices.contains(s.trim)))),
    "Expected answers must be nonempty and belong to the choices")

  def accepts(value: String): Boolean = value.trim.nonEmpty && (choices.isEmpty || choices.contains(value.trim))

  def isCorrect(value: String): Option[Boolean] = expected.map(_.exists(_.trim == value.trim))
}

case class TableAnswer(values: List[String]) derives ReadWriter

case class TableGrade(correct: Int, total: Int) derives upickle.default.ReadWriter {
  def passed: Boolean = total > 0 && correct == total
}

/** Learner values contain only editable cells, in row-major order. Fixed cells cannot be overwritten. */
case class AnswerTableInteraction(elementId: String, caption: LanguageMapContentId,
                                  rowLabels: List[LanguageMapContentId], columnLabels: List[LanguageMapContentId], rows: List[List[AnswerTableCell]])
  extends WorkbookInteractionElement[TableAnswer] derives upickle.default.ReadWriter {
  require(rowLabels.nonEmpty && columnLabels.nonEmpty && rows.size == rowLabels.size && rows.forall(_.size == columnLabels.size),
    "Table labels and rectangular cells must have matching dimensions")
  val editableCells: List[EditableTableCell] = rows.flatten.collect { case c: EditableTableCell => c }
  require(editableCells.nonEmpty, "An answer table requires an editable cell")
  override lazy val childrenOfThisElement = Nil
  override val defaultValue = TableAnswer(List.fill(editableCells.size)(""))

  private def checked(answer: TableAnswer): TableAnswer = {
    require(answer.values.size == editableCells.size, "Stored table dimensions do not match the exercise")
    answer
  }

  override val serializerInteractionContent: Serializer[TableAnswer] =
    Serializer.fromUpickleJson(summon[ReadWriter[TableAnswer]]).map(checked, checked)
  override val associatedFactory = AnswerTableInteraction.factory

  def editableIndex(row: Int, column: Int): Option[Int] = {
    require(row >= 0 && row < rows.size && column >= 0 && column < columnLabels.size, "Cell is outside the table")
    rows(row)(column) match {
      case _: FixedTableCell => None
      case _: EditableTableCell => Some(rows.flatten.take(row * columnLabels.size + column).count(_.isInstanceOf[EditableTableCell]))
    }
  }

  def update(answer: TableAnswer, row: Int, column: Int, value: String): TableAnswer = {
    checked(answer)
    val index = editableIndex(row, column).getOrElse(throw IllegalArgumentException("Fixed cells cannot be edited"))
    val cell = editableCells(index)
    require(cell.choices.isEmpty || value.isEmpty || cell.choices.contains(value), "Value is not an allowed choice")
    TableAnswer(answer.values.updated(index, value))
  }

  def isAnswered(answer: TableAnswer): Boolean = {
    checked(answer)
    editableCells.zip(answer.values).forall((cell, value) => cell.accepts(value))
  }

  def grade(answer: TableAnswer): Option[TableGrade] = {
    checked(answer)
    val results = editableCells.zip(answer.values).flatMap((cell, value) => cell.isCorrect(value))
    Option.when(results.nonEmpty)(TableGrade(results.count(identity), results.size))
  }
}

object AnswerTableInteraction {
  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[AnswerTableInteraction] {
    override protected val constructorFieldOrder = List("elementId", "caption", "rowLabels", "columnLabels", "rows")

    override def finishSerialization(base: WorkbookElementSerializable, e: AnswerTableInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("caption", e.caption).withElementAddedAs("rowLabels", e.rowLabels)
        .withElementAddedAs("columnLabels", e.columnLabels).withElementAddedAs("rows", e.rows)

    override def finishDeserialization(e: WorkbookElementSerializable): AnswerTableInteraction =
      AnswerTableInteraction(e.elementId, e.getElementAs[LanguageMapContentId]("caption"),
        e.getElementAs[List[LanguageMapContentId]]("rowLabels"), e.getElementAs[List[LanguageMapContentId]]("columnLabels"),
        e.getElementAs[List[List[AnswerTableCell]]]("rows"))
  }
}
