package it.evadid.workbook.elements.structureElements

import cats.data
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.util.logging.Logger
import it.evadid.workbook.abstractions.WorkbookStructuringType.WORKBOOK
import it.evadid.workbook.abstractions.*
import it.evadid.workbook.elements.structureElements.Workbook.WorkbookMetadata
import it.evadid.workbook.elements.structureElements.*
import it.evadid.workbook.interaction.sync.SyncContext
import it.evadid.workbook.interaction.sync.model.InteractionHistoryJson
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}
import it.evadid.workbook.meta.ExecutionContextInformation
import ujson.Value
import upickle.default
import upickle.default.*

case class Workbook(
                     elementId: String,
                     metadata: WorkbookMetadata,
                     sections: Seq[WorkbookSection],
                   ) extends WorkbookStructureElement[WorkbookSection] {

  val workbookId: String = elementId

  override val groupElements: Seq[WorkbookSection] = sections

  override lazy val structureType: WorkbookStructuringType = WORKBOOK

  lazy val allContainedInteractionsById: Map[String, WorkbookInteractionElement[? <: InteractionFormat]] =
    allInteractionsInThisInstance.map(interaction => interaction.elementId -> interaction).toMap

  override val associatedFactory: WorkbookElementFactory[? <: WorkbookElement] = Workbook.factory


  /** Restores saved histories of this workbook into its interactions; the user is not checked. Histories of
    * other workbooks are ignored, even where their interactions reuse this workbook's element ids.
    */
  def restoreHistories(logger: Logger, data: Map[SyncContext, InteractionHistoryJson], executionContextInformation: ExecutionContextInformation): Unit = {
    val own = data.collect { case (key, history) if key.scenarioId == workbookId => key.keyForSerialisation -> history }
    allInteractionsInThisInstance.foreach(interaction => own.get(interaction.interactionVariable.keyForSerialization)
      .foreach(history => interaction.interactionVariable.restoreHistory(logger, history, executionContextInformation)))
  }
}


object Workbook {
  given ReadWriter[Workbook] = WorkbookElement.subtypeCodec


  case class WorkbookMetadata(
                               author: Set[User],
                               contributors: Set[User],
                               workbookTitle: LanguageMapContentId,
                               availableLanguages: Seq[HumanLanguage]
                             ) derives ReadWriter {

  }


  lazy val factory: WorkbookElementFactory[Workbook] = new WorkbookElementFactory[Workbook]() {
    override def serializedElementKeysThatContainOtherSerializations(element: WorkbookElementSerializable): Set[String] = {
      Set("serializedElements")
    }

    def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = {
      element.getElementsAs[WorkbookElementReference]("sections").map(curRef => curRef.referencedId).toSet
    }

    def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): Workbook = {
      val sections = element.getAndResolveWorkbookElements[WorkbookSection]("sections", parsedElements)
      Workbook(
        element.elementId,
        element.getElementAs[WorkbookMetadata]("metadata"),
        sections
      )
    }

    override lazy val elementMapAndOrderForConstructorLike: Map[Int, Seq[ConstructorLikeSerializer.VariableDisplayConfig]] = {
      Map(
        0 -> List(VariableDisplayConfig("elementId", true)),
        1 -> List(VariableDisplayConfig("metadata", true)),
        2 -> List(VariableDisplayConfig("sections", false)),
        3 -> List(VariableDisplayConfig("serializedElements", false))
      )
    }
    override lazy val writerJsonRegularRefBased: default.Writer[Workbook] = writer[ujson.Value].comap { workbook =>
      ujson.Obj.from(
        Map(
          "elementId" -> writeJs(workbook.elementId),
          "metadata" -> writeJs(workbook.metadata),
          "sections" -> writeJs(workbook.sections.map(_.asRef))
        ) ++ addElementsToSerialization(workbook)
      )
    }

    override def addElementsToSerialization(element: Workbook): Map[String, Value] = {
      val allRequiredIds = element.sections.map(_.elementId).toSet ++ element.allChildrenFullSubtree.flatMap { child =>
        child.associatedFactory.idsRequiredForDeserialization(child.toSerialized)
      }
      val requiredToSerialize = element.allChildrenFullSubtree.filter(child => allRequiredIds.contains(child.elementId))
      Map(
        "serializedElements" -> writeJs(requiredToSerialize.map(_.toSerialized))
      )
    }
  }
}
