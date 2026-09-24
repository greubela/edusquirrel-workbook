package it.evadid.workbook.elements.structureElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookStructureElement, WorkbookStructuringType}
import it.evadid.workbook.abstractions.WorkbookStructuringType.SECTION
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

case class WorkbookSection(
                            sectionId: String,
                            sectionTitle: LanguageMapContentId,
                            sectionContent: List[WorkbookElement],
                            sectionsRequiredBefore: List[WorkbookSection] = List(),
                            sectionsRecommendedBefore: List[WorkbookSection] = List()
                          ) extends WorkbookStructureElement[WorkbookElement] {

  override val groupElements: List[WorkbookElement] = sectionContent

  override lazy val structureType: WorkbookStructuringType = SECTION

  override val elementId: String = sectionId

  override def toSerializableType: WorkbookElementSerializable = toFactoryBase.withContentIdAdded("title", sectionTitle)
    .withSerializedElementsAdded("content", sectionContent)
    .withSerializedElementsAdded("required", sectionsRequiredBefore)
    .withSerializedElementsAdded("recommended", sectionsRecommendedBefore)
}

object WorkbookSection { def fromFactory(f: WorkbookElementSerializable): WorkbookSection = WorkbookSection(f.elementId, f.getElementAsContentId("title"), f.getElementAsSerializedElements("content"), f.getElementAsSerializedElements("required").map(_.asInstanceOf[WorkbookSection]), f.getElementAsSerializedElements("recommended").map(_.asInstanceOf[WorkbookSection])) }
