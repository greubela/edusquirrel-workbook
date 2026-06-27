package it.evadid.homepage.control

import it.evadid.homepage.control.*
import it.evadid.core.datastructures.file.{FileDescription, LoadedFile}
import it.evadid.core.datastructures.state.storage.AsyncDataCache
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.homepage.control.info.control.TechnicalControl
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlFullScreenContainerElement
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future, Promise}

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
