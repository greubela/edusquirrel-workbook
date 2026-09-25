package it.evadid.workbook.elements.structureElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.WorkbookStructuringType.SECTION
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookStructureElement, WorkbookStructuringType}
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementReference, WorkbookElementSerializable}

case class WorkbookSection(
                            sectionId: String,
                            sectionTitle: LanguageMapContentId,
                            sectionContent: List[WorkbookElement],
                            sectionsRequiredBefore: List[WorkbookSection] = List(),
                            sectionsRecommendedBefore: List[WorkbookSection] = List()
                          ) extends WorkbookStructureElement[WorkbookElement] {
  override val associatedFactory = WorkbookSection.factory

  override val groupElements: List[WorkbookElement] = sectionContent

  override lazy val structureType: WorkbookStructuringType = SECTION

  override val elementId: String = sectionId


}

object WorkbookSection {
  val factory: WorkbookElementFactory[WorkbookSection] = new WorkbookElementFactory[WorkbookSection] {
    private def references(element: WorkbookElementSerializable, key: String) =
      element.getElementsAs[WorkbookElementReference](key)

    override def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String] =
      (references(element, "content") ++ references(element, "requiredBefore") ++ references(element, "recommendedBefore"))
        .map(_.referencedId).toSet

    override def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable] = Seq.empty

    override def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): WorkbookSection =
      WorkbookSection(
        element.elementId,
        element.getElementAs[LanguageMapContentId]("title"),
        element.getAndResolveWorkbookElements("content", parsedElements),
        element.getAndResolveWorkbookElements("requiredBefore", parsedElements),
        element.getAndResolveWorkbookElements("recommendedBefore", parsedElements)
      )

    override def toSerializableElement(element: WorkbookSection): WorkbookElementSerializable =
      toFactoryBase(element)
        .withElementAddedAs("title", element.sectionTitle)
        .withElementsAddedAs[WorkbookElementReference]("content", element.sectionContent.map(_.asRef))
        .withElementsAddedAs[WorkbookElementReference]("requiredBefore", element.sectionsRequiredBefore.map(_.asRef))
        .withElementsAddedAs[WorkbookElementReference]("recommendedBefore", element.sectionsRecommendedBefore.map(_.asRef))
  }
}
