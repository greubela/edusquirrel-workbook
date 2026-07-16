package it.evadid.workbook.elements.interactionElements.codeTaskToggle

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction

case class CodeTaskToggleInteraction(
  override val id: String,
  reorder: ReorderInteraction.ReorderCodeInteraction,
  codeEditorTitle: LanguageMapContentId,
  advancedCodeTemplate: String,
  advancedRequirements: List[AdvancedCodeRequirement] = Nil,
  advancedSuccessMessage: LanguageMapContentId = LanguageMapContentId("basic/advancedCodeFeedbackSuccess")
) extends WorkbookInteractionElement[CodeTaskToggleState] {

  override val defaultValue: CodeTaskToggleState = CodeTaskToggleState(
    isBeginnerMode = true,
    advancedCode = advancedCodeTemplate
  )

  override val serializer: Serializer[CodeTaskToggleState] = CodeTaskToggleState.serializer

  override lazy val childrenOfThisElement: List[WorkbookElement] = List(reorder)

  override lazy val allContainedInteractions: List[WorkbookInteractionElement[?]] = List(this, reorder)

}
