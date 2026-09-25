package it.evadid.workbook.elements.structureElements

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.workbook.abstractions.WorkbookStructuringType.WORKBOOK
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement, WorkbookStructureElement, WorkbookStructuringType}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

case class Workbook(
                     workbookId: String,
                     workbookTitle: LanguageMapContentId,
                     sections: List[WorkbookSection],
                     availableLanguages: List[HumanLanguage]
                   ) extends WorkbookStructureElement[WorkbookSection] {

  override val groupElements: List[WorkbookSection] = sections

  override lazy val structureType: WorkbookStructuringType = WORKBOOK

  lazy val allContainedInteractionsById: Map[String, WorkbookInteractionElement[?]] =
    allContainedInteractions.map(interaction => interaction.elementId -> interaction).toMap

  override val elementId: String = workbookId

  override val associatedFactory: WorkbookElementFactory[? <: WorkbookElement] = Workbook.factory

}


object Workbook {


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
        element.getElementAs[LanguageMapContentId]("title"),
        sections,
        element.getElementsAs[AppLanguage]("availableLanguages").map(_.asInstanceOf[HumanLanguage])
      )
    }

    override def toSerializableElement(element: Workbook): WorkbookElementSerializable = {
      val allRequiredIds = element.allChildrenFullSubtree.flatMap(el => el.associatedFactory.idsRequiredForDeserialization(el.toSerializableType))
      val requiredToSerialize = element.allChildrenFullSubtree.filter(curChild => allRequiredIds.contains(curChild.elementId))

      toFactoryBase(element)
        .withElementAddedAs("title", element.workbookTitle)
        .withElementsAdded("test", element.sections.map(_.sectionId))
        .withElementsAddedAs[AppLanguage]("availableLanguages", element.availableLanguages.map(_.asInstanceOf[AppLanguage]))
        .withElementsAddedAs[WorkbookElementSerializable]("serializedElements", requiredToSerialize.map(_.toSerializableType))(using WorkbookElementSerializable.serializer.uPickleReadWrite)
        .withElementsAddedAs[WorkbookElementReference]("sections", element.sections.map(_.asRef))
    }
  }


}
