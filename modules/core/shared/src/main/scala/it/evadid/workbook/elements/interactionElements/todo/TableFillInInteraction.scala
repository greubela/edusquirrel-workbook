package it.evadid.workbook.elements.interactionElements.todo


/*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction

case class TableFillInInteraction(
                                   override val id: String,
                                   rows: List[List[Option[LanguageMapContentId]]]
                                 ) extends WorkbookInteraction[TableFillInState] {
  lazy val blankCount: Int = rows.flatten.count(_.isEmpty)
  override val defaultValue: TableFillInState = TableFillInState(List.fill(blankCount)(""))
  override val serializer: Serializer[TableFillInState] = TableFillInState.serializer
}
*/