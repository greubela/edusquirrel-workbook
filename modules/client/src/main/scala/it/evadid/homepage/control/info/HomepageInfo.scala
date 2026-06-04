package it.evadid.homepage.control.info

import it.evadid.core.datastructures.language.AppLanguage.*

case class HomepageInfo(
                         private[info] val homepageDefaults: HomepageDefaults,
                         currentLanguage: HumanLanguage,
                         workbookInfo: Option[AllWorkbookInfo],
                         userInfo: Option[AllUserInfo],
                       ) {


  override val toString: String = s"HomepageInfo(userInfo: $userInfo, workbookInfo: $workbookInfo)"

}


