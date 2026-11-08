package it.evadid.workbook.elements.interactionElements.gpt

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElementFull}
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading.ContentForLlmInteraction
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*


/** One saved answer and its task, hint configuration and independent grading criteria. */
case class TextInputWithScaffoldingAndGrading(
                                               elementId: String,
                                               instructionText: LanguageMapContentId,
                                               sampleSolution: Option[LanguageMapContentId] = None,
                                               contentScaffolding: Option[ContentForLlmInteraction] = None,
                                               contentGrading: List[ContentForLlmInteraction] = List(),
                                             ) extends WorkbookInteractionElementFull[String, MessengerModel, MessengerModel] derives ReadWriter {
  override val associatedFactory: WorkbookElementFactory.SimpleWorkbookElementFactory[TextInputWithScaffoldingAndGrading] = TextInputWithScaffoldingAndGrading.factory

  override lazy val childrenOfThisElement: List[WorkbookElement] = List()

  override val gradingElements: List[TextInputLlmGrader] = contentGrading.map(gradeData => TextInputLlmGrader(instructionText, sampleSolution, gradeData))
  override val scaffoldingElement: Option[TextInputLlmScaffolder] = contentScaffolding.map(scaffData => TextInputLlmScaffolder(instructionText, sampleSolution, scaffData))

  override val defaultValue: String = ""

}

object TextInputWithScaffoldingAndGrading {
  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[TextInputWithScaffoldingAndGrading] {
    override protected val constructorFieldOrder = List("elementId", "instructionText", "sampleSolution", "contentScaffolding", "contentGrading")

    override def finishSerialization(base: WorkbookElementSerializable, element: TextInputWithScaffoldingAndGrading): WorkbookElementSerializable =
      base.withElementAddedAs("instructionText", element.instructionText)
        .withElementAddedAs("sampleSolution", element.sampleSolution)
        .withElementAddedAs("contentScaffolding", element.contentScaffolding)
        .withElementAddedAs("contentGrading", element.contentGrading)

    override def finishDeserialization(element: WorkbookElementSerializable): TextInputWithScaffoldingAndGrading =
      TextInputWithScaffoldingAndGrading(element.elementId,
        element.getElementAs[LanguageMapContentId]("instructionText"),
        element.getOptionalElementAs[Option[LanguageMapContentId]]("sampleSolution", None),
        element.getOptionalElementAs[Option[ContentForLlmInteraction]]("contentScaffolding", None),
        element.getOptionalElementAs[List[ContentForLlmInteraction]]("contentGrading", List())
      )
  }

  case class ContentForLlmInteraction(
                                       instructionForLlm: LanguageMapContentId,
                                       goodExampleInteractions: Map[LanguageMapContentId, LanguageMapContentId]
                                     ) derives ReadWriter {

  }

}
