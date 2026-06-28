package it.evadid.homepage.control.singletons

import it.evadid.core.datastructures.file.{FileDescription, LoadedFile}
import it.evadid.core.datastructures.state.storage.AsyncDataCache
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.control.model.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlFullScreenContainerElement

case class TechnicalHomepageElements(
                                      fullScreenContainer: HtmlFullScreenContainerElement,
                                      fileStore: AsyncDataCache[FileDescription, LoadedFile],
                                      backendServerExecutor: ExecutionClient,
                                      //   workerServerExecutor: ExecutionClient,
                                    ) extends TechnicalControl {


  def makeFullscreen(element: HtmlAppElement): Unit = {
    fullScreenContainer.setElementFullscreen(element.getDomElement())
  }


}
