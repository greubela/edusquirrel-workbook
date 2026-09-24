package it.evadid.workbook.elements.structureElements

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.workbook.abstractions.WorkbookStructuringType.WORKBOOK
import it.evadid.workbook.abstractions.{WorkbookInteractionElement, WorkbookStructureElement, WorkbookStructuringType}
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

case class Workbook(
                     workbookId: String,
                     workbookTitle: LanguageMapContentId,
                     sections: List[WorkbookSection],
                     availableLanguages: List[HumanLanguage],
                   ) extends WorkbookStructureElement[WorkbookSection] {

  override val groupElements: List[WorkbookSection] = sections

  override lazy val structureType: WorkbookStructuringType = WORKBOOK

  lazy val allContainedInteractionsById: Map[String, WorkbookInteractionElement[?]] =
    allContainedInteractions.map(interaction => interaction.elementId -> interaction).toMap

  override val elementId: String = workbookId

  override def toSerializableType: WorkbookElementSerializable = {
    toFactoryBase
      .withContentIdAdded("title", workbookTitle)
      .withSerializedElementsAdded("sections", sections)
  }
}


object Workbook {
  def fromFactory(f: WorkbookElementSerializable): Workbook = {
    Workbook(f.elementId, f.getElementAsContentId("title"), f.getElementAsSerializedElements("sections").map(_.asInstanceOf[WorkbookSection]), List.empty)
  }
}
