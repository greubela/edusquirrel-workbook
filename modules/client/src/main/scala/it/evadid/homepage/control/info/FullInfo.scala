package it.evadid.homepage.control.info

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.state.{State, StateHelper}
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.TechnicalHomepageElements
import it.evadid.homepage.control.info.control.{HomepageCurrentInfo, HomepageDataControl, HomepageSignalInfo, TechnicalControl}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlFullScreenContainerElement
import it.evadid.homepage.workbook.legacy.singletons.FileDataStorage
import it.evadid.workbook.model.interaction.sync.SyncInformation
import org.scalajs.dom

case class FullInfo(
                     private[info] val defaults: HomepageDefaults,
                     val technical: TechnicalControl,
                     private[info] val initInfo: HomepageInfo) {


  private[info] val homepageInfoState: Var[HomepageInfo] = Var(initInfo)
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
