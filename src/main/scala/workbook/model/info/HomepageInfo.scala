package workbook.model.info

import datastructures.core.language.HumanLanguage
import workbook.model.Workbook
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.FullInfo.HomepageDefaults
import workbook.model.info.{AllUserInfo, AllWorkbookInfo}


case class HomepageInfo(
                         private[info] val homepageDefaults: HomepageDefaults,
                         currentLanguage: HumanLanguage,
                         workbookInfo: Option[AllWorkbookInfo],
                         userInfo: Option[AllUserInfo],
                       ) {


}


