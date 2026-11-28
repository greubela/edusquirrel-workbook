package it.evadid.homepage.control

import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}

/** Binds exercises that are not part of the page's workbook, as a workbook containing them would. */
object TestAnswers {
  def bind(fullInfo: FullInfo, interactions: WorkbookInteractionElement[? <: InteractionFormat]*): Unit = {
    val page = fullInfo.homepageInfoNow()
    fullInfo.interactions.bind(page.toContext, fullInfo.interactions.info.interactions ++ interactions, fullInfo.connectionFor(page))
  }
}
