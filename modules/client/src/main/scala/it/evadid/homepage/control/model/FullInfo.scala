package it.evadid.homepage.control.model

import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.change.*
import it.evadid.homepage.control.info.*

case class FullInfo(
                     val defaults: HomepageDefaults,
                     val technical: TechnicalControl,
                     private[control] val initInfo: HomepageInfo) {


  private[control] val homepageInfoState: Var[HomepageInfo] = Var(initInfo)
  /* private[info] val homepageInfoState2: Var[HomepageInfo] = State(defaultInfo).toAirstreamVar
  homepageInfoState.signal.foreach(onNext => {
    println("Changed homepageInfoVar at: " + new Exception().getStackTrace().take(6).map(_.getMethodName).mkString(" -> ") + " (to: " + onNext.toString + ")")
  })(using unsafeWindowOwner)*/

  lazy val control: HomepageDataControl = HomepageDataControl(this)

  lazy val signals: HomepageSignalInfo = HomepageSignalInfo(this)

  lazy val current: HomepageCurrentInfo = HomepageCurrentInfo(this)

}

object FullInfo {


}
