package it.evadid.workbook.model.interaction.plugins.gpt

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.Workbook
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.basic.MessagingInteraction
import upickle.default.{ReadWriter, macroRW}

case class GptInteractionElement(
                                  id: String,
                                  underlyingTextInteraction: WorkbookInteraction[String],
                                  exerciseText: LanguageMapContentId,
                                  scaffoldingHints: List[LanguageMapContentId],
                                  gradingCriteria: List[LanguageMapContentId]
                                ) extends WorkbookElement {

  val scaffoldingInteractionOp: Option[MessagingInteraction] = {
    if(scaffoldingHints.nonEmpty){
      println("[WARN] creating messaging interaction for id '" + id + "' with test scaffolding hints")
      Some(MessagingInteraction(id + "_scaffoldingMessenger", MessengerModel.testCompletion))
    }else{
      None
    }
  }

  override lazy val childrenOfThisElement: List[WorkbookElement] = scaffoldingInteractionOp.toList

  lazy val serialized: SerializedGptInteractionElement = SerializedGptInteractionElement.fromElement(this)

  //private var htmlGptGrader = HtmlGptGrader(fullInfo, textInteraction)

  //val htmlGPTMessenger: Option[HtmlGPTMessenger] = languageMapIdScaffoldingHints.map(hintID => HtmlGPTMessenger(fullInfo, textInteraction, languageMapIDExerciseText, hintID))


}

/**
 * Authored/importable representation of a [[GptInteractionElement]].
 *
 * The underlying text interaction is referenced by id so imports can resolve it
 * from the surrounding workbook interaction id map. The derived
 * scaffolding messenger is intentionally not serialized as a separate authored
 * workbook element; [[GptInteractionElement]] recreates it from
 * `scaffoldingHints` when the imported element is materialized.
 */
case class SerializedGptInteractionElement(
                                            id: String,
                                            underlyingTextInteractionId: String,
                                            exerciseText: LanguageMapContentId,
                                            scaffoldingHints: List[LanguageMapContentId],
                                            gradingCriteria: List[LanguageMapContentId]
                                          ) {

  def toElement(workbook: Workbook): GptInteractionElement =
    toElement(workbook.allContainedInteractionsById)

  def toElement(interactionsById: Map[String, WorkbookInteraction[?]]): GptInteractionElement = {
    val underlyingTextInteraction = interactionsById
      .getOrElse(
        underlyingTextInteractionId,
        throw new NoSuchElementException(s"No workbook interaction found for id '$underlyingTextInteractionId'.")
      )
      .asInstanceOf[WorkbookInteraction[String]]

    GptInteractionElement(
      id,
      underlyingTextInteraction,
      exerciseText,
      scaffoldingHints,
      gradingCriteria
    )
  }

}

object SerializedGptInteractionElement {

  private given languageMapContentIdReadWriter: ReadWriter[LanguageMapContentId] =
    LanguageMapContentId.serializer.uPickleReadWrite

  given readWriter: ReadWriter[SerializedGptInteractionElement] = macroRW

  def fromElement(element: GptInteractionElement): SerializedGptInteractionElement =
    SerializedGptInteractionElement(
      element.id,
      element.underlyingTextInteraction.id,
      element.exerciseText,
      element.scaffoldingHints,
      element.gradingCriteria
    )

}
