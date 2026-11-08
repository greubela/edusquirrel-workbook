package it.evadid.homepage.control.info

import it.evadid.core.util.InfoUtil
import it.evadid.homepage.control.model.*
import it.evadid.util.DownloadToDisc
import it.evadid.util.logging.Logger
import upickle.default.*

object WorkbookUserDataAnalyzer {


}

case class WorkbookUserDataAnalyzer() {

  def downloadAllData(fullInfo: FullInfo): Unit = {
    downloadAllData(fullInfo.loggerSystemInfo.contentControlLogger, fullInfo.contentControl.downloadToDisc, fullInfo.homepageInfoNow())
  }

  def downloadAllData(logger: Logger, downloadToDisc: DownloadToDisc, homepageInfo: HomepageInfo): Unit = {
    logger.logInfo("WorkbookUserDataAnalyzer: now downloading all session data!")
    val dataOp = SessionData.createSessionData(homepageInfo)
    if (dataOp.nonEmpty) {
      val data = dataOp.get
      val name = s"${InfoUtil.datetimeFormattedForFilenames(data.timestampStorage)}-eva-${data.userInfo.id}-${data.workbookInfo.loadedWorkbook.elementId.replace("[/]", "-")}.eva"
      downloadToDisc.downloadFile(name, write(data))
    }
  }

  /*
    private def upload(file: FileDescription): Unit = {
      logger.logInfo(s"WorkbookUserDataAnalyzer: Trying to load prior session data based on file ${file.asUrlString}!")
      file.loadData().foreach(loadedFile => {
        val str = loadedFile.fileDataAsUtf8String
        val data: SessionData = upickle.default.read(str)

        tryToLoad(data)
      })(using ExecutionContext.global)
    }
  */

}
