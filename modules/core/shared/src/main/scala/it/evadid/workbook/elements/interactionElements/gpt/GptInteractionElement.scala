package it.evadid.workbook.elements.interactionElements.gpt

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.{LanguageMapContentId, LanguageMapIdResolver}
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.basic.MessagingInteraction
import it.evadid.workbook.elements.interactionElements.basic.MessagingInteraction.MessengerModelScaffolding
import it.evadid.workbook.elements.structureElements.Workbook
import it.evadid.workbook.interaction.sync.SyncControl
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR
import upickle.default.{ReadWriter, macroRW}

import scala.concurrent.*
import scala.util.{Failure, Success}

case class GptInteractionElement(
                                  id: String,
                                  underlyingTextInteraction: WorkbookInteractionElement[String],
                                  exerciseText: LanguageMapContentId,
                                  scaffoldingHints: List[LanguageMapContentId],
                                  gradingCriteria: List[LanguageMapContentId]
                                ) extends WorkbookDisplayElement {
  println("[WARN] creating messaging interaction for id '" + id + "' with no grading!")

  private val allContentIds: Set[LanguageMapContentId] = scaffoldingHints.toSet ++ gradingCriteria.toSet ++ List(exerciseText)
  private val scaffoldingInteraction: MessagingInteraction = MessagingInteraction(id + "_scaffoldingMessenger")
  lazy val scaffoldingInteractionOp: Option[MessagingInteraction] = if (scaffoldingHints.nonEmpty) Some(scaffoldingInteraction) else None
  override lazy val childrenOfThisElement: List[WorkbookElement] = scaffoldingInteractionOp.toList

  lazy val serialized: SerializedGptInteractionElement = SerializedGptInteractionElement.fromElement(this)

  private given ExecutionContext = ExecutionContext.global

  def initScaffoldingIfEmpty(syncControl: SyncControl, resolver: LanguageMapIdResolver): Future[Boolean] = {
    val curMessages = scaffoldingInteraction.interactionVariable.currentValue.messengerModel.orderedMessages
    if (curMessages.nonEmpty) Future.successful(curMessages.last.author.personId == MessengerModel.pStudent.personId)
    val res = Promise[Unit]()
    resolver.resolveAll(allContentIds.toSeq).transform {
      case Success((map, lang)) =>
        if (map.keySet.size != allContentIds.size) syncControl.syncLogger.logWarn("Could not resolve all content ids. Resolved: " + map.keySet.mkString(", ") + " not: " + allContentIds.filter(!map.contains(_)).mkString(", "))
        Success(initScaffoldingIfEmpty(syncControl, map, lang))
      case Failure(err) =>
        syncControl.syncLogger.logExceptionWarn(s"GptInteractionElement: failure while resolving language map strings for $id, init will be ignored now!", err)
        Success(false)
    }
  }

  def initScaffoldingIfEmpty(syncControl: SyncControl, resolvedIds: Map[LanguageMapContentId, String], resolvedLanguage: HumanLanguage): Boolean =
    if (scaffoldingInteraction.interactionVariable.currentValue.messengerModel.orderedMessages.nonEmpty) false else {
      //logger.logInfo("GptInteractionElement has resolved the following ids: " + resolvedIds.keys.mkString(", "))

      val exText: String = resolvedIds.getOrElse(exerciseText, "[unresolved: " + exerciseText.fullId + "]")
      val scaffHints: List[String] = scaffoldingHints.map(id => resolvedIds.getOrElse(id, s"[unresolved: $id]"))
      val curInput = underlyingTextInteraction.interactionVariable.currentValue
      val msg: MessengerModel = MessengerModel.getScaffoldingInitMessage(exText, curInput, scaffHints, resolvedLanguage)
      val msgSc: MessengerModelScaffolding = MessengerModelScaffolding(msg)
      syncControl.syncLogger.logInfo(s"GptInteractionElement: setting scaffolding messenger for $id to init state (was empty before, now ${msgSc.messengerModel.messages.size} messages)")
      scaffoldingInteraction.interactionVariable.setStateFromUserInteraction(syncControl, msgSc, MAJOR)
      msg.messages.exists(_.author.personId == MessengerModel.pStudent.personId)
    }


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

  def toElement(interactionsById: Map[String, WorkbookInteractionElement[?]]): GptInteractionElement = {
    val underlyingTextInteraction = interactionsById
      .getOrElse(
        underlyingTextInteractionId,
        throw new NoSuchElementException(s"No workbook interaction found for id '$underlyingTextInteractionId'.")
      )
      .asInstanceOf[WorkbookInteractionElement[String]]

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
