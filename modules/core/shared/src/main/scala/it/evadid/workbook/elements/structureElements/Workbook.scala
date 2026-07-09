package it.evadid.workbook.elements.structureElements

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.model.abstractions.*
import it.evadid.workbook.model.abstractions.WorkbookStructuringType.{EXERCISE_CONTAINER, WORKBOOK}

case class Workbook(
                     workbookId: String,
                     workbookTitle: LanguageMapContentId,
                     sections: List[WorkbookSection],
                     availableLanguages: List[HumanLanguage]
                   ) extends WorkbookStructureElement[WorkbookSection] {


  override val groupElements: List[WorkbookSection] = sections

  override lazy val structureType: WorkbookStructuringType = WORKBOOK

  lazy val allContainedInteractionsById: Map[String, WorkbookInteractionElement[?]] =
    allContainedInteractions.map(interaction => interaction.id -> interaction).toMap

}

