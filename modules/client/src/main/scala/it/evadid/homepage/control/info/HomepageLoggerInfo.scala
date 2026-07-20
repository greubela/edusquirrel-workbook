package it.evadid.homepage.control.info

import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
import it.evadid.util.logging.{BasicLogger, Logger}

case class HomepageLoggerInfo() {

  lazy val uiAndDomLogger: Logger = Logger.withNameAndPrefixes(Some("UserInterface"), PrintToStdLogger.printWarnAndError)

  lazy val workbookElementLogger: Logger = Logger.withNameAndPrefixes(Some("WorkbookElementLogger"), PrintToStdLogger.printEverything)

  lazy val contentControlLogger: Logger = Logger.withNameAndPrefixes(Some("WorkbookHomepageControl"), PrintToStdLogger.printEverything)

  lazy val contentStorageLogger: Logger = Logger.withNameAndPrefixes(Some("WorkbookContentControl"), PrintToStdLogger.printWarnAndError)

  lazy val syncControlLogger: SyncLogger = SyncLogger(PrintToStdLogger(BasicLogger(), PrintToStdLogger.printWarnAndError))

  lazy val syncCacheLogger: Logger = Logger.withNameAndPrefixes(Some("SyncCacheLogger"), PrintToStdLogger.printWarnAndError)

  lazy val fileDataStorageLogger: Logger = Logger.withNameAndPrefixes(Some("FileDataStorage"), PrintToStdLogger.printError)

}

object HomepageLoggerInfo {

  lazy val singleton = HomepageLoggerInfo()

}
