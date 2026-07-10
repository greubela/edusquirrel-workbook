package it.evadid.homepage.control.model

import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.change.*
import it.evadid.homepage.control.info.*
import it.evadid.homepage.control.singletons.HomepageDefaults
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.workbook.interaction.sync.SyncControl

case class FullInfo(
                     val defaults: HomepageDefaults,
                     val technical: TechnicalControl,
                     private[control] val initInfo: HomepageInfo
                   ) {

  lazy val loggerSystemInfo: HomepageLoggerInfo = HomepageLoggerInfo.singleton

  private[control] val homepageInfoState: Var[HomepageInfo] = Var(initInfo)

  lazy val control: HomepageUsageControl = HomepageUsageControl(this)

  lazy val signals: HomepageSignalInfo = HomepageSignalInfo(this)

  lazy val current: HomepageCurrentInfo = HomepageCurrentInfo(this)

  lazy val syncControl: SyncControl = RemoteInteractionCacheControl(this)


}

object FullInfo {


}
