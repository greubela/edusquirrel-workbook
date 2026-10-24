package it.evadid.workbook.elements.interactionElements.gpt

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElementFull}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

/** One saved answer and its task, hint configuration and independent grading criteria. */
case class TextInputWithScaffoldingAndGrading(
    elementId: String,
    exerciseText: LanguageMapContentId,
    scaffoldingHints: List[LanguageMapContentId] = Nil,
    gradingCriteria: List[LanguageMapContentId] = Nil,
    override val defaultValue: String = ""
) extends WorkbookInteractionElementFull[String, MessengerModel, MessengerModel] derives ReadWriter {
  override val associatedFactory = TextInputWithScaffoldingAndGrading.factory
  override lazy val childrenOfThisElement: List[WorkbookElement] = Nil
  override val gradingElements: List[TextInputLlmGrader] = gradingCriteria.map(TextInputLlmGrader(exerciseText, _))
  override val scaffoldingElement: Option[TextInputLlmScaffolder] =
    Option.when(scaffoldingHints.nonEmpty)(TextInputLlmScaffolder(exerciseText, scaffoldingHints))
}

object TextInputWithScaffoldingAndGrading {
  val factory = new WorkbookElementFactory.SimpleWorkbookElementFactory[TextInputWithScaffoldingAndGrading] {
    override protected val constructorFieldOrder = List("elementId", "exerciseText", "scaffoldingHints", "gradingCriteria", "defaultValue")
    override def finishSerialization(base: WorkbookElementSerializable, element: TextInputWithScaffoldingAndGrading): WorkbookElementSerializable =
      base.withElementAddedAs("exerciseText", element.exerciseText)
        .withElementAddedAs("scaffoldingHints", element.scaffoldingHints)
        .withElementAddedAs("gradingCriteria", element.gradingCriteria)
        .withElementAddedAs("defaultValue", element.defaultValue)
    override def finishDeserialization(element: WorkbookElementSerializable): TextInputWithScaffoldingAndGrading =
      TextInputWithScaffoldingAndGrading(element.elementId, element.getElementAs[LanguageMapContentId]("exerciseText"),
        element.getOptionalElementAs[List[LanguageMapContentId]]("scaffoldingHints", Nil),
        element.getOptionalElementAs[List[LanguageMapContentId]]("gradingCriteria", Nil),
        element.getOptionalElementAs[String]("defaultValue", ""))
  }
}
