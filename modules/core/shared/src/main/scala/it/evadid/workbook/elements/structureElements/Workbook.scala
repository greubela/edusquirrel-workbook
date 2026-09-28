package it.evadid.workbook.elements.structureElements

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer
import it.evadid.workbook.abstractions.WorkbookStructuringType.WORKBOOK
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement, WorkbookStructureElement, WorkbookStructuringType}
import it.evadid.workbook.elements.structureElements.Workbook.WorkbookMetadata
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}
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

  private val regularSerializer: ReadWriter[Workbook] = macroRW

  val constructorSerializer: Serializer[Workbook] = new ConstructorLikeSerializer[Workbook] {
    override implicit val regularSerializer: Serializer[Workbook] = Serializer.fromUpickleJson(Workbook.regularSerializer)
    override val constructorName: String = Workbook.this.getClass.getSimpleName

    override val elementMapAndOrder: Map[Int, List[VariableDisplayConfig]] = {
      {
        Map(
          0 -> List(VariableDisplayConfig("elementId", true)),
          1 -> List(VariableDisplayConfig("metadata", true)),
          2 -> List(VariableDisplayConfig("sections", false))
        )
      }
    }
  }

  lazy val factory: WorkbookElementFactory[Workbook] = new WorkbookElementFactory[Workbook]() {
    override def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable] = {
      element.getElementsAs[WorkbookElementSerializable]("serializedElements")
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

    override def toSerializableElement(element: Workbook): WorkbookElementSerializable = {
      val allRequiredIds = element.allChildrenFullSubtree.flatMap(el => el.associatedFactory.idsRequiredForDeserialization(el.toSerializableType))
      val requiredToSerialize = element.allChildrenFullSubtree.filter(curChild => allRequiredIds.contains(curChild.elementId))

      toFactoryBase(element)
        .withElementAddedAs("metadata", element.metadata)
        .withElementsAdded("test", element.sections.map(_.sectionId))
        .withElementsAddedAs[WorkbookElementSerializable]("serializedElements", requiredToSerialize.map(_.toSerializableType))
        .withElementsAddedAs[WorkbookElementReference]("sections", element.sections.map(_.asRef))
    }
  }


}
