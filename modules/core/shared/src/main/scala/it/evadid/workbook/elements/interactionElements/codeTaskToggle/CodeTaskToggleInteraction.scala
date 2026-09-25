package it.evadid.workbook.elements.interactionElements.codeTaskToggle

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.{ReadWriter, macroRW}

case class CodeTaskToggleInteraction(
                                      override val elementId: String,
                                      reorder: ReorderInteraction.ReorderCodeInteraction,
                                      codeEditorTitle: LanguageMapContentId,
                                      advancedCodeTemplate: String,
                                      advancedRequirements: List[AdvancedCodeRequirement] = Nil,
                                      advancedSuccessMessage: LanguageMapContentId = LanguageMapContentId("basic/advancedCodeFeedbackSuccess")
                                    ) extends WorkbookInteractionElement[CodeTaskToggleState] {
  override val associatedFactory = CodeTaskToggleInteraction.factory

  override val defaultValue: CodeTaskToggleState = CodeTaskToggleState(
    isBeginnerMode = true,
    advancedCode = advancedCodeTemplate
  )

  override val serializerInteractionContent: Serializer[CodeTaskToggleState] = CodeTaskToggleState.serializer

  override lazy val childrenOfThisElement: List[WorkbookElement] = List(reorder)

  override lazy val allContainedInteractions: List[WorkbookInteractionElement[?]] = List(this, reorder)

}

object CodeTaskToggleInteraction {
  private given contentIdRW: ReadWriter[LanguageMapContentId] = DefaultSerializer.serializerLangMapId.uPickleReadWrite

  private given requirementRW: ReadWriter[AdvancedCodeRequirement] = macroRW

  private[workbook] val requirementsSerializer = Serializer.fromUpickleJson(summon[ReadWriter[List[AdvancedCodeRequirement]]])
  val factory: WorkbookElementFactory[CodeTaskToggleInteraction] = new WorkbookElementFactory[CodeTaskToggleInteraction] {
    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set(element.getElementAsWorkbookReference("reorder").referencedId)

    override def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable) = Seq.empty

    override def toSerializableElement(e: CodeTaskToggleInteraction) = WorkbookElementSerializable(e.elementId, classOf[CodeTaskToggleInteraction].getSimpleName, Map()).withReferenceAdded("reorder", e.reorder.asRef).withContentIdAdded("codeEditorTitle", e.codeEditorTitle).withElementAdded("advancedCodeTemplate", e.advancedCodeTemplate).withElementAdded("advancedRequirements", e.advancedRequirements)(requirementsSerializer).withContentIdAdded("advancedSuccessMessage", e.advancedSuccessMessage)

    override def fromSerializedElement(f: WorkbookElementSerializable, parsed: Map[String, WorkbookElement]) = CodeTaskToggleInteraction(f.elementId, f.getAndResolveWorkbookElement[ReorderInteraction.ReorderCodeInteraction]("reorder", parsed), f.getElementAsContentId("codeEditorTitle"), f.getElementAsString("advancedCodeTemplate"), f.getElementAs("advancedRequirements")(requirementsSerializer), f.getElementAsContentId("advancedSuccessMessage"))
  }
}
