package it.evadid.workbook.model.interaction.plugins.gpt

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.{LanguageMapContentId, LanguageMapIdResolver}
import it.evadid.util.logging.Logger
import it.evadid.workbook.model.abstractions.WorkbookElement
import it.evadid.workbook.model.elements.Workbook
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.basic.MessagingInteraction
import it.evadid.workbook.model.interaction.basic.MessagingInteraction.MessengerModelScaffolding
import it.evadid.workbook.model.interaction.sync.UpdateImportance.MAJOR
import upickle.default.{ReadWriter, macroRW}

import scala.concurrent.*
import scala.util.{Failure, Success}

case class GptInteractionElement(
                                  id: String,
                                  underlyingTextInteraction: WorkbookInteraction[String],
                                  exerciseText: LanguageMapContentId,
                                  scaffoldingHints: List[LanguageMapContentId],
                                  gradingCriteria: List[LanguageMapContentId]
                                ) extends WorkbookElement {
  println("[WARN] creating messaging interaction for id '" + id + "' with no grading!")

  private val allContentIds: Set[LanguageMapContentId] = scaffoldingHints.toSet ++ gradingCriteria.toSet
  private val scaffoldingInteraction: MessagingInteraction = MessagingInteraction(id + "_scaffoldingMessenger")
  lazy val scaffoldingInteractionOp: Option[MessagingInteraction] = if (scaffoldingHints.nonEmpty) Some(scaffoldingInteraction) else None
  override lazy val childrenOfThisElement: List[WorkbookElement] = scaffoldingInteractionOp.toList

  lazy val serialized: SerializedGptInteractionElement = SerializedGptInteractionElement.fromElement(this)

  def initScaffoldingIfEmpty(logger: Logger, resolver: LanguageMapIdResolver): Future[Unit] = {
    val res = Promise[Unit]()
    resolver.resolveToStrings(allContentIds.toSeq).onComplete {
      case Success(map) =>
        initScaffoldingIfEmpty(logger, map)
        res.success(())
      case Failure(err) =>
        logger.logExceptionWarn(s"GptInteractionElement: failure while resolving language map strings for $id, init will be ignored now!", err)
        res.failure(err)
    }(using ExecutionContext.global)
    res.future
  }

  def initScaffoldingIfEmpty(logger: Logger, resolvedIds: Map[LanguageMapContentId, String]): Unit = {
    logger.logInfo("GptInteractionElement has resolved the following ids: " + resolvedIds.keys.mkString("[", ", ", "]"))

    val exText: String = resolvedIds.getOrElse(exerciseText, "[unresolved: " + exerciseText.fullId + "]")
    val scaffHints: List[String] = scaffoldingHints.map(id => resolvedIds.getOrElse(id, s"[unresolved: $id]"))
    val curInput = underlyingTextInteraction.interactionVariable.currentValue
    val msg: MessengerModel = MessengerModel.getScaffoldingInitMessage(exText, curInput, scaffHints)
    val msgSc: MessengerModelScaffolding = MessengerModelScaffolding(msg)
    if (scaffoldingInteraction.interactionVariable.currentValue.messengerModel.orderedMessages.isEmpty) {
      logger.logInfo(s"GptInteractionElement: setting scaffolding messenger for $id to init state (was empty before, now ${msgSc.messengerModel.messages.size} messages)")
      scaffoldingInteraction.interactionVariable.setStateFromUserInteraction(msgSc, MAJOR)
    }
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
