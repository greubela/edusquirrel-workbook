package workbook.model.info.control

import com.raquo.laminar.api.L.*
import content.WorkbookFactory
import datastructures.core.language.HumanLanguage
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.analyze.WorkbookUserDataAnalyzer
import workbook.model.info.*

case class HomepageDataControl(fullInfo: FullInfo) {

  private def interactions: List[WorkbookInteraction[_]] = fullInfo.current.allAvailableInteractions


  def downloadAllAvailableData(): Unit = fullInfo.current.workbookUserData.foreach(_.downloadAllData())

  def saveAndResetAllInfo(): Unit = fullInfo.synchronized {
    val curHomepageInfo = fullInfo.homepageInfoVar.now()
    // Save everything that is still present
    interactions.foreach(_.interactionVariable.syncToAll())
    downloadAllAvailableData()
    // Clear old Status
    FullInfo.resetLocalStorage()
    interactions.foreach(_.resetInteraction(syncBefore = false, syncAfter = false))
  }

  def changeWorkbook(factory: WorkbookFactory): Unit = {
    //Future {
    val workbook = factory.createEverything
    changeWorkbook(workbook)
    //}(ExecutionContext.global)
  }

  def changeWorkbook(newWorkbook: AllWorkbookInfo): Unit = fullInfo.synchronized {
    //saveAndResetAllInfo()
    fullInfo.homepageInfoVar.update(curInfo => curInfo.copy(workbookInfo = Some(newWorkbook)))
    interactions.foreach(_.interactionVariable.syncFromAll())
  }

  def updateWorkbookConfig(func: WorkbookConfig => WorkbookConfig): Unit = fullInfo.synchronized {
    if (fullInfo.homepageInfoVar.now().workbookInfo.isEmpty) throw new Exception("No workbook loaded!")
    fullInfo.homepageInfoVar.update(curInfo => curInfo.copy(workbookInfo = curInfo.workbookInfo.map(curWorkbook => curWorkbook.copy(config = func(curWorkbook.config)))))
  }

  def changeUser(userInfo: Option[AllUserInfo]): Unit = fullInfo.synchronized {
    //saveAndResetAllInfo() //todo without dummy

    // set new info into var
    fullInfo.homepageInfoVar.update(curInfo => curInfo.copy(userInfo = userInfo))
    // propagate
    interactions.foreach(_.resetInteraction(syncBefore = false, syncAfter = true))
  }

  def changeLanguage(language: HumanLanguage): Unit = fullInfo.synchronized {
    fullInfo.homepageInfoVar.update(_.copy(currentLanguage = language))
  }


}
