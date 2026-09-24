package it.evadid.workbook.elements.interactionElements.gpt

import it.evadid.core.datastructures.chat.SenderRole.USER
import it.evadid.core.datastructures.chat.{MessengerModel, Person}
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.language.control.LanguageMapIdResolver
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.basic.MessagingInteraction
import it.evadid.workbook.elements.interactionElements.basic.MessagingInteraction.MessengerModelScaffolding
import it.evadid.workbook.interaction.sync.SyncControl
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.ReadWriter

import scala.concurrent.*
import scala.util.{Failure, Success}

object GptInteractionElement {
  private given contentIdReadWriter: ReadWriter[LanguageMapContentId] = LanguageMapContentId.serializer.uPickleReadWrite

  private val contentIdsSerializer = it.evadid.core.util.io.Serializer.fromUpickleJson(summon[ReadWriter[List[LanguageMapContentId]]])

  val factory: WorkbookElementFactory[GptInteractionElement] = new WorkbookElementFactory[GptInteractionElement] {
    override def createFromSerialized(factory: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): GptInteractionElement = {
      GptInteractionElement(
        factory.elementId,
        factory.getElementAsWorkbookReference("underlyingTextInteraction").asInstanceOf[WorkbookInteractionElement[String]],
        factory.getElementAsContentId("exerciseText"),
        factory.getElementAs("scaffoldingHints")(contentIdsSerializer),
        factory.getElementAs("gradingCriteria")(contentIdsSerializer)
      )
    }

    override def requireIds(factory: WorkbookElementSerializable): List[String] = {
      List(factory.getElementAsWorkbookReference("underlyingTextInteraction").referencedId)
    }
  }
}

case class GptInteractionElement(
                                  override val elementId: String,
                                  underlyingTextInteraction: WorkbookInteractionElement[String],
                                  exerciseText: LanguageMapContentId,
                                  scaffoldingHints: List[LanguageMapContentId],
                                  gradingCriteria: List[LanguageMapContentId]
                                ) extends WorkbookDisplayElement {
  println("[WARN] creating messaging interaction for id '" + elementId + "' with no grading!")

  private val allContentIds: Set[LanguageMapContentId] = scaffoldingHints.toSet ++ gradingCriteria.toSet ++ List(exerciseText)
  private val scaffoldingInteraction: MessagingInteraction = MessagingInteraction(elementId + "_scaffoldingMessenger")
  lazy val scaffoldingInteractionOp: Option[MessagingInteraction] = if (scaffoldingHints.nonEmpty) Some(scaffoldingInteraction) else None
  override lazy val childrenOfThisElement: List[WorkbookElement] = scaffoldingInteractionOp.toList

  //  lazy val serialized: SerializedGptInteractionElement = SerializedGptInteractionElement.fromElement(this)

  private given ExecutionContext = ExecutionContext.global

  def initScaffoldingIfEmpty(curUser: Person, syncControl: SyncControl, resolver: LanguageMapIdResolver): Future[Boolean] = {
    val curMessages = scaffoldingInteraction.interactionVariable.currentValue.messengerModel.orderedMessages
    if (curMessages.nonEmpty) Future.successful(curMessages.last.author.role == USER)
    val res = Promise[Unit]()
    resolver.resolveAll(allContentIds.toSeq).transform {
      case Success((map, lang)) =>
        if (map.keySet.size != allContentIds.size) syncControl.syncLogger.logWarn("Could not resolve all content ids. Resolved: " + map.keySet.mkString(", ") + " not: " + allContentIds.filter(!map.contains(_)).mkString(", "))
        Success(initScaffoldingIfEmpty(curUser, syncControl, map, lang))
      case Failure(err) =>
        syncControl.syncLogger.logExceptionWarn(s"GptInteractionElement: failure while resolving language map strings for $elementId, init will be ignored now!", err)
        Success(false)
    }
  }

  def initScaffoldingIfEmpty(curUser: Person, syncControl: SyncControl, resolvedIds: Map[LanguageMapContentId, String], resolvedLanguage: HumanLanguage): Boolean =
    if (scaffoldingInteraction.interactionVariable.currentValue.messengerModel.orderedMessages.nonEmpty) false else {
      //logger.logInfo("GptInteractionElement has resolved the following ids: " + resolvedIds.keys.mkString(", "))

      val exText: String = resolvedIds.getOrElse(exerciseText, "[unresolved: " + exerciseText.fullId + "]")
      val scaffHints: List[String] = scaffoldingHints.map(id => resolvedIds.getOrElse(id, s"[unresolved: $id]"))
      val curInput = underlyingTextInteraction.interactionVariable.currentValue
      val msg: MessengerModel = MessengerModel.getScaffoldingInitMessage(curUser, exText, curInput, scaffHints, resolvedLanguage)
      val msgSc: MessengerModelScaffolding = MessengerModelScaffolding(msg)
      syncControl.syncLogger.logInfo(s"GptInteractionElement: setting scaffolding messenger for $elementId to init state (was empty before, now ${msgSc.messengerModel.messages.size} messages)")
      scaffoldingInteraction.interactionVariable.setStateFromUserInteraction(syncControl, msgSc, MAJOR)
      msg.messages.exists(_.author.role == USER)
    }

  override def toSerializableType: WorkbookElementSerializable = toFactoryBase
    .withElementAdded("underlyingTextInteraction", underlyingTextInteraction)
    .withContentIdAdded("exerciseText", exerciseText)
    .withElementAdded("scaffoldingHints", scaffoldingHints)(GptInteractionElement.contentIdsSerializer)
    .withElementAdded("gradingCriteria", gradingCriteria)(GptInteractionElement.contentIdsSerializer)

  override val factoryMethod: WorkbookElementFactory[_ <: WorkbookElement] = GptInteractionElement.factory
}




