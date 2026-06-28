package it.evadid.homepage.control.model

import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.change.*
import it.evadid.homepage.control.info.*

case class FullInfo(
                     val defaults: HomepageDefaults,
                     val technical: TechnicalControl,
                     private[control] val initInfo: HomepageInfo
                   ) {

  lazy val loggerSystemInfo: HomepageLoggerInfo = HomepageLoggerInfo.singleton

  private[control] val homepageInfoState: Var[HomepageInfo] = Var(initInfo)

  lazy val control: HomepageDataControl = HomepageDataControl(this)

  lazy val signals: HomepageSignalInfo = HomepageSignalInfo(this)

  lazy val current: HomepageCurrentInfo = HomepageCurrentInfo(this)


}

object FullInfo {


}
