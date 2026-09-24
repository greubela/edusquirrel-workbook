package it.evadid.workbook.elements.interactionElements.codeTaskToggle

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable
import upickle.default.{ReadWriter, macroRW}

case class CodeTaskToggleInteraction(
                                      override val elementId: String,
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

  override val serializerInteractionContent: Serializer[CodeTaskToggleState] = CodeTaskToggleState.serializer

  override lazy val childrenOfThisElement: List[WorkbookElement] = List(reorder)

  override lazy val allContainedInteractions: List[WorkbookInteractionElement[?]] = List(this, reorder)

  override def toSerializableType: WorkbookElementSerializable = toFactoryBase.withSerializedElementAdded("reorder", reorder)
    .withContentIdAdded("codeEditorTitle", codeEditorTitle).withElementAdded("advancedCodeTemplate", advancedCodeTemplate)
    .withElementAdded("advancedRequirements", advancedRequirements)(CodeTaskToggleInteraction.requirementsSerializer)
    .withContentIdAdded("advancedSuccessMessage", advancedSuccessMessage)
}

object CodeTaskToggleInteraction {
 private given contentIdRW: ReadWriter[LanguageMapContentId] = LanguageMapContentId.serializer.uPickleReadWrite
 private given requirementRW: ReadWriter[AdvancedCodeRequirement] = macroRW
 private[workbook] val requirementsSerializer = Serializer.fromUpickleJson(summon[ReadWriter[List[AdvancedCodeRequirement]]])
 def fromFactory(f: WorkbookElementSerializable): CodeTaskToggleInteraction = CodeTaskToggleInteraction(f.elementId, f.getElementAsSerializedElement("reorder").asInstanceOf[ReorderInteraction.ReorderCodeInteraction], f.getElementAsContentId("codeEditorTitle"), f.getElementAsString("advancedCodeTemplate"), f.getElementAs("advancedRequirements")(requirementsSerializer), f.getElementAsContentId("advancedSuccessMessage"))
}
