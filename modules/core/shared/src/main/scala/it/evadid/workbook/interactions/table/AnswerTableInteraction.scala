package it.evadid.workbook.interactions.table

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement, *}
import it.evadid.workbook.serialization.{WorkbookElementFactory, WorkbookElementSerializable}
import it.evadid.core.util.io.serializer.{DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.InteractionFormat.AnswerTableInteractionFormat
import it.evadid.workbook.history.InteractionVariableHistory
import upickle.default.*
import scala.util.chaining.*

sealed trait AnswerTableCell derives ReadWriter

case class FixedTableCell(content: LanguageMapContentId) extends AnswerTableCell derives ReadWriter

case class EditableTableCell(expected: Option[Seq[String]] = None, choices: Seq[String] = Nil)
  extends AnswerTableCell derives ReadWriter {
  require(choices.forall(s => s.nonEmpty && s == s.trim) && choices.distinct == choices, "Choices must be distinct nonempty values")
  require(expected.forall(xs => xs.nonEmpty && xs.forall(s => s.trim.nonEmpty && (choices.isEmpty || choices.contains(s.trim)))),
    "Expected answers must be nonempty and belong to the choices")

  def accepts(value: String): Boolean = value.trim.nonEmpty && (choices.isEmpty || choices.contains(value.trim))

}

case class TableAnswer(values: Seq[String]) derives ReadWriter


/** Learner values contain only editable cells, in row-major order. Fixed cells cannot be overwritten.
 * `rowHeader` labels the column of row labels; without it the renderer uses a generic "row" label. */
case class AnswerTableInteraction(elementId: String, caption: LanguageMapContentId,
                                  rowLabels: Seq[LanguageMapContentId], columnLabels: Seq[LanguageMapContentId], rows: Seq[Seq[AnswerTableCell]],
                                  rowHeader: Option[LanguageMapContentId] = None)
  extends WorkbookInteractionElement[AnswerTableInteractionFormat.type] {

  require(rowLabels.nonEmpty && columnLabels.nonEmpty && rows.size == rowLabels.size && rows.forall(_.size == columnLabels.size),
    "Table labels and rectangular cells must have matching dimensions")
  val editableCells: Seq[EditableTableCell] = rows.flatten.collect { case c: EditableTableCell => c }
  override val gradingElements = editableCells.zipWithIndex.flatMap((cell, index) => cell.expected.map(TableCellGrader(index, _)))
  require(editableCells.nonEmpty, "An answer table requires an editable cell")
  override lazy val childrenOfThisElement = Nil
  override val defaultValue = TableAnswer(List.fill(editableCells.size)(""))

  override def validateState(value: TableAnswer): Unit = { checked(value); () }

  private def checked(answer: TableAnswer): TableAnswer = {
    require(answer.values.size == editableCells.size, "Stored table dimensions do not match the exercise")
    answer
  }


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

  /** Indices of the filled editable cells whose value is not accepted. */
  override def wrongParts(answer: TableAnswer): Set[Int] =
    editableCells.zip(answer.values).zipWithIndex.collect { case ((cell, value), index)
      if value.trim.nonEmpty && cell.expected.exists(expected => !TableCellGrader.accepts(expected, value)) => index }.toSet

  def isAnswered(answer: TableAnswer): Boolean = {
    checked(answer)
    editableCells.zip(answer.values).forall((cell, value) => cell.accepts(value))
  }

}

object AnswerTableInteraction  {

  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[AnswerTableInteraction] {
    override protected val constructorFieldOrder = List("elementId", "caption", "rowLabels", "columnLabels", "rows", "rowHeader")

    override def finishSerialization(base: WorkbookElementSerializable, e: AnswerTableInteraction): WorkbookElementSerializable =
      base.withElementAddedAs("caption", e.caption).withElementAddedAs("rowLabels", e.rowLabels)
        .withElementAddedAs("columnLabels", e.columnLabels).withElementAddedAs("rows", e.rows)
        .pipe(withRows => e.rowHeader.fold(withRows)(_ => withRows.withElementAddedAs("rowHeader", e.rowHeader)))

    override def finishDeserialization(e: WorkbookElementSerializable): AnswerTableInteraction =
      AnswerTableInteraction(e.elementId, e.getElementAs[LanguageMapContentId]("caption"),
        e.getElementAs[Seq[LanguageMapContentId]]("rowLabels"), e.getElementAs[Seq[LanguageMapContentId]]("columnLabels"),
        e.getElementAs[Seq[Seq[AnswerTableCell]]]("rows"), e.getOptionalElementAs[Option[LanguageMapContentId]]("rowHeader", None))
  }
}
