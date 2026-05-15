package workbook.model.info

import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.core.datastructures.state.{State, StateHelper}
import org.scalajs.dom
import workbook.htmlElements.TechnicalHomepageElements
import workbook.htmlElements.container.HtmlFullScreenContainerElement
import workbook.model.info.control.{HomepageCurrentInfo, HomepageDataControl, HomepageSignalInfo, TechnicalControl}
import workbook.model.interaction.sync.SyncInformation
import workbook.singletons.FileDataStorage
import workbook.user.User

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
