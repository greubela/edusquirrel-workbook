package it.evadid.workbook.model.interaction.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction

case class FillInBlanksInteraction(
                                    override val id: String,
                                    sentenceParts: List[LanguageMapContentId]
                                  ) extends WorkbookInteraction[FillInBlanksState] {
  require(sentenceParts.nonEmpty, "FillInBlanksInteraction requires at least one sentence part.")

  lazy val blankCount: Int = (sentenceParts.size - 1).max(0)
  override val defaultValue: FillInBlanksState = FillInBlanksState(List.fill(blankCount)(""))
  override val serializer: Serializer[FillInBlanksState] = FillInBlanksState.serializer
}
