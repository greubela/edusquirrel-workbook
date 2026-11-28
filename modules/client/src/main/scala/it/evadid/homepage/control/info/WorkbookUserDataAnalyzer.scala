package it.evadid.homepage.control.info

import it.evadid.workbook.model.InteractionInfo

import it.evadid.homepage.control.model.*
import it.evadid.util.DownloadToDisc
import it.evadid.util.logging.Logger
import upickle.default.*

object WorkbookUserDataAnalyzer {


}

case class WorkbookUserDataAnalyzer() {

  def downloadAllData(fullInfo: FullInfo): Unit = {
    downloadAllData(fullInfo.loggerSystemInfo.contentControlLogger, fullInfo.contentControl.downloadToDisc, fullInfo.homepageInfoNow(), fullInfo.interactions.info)
  }

  def downloadAllData(logger: Logger, downloadToDisc: DownloadToDisc, homepageInfo: HomepageInfo, answers: InteractionInfo): Unit = {
    logger.logInfo("WorkbookUserDataAnalyzer: now downloading all session data!")
    val dataOp = homepageInfo.toSessionData(answers)
    if (dataOp.nonEmpty) {
      val data = dataOp.get
      downloadToDisc.downloadFile(data.fileName, write(data))
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
