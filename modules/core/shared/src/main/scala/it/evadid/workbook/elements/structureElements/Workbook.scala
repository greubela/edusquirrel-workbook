package it.evadid.workbook.elements.structureElements

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.workbook.abstractions.WorkbookStructuringType.WORKBOOK
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement, WorkbookStructureElement, WorkbookStructuringType}
import it.evadid.workbook.elements.structureElements.Workbook.WorkbookMetadata
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}
import ujson.Value
import upickle.default
import upickle.default.*

case class Workbook(
                     elementId: String,
                     metadata: WorkbookMetadata,
                     sections: List[WorkbookSection],
                   ) extends WorkbookStructureElement[WorkbookSection] {

  val workbookId: String = elementId

  override val groupElements: List[WorkbookSection] = sections

  override lazy val structureType: WorkbookStructuringType = WORKBOOK

  lazy val allContainedInteractionsById: Map[String, WorkbookInteractionElement[?]] =
    allContainedInteractions.map(interaction => interaction.elementId -> interaction).toMap

  override val associatedFactory: WorkbookElementFactory[? <: WorkbookElement] = Workbook.factory

}


object Workbook {

  case class WorkbookMetadata(
                               author: User,
                               contributors: Set[User],
                               workbookTitle: LanguageMapContentId,
                               availableLanguages: List[HumanLanguage]
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

    override lazy val elementMapAndOrderForConstructorLike: Map[Int, List[ConstructorLikeSerializer.VariableDisplayConfig]] = {
      Map(
        0 -> List(VariableDisplayConfig("elementId", true)),
        1 -> List(VariableDisplayConfig("metadata", true)),
        2 -> List(VariableDisplayConfig("sections", false))
      )
    }
    override lazy val writerJsonRegularRefBased: default.Writer[Workbook] = writer[ujson.Value].comap { workbook =>
      ujson.Obj(
        "elementId" -> workbook.elementId,
        "metadata" -> writeJs(workbook.metadata),
        "sections" -> writeJs(workbook.sections.map(_.asRef))
      )
    }

    override def addElementsToSerialization(element: Workbook): Map[String, Value] = {
      val allRequiredIds = element.allChildrenFullSubtree.flatMap(el => el.associatedFactory.idsRequiredForDeserialization(el.associatedFactory.toSerializableElementUnsafe(el)))
      val requiredToSerialize = element.allChildrenFullSubtree.filter(curChild => allRequiredIds.contains(curChild.elementId))
      Map(
        "serializedElements" -> writeJs(requiredToSerialize.map(el => el.associatedFactory.toStringConstructorLikeUnsafe(el)))
      )
    }
  }
}
