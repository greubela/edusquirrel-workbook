package it.evadid.homepage.workbook.legacy.model.info.control

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.workbook.content.WorkbookFactory
import it.evadid.homepage.workbook.legacy.model.info.{AllUserInfo, AllWorkbookInfo, FullInfo, WorkbookConfig}
import it.evadid.workbook.model.interaction.WorkbookInteraction
case class HomepageDataControl(fullInfo: FullInfo) {

  private def interactions: List[WorkbookInteraction[?]] = fullInfo.current.allAvailableInteractions


  def downloadAllAvailableData(): Unit = fullInfo.current.workbookUserData.foreach(_.downloadAllData())

  def saveAndResetAllInfo(): Unit = fullInfo.synchronized {
    val curHomepageInfo = fullInfo.homepageInfoState.now()
    // Save everything that is still present
    interactions.foreach(_.interactionVariable.syncToAll())
    downloadAllAvailableData()
    // Clear old Status
    fullInfo.technical.resetLocalStorage()
    println("HomepageDataControl::saveAndResetAllInfo [WARN] not implemented propely, now removing all sync destinations!")
    interactions.foreach(curInteraction => curInteraction.resetInteraction(syncBefore = false, syncAfter = false, List()))
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

    // set new info into var
    fullInfo.homepageInfoState.update(curInfo => curInfo.copy(userInfo = userInfo))
    // propagate
    println("HomepageDataControl::changeUser [WARN] not implemented propely, now removing all sync destinations!")
    interactions.foreach(_.resetInteraction(syncBefore = false, syncAfter = true, List()))
  }

  def changeLanguage(language: HumanLanguage): Unit = fullInfo.synchronized {
    fullInfo.homepageInfoState.update(_.copy(currentLanguage = language))
  }


}
