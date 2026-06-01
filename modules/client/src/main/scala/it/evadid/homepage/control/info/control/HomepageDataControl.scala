package it.evadid.homepage.control.info.control

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.control.info.{AllUserInfo, AllWorkbookInfo, FullInfo, WorkbookConfig}
import it.evadid.homepage.workbook.content.WorkbookFactory
import it.evadid.workbook.model.interaction.WorkbookInteraction

case class HomepageDataControl(fullInfo: FullInfo) {

  private def interactions: List[WorkbookInteraction[?]] = fullInfo.current.allAvailableInteractions


  def downloadAllAvailableData(): Unit = fullInfo.current.workbookUserData.foreach(_.downloadAllData())

  def saveAndResetAllInfo(): Unit = fullInfo.synchronized {
    // Save everything that is still present
    interactions.foreach(_.interactionVariable.syncToAll(true))
    downloadAllAvailableData()
    // Clear old Status
    fullInfo.technical.resetLocalStorage()
    interactions.foreach(_.clearHistory(false))
  }

  def changeWorkbook(factory: WorkbookFactory): Unit = {
    //Future {
    val workbook = factory.createEverything
    changeWorkbook(workbook)
    //}(ExecutionContext.global)
  }

  def changeWorkbook(newWorkbook: AllWorkbookInfo): Unit = fullInfo.synchronized {
    //saveAndResetAllInfo()
    fullInfo.homepageInfoState.update(curInfo => curInfo.copy(workbookInfo = Some(newWorkbook)))
    interactions.foreach(_.interactionVariable.syncFromAll())

  }

  def updateWorkbookConfig(func: WorkbookConfig => WorkbookConfig): Unit = fullInfo.synchronized {
    if (fullInfo.homepageInfoState.now().workbookInfo.isEmpty) throw new Exception("No workbook loaded!")
    fullInfo.homepageInfoState.update(curInfo => curInfo.copy(workbookInfo = curInfo.workbookInfo.map(curWorkbook => curWorkbook.copy(config = func(curWorkbook.config)))))
  }

  def changeUser(userInfo: Option[AllUserInfo]): Unit = fullInfo.synchronized {
    //saveAndResetAllInfo() //todo without dummy

    interactions.foreach(_.interactionVariable.syncToAll(true))

    val syncDest = userInfo.map(_.config.syncDestinations).getOrElse(fullInfo.defaults.defaultSyncLocation)
    fullInfo.homepageInfoState.update(curInfo => curInfo.copy(userInfo = userInfo))

    interactions.foreach(_.resetInteraction(syncBefore = false, syncAfter = true, syncDest))
  }

  def changeLanguage(language: HumanLanguage): Unit = fullInfo.synchronized {
    fullInfo.homepageInfoState.update(_.copy(currentLanguage = language))
  }


}
