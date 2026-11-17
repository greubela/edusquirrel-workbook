package it.evadid.workbook.elements.interactionElements.codeTaskToggle

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.{DefaultSerializer}
import it.evadid.core.util.io.serializer.DefaultSerializer.given
import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}
import it.evadid.workbook.interaction.variable.InteractionVariableHistory

import it.evadid.workbook.abstractions.*
import it.evadid.workbook.abstractions.InteractionFormat.*
import upickle.default.{ReadWriter, macroRW}

case class CodeTaskToggleInteraction(
                                      override val elementId: String,
                                      reorder: ReorderInteraction.ReorderCodeInteraction,
                                      codeEditorTitle: LanguageMapContentId,
                                      advancedCodeTemplate: String,
                                      advancedRequirements: Seq[AdvancedCodeRequirement] = Nil,
                                      advancedSuccessMessage: LanguageMapContentId = LanguageMapContentId("basic/advancedCodeFeedbackSuccess")
                                    ) extends WorkbookInteractionElement[CodeTaskToggleInteractionFormat.type] {

  override val associatedFactory = CodeTaskToggleInteraction.factory

  override val defaultValue: CodeTaskToggleState = CodeTaskToggleState(
    isBeginnerMode = true,
    advancedCode = advancedCodeTemplate
  )

  override lazy val childrenOfThisElement: Seq[WorkbookElement] = List(reorder)

  override lazy val allInteractionsInThisInstance: Seq[WorkbookInteractionElement[? <: InteractionFormat]] = List(this.asInstanceOf[WorkbookInteractionElement[? <: InteractionFormat]], reorder.asInstanceOf[WorkbookInteractionElement[? <: InteractionFormat]])
}

object CodeTaskToggleInteraction {
  private given requirementRW: ReadWriter[AdvancedCodeRequirement] = macroRW

  private[workbook] val requirementsSerializer = Serializer.fromUpickleJson(summon[ReadWriter[Seq[AdvancedCodeRequirement]]])
  val factory: WorkbookElementFactory[CodeTaskToggleInteraction] = new WorkbookElementFactory[CodeTaskToggleInteraction] {
    override lazy val elementMapAndOrderForConstructorLike = Map(0 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("elementId", true)), 1 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("reorder", false)), 2 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("codeEditorTitle", false)), 3 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("advancedCodeTemplate", false)), 4 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("advancedRequirements", false)), 5 -> List(it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig("advancedSuccessMessage", false)))

    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = Set(element.getElementAs[WorkbookElementReference]("reorder").referencedId)

    override def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable) = Seq.empty

    override def toSerializableElement(e: CodeTaskToggleInteraction) = {
      WorkbookElementSerializable(
        e.elementId,
        classOf[CodeTaskToggleInteraction].getSimpleName,
        Map())
        .withElementAddedAs("reorder", e.reorder.asRef)
        .withElementAddedAs[LanguageMapContentId]("codeEditorTitle", e.codeEditorTitle)
        .withElementAdded("advancedCodeTemplate", e.advancedCodeTemplate)
        .withElementsAddedAs("advancedRequirements", e.advancedRequirements)
        .withElementAddedAs("advancedSuccessMessage", e.advancedSuccessMessage)
    }

    override def fromSerializedElement(f: WorkbookElementSerializable, parsed: Map[String, WorkbookElement]) = {
      CodeTaskToggleInteraction(
        f.elementId,
        f.getAndResolveWorkbookElement[ReorderInteraction.ReorderCodeInteraction]("reorder", parsed),
        f.getElementAs[LanguageMapContentId]("codeEditorTitle"), f.getElementAs("advancedCodeTemplate"),
        f.getElementsAs[AdvancedCodeRequirement]("advancedRequirements"),
        f.getElementAs[LanguageMapContentId]("advancedSuccessMessage"))
    }
  }
}
