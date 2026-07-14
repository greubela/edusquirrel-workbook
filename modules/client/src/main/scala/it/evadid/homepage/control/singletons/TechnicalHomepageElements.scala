package it.evadid.homepage.control.singletons

import it.evadid.core.datastructures.file.{FileDescription, LoadedFile}
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.control.change.TechnicalControl
import it.evadid.homepage.control.info.HomepageLoggerInfo
import it.evadid.homepage.control.model.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.controlElements.HtmlWorkbookDomElement

import scala.concurrent.{ExecutionContext, Future}

case class TechnicalHomepageElements(

                                      backendServerExecutor: ExecutionClient,
                                      //   workerServerExecutor: ExecutionClient,
                                    ) extends TechnicalControl {



  override lazy val fileStore: AsyncDataCache[FileDescription, LoadedFile] = new AsyncDataCache[FileDescription, LoadedFile](HomepageLoggerInfo.singleton.fileDataStorageLogger) {
    def load(file: FileDescription)(using ec: ExecutionContext): Future[LoadedFile] = file.loadData()

    override protected def executeLoading(file: FileDescription)(ec: ExecutionContext): Future[LoadedFile] = file.loadData()

    override protected def formatInputForLogging(in: FileDescription): String = in.toString

    override protected def formatOutputForLogging(out: LoadedFile): String = out.toString
  }

}
