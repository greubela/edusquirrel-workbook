package it.evadid.workbook.interactions.gpt

import it.evadid.workbook.serialization.WorkbookElementFactory.constructorLayout


import it.evadid.core.datastructures.chat.SenderRole.USER
import it.evadid.core.datastructures.chat.{ChatParticipant, MessengerModel}
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.workbook.abstractions.InteractionFormat.TextInteractionFormat
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.interactions.basic.MessagingInteraction
import it.evadid.workbook.interactions.basic.MessagingInteraction.MessengerModelScaffolding
import it.evadid.workbook.sync.UpdateImportance.MAJOR
import it.evadid.workbook.serialization.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

import it.evadid.workbook.control.InteractionController
import java.time.Instant
import scala.concurrent.*
import scala.util.{Failure, Success}

object GptInteractionElement {

  val factory: WorkbookElementFactory[GptInteractionElement] = new WorkbookElementFactory[GptInteractionElement] {
    override lazy val elementMapAndOrderForConstructorLike = constructorLayout("elementId", "underlyingTextInteraction", "exerciseText", "scaffoldingHints", "gradingCriteria")

    override def idsRequiredForDeserialization(element: WorkbookElementSerializable) = Set(element.getElementAs[WorkbookElementReference]("underlyingTextInteraction").referencedId)

    override def toSerializableElement(e: GptInteractionElement) = {
      toFactoryBase(e).withElementAddedAs[WorkbookElementReference]("underlyingTextInteraction", e.underlyingTextInteraction.asRef)
        .withElementAddedAs("exerciseText", e.exerciseText)
        .withElementsAddedAs[LanguageMapContentId]("scaffoldingHints", e.scaffoldingHints)
        .withElementsAddedAs[LanguageMapContentId]("gradingCriteria", e.gradingCriteria)
    }

    override def fromSerializedElement(f: WorkbookElementSerializable, parsed: Map[String, WorkbookElement]) = {
      GptInteractionElement(f.elementId,
        f.getAndResolveWorkbookElement[WorkbookInteractionElement[TextInteractionFormat.type]]("underlyingTextInteraction", parsed),
        f.getElementAs[LanguageMapContentId]("exerciseText"),
        f.getElementsAs[LanguageMapContentId]("scaffoldingHints"),
        f.getElementsAs[LanguageMapContentId]("gradingCriteria"))
    }
  }

}

case class GptInteractionElement(
                                  override val elementId: String,
                                  underlyingTextInteraction: WorkbookInteractionElement[TextInteractionFormat.type],
                                  exerciseText: LanguageMapContentId,
                                  scaffoldingHints: Seq[LanguageMapContentId],
                                  gradingCriteria: Seq[LanguageMapContentId]
                                ) extends WorkbookDisplayElement {
  override val associatedFactory = GptInteractionElement.factory

  private val allContentIds: Set[LanguageMapContentId] = scaffoldingHints.toSet ++ gradingCriteria.toSet ++ List(exerciseText)
  private val scaffoldingInteraction: MessagingInteraction = MessagingInteraction(elementId + "_scaffoldingMessenger")
  lazy val scaffoldingInteractionOp: Option[MessagingInteraction] = if (scaffoldingHints.nonEmpty) Some(scaffoldingInteraction) else None
  override lazy val childrenOfThisElement: Seq[WorkbookElement] = scaffoldingInteractionOp.toList

  //  lazy val serialized: SerializedGptInteractionElement = SerializedGptInteractionElement.fromElement(this)

  private given ExecutionContext = ExecutionContext.global

  /** Starts the hint chat from the task and current answer, unless it already has messages. */
  def initScaffoldingIfEmpty(answers: InteractionController, curUser: ChatParticipant, resolver: LanguageMapIdResolver): Future[Boolean] = {
    val curMessages = answers.currentValue(scaffoldingInteraction).messengerModel.orderedMessages
    if (curMessages.nonEmpty) Future.successful(curMessages.last.author.role == USER)
    else resolver.resolveAll(allContentIds.toSeq).transform {
      case Success((map, lang)) =>
        // if (map.keySet.size != allContentIds.size) syncControl.syncLogger.logWarn("Could not resolve all content ids. Resolved: " + map.keySet.mkString(", ") + " not: " + allContentIds.filter(!map.contains(_)).mkString(", "))
        Success(initScaffoldingIfEmpty(answers, curUser, map, lang))
      case Failure(err) =>
        //  syncControl.syncLogger.logExceptionWarn(s"GptInteractionElement: failure while resolving language map strings for $elementId, init will be ignored now!", err)
        Success(false)
    }
  }

  def initScaffoldingIfEmpty(answers: InteractionController, curUser: ChatParticipant, resolvedIds: Map[LanguageMapContentId, String], resolvedLanguage: HumanLanguage): Boolean =
    if (answers.currentValue(scaffoldingInteraction).messengerModel.orderedMessages.nonEmpty) false else {
      //logger.logInfo("GptInteractionElement has resolved the following ids: " + resolvedIds.keys.mkString(", "))

      val exText: String = resolvedIds.getOrElse(exerciseText, "[unresolved: " + exerciseText.fullId + "]")
      val scaffHints: Seq[String] = scaffoldingHints.map(id => resolvedIds.getOrElse(id, s"[unresolved: $id]"))
      val curInput: String = answers.currentValue(underlyingTextInteraction)
      val msg: MessengerModel = MessengerModel.getScaffoldingInitMessage(curUser, exText, curInput, scaffHints, resolvedLanguage)
      val msgSc: MessengerModelScaffolding = MessengerModelScaffolding(msg)
      // syncControl.syncLogger.logInfo(s"GptInteractionElement: setting scaffolding messenger for $elementId to init state (was empty before, now ${msgSc.messengerModel.messages.size} messages)")
      answers.setStateFromUserInteraction(scaffoldingInteraction, msgSc, MAJOR, Instant.now())
      msg.messages.exists(_.author.role == USER)
    }

}

