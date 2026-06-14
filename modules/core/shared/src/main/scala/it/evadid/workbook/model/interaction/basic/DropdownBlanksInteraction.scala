package it.evadid.workbook.model.interaction.basic

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.model.interaction.WorkbookInteraction

case class DropdownBlanksInteraction(
                                      override val id: String,
                                      sentenceParts: List[LanguageMapContentId],
                                      optionsByBlank: List[List[LanguageMapContentId]]
                                    ) extends WorkbookInteraction[DropdownBlanksState] {
  require(sentenceParts.nonEmpty, "DropdownBlanksInteraction requires at least one sentence part.")
  require(optionsByBlank.size == sentenceParts.size - 1, "DropdownBlanksInteraction requires exactly one option list between each pair of sentence parts.")

  override val defaultValue: DropdownBlanksState = DropdownBlanksState(List.fill(optionsByBlank.size)(None))
  override val serializer: Serializer[DropdownBlanksState] = DropdownBlanksState.serializer
}
