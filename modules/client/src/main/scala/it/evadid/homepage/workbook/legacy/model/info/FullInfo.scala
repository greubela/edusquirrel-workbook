package it.evadid.homepage.workbook.legacy.model.info

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.state.{State, StateHelper}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.legacy.htmlElements.TechnicalHomepageElements
import it.evadid.homepage.workbook.legacy.htmlElements.container.HtmlFullScreenContainerElement
import it.evadid.homepage.workbook.legacy.model.info.control.{HomepageCurrentInfo, HomepageDataControl, HomepageSignalInfo, TechnicalControl}
import it.evadid.homepage.workbook.legacy.singletons.FileDataStorage
import it.evadid.homepage.workbook.legacy.user.User
import it.evadid.workbook.model.interaction.sync.SyncInformation
import org.scalajs.dom

case class FullInfo(
                     private[info] val defaults: HomepageDefaults,
                     val technical: TechnicalControl,
                     private[info] val initInfo: HomepageInfo) {


  private[info] val homepageInfoState: Var[HomepageInfo] = Var(initInfo)
  // private[info] val homepageInfoState2: Var[HomepageInfo] = State(defaultInfo).toAirstreamVar
  /*  homepageInfoVar.signal.foreach(onNext => {
      println("Changed homepageInfoVar at: " + new Exception().getStackTrace().take(3).map(_.getMethodName).mkString(" -> ") + " (to: " + onNext.toString + ")")
    })(unsafeWindowOwner)*/

  def control: HomepageDataControl = HomepageDataControl(this)

  def signals: HomepageSignalInfo = HomepageSignalInfo(this)

  def current: HomepageCurrentInfo = HomepageCurrentInfo(this)

}

object FullInfo {


}
