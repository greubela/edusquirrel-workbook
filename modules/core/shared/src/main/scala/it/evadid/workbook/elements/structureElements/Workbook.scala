package it.evadid.workbook.elements.structureElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.WorkbookStructuringType.WORKBOOK
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement, WorkbookStructureElement, WorkbookStructuringType}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}

case class Workbook(
                     workbookId: String,
                     workbookTitle: LanguageMapContentId,
                     sections: List[WorkbookSection]
                   ) extends WorkbookStructureElement[WorkbookSection] {

  override val groupElements: List[WorkbookSection] = sections

  override lazy val structureType: WorkbookStructuringType = WORKBOOK

  lazy val allContainedInteractionsById: Map[String, WorkbookInteractionElement[?]] =
    allContainedInteractions.map(interaction => interaction.elementId -> interaction).toMap

  override val elementId: String = workbookId

  override val associatedFactory: WorkbookElementFactory[_ <: WorkbookElement] = Workbook.factory

}


object Workbook {

  lazy val factory: WorkbookElementFactory[Workbook] = new WorkbookElementFactory[Workbook]() {
    override def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable] = {
      element.getElementsAsSerializedElements("serializedElements")
    }

    def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] = {
      element.getElementsAsWorkbookReferences("sections").map(curRef => curRef.referencedId).toSet
    }

    def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): Workbook = {
      val sections = element.getAndResolveWorkbookElements[WorkbookSection]("sections", parsedElements)
      Workbook(element.elementId, element.getElementAsContentId("title"), sections)
    }

    override def toSerializableElement(element: Workbook): WorkbookElementSerializable = {
      val allRequiredIds = element.allChildrenRec.flatMap(curEl => curEl.associatedFactory.idsRequiredForDeserialization(curEl.toSerializableType))
      val requiredToSerialize = element.allChildrenRec.filter(curChild => allRequiredIds.contains(curChild.elementId))
      toFactoryBase(element)
        .withContentIdAdded("title", element.workbookTitle)
        .withSerializationsAdded("serializedElements", requiredToSerialize.map(_.toSerializableType))
        .withReferencesAdded("sections", element.sections.map(_.asRef))
    }
  }


}
