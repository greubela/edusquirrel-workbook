package it.evadid.homepage.control.info

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.model.interaction.sync.UsageContext

case class HomepageInfo(
                         private[info] val homepageDefaults: HomepageDefaults,
                         currentLanguage: HumanLanguage,
                         workbookInfo: Option[AllWorkbookInfo],
                         userInfo: Option[AllUserInfo],
                       ) {


  override val toString: String = s"HomepageInfo(userInfo: $userInfo, workbookInfo: $workbookInfo)"

  lazy val toContext = UsageContext("edusquirrel", workbookInfo.map(_.loadedWorkbook.workbookId).getOrElse("[no workbook]"), userInfo.map(_.user.id).getOrElse("[no user]"))


}

object HomepageInfo {


}


