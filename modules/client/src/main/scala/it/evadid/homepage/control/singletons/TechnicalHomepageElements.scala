package it.evadid.homepage.control.singletons

import it.evadid.core.datastructures.file.{FileDescription, LoadedFile}
import it.evadid.core.datastructures.storage.AsyncDataCache
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.control.model.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlFullScreenContainerElement

import scala.concurrent.{ExecutionContext, Future}

case class TechnicalHomepageElements(
                                      fullScreenContainer: HtmlFullScreenContainerElement,
                                      backendServerExecutor: ExecutionClient,
                                      //   workerServerExecutor: ExecutionClient,
                                    ) extends TechnicalControl {


  def makeFullscreen(element: HtmlAppElement): Unit = {
    fullScreenContainer.setElementFullscreen(element.getDomElement())
  }

  override lazy val fileStore: AsyncDataCache[FileDescription, LoadedFile] = new AsyncDataCache[FileDescription, LoadedFile](HomepageLoggerInfo.singleton.fileDataStorageLogger) {
    def load(file: FileDescription)(using ec: ExecutionContext): Future[LoadedFile] = file.loadData()

    override protected def executeLoading(file: FileDescription)(ec: ExecutionContext): Future[LoadedFile] = file.loadData()

    override protected def formatInputForLogging(in: FileDescription): String = in.toString

    override protected def formatOutputForLogging(out: LoadedFile): String = out.toString
  }

}
