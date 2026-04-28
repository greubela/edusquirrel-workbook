package workbook.model.info

import it.evadid.core.datastructures.language.AppLanguage.*
import workbook.model.info.FullInfo.HomepageDefaults

case class HomepageInfo(
                         private[info] val homepageDefaults: HomepageDefaults,
                         currentLanguage: HumanLanguage,
                         workbookInfo: Option[AllWorkbookInfo],
                         userInfo: Option[AllUserInfo],
                       ) {


}


