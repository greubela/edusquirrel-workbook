package it.evadid.homepage.control.model

import it.evadid.util.logging.{BasicLogger, Logger}
import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}

case class HomepageLoggerInfo() {

  lazy val uiAndDomLogger: Logger = Logger.withNameAndPrefixes(Some("UserInterface"), PrintToStdLogger.printEverything)

  lazy val workbookElementLogger: Logger = Logger.withNameAndPrefixes(Some("WorkbookElementLogger"), PrintToStdLogger.printEverything)

  lazy val workbookControlLogger: Logger = Logger.withNameAndPrefixes(Some("WorkbookHomepageControl"), PrintToStdLogger.printEverything)

  lazy val contentStorageLogger: Logger = Logger.withNameAndPrefixes(Some("WorkbookContentStorage"), PrintToStdLogger.printEverything)

  lazy val syncControlLogger: SyncLogger = SyncLogger(PrintToStdLogger(BasicLogger(), PrintToStdLogger.printWarnAndError))

  lazy val syncCacheLogger: Logger = Logger.withNameAndPrefixes(Some("SyncCacheLogger"), PrintToStdLogger.printWarnAndError)

  lazy val fileDataStorageLogger: Logger = Logger.withNameAndPrefixes(Some("FileDataStorage"), PrintToStdLogger.printError)

}

object HomepageLoggerInfo {

  lazy val singleton = HomepageLoggerInfo()

}
