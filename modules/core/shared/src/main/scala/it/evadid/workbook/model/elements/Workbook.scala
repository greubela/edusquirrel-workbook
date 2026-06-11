package it.evadid.workbook.model.elements

import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.model.abstractions.*
import it.evadid.workbook.model.interaction.WorkbookInteraction

case class Workbook(
                     workbookId: String,
                     workbookTitle: LanguageMapContentId,
                     sections: List[WorkbookSection],
                     availableLanguages: List[HumanLanguage]
                   ) extends WorkbookElementGroup[WorkbookSection] {


  override val groupElements: List[WorkbookSection] = sections

  override val groupType: Option[WorkbookGroupType] = Some(WorkbookGroupType.WORKBOOK)

  lazy val allContainedInteractionsById: Map[String, WorkbookInteraction[?]] =
    allContainedInteractions.map(interaction => interaction.id -> interaction).toMap

}

