package it.evadid.workbook.elements.interactionElements.codeTaskToggle

import it.evadid.core.datastructures.language.LanguageMapContentId

/** A checklist item for advanced-mode free-text code: all `mustContain` fragments must appear. */
case class AdvancedCodeRequirement(
  mustContain: List[String],
  missingHint: LanguageMapContentId
)

object AdvancedCodeRequirement {

  def apply(mustContain: String, missingHint: LanguageMapContentId): AdvancedCodeRequirement =
    AdvancedCodeRequirement(List(mustContain), missingHint)

}
