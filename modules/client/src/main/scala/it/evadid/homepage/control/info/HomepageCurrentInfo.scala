package it.evadid.homepage.control.info

import it.evadid.workbook.session.AllWorkbookInfo
import it.evadid.core.datastructures.user.AllUserInfo
import it.evadid.homepage.control.model.*
import it.evadid.workbook.abstractions.{InteractionFormat, WorkbookInteractionElement}

/** Current values of the central homepage state, for imperative callers outside signals. */
case class HomepageCurrentInfo(fullInfo: FullInfo) {

  private def now(): HomepageInfo = fullInfo.homepage.now()

  def workbookInfo: Option[AllWorkbookInfo] = now().workbookInfo

  def userInfo: Option[AllUserInfo] = now().userInfo

  def allAvailableInteractions: Seq[WorkbookInteractionElement[? <: InteractionFormat]] =
    now().workbookInfo.map(_.loadedWorkbook.allInteractionsInThisInstance).getOrElse(Seq.empty)
}
